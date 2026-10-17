package com.petcare.app.util

import android.app.Application
import android.content.Context
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.R
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class LauncherIconTest {
    @Test
    fun `launcher icon still loads on Android versions without themed icons`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val icon = context.getDrawable(R.mipmap.ic_launcher)

        assertTrue(icon is AdaptiveIconDrawable)
        val adaptiveIcon = icon as AdaptiveIconDrawable
        assertNotNull(adaptiveIcon.background)
        assertNotNull(adaptiveIcon.foreground)
    }
}