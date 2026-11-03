package com.selawi.cockpit.core.platform

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat

fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** Most recent fix any provider has, or null. Uses the car's GPS, not Google services. */
@SuppressLint("MissingPermission")
fun Context.lastKnownLocation(): Location? {
    if (!hasLocationPermission()) return null
    val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
    return runCatching {
        lm.getProviders(true).mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
    }.getOrNull()
}
