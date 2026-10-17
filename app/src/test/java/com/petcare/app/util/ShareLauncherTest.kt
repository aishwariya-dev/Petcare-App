package com.petcare.app.util

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ShareLauncherTest {
    @Test
    fun `share intent wraps a plain-text send in a chooser with the exact text`() {
        val chooser = ShareLauncher.buildIntent("PetCare: Milo (Beagle)\nFeeding – Breakfast", "Share task")

        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        assertEquals("Share task", chooser.getStringExtra(Intent.EXTRA_TITLE))
        @Suppress("DEPRECATION")
        val target = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(target)
        assertEquals(Intent.ACTION_SEND, target!!.action)
        assertEquals("text/plain", target.type)
        assertEquals("PetCare: Milo (Beagle)\nFeeding – Breakfast", target.getStringExtra(Intent.EXTRA_TEXT))
    }
}
