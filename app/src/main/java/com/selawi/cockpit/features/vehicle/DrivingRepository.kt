package com.selawi.cockpit.features.vehicle

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.SystemClock
import com.selawi.cockpit.core.platform.hasLocationPermission
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Distance, moving time and speeds since the trip started. */
data class Trip(
    val distanceKm: Double = 0.0,
    val movingSeconds: Long = 0,
    val maxKmh: Int = 0,
    /** Times the car came to a halt (traffic lights included). */
    val stops: Int = 0,
) {
    val averageKmh: Int get() = if (movingSeconds < 30) 0 else (distanceKm / (movingSeconds / 3600.0)).roundToInt()
}

data class DrivingState(
    val speedKmh: Int = 0,
    val driving: Boolean = false,
    /** Direction of travel in degrees from north, or null before the car has moved. */
    val headingDeg: Float? = null,
    val trip: Trip = Trip(),
    val motion: MotionState = MotionState(),
    /** Sideways acceleration in m/s² from speed and the change in heading; positive turning right. */
    val lateral: Float = 0f,
    /** Which way the car is turning, as a stand-in for its indicators (which apps cannot read). */
    val signal: Turn = Turn.None,
)

enum class Turn { None, Left, Right }

/** Indicator blink: about 90 a minute, shared so the arrows and the car blink together. */
fun blinkOn(now: Long = System.currentTimeMillis()) = now % 667 < 370

/**
 * Speed, heading and trip from the car's GPS. Android gives apps no access to the car's own speed
 * signal, so this is the documented way to know how the car is moving. A new trip starts after the
 * car has been parked for two hours.
 */
class DrivingRepository(private val context: Context, scope: kotlinx.coroutines.CoroutineScope) {
    private val _state = MutableStateFlow(DrivingState())
    val state: StateFlow<DrivingState> = _state

    val motion = MotionDetector(context)

    init {
        // Braking and acceleration join the driving state as they happen, between GPS fixes too.
        scope.launch { motion.state.collect { m -> _state.value = _state.value.copy(motion = m) } }
    }

    private var listening = false
    private var slowSince = 0L
    private var lastFix: Location? = null
    private var lastFixAt = 0L
    private var lastMovingAt = 0L
    private var lateral = 0f
    private var signal = Turn.None
    private var movingMs = 0L
    private var movedSinceStop = false
    private var refHeading: Float? = null
    private var refHeadingAt = 0L
    private var turnRate = 0f
    private var signalUntil = 0L

    // All methods overridden: before Android 11 they have no default implementations.
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = update(location)
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    @SuppressLint("MissingPermission")
    fun start() {
        motion.start()
        if (listening || !context.hasLocationPermission()) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        listening = runCatching {
            // Ask for a fix every 0.2 s; the GPS sends as often as it can (many only once a second).
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 200L, 0f, listener)
        }.isSuccess
    }

    fun stop() {
        motion.stop()
        if (!listening) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.removeUpdates(listener)
        listening = false
        lastFix = null
        lateral = 0f
        signal = Turn.None
        _state.value = _state.value.copy(speedKmh = 0, driving = false, motion = MotionState(), lateral = 0f, signal = Turn.None)
    }

    private fun update(location: Location) {
        val now = SystemClock.elapsedRealtime()
        val mps = if (location.hasSpeed()) location.speed else 0f
        motion.onGps(mps, location.accuracy)
        val kmh = (mps * 3.6f).roundToInt()
        val current = _state.value

        // Start driving above 8 km/h; stop only after 5 s below 3 km/h, so traffic lights do not flicker it.
        val driving = when {
            kmh >= 8 -> { slowSince = 0; true }
            kmh < 3 && current.driving -> {
                if (slowSince == 0L) slowSince = now
                now - slowSince < 5000
            }
            else -> current.driving
        }

        var trip = current.trip
        if (lastMovingAt != 0L && now - lastMovingAt > NEW_TRIP_AFTER_MS) {
            trip = Trip()
            movingMs = 0L
        }
        val previous = lastFix
        if (previous != null && kmh >= 3 && location.accuracy < 40f) {
            val metres = previous.distanceTo(location)
            // Whole milliseconds, so fixes that arrive a little under a second apart still count.
            val elapsed = (now - lastFixAt).coerceIn(0L, 5_000L)
            // Ignore GPS jumps that would need an impossible speed.
            if (metres < 120f) {
                movingMs += elapsed
                trip = trip.copy(distanceKm = trip.distanceKm + metres / 1000.0, movingSeconds = movingMs / 1000)
            }
        }
        // A stop: the car slows below 3 km/h after having driven at 15 km/h or more since the last one.
        if (kmh >= 15) movedSinceStop = true
        if (kmh < 3 && movedSinceStop) {
            movedSinceStop = false
            trip = trip.copy(stops = trip.stops + 1)
        }
        if (kmh >= 3) lastMovingAt = now
        if (kmh > trip.maxKmh && location.accuracy < 30f) trip = trip.copy(maxKmh = kmh)
        lastFix = location
        lastFixAt = now

        val heading = if (location.hasBearing() && kmh >= 5) location.bearing else current.headingDeg
        // Turn rate over at least 0.9 s, however often the GPS reports, so small jitter between
        // quick fixes does not count as turning.
        val ref = refHeading
        if (heading == null || kmh < 5) {
            refHeading = null
            turnRate = 0f
        } else if (ref == null) {
            refHeading = heading
            refHeadingAt = now
        } else if (now - refHeadingAt >= 900) {
            val dt = (now - refHeadingAt) / 1000f
            turnRate = if (dt <= 3f) ((((heading - ref + 540f) % 360f) - 180f) / dt) else 0f
            refHeading = heading
            refHeadingAt = now
        }
        // Cornering force: speed times how fast the heading turns (v·ω), smoothed.
        val turn = mps * Math.toRadians(turnRate.toDouble()).toFloat()
        lateral = lateral * 0.5f + turn.coerceIn(-12f, 12f) * 0.5f
        // Turning at 7° a second or more counts as a corner; the "indicator" stays on a moment
        // after the corner, as a real one does until the wheel straightens.
        if (kmh >= 8 && kotlin.math.abs(turnRate) >= 7f) {
            signal = if (turnRate > 0) Turn.Right else Turn.Left
            signalUntil = now + 1_500
        } else if (now > signalUntil || !driving) {
            signal = Turn.None
        }
        val shownLateral = (lateral / 0.2f).roundToInt() * 0.2f
        _state.value = DrivingState(kmh, driving, heading, trip, _state.value.motion, shownLateral, signal)
    }

    private companion object {
        const val NEW_TRIP_AFTER_MS = 2 * 60 * 60 * 1000L
    }
}
