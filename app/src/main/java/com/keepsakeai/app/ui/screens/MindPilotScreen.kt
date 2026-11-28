package com.keepsakeai.app.ui.screens

import com.keepsakeai.app.ui.theme.Ts
import com.keepsakeai.app.ui.theme.Gap
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.ai.AiProvider
import com.keepsakeai.app.context.DeviceContext
import com.keepsakeai.app.ui.MindSpaceViewModel
import com.keepsakeai.app.ui.components.Badge
import com.keepsakeai.app.ui.components.DockClearance
import com.keepsakeai.app.ui.components.FlowHeader
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.GlassField
import com.keepsakeai.app.ui.components.GlassToggle
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.components.Pill
import com.keepsakeai.app.ui.components.LogoMark
import com.keepsakeai.app.ui.components.MarkGlyph
import com.keepsakeai.app.ui.components.GlassButton
import androidx.compose.material.icons.rounded.Add
import com.keepsakeai.app.ui.components.simpleMarkdown
import androidx.compose.ui.text.AnnotatedString
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow

private fun providerColor(p: AiProvider) = when (p) {
    AiProvider.GEMINI -> Flow.Indigo
    AiProvider.OPENAI -> Flow.Emerald
    AiProvider.PERPLEXITY -> Flow.Sky
}

