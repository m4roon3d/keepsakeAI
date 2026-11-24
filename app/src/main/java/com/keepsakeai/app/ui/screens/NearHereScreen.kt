package com.keepsakeai.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val hits by viewModel.nearHere.collectAsState()
    LaunchedEffect(Unit) { viewModel.refreshNearHere() }

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FlowHeader(
                eyebrow = "within ${NearHere.RADIUS_M.toInt()} m of you",
                title = "near ",
                gradientSuffix = "here",
                joinSuffix = true,
                leading = { LogoMark(size = 46.dp, glyph = MarkGlyph.ORBIT) }
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("map", onClick = { viewModel.overlay.value = "map" })
                    GlassButton("close", onClick = onClose, icon = Icons.Rounded.Close)
                }
            }
        }
        if (hits.isEmpty()) item { SectionLabel("nothing saved around here yet") }
        items(hits, key = { it.memory.id }) { h ->
            GlassCard(Modifier.fillMaxWidth(), onClick = { viewModel.openMemory(h.memory.id) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(h.memory.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.weight(1f))
                    Text("${h.meters} m", fontSize = 12.sp, color = c.muted)
                }
                Text(h.memory.aiSummary.replace("\n", " ").take(120), fontSize = 12.sp, color = c.muted, maxLines = 2)
            }
        }
    }
}
