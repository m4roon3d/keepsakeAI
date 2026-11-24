package com.keepsakeai.app.ui.screens

import android.graphics.Color as AColor
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

/** Pins for everything you kept with a location, and everywhere you stayed. Free OpenStreetMap tiles, no key. */
@Composable
fun MapScreen(viewModel: MindSpaceViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalFlow.current
    val context = LocalContext.current
    val dao = remember { (context.applicationContext as MindSpaceApp).database.dao() }

    var memoryPins by remember { mutableStateOf<List<MapPoint>>(emptyList()) }
    var stayPins by remember { mutableStateOf<List<MapPoint>>(emptyList()) }
    var showMemories by remember { mutableStateOf(true) }
    var showStays by remember { mutableStateOf(true) }
    var selected by remember { mutableStateOf<MapPoint?>(null) }
    var fitKey by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        memoryPins = MapPoints.memories(dao.getAllMemories())
        stayPins = MapPoints.stays(dao.allVisits())
    }
    val points = (if (showMemories) memoryPins else emptyList()) + (if (showStays) stayPins else emptyList())

    val mapView = remember {
        Configuration.getInstance().apply {
            userAgentValue = context.packageName
            osmdroidBasePath = File(context.cacheDir, "osmdroid")
            osmdroidTileCache = File(context.cacheDir, "osmdroid/tiles")
        }
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
            controller.setZoom(5.0)
            controller.setCenter(GeoPoint(20.5937, 78.9629))
        }
    }
    DisposableEffect(Unit) {
        mapView.onResume()
        onDispose { mapView.onPause(); mapView.onDetach() }
    }

    val memoryColor = Flow.Indigo.toArgb()
    val stayColor = Flow.Amber.toArgb()

    Box(modifier) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { map ->
                map.overlays.clear()
                points.forEach { p ->
                    val marker = Marker(map).apply {
                        position = GeoPoint(p.lat, p.lng)
                        title = p.title
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        icon = GradientDrawable().apply {
                            shape = GradientDrawable.OVAL
                            setColor(if (p.kind == MapPoint.Kind.MEMORY) memoryColor else stayColor)
                            setStroke(4, AColor.WHITE)
                            setSize(40, 40)
                        }
                        setOnMarkerClickListener { _, _ -> selected = p; true }
                    }
                    map.overlays.add(marker)
                }
                val key = "${points.size}"
                val b = MapPoints.bounds(points)
                if (b != null && key != fitKey) {
                    fitKey = key
                    val fit = {
                        if (b[0] == b[2] && b[1] == b[3]) {
                            map.controller.setZoom(16.0); map.controller.setCenter(GeoPoint(b[0], b[1]))
                        } else {
                            map.zoomToBoundingBox(BoundingBox(b[2], b[3], b[0], b[1]).increaseByScale(1.4f), false)
                        }
                    }
                    if (map.width > 0) fit() else map.addOnFirstLayoutListener { _, _, _, _, _ -> fit() }
                }
                map.invalidate()
            }
        )

        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
