package com.petcare.app.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Copies picked images into app-private storage so they remain readable after
 * the temporary content-URI permission from the photo picker expires.
 *
 * Images are decoded and re-encoded instead of copied byte-for-byte. This
 * prevents a picked camera original from consuming unbounded private storage
 * and bounds the bitmap allocated for a maliciously large image.
 */
class ImageStorage(private val context: Context) {

    private val directory: File
        get() = File(context.filesDir, "images").apply { mkdirs() }

    /**
     * Imports [uri] into app-private storage.
     *
     * A null result is an import failure, not a valid "no photo" value. Every
     * caller that requested a photo must check it and abort its database write.
     */
    suspend fun persist(uri: Uri): String? {
        var importedPath: String? = null
        try {
            return withContext(Dispatchers.IO) {
                persistBlocking(uri) { importedPath = it }
            }
        } catch (cancelled: CancellationException) {
            // Cancellation can occur when returning from IO, after the file was
            // written but before the caller receives its path. It is not owned
            // by a database record yet, so remove it even in a cancelled scope.
            withContext(NonCancellable + Dispatchers.IO) {
                importedPath?.let { File(it).delete() }
                deleteCapture(context, uri)
            }
            throw cancelled
        }
    }

    private fun persistBlocking(uri: Uri, onImported: (String) -> Unit): String? {
        try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            // Android's bounds-only decoder is allowed to return null even when
            // it populated outWidth/outHeight.  Only a missing input stream is
            // an import failure here; do not use the decoder result as the
            // stream-null check.
            val boundsInput = context.contentResolver.openInputStream(uri) ?: return null
            boundsInput.use { input ->
                BitmapFactory.decodeStream(input, null, bounds)
            }

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val bitmapInput = context.contentResolver.openInputStream(uri) ?: return null
            val bitmap = bitmapInput.use { input ->
                BitmapFactory.decodeStream(input, null, options)
            } ?: return null

            var orientedBitmap: Bitmap? = null
            var target: File? = null
            var temporary: File? = null
            var keepTarget = false
            try {
                val orientation = runCatching {
                    val exifInput = context.contentResolver.openInputStream(uri)
                        ?: return@runCatching ExifInterface.ORIENTATION_NORMAL
                    exifInput.use { input ->
                        ExifInterface(input).getAttributeInt(
                            ExifInterface.TAG_ORIENTATION,
                            ExifInterface.ORIENTATION_NORMAL
                        )
                    }
                }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
                orientedBitmap = orient(bitmap, orientation)
                val targetFile = File(directory, "${UUID.randomUUID()}.jpg")
                val temporaryFile = File(directory, ".${targetFile.name}.tmp")
                target = targetFile
                temporary = temporaryFile
                val compressed = temporaryFile.outputStream().use { output ->
                    orientedBitmap!!.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
                }
                if (!compressed || !temporaryFile.renameTo(targetFile)) {
                    return null
                }
                val importedPath = targetFile.absolutePath
                onImported(importedPath)
                keepTarget = true
                return importedPath
            } finally {
                if (!keepTarget) target?.delete()
                temporary?.delete()
                if (orientedBitmap != null && orientedBitmap !== bitmap) orientedBitmap?.recycle()
                bitmap.recycle()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return null
        } finally {
            // Camera captures live in cache until they are imported. Never
            // leave one behind after an import failure either.
            deleteCapture(context, uri)
        }
    }

    suspend fun delete(path: String) = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(path).canonicalFile
            val managedRoot = File(context.filesDir, "images")
            if (file.isManagedBy(managedRoot) && file.exists()) file.delete()
        }
    }

    /** Discards a temporary camera capture owned by this storage instance. */
    fun discardCapture(uri: Uri?) = deleteCapture(context, uri)

    companion object {
        private const val JPEG_QUALITY = 90
        // Keep a decoded ARGB image <=16 MiB, leaving room for its rotated
        // copy and Compose on lower-memory phones.
        private const val MAX_IMAGE_DIMENSION = 2_048
        private const val MAX_IMAGE_PIXELS = 4_194_304L
        private const val CAPTURE_DIRECTORY = "image-captures"
        private const val FILE_PROVIDER_SUFFIX = ".fileprovider"

        /**
         * Creates a private cache destination for TakePicture. The application
         * must expose `cache-path` for [CAPTURE_DIRECTORY] through a
         * FileProvider; no camera or broad storage permission is required.
         */
        fun createCaptureUri(context: Context): Uri {
            val directory = File(context.cacheDir, CAPTURE_DIRECTORY).apply { mkdirs() }
            val file = File.createTempFile("capture-", ".jpg", directory)
            return try {
                FileProvider.getUriForFile(
                    context,
                    context.packageName + FILE_PROVIDER_SUFFIX,
                    file
                )
            } catch (error: Exception) {
                file.delete()
                throw IllegalStateException("Camera capture is not available.", error)
            }
        }

        /**
         * Deletes only a capture created by [createCaptureUri]. A picker URI
         * from another provider is intentionally ignored.
         */
        fun deleteCapture(context: Context, uri: Uri?) {
            if (uri == null || uri.authority != context.packageName + FILE_PROVIDER_SUFFIX) return
            val filename = uri.lastPathSegment?.takeIf {
                it.isNotBlank() && it != "." && it != ".." && '/' !in it && '\\' !in it
            } ?: return
            val captureRoot = File(context.cacheDir, CAPTURE_DIRECTORY)
            val file = File(captureRoot, filename)
            runCatching {
                if (file.canonicalFile.isManagedBy(captureRoot.canonicalFile)) file.delete()
            }
        }

        internal fun sampleSize(width: Int, height: Int): Int {
            var sample = 1
            while (
                (width.toLong() + sample - 1) / sample > MAX_IMAGE_DIMENSION ||
                (height.toLong() + sample - 1) / sample > MAX_IMAGE_DIMENSION ||
                ((width.toLong() + sample - 1) / sample) *
                    ((height.toLong() + sample - 1) / sample) > MAX_IMAGE_PIXELS
            ) {
                sample *= 2
            }
            return sample
        }

        private fun orient(bitmap: Bitmap, orientation: Int): Bitmap {
            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> {
                    matrix.setRotate(180f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_TRANSPOSE -> {
                    matrix.setRotate(90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
                ExifInterface.ORIENTATION_TRANSVERSE -> {
                    matrix.setRotate(-90f)
                    matrix.postScale(-1f, 1f)
                }
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(-90f)
                else -> return bitmap
            }
            return Bitmap.createBitmap(
                bitmap,
                0,
                0,
                bitmap.width,
                bitmap.height,
                matrix,
                true
            )
        }

    }
}

private fun File.isManagedBy(root: File): Boolean {
    val rootPath = root.canonicalPath
    val filePath = canonicalPath
    return filePath == rootPath || filePath.startsWith(rootPath + File.separator)
}
