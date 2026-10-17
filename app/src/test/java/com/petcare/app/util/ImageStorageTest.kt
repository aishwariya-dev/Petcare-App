package com.petcare.app.util

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.model.PetPhotoPaths
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowBitmapFactory
import org.robolectric.Shadows.shadowOf
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ImageStorageTest {

    @Before
    fun requireDecodableImages() {
        ShadowBitmapFactory.setAllowInvalidImageData(false)
    }

    private val context: Application
        get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `cancellation after decoding does not leave an unowned imported file`() = runBlocking {
        val bitmap = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { output ->
            try {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            } finally {
                bitmap.recycle()
            }
        }
        val job = Job()
        var closed = 0
        val uri = Uri.parse("content://image-test/cancel-after-decode")
        val input = object : ByteArrayInputStream(bytes) {
            override fun close() {
                pos = 0
                mark = 0
                closed++
                // Bounds, pixels, then EXIF: the blocking IO work can finish
                // writing after cancellation, but its path must not be leaked.
                if (closed == 3) job.cancel()
            }
        }
        shadowOf(context.contentResolver).registerInputStream(uri, input)
        val imageDir = File(context.filesDir, "images")
        val before = imageDir.listFiles()?.map { it.name }?.toSet().orEmpty()
        val attempt = async(job) { ImageStorage(context).persist(uri) }
        attempt.join()
        assertEquals(3, closed)
        assertTrue(attempt.isCancelled)
        assertEquals(before, imageDir.listFiles()?.map { it.name }?.toSet().orEmpty())
    }

    @Test
    fun `PNG import is decoded and copied to managed image storage`() = runBlocking<Unit> {
        val source = File(context.cacheDir, "import-${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(3, 2, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }

        val imported = ImageStorage(context).persist(Uri.fromFile(source))

        assertNotNull(imported)
        assertTrue(imported!!.startsWith(File(context.filesDir, "images").path))
        assertNotNull(BitmapFactory.decodeFile(imported))
        File(imported).delete()
        source.delete()
    }

    @Test
    fun `invalid import returns null and does not remove another managed file`() = runBlocking<Unit> {
        val source = File(context.cacheDir, "invalid-${System.nanoTime()}.png")
            .apply { writeText("not an image") }
        val existing = File(context.filesDir, "images/keep-${System.nanoTime()}.jpg")
            .apply {
                parentFile?.mkdirs()
                writeText("keep")
            }

        val imported = ImageStorage(context).persist(Uri.fromFile(source))

        assertNull(imported)
        assertTrue(existing.exists())
        existing.delete()
        source.delete()
    }

    @Test
    fun `delete rejects files outside managed image directory`() = runBlocking<Unit> {
        val outside = File(context.cacheDir, "outside-${System.nanoTime()}.jpg")
            .apply { writeText("do not delete") }

        ImageStorage(context).delete(outside.absolutePath)

        assertTrue(outside.exists())
        outside.delete()
    }

    @Test
    fun `EXIF rotation is applied while importing`() = runBlocking<Unit> {
        val source = File(context.cacheDir, "rotated-${System.nanoTime()}.jpg")
        val bitmap = Bitmap.createBitmap(8, 4, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
        ExifInterface(source.absolutePath).apply {
            setAttribute(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_ROTATE_90.toString()
            )
            saveAttributes()
        }

        val imported = ImageStorage(context).persist(Uri.fromFile(source))
        val decoded = imported?.let { BitmapFactory.decodeFile(it) }

        assertNotNull(decoded)
        assertEquals(4, decoded!!.width)
        assertEquals(8, decoded.height)
        imported?.let { File(it).delete() }
        source.delete()
    }

    @Test
    fun `gallery JSON round trip removes duplicates and blanks`() {
        val paths = listOf("/data/a.jpg", "", "/data/b.jpg", "/data/a.jpg")

        val encoded = PetPhotoPaths.encode(paths)

        assertEquals(listOf("/data/a.jpg", "/data/b.jpg"), PetPhotoPaths.decode(encoded))
        assertEquals(emptyList<String>(), PetPhotoPaths.decode("not-json"))
    }

    @Test
    fun `camera URI is FileProvider-backed and cancel cleanup removes only capture`() {
        val uri = ImageStorage.createCaptureUri(context)
        val capture = File(context.cacheDir, "image-captures/${uri.lastPathSegment}")

        assertEquals("${context.packageName}.fileprovider", uri.authority)
        assertTrue(capture.exists())

        ImageStorage.deleteCapture(context, uri)

        assertFalse(capture.exists())
    }
}