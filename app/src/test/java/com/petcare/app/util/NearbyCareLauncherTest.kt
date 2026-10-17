package com.petcare.app.util

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class NearbyCareLauncherTest {
    @Test
    fun `map search intent encodes the care query`() {
        val intent = NearbyCareLauncher.mapIntent(NearbyCareType.PET_PHARMACIES)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals(
            "geo:0,0?q=pet%20pharmacies%20near%20me",
            intent.dataString
        )
    }

    @Test
    fun `browser fallback is an HTTPS search and encodes the care query`() {
        val intent = NearbyCareLauncher.browserIntent(NearbyCareType.ANIMAL_HOSPITALS)

        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertTrue(intent.dataString!!.startsWith("https://"))
        assertEquals(
            "https://www.google.com/search?q=animal%20hospitals%20near%20me",
            intent.dataString
        )
    }

    @Test
    fun `launcher tries browser after Maps rejects the geo intent`() {
        val context = RecordingContext(
            ApplicationProvider.getApplicationContext<Application>(),
            rejectGeo = true
        )

        assertTrue(
            NearbyCareLauncher.open(
                context,
                NearbyCareType.VETERINARIANS,
                onFailure = { message -> error("Unexpected launch failure: $message") }
            )
        )
        assertEquals(2, context.started.size)
        assertTrue(context.started[0].dataString!!.startsWith("geo:"))
        assertTrue(context.started[1].dataString!!.startsWith("https://"))
    }

    @Test
    fun `launcher reports failure when Maps and browser reject the launch`() {
        val context = RecordingContext(
            ApplicationProvider.getApplicationContext<Application>(),
            rejectGeo = true,
            rejectBrowser = true
        )
        var failure: String? = null

        val opened = NearbyCareLauncher.open(
            context,
            NearbyCareType.VETERINARIANS,
            onFailure = { failure = it }
        )

        assertEquals(false, opened)
        assertTrue(failure!!.contains("browser", ignoreCase = true))
    }

    private class RecordingContext(
        base: Context,
        private val rejectGeo: Boolean,
        private val rejectBrowser: Boolean = false
    ) : ContextWrapper(base) {
        val started = mutableListOf<Intent>()

        override fun startActivity(intent: Intent) {
            started += intent
            val isGeo = intent.data?.scheme == "geo"
            if ((isGeo && rejectGeo) || (!isGeo && rejectBrowser)) {
                throw ActivityNotFoundException("No handler")
            }
        }
    }
}