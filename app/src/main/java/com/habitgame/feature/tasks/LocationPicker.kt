package com.habitgame.feature.tasks

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.habitgame.feature.journal.fieldColors
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import java.io.File

/**
 * Full-screen map for choosing where a location task happens. Uses OpenStreetMap,
 * which needs no API key. Tap the map to drop the pin.
 */
@Composable
fun LocationPickerDialog(onDismiss: () -> Unit, onLocationSelected: (latitude: Double, longitude: Double, name: String) -> Unit) {
    val context = LocalContext.current
    var picked by remember { mutableStateOf<GeoPoint?>(null) }
    var locationName by remember { mutableStateOf("") }
    val mapView = remember { createMapView(context) }
    val marker = remember { Marker(mapView).apply { setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM); title = "Here" } }

    // Tap on the map → move the pin there
    DisposableEffect(mapView) {
        val events = MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                picked = p
                return true
            }
            override fun longPressHelper(p: GeoPoint): Boolean = false
        })
        mapView.overlays.add(0, events)
        mapView.overlays.add(CopyrightOverlay(context))
        mapView.onResume()
        onDispose {
            mapView.onPause()
            mapView.onDetach()
        }
    }

    // Start at the phone's last known position when location is allowed
    LaunchedEffect(Unit) { centerOnMyLocation(context, mapView, zoom = 16.0) }

    LaunchedEffect(picked) {
        val point = picked ?: return@LaunchedEffect
        marker.position = point
        if (marker !in mapView.overlays) mapView.overlays.add(marker)
        mapView.invalidate()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Surface(Modifier.fillMaxSize(), color = com.habitgame.ui.theme.LocalGameColors.current.solidBackground) {
            Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Text(
                        "Pick a location",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { centerOnMyLocation(context, mapView, zoom = 17.0, dropPin = { picked = it }) }) {
                        Text("📍 My location")
                    }
                }

                // clipToBounds: the map view otherwise paints over the bars above and below it
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                    AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
                    if (picked == null) {
                        Text(
                            "Tap the map to drop a pin",
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .padding(12.dp)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f), RoundedCornerShape(10.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                // Extra bottom room so the button clears the gesture bar on every phone
                Column(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = locationName,
                        onValueChange = { locationName = it },
                        label = { Text("Name this place (e.g. Gym, Library)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = fieldColors()
                    )
                    Button(
                        onClick = {
                            picked?.let { onLocationSelected(it.latitude, it.longitude, locationName.ifBlank { "My place" }) }
                        },
                        enabled = picked != null,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("Save location", fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

private fun createMapView(context: Context): MapView {
    // osmdroid keeps its settings and tile cache in the app's own storage (no storage permission needed)
    Configuration.getInstance().apply {
        load(context, context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE))
        userAgentValue = context.packageName
        osmdroidBasePath = File(context.cacheDir, "osmdroid")
        osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
    }
    return MapView(context).apply {
        setTileSource(TileSourceFactory.MAPNIK)
        setMultiTouchControls(true)
        zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.SHOW_AND_FADEOUT)
        controller.setZoom(3.0)
        controller.setCenter(GeoPoint(15.0, 20.0))
    }
}

@SuppressLint("MissingPermission")
private fun centerOnMyLocation(context: Context, mapView: MapView, zoom: Double, dropPin: ((GeoPoint) -> Unit)? = null) {
    val allowed = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
    if (!allowed) return
    LocationServices.getFusedLocationProviderClient(context).lastLocation.addOnSuccessListener { location ->
        location ?: return@addOnSuccessListener
        val point = GeoPoint(location.latitude, location.longitude)
        mapView.controller.setZoom(zoom)
        mapView.controller.animateTo(point)
        dropPin?.invoke(point)
    }
}
