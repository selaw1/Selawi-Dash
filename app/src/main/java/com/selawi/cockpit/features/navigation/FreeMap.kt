package com.selawi.cockpit.features.navigation

import android.annotation.SuppressLint
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.selawi.cockpit.core.design.CockpitCard
import com.selawi.cockpit.core.design.CockpitColors
import com.selawi.cockpit.core.design.CockpitIcons
import com.selawi.cockpit.core.design.PillButton
import com.selawi.cockpit.core.design.RoundIconButton
import com.selawi.cockpit.core.design.ScreenTitle
import com.selawi.cockpit.core.design.SectionLabel
import com.selawi.cockpit.core.design.Space
import com.selawi.cockpit.core.platform.hasLocationPermission
import com.selawi.cockpit.core.platform.lastKnownLocation
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

private const val STYLE = "https://tiles.openfreemap.org/styles/liberty"
private val AMMAN = LatLng(31.9539, 35.9106)

/** Free OpenStreetMap map that follows the car. Needs no Google services and no key. */
@Composable
fun FreeMap(
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    onMapReady: (MapLibreMap) -> Unit = {},
) {
    val context = LocalContext.current
    val mapView = remember { createMap(context, interactive, onMapReady) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }
    AndroidView(factory = { mapView }, modifier = modifier)
}

@SuppressLint("MissingPermission")
private fun createMap(context: Context, interactive: Boolean, onMapReady: (MapLibreMap) -> Unit): MapView {
    MapLibre.getInstance(context)
    return MapView(context).apply {
        onCreate(null)
        getMapAsync { map ->
            val start = context.lastKnownLocation()?.let { LatLng(it.latitude, it.longitude) } ?: AMMAN
            map.cameraPosition = CameraPosition.Builder().target(start).zoom(if (interactive) 14.0 else 13.0).build()
            map.uiSettings.setAllGesturesEnabled(interactive)
            map.uiSettings.isAttributionEnabled = true
            map.uiSettings.isLogoEnabled = false
            map.setStyle(Style.Builder().fromUri(STYLE)) { style ->
                if (context.hasLocationPermission()) {
                    runCatching {
                        val location = map.locationComponent
                        location.activateLocationComponent(
                            LocationComponentActivationOptions.builder(context, style).build(),
                        )
                        location.isLocationComponentEnabled = true
                        location.renderMode = RenderMode.COMPASS
                        location.cameraMode = CameraMode.TRACKING
                    }
                }
                onMapReady(map)
            }
        }
    }
}

/** Full map screen with a recenter button and the phone navigation shortcut. */
@Composable
fun MapScreen(onDriveMode: () -> Unit, onOpenPhoneNav: () -> Unit) {
    // Plain holder: the map object is not UI state and must survive recomposition.
    val mapRef = remember { arrayOfNulls<MapLibreMap>(1) }
    Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(Space.m)) {
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(Space.cardRadius)),
        ) {
            FreeMap(Modifier.fillMaxSize(), interactive = true) { mapRef[0] = it }
            RoundIconButton(
                CockpitIcons.MyLocation,
                "Centre on the car",
                modifier = Modifier.align(Alignment.BottomEnd).padding(Space.m),
            ) {
                mapRef[0]?.let { map ->
                    runCatching {
                        if (map.locationComponent.isLocationComponentActivated) {
                            map.locationComponent.cameraMode = CameraMode.TRACKING
                        } else {
                            map.animateCamera(CameraUpdateFactory.newLatLng(AMMAN))
                        }
                    }
                }
            }
        }
        Column(Modifier.width(320.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(Space.m)) {
            ScreenTitle("Map")
            CockpitCard(Modifier.fillMaxWidth()) {
                SectionLabel("Navigate with your phone")
                Spacer(Modifier.height(Space.s))
                Text(
                    "Google Maps runs on your phone and shows here through DiAuto.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = CockpitColors.TextSecondary,
                )
                Spacer(Modifier.height(Space.m))
                PillButton("Drive mode", Modifier.fillMaxWidth(), icon = CockpitIcons.SplitScreen, primary = true, onClick = onDriveMode)
                Spacer(Modifier.height(Space.s))
                PillButton("Open DiAuto", Modifier.fillMaxWidth(), icon = CockpitIcons.Navigation, onClick = onOpenPhoneNav)
            }
        }
    }
}
