package com.riderai.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.riderai.safety.CrashDetector
import kotlin.math.sqrt

data class MotionSample(
    val leanDeg: Double,
    val gForce: Double,
    val ax: Float,
    val ay: Float,
    val az: Float,
    val timestampMs: Long,
)

/**
 * Lean angle (from the gravity sensor) and g-force (from the accelerometer).
 * Assumes the phone is mounted upright (portrait) on the bars; a calibration offset corrects
 * small mounting tilts.
 */
class MotionTracker(context: Context, private val onSample: (MotionSample) -> Unit) :
    SensorEventListener {

    private val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gravity = sm.getDefaultSensor(Sensor.TYPE_GRAVITY)

    var leanOffsetDeg = 0.0
    private var rawLean = 0.0
    private var gx = 0f
    private var gy = SensorManager.GRAVITY_EARTH

    val currentRawLean: Double get() = rawLean

    fun start() {
        accel?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        gravity?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    fun stop() = sm.unregisterListener(this)

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                gx = e.values[0]; gy = e.values[1]
                rawLean = CrashDetector.leanFromGravity(gx, gy)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                val (x, y, z) = Triple(e.values[0], e.values[1], e.values[2])
                if (gravity == null) {
                    // Low-pass the accelerometer as a gravity estimate on devices without one.
                    gx = 0.9f * gx + 0.1f * x
                    gy = 0.9f * gy + 0.1f * y
                    rawLean = CrashDetector.leanFromGravity(gx, gy)
                }
                val g = sqrt((x * x + y * y + z * z).toDouble()) / SensorManager.GRAVITY_EARTH
                onSample(MotionSample(rawLean - leanOffsetDeg, g, x, y, z, System.currentTimeMillis()))
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
