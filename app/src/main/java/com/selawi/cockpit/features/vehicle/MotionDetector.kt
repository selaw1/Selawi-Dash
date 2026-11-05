package com.selawi.cockpit.features.vehicle

import android.content.Context
import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.roundToInt

enum class Motion { Steady, Accelerating, Braking, HardBraking }

/**
 * [level] runs 0…1: how strongly the car is braking or accelerating. [accel] is the forward
 * acceleration in m/s² (negative while slowing), for the G-force meter.
 */
data class MotionState(val motion: Motion = Motion.Steady, val level: Float = 0f, val accel: Float = 0f)

/**
 * Braking and acceleration from GPS speed. The car shares no pedal or brake-light signal with
 * apps, so this watches how fast the speed changes.
 *
 * The acceleration is the trend of the last few speed readings (a straight-line fit over about two
 * and a half seconds), which ignores the small jitter in each GPS reading. A state starts at one
 * level and only ends well below it, and braking never turns straight into accelerating (or back):
 * it passes through steady first. The cost is about a second of delay.
 * It shows motion, not the pedal: the car slowing by itself (regeneration, a hill) counts too.
 */
@Suppress("UNUSED_PARAMETER")
class MotionDetector(context: Context) {
    private val _state = MutableStateFlow(MotionState())
    val state: StateFlow<MotionState> = _state

    /** Shown in Settings. */
    val source: StateFlow<String> = MutableStateFlow("GPS speed (about a second behind)")

    private class Sample(val at: Long, val speed: Float)
    private val samples = ArrayDeque<Sample>()

    fun start() {}

    fun stop() {
        samples.clear()
        _state.value = MotionState()
    }

    /** Each GPS fix. */
    fun onGps(speedMps: Float, accuracy: Float) {
        val now = SystemClock.elapsedRealtime()
        if (accuracy > 30f) return
        // A jump no car can make is a GPS glitch (a tunnel, a bad fix): start the trend again.
        samples.lastOrNull()?.let { last ->
            val dt = (now - last.at) / 1000f
            if (dt > 0f && abs(speedMps - last.speed) / dt > 12f) samples.clear()
        }
        samples.addLast(Sample(now, speedMps))
        while (samples.isNotEmpty() && now - samples.first().at > WINDOW_MS) samples.removeFirst()
        val accel = trend() ?: return
        update(accel, speedMps * 3.6f)
    }

    /** Slope of speed over time (m/s²) by least squares, or null with too few readings. */
    private fun trend(): Float? {
        if (samples.size < 3) return null
        val t0 = samples.first().at
        if (samples.last().at - t0 < 1_500) return null
        var st = 0.0; var sv = 0.0; var stt = 0.0; var stv = 0.0
        for (s in samples) {
            val t = (s.at - t0) / 1000.0
            st += t; sv += s.speed; stt += t * t; stv += t * s.speed
        }
        val n = samples.size
        val d = n * stt - st * st
        if (d <= 0.0) return null
        return ((n * stv - st * sv) / d).toFloat()
    }

    private fun update(accel: Float, kmh: Float) {
        val current = _state.value.motion
        val braking = current == Motion.Braking || current == Motion.HardBraking
        val motion = when {
            // Stopped or creeping: nothing to show.
            kmh < 8f && !braking -> Motion.Steady
            braking -> when {
                accel > -BRAKE_END -> Motion.Steady
                accel <= -HARD_BRAKE -> Motion.HardBraking
                current == Motion.HardBraking && accel <= -HARD_BRAKE_END -> Motion.HardBraking
                else -> Motion.Braking
            }
            current == Motion.Accelerating -> if (accel < ACCELERATE_END) Motion.Steady else Motion.Accelerating
            accel <= -HARD_BRAKE -> Motion.HardBraking
            accel <= -BRAKE -> Motion.Braking
            accel >= ACCELERATE -> Motion.Accelerating
            else -> Motion.Steady
        }
        val level = when (motion) {
            Motion.Steady -> 0f
            Motion.Accelerating -> ((accel - ACCELERATE_END) / 3f).coerceIn(0.2f, 1f)
            else -> ((-accel - BRAKE_END) / (HARD_BRAKE * 1.5f - BRAKE_END)).coerceIn(0.25f, 1f)
        }
        // Rounded, so the screen only updates when the reading changes enough to see.
        val shown = if (kmh < 3f) 0f else (accel / 0.1f).roundToInt() * 0.1f
        _state.value = MotionState(motion, (level / 0.05f).roundToInt() * 0.05f, shown)
    }

    private companion object {
        const val WINDOW_MS = 2_600L
        /** Braking starts at about 5 km/h lost per second and ends below about 2. */
        const val BRAKE = 1.4f
        const val BRAKE_END = 0.6f
        /** Hard braking: about 13 km/h lost per second. */
        const val HARD_BRAKE = 3.6f
        const val HARD_BRAKE_END = 2.6f
        /** Accelerating starts at about 6 km/h gained per second and ends below about 3. */
        const val ACCELERATE = 1.7f
        const val ACCELERATE_END = 0.8f
    }
}
