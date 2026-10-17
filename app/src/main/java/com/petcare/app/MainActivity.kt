package com.petcare.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.petcare.app.model.ThemeMode
import com.petcare.app.navigation.PetCareNavHost
import com.petcare.app.navigation.Routes
import com.petcare.app.session.SessionState
import com.petcare.app.session.SessionViewModel
import com.petcare.app.ui.theme.PetCareTheme
import com.petcare.app.util.invalidateLocalDateChanges

class MainActivity : ComponentActivity() {

    private var calendarReceiverRegistered = false

    private val sessionViewModel: SessionViewModel by viewModels {
        viewModelFactory {
            initializer {
                val container = (application as PetCareApp).container
                SessionViewModel(container.authRepository, container::ensureSeeded)
            }
        }
    }

    private val calendarReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action in calendarActions) invalidateLocalDateChanges()
        }
    }

    override fun onStart() {
        super.onStart()
        ContextCompat.registerReceiver(
            this,
            calendarReceiver,
            IntentFilter().apply { calendarActions.forEach(::addAction) },
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
        calendarReceiverRegistered = true
        (application as PetCareApp).reminderCoordinator.refresh()
    }

    override fun onResume() {
        super.onResume()
        // A short pause can leave a WhileSubscribed upstream alive. Force it
        // to recalculate instead of trusting its pre-resume midnight delay.
        invalidateLocalDateChanges()
    }

    override fun onStop() {
        if (calendarReceiverRegistered) {
            unregisterReceiver(calendarReceiver)
            calendarReceiverRegistered = false
        }
        super.onStop()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // The system splash stays up until the saved session is known, so the
        // first in-app frame is already Home or Welcome — no second splash.
        val splash = installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { sessionViewModel.state.value == null }

        val preferences = (application as PetCareApp).container.preferences

        setContent {
            val themeMode by preferences.themeMode.collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)
            val session by sessionViewModel.state.collectAsStateWithLifecycle()
            PetCareTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val startDestination = when (session) {
                        SessionState.SIGNED_IN -> Routes.HOME
                        SessionState.SIGNED_OUT -> Routes.WELCOME
                        null -> null
                    }
                    if (startDestination != null) {
                        // Start invisible and animate in: a Boolean `visible = true`
                        // on first composition would skip the fade entirely.
                        val appear = remember { MutableTransitionState(false).apply { targetState = true } }
                        AnimatedVisibility(visibleState = appear, enter = fadeIn(tween(FIRST_FRAME_FADE_MILLIS))) {
                            PetCareNavHost(startDestination = startDestination)
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val FIRST_FRAME_FADE_MILLIS = 200
        val calendarActions = setOf(
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED
        )
    }
}
