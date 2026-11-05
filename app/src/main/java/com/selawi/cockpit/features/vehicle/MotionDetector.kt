package com.selawi.cockpit.features.vehicle

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.sqrt

enum class Motion { Steady, Accelerating, Braking, HardBraking }

/** [level] runs 0…1: how strongly the car is braking or accelerating. */
data class MotionState(val motion: Motion = Motion.Steady, val level: Float = 0f)

/**
 * Braking and acceleration. The car shares no pedal or brake-light signal with apps, so this
 * watches how fast the speed changes.
 *
 * GPS gives a reading every second, so on its own it reacts about a second late. When the head
 * unit has a motion sensor, the detector learns which of the sensor's axes points along the car by
 * comparing it with GPS during the first stops and starts, then reacts as soon as the car slows.
 * It shows motion, not the pedal: the car slowing by itself (regeneration, a hill) counts too.
 */
class MotionDetector(context: Context) {
    private val _state = MutableStateFlow(MotionState())
    val state: StateFlow<MotionState> = _state

    private val _source = MutableStateFlow("GPS only")
    /** Shown in Settings: whether the motion sensor is in use yet. */
    val source: StateFlow<String> = _source

    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val sensor: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val removesGravity = sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION

    // Sensor reading, smoothed, with gravity taken out when the sensor does not do it itself.
    private val gravity = FloatArray(3)
    private val smooth = FloatArray(3)
    // Learned direction of the car's travel in the sensor's axes.
    private val forward = FloatArray(3)
    private var samples = 0
    private val calibrated get() = samples >= CALIBRATION_SAMPLES

    private var lastSpeed = -1f
    private var lastSpeedAt = 0L
    private var gpsAccel = 0f
    private var currentSpeedKmh = 0f
    private var motionSince = 0L
    private var listening = false

    private val listener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) = onSensor(event.values)
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    init {
        if (sensor == null) _source.value = "GPS only (no motion sensor on this car)"
    }

    fun start() {
        if (listening || sensor == null) return
        listening = sensors.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        if (listening && !calibrated) _source.value = "Learning the motion sensor (a few stops and starts)"
    }

    fun stop() {
        if (!listening) return
        sensors.unregisterListener(listener)
        listening = false
    }

    /** Each GPS fix: the reference the sensor learns from, and the fallback without a sensor. */
    fun onGps(speedMps: Float, accuracy: Float) {
        val now = SystemClock.elapsedRealtime()
        currentSpeedKmh = speedMps * 3.6f
        if (lastSpeed >= 0 && accuracy < 30f) {
            val dt = (now - lastSpeedAt) / 1000f
            if (dt in 0.4f..3f) {
                val a = (speedMps - lastSpeed) / dt
                // A change no car can make is a GPS glitch (a tunnel, a jump), not braking.
                if (abs(a) < 12f) {
                    gpsAccel = gpsAccel * 0.3f + a * 0.7f
                    learn(gpsAccel)
                    if (!calibrated) classify(gpsAccel, now)
                }
            }
        }
        lastSpeed = speedMps
        lastSpeedAt = now
    }

    private fun onSensor(v: FloatArray) {
        val a = FloatArray(3)
        for (i in 0..2) {
            if (removesGravity) {
                a[i] = v[i]
            } else {
                gravity[i] = gravity[i] * 0.95f + v[i] * 0.05f
                a[i] = v[i] - gravity[i]
            }
            smooth[i] = smooth[i] * 0.85f + a[i] * 0.15f
        }
        if (calibrated) {
            val f = smooth[0] * forward[0] + smooth[1] * forward[1] + smooth[2] * forward[2]
            classify(f, SystemClock.elapsedRealtime())
        }
    }

    /** During clear speed-ups and slow-downs, the sensor's reading points along (or against) travel. */
    private fun learn(accel: Float) {
        if (sensor == null || !listening || abs(accel) < 1.0f) return
        val sign = if (accel > 0) 1f else -1f
        for (i in 0..2) forward[i] += smooth[i] * sign
        val len = sqrt(forward[0] * forward[0] + forward[1] * forward[1] + forward[2] * forward[2])
        samples++
        if (calibrated && len > 0f) {
            for (i in 0..2) forward[i] /= len
            _source.value = "Motion sensor (instant)"
        }
    }

    private fun classify(accel: Float, now: Long) {
        val motion = when {
            currentSpeedKmh < 10f && accel < 0 -> Motion.Steady
            accel <= -HARD_BRAKE -> Motion.HardBraking
            accel <= -BRAKE -> Motion.Braking
            accel >= ACCELERATE && currentSpeedKmh > 5f -> Motion.Accelerating
            else -> Motion.Steady
        }
        val current = _state.value
        // Keep a state for at least half a second, so it never flickers.
        if (motion != current.motion && now - motionSince < 500 && current.motion != Motion.Steady) return
        if (motion != current.motion) motionSince = now
        val level = when (motion) {
            Motion.Steady -> 0f
            Motion.Accelerating -> ((accel - ACCELERATE) / 3f).coerceIn(0.2f, 1f)
            else -> ((-accel - BRAKE) / (HARD_BRAKE * 1.5f - BRAKE)).coerceIn(0.25f, 1f)
        }
        _state.value = MotionState(motion, level)
    }

    private companion object {
        /** About 5 km/h lost per second. */
        const val BRAKE = 1.4f
        /** About 15 km/h lost per second. */
        const val HARD_BRAKE = 4.2f
        /** About 6 km/h gained per second. */
        const val ACCELERATE = 1.7f
        const val CALIBRATION_SAMPLES = 12
    }
}
