package com.keepsakeai.app.ui.screens

import com.keepsakeai.app.ui.theme.Ts
import com.keepsakeai.app.ui.theme.Gap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.maps.Directions
import com.keepsakeai.app.places.NearHere
import com.keepsakeai.app.ui.MindSpaceViewModel
import com.keepsakeai.app.ui.components.FlowHeader
import com.keepsakeai.app.ui.components.GlassButton
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.LogoMark
import com.keepsakeai.app.ui.components.MarkGlyph
import com.keepsakeai.app.ui.components.SectionLabel
import com.keepsakeai.app.ui.theme.LocalFlow

/** Everything you kept within a few hundred metres of where you are now, nearest first. */
@Composable
fun NearHereScreen(viewModel: MindSpaceViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalFlow.current
    val context = LocalContext.current
    val hits by viewModel.nearHere.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshNearHere() }

    LazyColumn(modifier, contentPadding = PaddingValues(Gap.l), verticalArrangement = Arrangement.spacedBy(Gap.m)) {
        item {
            FlowHeader(
                eyebrow = "within ${NearHere.RADIUS_M.toInt()} m of you",
                title = "near ",
                gradientSuffix = "here",
                joinSuffix = true,
                leading = { LogoMark(size = 46.dp, glyph = MarkGlyph.ORBIT) }
            ) {
                GlassButton("close", onClick = onClose, icon = Icons.Rounded.Close)
            }
        }
        if (hits.isEmpty()) item { SectionLabel("nothing saved around here yet") }
        items(hits, key = { it.memory.id }) { h ->
            GlassCard(Modifier.fillMaxWidth(), onClick = { viewModel.openMemory(h.memory.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h.memory.title, fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.weight(1f))
                    Text("${h.meters} m", fontSize = Ts.meta, color = c.muted)
                }
                Text(h.memory.aiSummary.replace("\n", " ").take(120), fontSize = Ts.meta, color = c.muted, maxLines = 2)
                val lat = h.memory.lat
                val lng = h.memory.lng
                if (lat != null && lng != null) {
                    Spacer(Modifier.height(Gap.s))
                    GlassButton("open in Google Maps", onClick = { Directions.openPlace(context, lat, lng) })
                }
            }
        }
    }
}
