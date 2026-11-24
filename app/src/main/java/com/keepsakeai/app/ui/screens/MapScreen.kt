package com.keepsakeai.app.ui.screens

import android.graphics.Color as AColor
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.maps.Directions
import com.keepsakeai.app.places.MapPoint
import com.keepsakeai.app.places.MapPoints
import com.keepsakeai.app.ui.MindSpaceViewModel
import com.keepsakeai.app.ui.components.GlassButton
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.Pill
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val STYLE_URL = "https://tiles.openfreemap.org/styles/liberty"
private const val SOURCE_ID = "trove-pins"
private const val MEMORY_LAYER = "trove-pins-memory"
private const val STAY_LAYER = "trove-pins-stay"
private const val TRIP_LAYER = "trove-pins-trip"

/**
 * Pins for everything you kept with a location, and everywhere you stayed.
 * Vector map from MapLibre using free OpenFreeMap tiles (OpenStreetMap data): no key, no account.
 */
@Composable
fun MapScreen(viewModel: MindSpaceViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalFlow.current
    val context = LocalContext.current
    val dao = remember { (context.applicationContext as MindSpaceApp).database.dao() }

    var memoryPins by remember { mutableStateOf<List<MapPoint>>(emptyList()) }
    var stayPins by remember { mutableStateOf<List<MapPoint>>(emptyList()) }
    var showMemories by remember { mutableStateOf(true) }
    var showStays by remember { mutableStateOf(true) }
    val tripPins by viewModel.tripPins.collectAsState()
    var selected by remember { mutableStateOf<MapPoint?>(null) }
    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var fitKey by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        memoryPins = MapPoints.memories(dao.getAllMemories())
        stayPins = MapPoints.stays(dao.allVisits())
    }
    val points = tripPins + (if (showMemories) memoryPins else emptyList()) + (if (showStays) stayPins else emptyList())
    val latestPoints by rememberUpdatedState(points)

    val memoryColor = Flow.Indigo.toArgb()
    val stayColor = Flow.Amber.toArgb()
    val tripColor = Flow.Rose.toArgb()

    val mapView = remember {
        MapLibre.getInstance(context)
        MapView(context).also { mv ->
            mv.onCreate(null)
            mv.onStart()
            mv.onResume()
            mv.getMapAsync { m ->
                m.setStyle(Style.Builder().fromUri(STYLE_URL)) { style ->
                    style.addSource(GeoJsonSource(SOURCE_ID, FeatureCollection.fromFeatures(emptyList<Feature>())))
                    style.addLayer(
                        CircleLayer(MEMORY_LAYER, SOURCE_ID)
                            .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("MEMORY")))
                            .withProperties(
                                PropertyFactory.circleColor(memoryColor),
                                PropertyFactory.circleRadius(9f),
                                PropertyFactory.circleStrokeColor(AColor.WHITE),
                                PropertyFactory.circleStrokeWidth(2.5f)
                            )
                    )
                    style.addLayer(
                        CircleLayer(STAY_LAYER, SOURCE_ID)
                            .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("STAY")))
                            .withProperties(
                                PropertyFactory.circleColor(stayColor),
                                PropertyFactory.circleRadius(9f),
                                PropertyFactory.circleStrokeColor(AColor.WHITE),
                                PropertyFactory.circleStrokeWidth(2.5f)
                            )
                    )
                    style.addLayer(
                        CircleLayer(TRIP_LAYER, SOURCE_ID)
                            .withFilter(Expression.eq(Expression.get("kind"), Expression.literal("TRIP")))
                            .withProperties(
                                PropertyFactory.circleColor(tripColor),
                                PropertyFactory.circleRadius(11f),
                                PropertyFactory.circleStrokeColor(AColor.WHITE),
                                PropertyFactory.circleStrokeWidth(3f)
                            )
                    )
                    m.addOnMapClickListener { latLng ->
                        val hit = m.queryRenderedFeatures(m.projection.toScreenLocation(latLng), MEMORY_LAYER, STAY_LAYER, TRIP_LAYER)
                            .firstOrNull()?.getStringProperty("key")
                        val found = hit?.let { k -> latestPoints.firstOrNull { it.key == k } }
                        if (found != null) { selected = found; true } else false
                    }
                    map = m
                }
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { mapView.onPause(); mapView.onStop(); mapView.onDestroy() }
    }

    // Push the pins into the map whenever they or the filters change, and zoom to fit them.
    LaunchedEffect(map, points) {
        val m = map ?: return@LaunchedEffect
        m.getStyle { style ->
            val features = points.map { p ->
                Feature.fromGeometry(Point.fromLngLat(p.lng, p.lat)).apply {
                    addStringProperty("key", p.key)
                    addStringProperty("kind", p.kind.name)
                }
            }
            style.getSourceAs<GeoJsonSource>(SOURCE_ID)?.setGeoJson(FeatureCollection.fromFeatures(features))
        }
        val key = "${points.size}"
        if (points.isNotEmpty() && key != fitKey) {
            fitKey = key
            val b = MapPoints.bounds(points)!!
            if (b[0] == b[2] && b[1] == b[3]) {
                m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(b[0], b[1]), 15.0))
            } else {
                val bounds = LatLngBounds.Builder().include(LatLng(b[0], b[1])).include(LatLng(b[2], b[3])).build()
                m.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
            }
        }
    }

    Box(modifier) {
        AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (tripPins.isNotEmpty()) Pill("Trip (${tripPins.size})", true, { viewModel.tripPins.value = emptyList(); selected = null })
                Pill("Kept (${memoryPins.size})", showMemories, { showMemories = !showMemories; selected = null })
                Pill("Stays (${stayPins.size})", showStays, { showStays = !showStays; selected = null })
                Box(Modifier.weight(1f))
                GlassButton("close", onClick = onClose, icon = Icons.Rounded.Close)
            }
            if (memoryPins.isEmpty() && stayPins.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        "Nothing with a location yet. Photos with GPS, and anything you save while Places visited is on, will appear here.",
                        fontSize = 13.sp, color = c.muted
                    )
                }
            }
        }

        selected?.let { p ->
            GlassCard(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp)) {
                Text(p.title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = c.text)
                Text(p.subtitle, fontSize = 12.sp, color = c.muted)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    p.memoryId?.let { id -> GlassButton("open", onClick = { viewModel.openMemory(id) }, modifier = Modifier.weight(1f)) }
                    GlassButton("directions", onClick = { Directions.open(context, "${p.lat},${p.lng}") }, modifier = Modifier.weight(1f))
                    GlassButton("hide", onClick = { selected = null }, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
