package com.signoff.mobile.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.sqrt

/**
 * Detects phone motion using LINEAR_ACCELERATION sensor (gravity removed).
 * Reports whether the phone is actively being moved/carried.
 */
class MotionDetector(context: Context) : SensorEventListener {

    companion object {
        private const val TAG = "MotionDetector"
        private const val SMOOTHING_FACTOR = 0.3f  // Exponential moving average
        private const val MOTION_THRESHOLD = 2.0f   // m/s² to consider "moving"
        private const val DEBOUNCE_MS = 1000L       // Must sustain motion for 1 second
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val linearAccelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
    private val accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    // State
    private var smoothedMagnitude = 0f
    private var motionStartTime = 0L
    var isMoving = false
        private set
    var currentAcceleration = 0f
        private set

    // Callback
    var onMotionChanged: ((moving: Boolean, acceleration: Float) -> Unit)? = null

    fun start() {
        // Prefer LINEAR_ACCELERATION (gravity removed), fallback to ACCELEROMETER
        val sensor = linearAccelSensor ?: accelerometerSensor
        if (sensor != null) {
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
            Log.d(TAG, "Motion detector started with sensor: ${sensor.name}")
        } else {
            Log.e(TAG, "No acceleration sensor available!")
        }
    }

    fun stop() {
        sensorManager.unregisterListener(this)
        Log.d(TAG, "Motion detector stopped")
    }

    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]

        // Calculate magnitude
        var magnitude = sqrt(x * x + y * y + z * z)

        // If using raw accelerometer, subtract gravity (~9.81)
        if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            magnitude = (magnitude - SensorManager.GRAVITY_EARTH).coerceAtLeast(0f)
        }

        // Smooth with exponential moving average
        smoothedMagnitude = SMOOTHING_FACTOR * magnitude + (1 - SMOOTHING_FACTOR) * smoothedMagnitude
        currentAcceleration = smoothedMagnitude

        val now = System.currentTimeMillis()

        if (smoothedMagnitude > MOTION_THRESHOLD) {
            if (motionStartTime == 0L) {
                motionStartTime = now
            }
            // Debounce: must sustain motion for DEBOUNCE_MS
            if (!isMoving && (now - motionStartTime) >= DEBOUNCE_MS) {
                isMoving = true
                onMotionChanged?.invoke(true, smoothedMagnitude)
                Log.d(TAG, "📱 Motion DETECTED — accel: ${smoothedMagnitude} m/s²")
            }
        } else {
            motionStartTime = 0L
            if (isMoving) {
                isMoving = false
                onMotionChanged?.invoke(false, smoothedMagnitude)
                Log.d(TAG, "📱 Motion STOPPED — accel: ${smoothedMagnitude} m/s²")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not needed
    }
}
