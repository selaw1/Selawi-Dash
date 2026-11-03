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
import kotlin.math.roundToInt

/** Distance, moving time and speeds since the trip started. */
data class Trip(
    val distanceKm: Double = 0.0,
    val movingSeconds: Long = 0,
    val maxKmh: Int = 0,
) {
    val averageKmh: Int get() = if (movingSeconds < 30) 0 else (distanceKm / (movingSeconds / 3600.0)).roundToInt()
}

data class DrivingState(
    val speedKmh: Int = 0,
    val driving: Boolean = false,
    /** Direction of travel in degrees from north, or null before the car has moved. */
    val headingDeg: Float? = null,
    val trip: Trip = Trip(),
)

/**
 * Speed, heading and trip from the car's GPS. Android gives apps no access to the car's own speed
 * signal, so this is the documented way to know how the car is moving. A new trip starts after the
 * car has been parked for two hours.
 */
class DrivingRepository(private val context: Context) {
    private val _state = MutableStateFlow(DrivingState())
    val state: StateFlow<DrivingState> = _state

    private var listening = false
    private var slowSince = 0L
    private var lastFix: Location? = null
    private var lastFixAt = 0L
    private var lastMovingAt = 0L

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
        if (listening || !context.hasLocationPermission()) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        listening = runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener)
        }.isSuccess
    }

    fun stop() {
        if (!listening) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.removeUpdates(listener)
        listening = false
        lastFix = null
        _state.value = _state.value.copy(speedKmh = 0, driving = false)
    }

    private fun update(location: Location) {
        val now = SystemClock.elapsedRealtime()
        val mps = if (location.hasSpeed()) location.speed else 0f
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
        if (lastMovingAt != 0L && now - lastMovingAt > NEW_TRIP_AFTER_MS) trip = Trip()
        val previous = lastFix
        if (previous != null && kmh >= 3 && location.accuracy < 40f) {
            val metres = previous.distanceTo(location)
            val seconds = ((now - lastFixAt) / 1000).coerceIn(0, 5)
            // Ignore GPS jumps that would need an impossible speed.
            if (metres < 120f) {
                trip = trip.copy(distanceKm = trip.distanceKm + metres / 1000.0, movingSeconds = trip.movingSeconds + seconds)
            }
        }
        if (kmh >= 3) lastMovingAt = now
        if (kmh > trip.maxKmh && location.accuracy < 30f) trip = trip.copy(maxKmh = kmh)
        lastFix = location
        lastFixAt = now

        val heading = if (location.hasBearing() && kmh >= 5) location.bearing else current.headingDeg
        _state.value = DrivingState(kmh, driving, heading, trip)
    }

    private companion object {
        const val NEW_TRIP_AFTER_MS = 2 * 60 * 60 * 1000L
    }
}