@Composable
fun MindPilotScreen(
    viewModel: MindSpaceViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val c = LocalFlow.current
    val selectedProviders by viewModel.selectedProviders.collectAsState()
    val agentMode by viewModel.agentModeEnabled.collectAsState()
    val useMemory by viewModel.useMemoryContext.collectAsState()
    val isAsking by viewModel.isAskingPilot.collectAsState()
    val responses by viewModel.pilotResponses.collectAsState()
    val place by viewModel.currentPlace.collectAsState()
    val sources by viewModel.pilotSources.collectAsState()
    val focus by viewModel.pilotFocus.collectAsState()
    var attachFor by remember { mutableStateOf<com.keepsakeai.app.ai.MindPilotResponse?>(null) }

    var query by rememberSaveable { mutableStateOf("") }
    var hasLocation by remember { mutableStateOf(DeviceContext.hasLocationPermission(context)) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasLocation = grants.values.any { it }
        if (hasLocation) viewModel.refreshPlace()
    }
    LaunchedEffect(hasLocation) { if (hasLocation) viewModel.refreshPlace() }
    val draft by viewModel.pilotDraft.collectAsState()
    LaunchedEffect(draft) {
        draft?.let {
            query = it
            viewModel.pilotDraft.value = null
            viewModel.runMindPilot(it)
        }
    }

    val presets = if (agentMode)
        listOf("Plan my weekend from my notes", "Turn my receipts into a budget")
    else
        listOf("Weather tomorrow where I am?", "What did I spend on food this month?", "What did I save this week?", "Summarise my study notes")

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = Gap.l, end = Gap.l, top = Gap.s, bottom = DockClearance),
        verticalArrangement = Arrangement.spacedBy(Gap.m)
    ) {
        item {
            FlowHeader(
                eyebrow = "ask across your memories",
                title = "trove",
                gradientSuffix = "pilot",
                leading = { LogoMark(size = 46.dp, glyph = MarkGlyph.ORBIT) }
            ) {
                GlassButton(
                    "new",
                    onClick = { query = ""; viewModel.pilotResponses.value = emptyList(); viewModel.pilotFocus.value = null },
                    icon = Icons.Rounded.Add
                )
            }
        }

        // Location chip / prompt
        item {
            if (hasLocation) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(c.glass)
                        .clickable { viewModel.refreshPlace() }
                        .padding(horizontal = Gap.m, vertical = Gap.s),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.LocationOn, null, tint = Flow.Pink, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(Gap.s))
                    Text(place ?: "Finding your location…", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                GlassCard(
                    Modifier.fillMaxWidth(),
                    onClick = {
                        locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
                    }
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(Flow.Pink.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                            Icon(Icons.Rounded.LocationOn, null, tint = Flow.Pink)
                        }
                        Spacer(Modifier.width(Gap.m))
                        Column(Modifier.weight(1f)) {
                            Text("Use my location", style = MaterialTheme.typography.titleSmall, color = c.text)
                            Text("So Pilot can answer weather, nearby and \"where I live\" questions.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                AiProvider.entries.forEach { p ->
                    Pill(p.tag, p in selectedProviders, { viewModel.toggleProvider(p) }, Modifier.weight(1f))
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                GlassToggle("Memory context", useMemory, { viewModel.useMemoryContext.value = !useMemory }, Modifier.weight(1f))
                GlassToggle("Agent mode", agentMode, { viewModel.agentModeEnabled.value = !agentMode }, Modifier.weight(1f), accent = Flow.Purple)
            }
        }

        focus?.let { f ->
            item {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Flow.Indigo.copy(alpha = 0.12f))
                        .padding(start = Gap.m, end = Gap.xs, top = Gap.xs, bottom = Gap.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Flow.Indigo, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(Gap.s))
                    Text(
                        "about: ${f.title}", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Indigo,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        "×", fontSize = Ts.lead, fontWeight = FontWeight.SemiBold, color = Flow.Indigo,
                        modifier = Modifier.clip(CircleShape).clickable { viewModel.pilotFocus.value = null }.padding(horizontal = Gap.s)
                    )
                }
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(Gap.s)) {
                GlassField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = when {
                        focus != null -> "Ask about ${focus?.title}…"
                        agentMode -> "Give Agent mode a multi-step goal…"
                        else -> "Ask anything…"
                    },
                    singleLine = false,
                    minLines = 2,
                    bare = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Gap.s),
                    contentPadding = PaddingValues(horizontal = Gap.s, vertical = Gap.s)
                ) {
                    items(presets) { p ->
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(Flow.Indigo.copy(alpha = 0.12f))
                                .clickable { query = p }
                                .padding(horizontal = Gap.m, vertical = Gap.s)
                        ) { Text(p, fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Indigo) }
                    }
                }
                GradientButton(
                    text = when {
                        isAsking -> "Thinking with ${selectedProviders.size} model(s)…"
                        agentMode -> "Run Agent mode"
                        else -> "Ask Pilot"
                    },
                    onClick = { viewModel.runMindPilot(query) },
                    enabled = query.isNotBlank() && !isAsking,
                    brush = Flow.auroraSweep,
                    icon = Icons.Rounded.AutoAwesome,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (isAsking) {
            item {
                Row(Modifier.fillMaxWidth().padding(Gap.s), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = Flow.Indigo, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                }
            }
        }

        if (responses.isEmpty() && !isAsking) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    LogoMark(Modifier.align(Alignment.CenterHorizontally), size = 64.dp)
                    Spacer(Modifier.height(Gap.s))
                    Text(
                        "Compare answers from Gemini, ChatGPT and Perplexity side by side, grounded in what you've saved, plus live web search, your location and today's date.",
                        color = c.muted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        items(responses) { resp ->
            val pc = providerColor(resp.provider)
            GlassCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(Brush.linearGradient(listOf(pc, Flow.Sky))))
                    Spacer(Modifier.width(Gap.s))
                    Text(resp.provider.tag, style = MaterialTheme.typography.titleSmall, color = pc, modifier = Modifier.weight(1f))
                    Text(resp.provider.displayName, fontSize = Ts.meta, color = c.muted)
                }
                Spacer(Modifier.height(Gap.s))
                val parsed = remember(resp.content) { com.keepsakeai.app.ai.PlaceMarkers.parse(resp.content) }
                SelectionContainer {
                    Text(
                        if (resp.isError) AnnotatedString(resp.content) else simpleMarkdown(parsed.text, citeColor = Flow.Indigo),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (resp.isError) Flow.Rose else c.text
                    )
                }
                if (!resp.isError && parsed.places.isNotEmpty()) {
                    Spacer(Modifier.height(Gap.m))
                    Text("PLACES", style = MaterialTheme.typography.labelSmall, color = c.muted)
                    Spacer(Modifier.height(Gap.s))
                    Column(verticalArrangement = Arrangement.spacedBy(Gap.s)) {
                        parsed.places.forEach { place ->
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Flow.Sky.copy(alpha = 0.10f))
                                    .clickable { com.keepsakeai.app.maps.Directions.open(context, place.query) }
                                    .padding(horizontal = Gap.m, vertical = Gap.s),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(place.name, fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("directions →", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Sky)
                            }
                        }
                    }
                }
                if (!resp.isError && !isAsking) {
                    var saved by remember(resp) { mutableStateOf(false) }
                    Spacer(Modifier.height(Gap.m))
                    Row(horizontalArrangement = Arrangement.spacedBy(Gap.s), verticalAlignment = Alignment.CenterVertically) {
                        val f = focus
                        if (saved) {
                            Text("saved ✓", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Emerald, modifier = Modifier.padding(Gap.s))
                        } else if (f != null) {
                            SaveChip("add to ${f.title.take(28)}", Flow.Indigo) { viewModel.addPilotAnswerToCard(resp); saved = true }
                        } else {
                            SaveChip("save as memory", Flow.Indigo) { viewModel.savePilotAnswerAsMemory(resp); saved = true }
                            SaveChip("add to a card…", c.soft) { attachFor = resp }
                        }
                    }
                }
                if (resp.cited.isNotEmpty()) {
                    Spacer(Modifier.height(Gap.m))
                    Text("FROM YOUR MEMORIES", style = MaterialTheme.typography.labelSmall, color = c.muted)
                    Spacer(Modifier.height(Gap.s))
                    Column(verticalArrangement = Arrangement.spacedBy(Gap.s)) {
                        resp.cited.forEach { id ->
                            val src = sources[id] ?: return@forEach
                            val n = sources.keys.indexOf(id) + 1
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Flow.Indigo.copy(alpha = 0.10f))
                                    .clickable { viewModel.openMemory(id) }
                                    .padding(horizontal = Gap.m, vertical = Gap.s),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("[$n]", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Indigo)
                                Spacer(Modifier.width(Gap.s))
                                Text(src.title, fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("open →", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Indigo)
                            }
                        }
                    }
                }
            }
        }
    }
    attachFor?.let { resp ->
        AttachToCardDialog(viewModel, onPick = { id -> viewModel.addPilotAnswerToCard(resp, id); attachFor = null }, onDismiss = { attachFor = null })
    }
}

