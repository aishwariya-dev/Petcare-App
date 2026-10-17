package com.petcare.app.util

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.sqrt

/**
 * Accelerometer-based shake detection.
 *
 * Deliberately conservative: a shake requires two strong movements
 * (> 2.7 g) within half a second, and fires at most once every 1.5 seconds,
 * so ordinary handling of the phone does not trigger it.
 */
class ShakeDetector(private val onShake: () -> Unit) : SensorEventListener {

    private var lastPeakAt = 0L
    private var lastTriggerAt = 0L
    private var peakCount = 0

    override fun onSensorChanged(event: SensorEvent) {
        val gX = event.values[0] / SensorManager.GRAVITY_EARTH
        val gY = event.values[1] / SensorManager.GRAVITY_EARTH
        val gZ = event.values[2] / SensorManager.GRAVITY_EARTH
        val gForce = sqrt(gX * gX + gY * gY + gZ * gZ)
        if (gForce < SHAKE_THRESHOLD_G) return

        val now = System.currentTimeMillis()
        if (now - lastPeakAt < PEAK_DEBOUNCE_MS) return
        peakCount = if (now - lastPeakAt > PEAK_WINDOW_MS) 1 else peakCount + 1
        lastPeakAt = now

        if (peakCount >= REQUIRED_PEAKS && now - lastTriggerAt > TRIGGER_COOLDOWN_MS) {
            lastTriggerAt = now
            peakCount = 0
            onShake()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    companion object {
        const val SHAKE_THRESHOLD_G = 2.7f
        const val PEAK_DEBOUNCE_MS = 80L
        const val PEAK_WINDOW_MS = 500L
        const val REQUIRED_PEAKS = 2
        const val TRIGGER_COOLDOWN_MS = 1500L
    }
}

/**
 * Registers a [ShakeDetector] while the host lifecycle is resumed and
 * [enabled] is true. Unregisters automatically on pause or disposal.
 */
@Composable
fun ShakeListener(enabled: Boolean = true, onShake: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnShake by rememberUpdatedState(onShake)

    DisposableEffect(enabled, lifecycleOwner) {
        if (!enabled) return@DisposableEffect onDispose { }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val detector = ShakeDetector { currentOnShake() }

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> accelerometer?.let {
                    sensorManager.registerListener(detector, it, SensorManager.SENSOR_DELAY_UI)
                }
                Lifecycle.Event.ON_PAUSE -> sensorManager.unregisterListener(detector)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            sensorManager.unregisterListener(detector)
        }
    }
}
