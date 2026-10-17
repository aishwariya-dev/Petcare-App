package com.petcare.app.util

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SmsLauncherTest {
    @Test
    fun `build intent uses send-to SMS action and preserves reviewed body`() {
        val intent = SmsLauncher.buildIntent(" +44 7700 900000 ", "Care plan\nFeed Max")

        assertEquals(Intent.ACTION_SENDTO, intent.action)
        assertEquals("smsto:+44 7700 900000", intent.dataString)
        assertEquals("Care plan\nFeed Max", intent.getStringExtra("sms_body"))
    }
}