@Composable
private fun SaveChip(label: String, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Text(
        label, fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.12f)).clickable(onClick = onClick)
            .padding(horizontal = Gap.m, vertical = Gap.s)
    )
}

/** Pick which saved card a Pilot answer should be added to. */
@Composable
private fun AttachToCardDialog(viewModel: MindSpaceViewModel, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    val c = LocalFlow.current
    val all by viewModel.allMemories.collectAsState()
    var q by remember { mutableStateOf("") }
    val shown = remember(all, q) {
        val t = q.trim().lowercase()
        (if (t.isEmpty()) all else all.filter { it.title.lowercase().contains(t) || it.aiSummary.lowercase().contains(t) })
            .filter { it.source != com.keepsakeai.app.data.local.CaptureSource.EMAIL && it.source != com.keepsakeai.app.data.local.CaptureSource.UPI_MESSAGE }
            .take(8)
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to which card?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Gap.s)) {
                GlassField(q, { q = it }, "Search your cards", Modifier.fillMaxWidth())
                shown.forEach { m ->
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onPick(m.id) }.padding(Gap.s)
                    ) {
                        Text(m.title, fontSize = Ts.body, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(m.collectionName, fontSize = Ts.meta, color = c.muted, maxLines = 1)
                    }
                }
                if (shown.isEmpty()) Text("No cards match.", fontSize = Ts.meta, color = c.muted)
            }
        },
        confirmButton = {},
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("cancel") } }
    )
}
