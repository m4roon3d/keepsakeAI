package com.keepsakeai.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.data.local.PlaceVisitEntity
import com.keepsakeai.app.maps.Directions
import com.keepsakeai.app.places.LocationPrefs
import com.keepsakeai.app.places.VisitTracker
import com.keepsakeai.app.ui.components.FlowHeader
import com.keepsakeai.app.ui.components.GlassButton
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.LogoMark
import com.keepsakeai.app.ui.components.MarkGlyph
import com.keepsakeai.app.ui.components.SectionLabel
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private data class Row2(val key: String, val header: String?, val visit: PlaceVisitEntity?)

/** Where you stayed, newest first, with arrival and departure. Everything here lives on the phone. */
@Composable
fun PlacesVisitedScreen(onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalFlow.current
    val context = LocalContext.current
    val app = context.applicationContext as MindSpaceApp
    val dao = remember { app.database.dao() }
    val prefs = remember { LocationPrefs(context) }
    val scope = rememberCoroutineScope()
    val all by dao.observeVisits().collectAsState(initial = emptyList())
    val visits = remember(all) { all.filter { VisitTracker.isVisit(it) } }
    var paused by remember { mutableStateOf(prefs.paused) }
    var renaming by remember { mutableStateOf<PlaceVisitEntity?>(null) }
    val dayFmt = remember { SimpleDateFormat("EEEE, d MMM", Locale.ENGLISH) }
    val timeFmt = remember { SimpleDateFormat("h:mm a", Locale.ENGLISH) }

    val rows = remember(visits) {
        val out = mutableListOf<Row2>()
        var last = ""
        visits.forEach { v ->
            val d = dayFmt.format(Date(v.arrivedAt))
            if (d != last) { out += Row2("h$d", d.lowercase(), null); last = d }
            out += Row2("v${v.id}", null, v)
        }
        out
    }

    fun duration(v: PlaceVisitEntity): String {
        val mins = ((v.lastSeenAt - v.arrivedAt) / 60_000L).toInt()
        return if (mins >= 60) "${mins / 60}h ${mins % 60}m" else "${mins}m"
    }

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FlowHeader(
                eyebrow = "stays of 10 minutes or more",
                title = "places ",
                gradientSuffix = "visited",
                joinSuffix = true,
                leading = { LogoMark(size = 46.dp, glyph = MarkGlyph.ORBIT) }
            ) { GlassButton("close", onClick = onClose, icon = Icons.Rounded.Close) }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text(
                    if (!prefs.enabled) "Off. Turn it on in Settings → Insights."
                    else if (paused) "Paused: nothing is being recorded."
                    else "${visits.size} stays kept · checked about every 15 minutes",
                    fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.text
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Stays on your phone. Only a place name is ever sent to an AI, and only when you ask Pilot about it. Android checks in about every 15 minutes, so very short stops are missed.",
                    fontSize = 12.sp, color = c.muted
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton(if (paused) "resume" else "pause", onClick = {
                        paused = !paused; prefs.paused = paused
                    }, modifier = Modifier.weight(1f))
                    GlassButton("delete all", onClick = { scope.launch { dao.deleteAllVisits() } }, tint = Flow.Rose, modifier = Modifier.weight(1f))
                }
            }
        }
        if (visits.isEmpty()) item { SectionLabel("no stays yet") }
        items(rows, key = { it.key }) { r ->
            if (r.header != null) { SectionLabel(r.header); return@items }
            val v = r.visit!!
            GlassCard(Modifier.fillMaxWidth(), onClick = { renaming = v }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        v.name ?: "Unnamed place",
                        fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text,
                        modifier = Modifier.weight(1f)
                    )
                    Text(duration(v), fontSize = 12.sp, color = c.muted)
                }
                Text(
                    "${timeFmt.format(Date(v.arrivedAt))} – ${timeFmt.format(Date(v.lastSeenAt))}".lowercase(),
                    fontSize = 12.sp, color = c.muted
                )
            }
        }
    }

    renaming?.let { v ->
        var text by remember(v.id) { mutableStateOf(v.name.orEmpty()) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("This stay") },
            text = {
                Column {
                    OutlinedTextField(text, { text = it }, label = { Text("Name") }, singleLine = true)
                    TextButton(onClick = { Directions.open(context, v.name ?: "${v.lat},${v.lng}") }) { Text("Directions") }
                    TextButton(onClick = {
                        prefs.excluded = prefs.excluded + (v.lat to v.lng)
                        scope.launch { dao.deleteVisit(v.id) }
                        renaming = null
                    }) { Text("Never record here") }
                    TextButton(onClick = { scope.launch { dao.deleteVisit(v.id) }; renaming = null }) { Text("Delete this stay") }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { dao.updateVisit(v.copy(name = text.trim().ifBlank { null }, confirmed = text.isNotBlank())) }
                    renaming = null
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } }
        )
    }
}
