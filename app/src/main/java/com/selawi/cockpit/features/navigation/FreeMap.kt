package com.selawi.cockpit.features.navigation

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.selawi.cockpit.core.platform.hasLocationPermission
import com.selawi.cockpit.core.platform.lastKnownLocation
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point

private const val DAY_STYLE = "https://tiles.openfreemap.org/styles/liberty"
private const val NIGHT_STYLE = "https://tiles.openfreemap.org/styles/dark"
private val AMMAN = LatLng(31.9539, 35.9106)
private const val CAR_SOURCE = "cockpit-car"

/**
 * Free OpenStreetMap map with the car's position. Needs no Google services and no key.
 * Only [CarMap] calls this, so screenshot tests never load the native map engine.
 *
 * The position dot is drawn by the cockpit itself from Android's GPS, not by the map library's
 * location component, which closed the app on the car.
 */
@Composable
internal fun FreeMap(
    modifier: Modifier,
    interactive: Boolean,
    controller: MapController?,
    dark: Boolean,
) {
    val context = LocalContext.current
    val holder = remember { MapHolder(context, interactive, controller) }
    // Dark map at night, light by day, like Google Maps.
    LaunchedEffect(dark) { holder.setStyle(dark) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> holder.view.onStart()
                Lifecycle.Event.ON_RESUME -> {
                    holder.view.onResume()
                    holder.startLocation()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    holder.stopLocation()
                    holder.view.onPause()
                }
                Lifecycle.Event.ON_STOP -> holder.view.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            holder.stopLocation()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) holder.view.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) holder.view.onStop()
            holder.view.onDestroy()
        }
    }
    AndroidView(factory = { holder.view }, modifier = modifier)
}

private class MapHolder(
    private val context: Context,
    interactive: Boolean,
    controller: MapController?,
) {
    val view: MapView
    private var map: MapLibreMap? = null
    private var source: GeoJsonSource? = null
    private var last: LatLng? = context.lastKnownLocation()?.let { LatLng(it.latitude, it.longitude) }
    private var following = true
    private var listening = false
    private var styleUrl = DAY_STYLE

    fun setStyle(dark: Boolean) {
        val url = if (dark) NIGHT_STYLE else DAY_STYLE
        if (url == styleUrl && map?.style != null) return
        styleUrl = url
        applyStyle()
    }

    private fun applyStyle() {
        val m = map ?: return
        source = null
        m.setStyle(Style.Builder().fromUri(styleUrl)) { style -> addCarDot(style) }
    }

    // All methods overridden: before Android 11 they have no default implementations.
    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) = moveTo(LatLng(location.latitude, location.longitude))
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
        @Deprecated("Deprecated in Android")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    init {
        MapLibre.getInstance(context)
        view = MapView(context)
        view.onCreate(null)
        view.getMapAsync { m ->
            map = m
            m.cameraPosition = CameraPosition.Builder().target(last ?: AMMAN).zoom(if (interactive) 15.0 else 13.0).build()
            m.uiSettings.setAllGesturesEnabled(interactive)
            m.uiSettings.isLogoEnabled = false
            // Dragging the map stops following the car until the recentre button is pressed.
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE) following = false
            }
            applyStyle()
        }
        // If the night style cannot load, fall back to the day map rather than a blank panel.
        view.addOnDidFailLoadingMapListener {
            if (styleUrl != DAY_STYLE) {
                styleUrl = DAY_STYLE
                applyStyle()
            }
        }
        controller?.recenter = {
            following = true
            map?.animateCamera(CameraUpdateFactory.newLatLng(last ?: AMMAN))
        }
    }

    private fun addCarDot(style: Style) {
        runCatching {
            val start = last ?: return@runCatching
            val src = GeoJsonSource(CAR_SOURCE, Point.fromLngLat(start.longitude, start.latitude))
            style.addSource(src)
            style.addLayer(
                CircleLayer("$CAR_SOURCE-halo", CAR_SOURCE).withProperties(
                    PropertyFactory.circleRadius(18f),
                    PropertyFactory.circleColor("#3DDCFF"),
                    PropertyFactory.circleOpacity(0.25f),
                ),
            )
            style.addLayer(
                CircleLayer("$CAR_SOURCE-dot", CAR_SOURCE).withProperties(
                    PropertyFactory.circleRadius(8f),
                    PropertyFactory.circleColor("#3DDCFF"),
                    PropertyFactory.circleStrokeColor("#FFFFFF"),
                    PropertyFactory.circleStrokeWidth(3f),
                ),
            )
            source = src
        }
    }

    private fun moveTo(position: LatLng) {
        last = position
        val m = map ?: return
        runCatching {
            val src = source
            if (src == null) {
                m.style?.let { addCarDot(it) }
            } else {
                src.setGeoJson(Point.fromLngLat(position.longitude, position.latitude))
            }
            if (following) m.animateCamera(CameraUpdateFactory.newLatLng(position), 800)
        }
    }

    @SuppressLint("MissingPermission")
    fun startLocation() {
        if (listening || !context.hasLocationPermission()) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            runCatching { lm.requestLocationUpdates(provider, 1000L, 2f, listener) }
        }
        listening = true
    }

    fun stopLocation() {
        if (!listening) return
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        runCatching { lm.removeUpdates(listener) }
        listening = false
    }
}
