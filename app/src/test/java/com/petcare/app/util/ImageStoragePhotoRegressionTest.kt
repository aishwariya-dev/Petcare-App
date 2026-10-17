package com.petcare.app.util

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowBitmapFactory
import kotlinx.coroutines.runBlocking

/**
 * Regression coverage for Android content-URI imports. In particular, the
 * bounds-only decoder may return null while still filling outWidth/outHeight.
 */
@RunWith(RobolectricTestRunner::class)
@Config(
    sdk = [28],
    application = Application::class,
    shadows = [BoundsNullBitmapFactoryShadow::class]
)
class ImageStoragePhotoRegressionTest {
    private val importedPaths = mutableListOf<String>()

    private val context: Application
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun requireStrictImageDecoding() {
        ShadowBitmapFactory.setAllowInvalidImageData(false)
    }

    @After
    fun removeImportedImages() {
        importedPaths.forEach { File(it).delete() }
    }

    @Test
    fun `valid PNG content URI survives bounds-only decode and is imported`() = runBlocking {
        val uri = Uri.parse("content://photo-regression/valid-png")
        registerPng(uri, width = 7, height = 4)

        val imported = ImageStorage(context).persist(uri) ?: error("PNG import failed")
        importedPaths += imported
        assertTrue(File(imported).isFile)
    }

    @Test
    fun `bounds decoder contract permits null with valid dimensions`() {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val result = BitmapFactory.decodeStream(
            ByteArrayInputStream(pngBytes(width = 7, height = 4)),
            null,
            options
        )

        // This is an explicit Android contract shadow, not an assertion about
        // Robolectric's legacy synthetic Bitmap return value.
        assertNull(result)
        assertEquals(7, options.outWidth)
        assertEquals(4, options.outHeight)
    }

    private fun registerPng(uri: Uri, width: Int, height: Int) {
        val bytes = pngBytes(width, height)
        val input = object : ByteArrayInputStream(bytes) {
            override fun close() {
                super.close()
                reset()
            }
        }
        shadowOf(context.contentResolver).registerInputStream(uri, input)
    }

    private fun pngBytes(width: Int, height: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        return try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }
}

/**
 * Android may report image bounds while returning null from a bounds-only
 * decode. Robolectric's legacy BitmapFactory shadow normally returns a
 * synthetic Bitmap instead, so make that documented contract deterministic
 * for this regression without weakening production image validation.
 */
@Implements(BitmapFactory::class)
class BoundsNullBitmapFactoryShadow {
    companion object {
        private val ORIGINAL_DECODE = ShadowBitmapFactory::class.java.getDeclaredMethod(
            "decodeStream",
            InputStream::class.java,
            Rect::class.java,
            BitmapFactory.Options::class.java
        ).apply { isAccessible = true }

        @Implementation
        @JvmStatic
        fun decodeStream(
            input: InputStream?,
            padding: Rect?,
            options: BitmapFactory.Options?
        ): Bitmap? {
            val decoded = ORIGINAL_DECODE.invoke(null, input, padding, options) as Bitmap?
            if (options?.inJustDecodeBounds == true) {
                decoded?.recycle()
                return null
            }
            return decoded
        }
    }
}