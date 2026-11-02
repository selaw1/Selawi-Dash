package com.selawi.cockpit.probe.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.maps.CameraUpdateFactory
import com.selawi.cockpit.probe.BuildConfig
import com.selawi.cockpit.probe.tryStart
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import com.google.android.gms.maps.MapView as GoogleMapView
import com.google.android.gms.maps.model.LatLng as GoogleLatLng
import org.maplibre.android.geometry.LatLng as LibreLatLng
import org.maplibre.android.maps.MapView as LibreMapView

private const val TEST_LAT = 31.9539 // Amman Citadel, a fixed test destination.
private const val TEST_LNG = 35.9349
private const val GOOGLE_MAPS = "com.google.android.apps.maps"
private const val FREE_STYLE = "https://tiles.openfreemap.org/styles/liberty"

private enum class MapKind { None, Free, Google }

@Composable
fun MapsScreen() {
    val context = LocalContext.current
    var kind by rememberSaveable { mutableStateOf(MapKind.None) }
    val location = rememberLocation()

    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Column(
            Modifier
                .width(380.dp)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(Panel)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Maps", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
            Text("Map inside the app", color = Accent, fontSize = 18.sp)
            BigButton("Free map (MapLibre)") { kind = MapKind.Free }
            BigButton("Google map (Maps SDK)") { kind = MapKind.Google }
            Text("Hand off to a navigation app", color = Accent, fontSize = 18.sp)
            BigButton("Google Maps: navigate") {
                context.tryStart(
                    Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$TEST_LAT,$TEST_LNG")).setPackage(GOOGLE_MAPS),
                    "Google Maps is not installed",
                )
            }
            BigButton("Google Maps: split screen") {
                context.tryStart(
                    Intent(Intent.ACTION_VIEW, Uri.parse("geo:$TEST_LAT,$TEST_LNG?q=$TEST_LAT,$TEST_LNG"))
                        .setPackage(GOOGLE_MAPS)
                        .addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK),
                    "Google Maps is not installed",
                )
            }
            BigButton("Waze: navigate") {
                context.tryStart(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?ll=$TEST_LAT,$TEST_LNG&navigate=yes"))
                        .setPackage("com.waze"),
                    "Waze is not installed",
                )
            }
            BigButton("Any map app") {
                context.tryStart(
                    Intent.createChooser(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$TEST_LAT,$TEST_LNG?q=$TEST_LAT,$TEST_LNG")), "Open with"),
                    "No map app installed",
                )
            }
            Note("Test destination: Amman Citadel.")
            Text("Location", color = Accent, fontSize = 18.sp)
            Note(location)
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(20.dp))
                .background(Panel),
            contentAlignment = Alignment.Center,
        ) {
            when (kind) {
                MapKind.None -> Note("Pick a map on the left.")
                MapKind.Free -> FreeMap()
                MapKind.Google -> GoogleMapOrReason()
            }
        }
    }
}

/** Free OpenStreetMap-based map; needs no Google services and no key. */
@Composable
private fun FreeMap() {
    val context = LocalContext.current
    val mapView = remember {
        MapLibre.getInstance(context)
        LibreMapView(context).apply {
            onCreate(null)
            getMapAsync { map ->
                map.setStyle(FREE_STYLE)
                map.cameraPosition = CameraPosition.Builder()
                    .target(LibreLatLng(TEST_LAT, TEST_LNG))
                    .zoom(13.0)
                    .build()
            }
        }
    }
    BindLifecycle(
        onStart = mapView::onStart,
        onResume = mapView::onResume,
        onPause = mapView::onPause,
        onStop = mapView::onStop,
        destroy = mapView::onDestroy,
    )
    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

@Composable
private fun GoogleMapOrReason() {
    val context = LocalContext.current
    val play = remember { GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) }
    when {
        play != ConnectionResult.SUCCESS -> Note(
            "Google Play Services is not usable here (" +
                GoogleApiAvailability.getInstance().getErrorString(play) +
                "), so the Google Maps SDK cannot draw a map. Use the free map or split screen instead.",
        )
        !BuildConfig.HAS_MAPS_KEY -> Note(
            "Play Services is available, so a Google map can work here. This build has no Maps API key yet; " +
                "add one and rebuild to see the map.",
        )
        else -> GoogleMapView()
    }
}

@Composable
private fun GoogleMapView() {
    val context = LocalContext.current
    val mapView = remember {
        GoogleMapView(context).apply {
            onCreate(Bundle())
            getMapAsync { map ->
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(GoogleLatLng(TEST_LAT, TEST_LNG), 13f))
            }
        }
    }
    BindLifecycle(
        onStart = mapView::onStart,
        onResume = mapView::onResume,
        onPause = mapView::onPause,
        onStop = mapView::onStop,
        destroy = mapView::onDestroy,
    )
    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
}

/** Forwards the screen's lifecycle to a classic Android map view. */
@Composable
private fun BindLifecycle(
    onStart: () -> Unit,
    onResume: () -> Unit,
    onPause: () -> Unit,
    onStop: () -> Unit,
    destroy: () -> Unit,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> onStart()
                Lifecycle.Event.ON_RESUME -> onResume()
                Lifecycle.Event.ON_PAUSE -> onPause()
                Lifecycle.Event.ON_STOP -> onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) onStop()
            destroy()
        }
    }
}

/** Asks for location once and describes the latest fix from GPS or the network. */
@Composable
private fun rememberLocation(): String {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(hasLocation(context)) }
    var text by remember { mutableStateOf("Waiting for location…") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        granted = result.values.any { it }
        if (!granted) text = "Location permission denied"
    }
    DisposableEffect(granted) {
        if (!granted) {
            launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            onDispose { }
        } else {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            // All methods overridden: before Android 11 they have no default implementations.
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) { text = describe(location) }
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
                @Deprecated("Deprecated in Android")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            }
            startLocation(lm, listener) { text = it }
            onDispose { lm.removeUpdates(listener) }
        }
    }
    return text
}

@SuppressLint("MissingPermission")
private fun startLocation(lm: LocationManager, listener: LocationListener, report: (String) -> Unit) {
    val providers = lm.getProviders(true)
    if (providers.isEmpty()) {
        report("No location provider is enabled")
        return
    }
    providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time }
        ?.let { report(describe(it) + " (last known)") }
        ?: report("Providers: ${providers.joinToString()}. Waiting for a fix…")
    providers.filter { it != LocationManager.PASSIVE_PROVIDER }.forEach { p ->
        runCatching { lm.requestLocationUpdates(p, 2000L, 0f, listener) }
    }
}

private fun describe(loc: Location) =
    "%.5f, %.5f ±%.0f m via %s".format(loc.latitude, loc.longitude, loc.accuracy, loc.provider)

private fun hasLocation(context: Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
