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

data class DrivingState(val speedKmh: Int = 0, val driving: Boolean = false)

/**
 * Speed from the car's GPS. Android gives apps no access to the car's own speed signal, so this is
 * the documented way to know whether the car is moving.
 */
class DrivingRepository(private val context: Context) {
    private val _state = MutableStateFlow(DrivingState())
    val state: StateFlow<DrivingState> = _state

    private var listening = false
    private var slowSince = 0L

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
        _state.value = DrivingState()
    }

    private fun update(location: Location) {
        val mps = if (location.hasSpeed()) location.speed else 0f
        val kmh = (mps * 3.6f).roundToInt()
        val now = SystemClock.elapsedRealtime()
        // Start driving above 8 km/h; stop only after 5 s below 3 km/h, so traffic lights do not flicker it.
        val driving = when {
            kmh >= 8 -> { slowSince = 0; true }
            kmh < 3 && _state.value.driving -> {
                if (slowSince == 0L) slowSince = now
                now - slowSince < 5000
            }
            else -> _state.value.driving
        }
        _state.value = DrivingState(kmh, driving)
    }
}
