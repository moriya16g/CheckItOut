package com.example.checkitout.util.context

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.sqrt

/**
 * Sensor-based activity proxy.
 *
 * - reads current step counter value (cumulative since boot)
 * - samples accelerometer for ~700ms and computes mean magnitude after subtracting gravity
 * - maps magnitude to a coarse activity label
 *
 * No Google Play Services dependency. ACTIVITY_RECOGNITION permission is required
 * on Android 10+ for the step counter; the accelerometer-only path still works
 * without it.
 */
object SensorContextCollector {

    data class SensorSnapshot(
        val activity: String,
        val stepCount: Int?,
        val accelMagnitude: Float?,
    )

    private const val SAMPLE_DURATION_MS = 700L

    suspend fun collect(context: Context): SensorSnapshot {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
            ?: return SensorSnapshot("UNKNOWN", null, null)

        val stepCount = readStepCounter(context, sm)
        val magnitude = sampleAccelMagnitude(sm)
        val activity = classify(magnitude)
        return SensorSnapshot(activity, stepCount, magnitude)
    }

    private suspend fun readStepCounter(context: Context, sm: SensorManager): Int? {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return null
        val sensor = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) ?: return null
        return suspendCancellableCoroutine { cont ->
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val v = event?.values?.firstOrNull()?.toInt()
                    sm.unregisterListener(this)
                    if (cont.isActive) cont.resume(v)
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            val ok = sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_FASTEST)
            if (!ok) {
                if (cont.isActive) cont.resume(null)
            }
            cont.invokeOnCancellation { sm.unregisterListener(listener) }
        }
    }

    private suspend fun sampleAccelMagnitude(sm: SensorManager): Float? {
        val sensor = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
            ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            ?: return null
        return suspendCancellableCoroutine { cont ->
            val samples = mutableListOf<Float>()
            val isLinear = sensor.type == Sensor.TYPE_LINEAR_ACCELERATION
            val deadline = System.currentTimeMillis() + SAMPLE_DURATION_MS
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent?) {
                    val v = event?.values ?: return
                    val x = v.getOrNull(0) ?: 0f
                    val y = v.getOrNull(1) ?: 0f
                    val z = v.getOrNull(2) ?: 0f
                    val mag = sqrt(x * x + y * y + z * z)
                    val net = if (isLinear) mag else (mag - 9.81f)
                    samples += kotlin.math.abs(net)
                    if (System.currentTimeMillis() >= deadline) {
                        sm.unregisterListener(this)
                        if (cont.isActive) {
                            cont.resume(if (samples.isEmpty()) null else samples.average().toFloat())
                        }
                    }
                }
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
            }
            val ok = sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
            if (!ok) {
                if (cont.isActive) cont.resume(null)
            }
            cont.invokeOnCancellation { sm.unregisterListener(listener) }
        }
    }

    private fun classify(magnitude: Float?): String = when {
        magnitude == null -> "UNKNOWN"
        magnitude < 0.3f -> "STILL"
        magnitude < 1.5f -> "WALKING"
        magnitude < 4.0f -> "RUNNING"
        else -> "VEHICLE" // strong/erratic motion (also covers running on rough terrain)
    }
}
