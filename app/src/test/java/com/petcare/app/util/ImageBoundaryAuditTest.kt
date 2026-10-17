package com.petcare.app.util

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowBitmapFactory
import org.robolectric.shadows.ShadowSensor
import java.io.File
import kotlin.math.sqrt
import kotlinx.coroutines.runBlocking

/**
 * Image picker/camera ownership and other small Android utility boundaries.
 * No camera, SMS application, or physical sensor is started by these tests.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class ImageBoundaryAuditTest {
    private val context: Application
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun requireDecodableImages() {
        ShadowBitmapFactory.setAllowInvalidImageData(false)
        clearFileProviderCache()
    }

    @After
    fun restoreFileProviderCache() {
        clearFileProviderCache()
    }

    @Test
    fun `persist re-encodes an oversized image as bounded JPEG`() = runBlocking<Unit> {
        val source = File(context.cacheDir, "boundary-oversized-${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(2_049, 2_048, Bitmap.Config.ARGB_8888)
        try {
            source.outputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }

        val imported = ImageStorage(context).persist(Uri.fromFile(source))

        assertNotNull(imported)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(imported, bounds)
        assertTrue(bounds.outWidth <= 2_048)
        assertTrue(bounds.outHeight <= 2_048)
        val importedFile = File(imported!!)
        assertTrue(importedFile.length() > 0L)
        val jpegHeader = ByteArray(3)
        importedFile.inputStream().use { input ->
            assertEquals(3, input.read(jpegHeader))
        }
        assertTrue(
            jpegHeader.contentEquals(
                byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
            )
        )

        importedFile.delete()
        source.delete()
    }

    @Test
    fun `missing content URI returns null without creating managed output`() = runBlocking<Unit> {
        val imageDirectory = File(context.filesDir, "images").apply { mkdirs() }
        val before = imageDirectory.listFiles()?.map { it.name }?.toSet().orEmpty()

        val imported = ImageStorage(context).persist(
            Uri.parse("content://unregistered-image-provider/missing")
        )

        assertEquals(null, imported)
        assertEquals(
            before,
            imageDirectory.listFiles()?.map { it.name }?.toSet().orEmpty()
        )
    }

    @Test
    fun `capture deletion only accepts the app FileProvider authority`() {
        val ownedUri = ImageStorage.createCaptureUri(context)
        val ownedFile = File(context.cacheDir, "image-captures/${ownedUri.lastPathSegment}")
        val foreignUri = Uri.parse("content://another-provider/${ownedUri.lastPathSegment}")

        ImageStorage.deleteCapture(context, foreignUri)

        assertTrue(ownedFile.exists())
        ImageStorage.deleteCapture(
            context,
            Uri.parse("content://${context.packageName}.fileprovider/..")
        )
        assertTrue(ownedFile.exists())

        ImageStorage.deleteCapture(context, ownedUri)

        assertFalse(ownedFile.exists())
    }

    @Test
    fun `SMS opening reports unsupported or invalid launch without sending anything`() {
        val body = "Reviewed care plan"

        assertFalse(SmsLauncher.open(context, "not a phone number", body))
        assertFalse(SmsLauncher.open(context, "", body))
        assertEquals("smsto:", SmsLauncher.buildIntent("   ", body).dataString)
    }

    @Test
    fun `shake detector uses vector magnitude and needs two peaks`() {
        var shakes = 0
        val detector = ShakeDetector { shakes++ }
        val sensor = ShadowSensor.newInstance(Sensor.TYPE_ACCELEROMETER)
        val vectorComponent = SensorManager.GRAVITY_EARTH * 3f / sqrt(3f)
        val strongEvent = SensorEventBuilder.newBuilder()
            .setValues(floatArrayOf(vectorComponent, vectorComponent, vectorComponent))
            .setSensor(sensor)
            .build()
        val ordinaryEvent = SensorEventBuilder.newBuilder()
            .setValues(floatArrayOf(0f, 0f, SensorManager.GRAVITY_EARTH))
            .setSensor(sensor)
            .build()

        detector.onSensorChanged(ordinaryEvent)
        detector.onSensorChanged(strongEvent)
        Thread.sleep(90L)
        detector.onSensorChanged(strongEvent)

        assertEquals(1, shakes)
    }

    private fun clearFileProviderCache() {
        val cache = FileProvider::class.java
            .getDeclaredField("sCache")
            .apply { isAccessible = true }
            .get(null) as MutableMap<*, *>
        cache.clear()
    }
}