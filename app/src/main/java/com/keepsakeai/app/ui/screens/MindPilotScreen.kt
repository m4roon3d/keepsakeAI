package com.keepsakeai.app.ui.screens

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

    var query by rememberSaveable { mutableStateOf("") }
    var hasLocation by remember { mutableStateOf(DeviceContext.hasLocationPermission(context)) }

    val locationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasLocation = grants.values.any { it }
        if (hasLocation) viewModel.refreshPlace()
    }
    LaunchedEffect(hasLocation) { if (hasLocation) viewModel.refreshPlace() }

    val presets = if (agentMode)
        listOf("Plan my weekend from my notes", "Turn my receipts into a budget")
    else
        listOf("Weather tomorrow where I am?", "What did I save this week?", "Summarise my study notes")

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = DockClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            FlowHeader(eyebrow = "Ask across your memories", title = "keepsake", gradientSuffix = "Pilot") {
                Badge("Multi-model", Color.White, brush = Flow.auroraSweep)
            }
        }

        // Location chip / prompt
        item {
            if (hasLocation) {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(c.glass)
                        .clickable { viewModel.refreshPlace() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.LocationOn, null, tint = Flow.Pink, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(place ?: "Finding your location…", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Use my location", style = MaterialTheme.typography.titleSmall, color = c.text)
                            Text("So Pilot can answer weather, nearby and \"where I live\" questions.", style = MaterialTheme.typography.bodySmall, color = c.muted)
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.forEach { p ->
                    Pill(p.tag, p in selectedProviders, { viewModel.toggleProvider(p) }, Modifier.weight(1f))
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassToggle("Memory context", useMemory, { viewModel.useMemoryContext.value = !useMemory }, Modifier.weight(1f))
                GlassToggle("Agent mode", agentMode, { viewModel.agentModeEnabled.value = !agentMode }, Modifier.weight(1f), accent = Flow.Purple)
            }
        }

        item {
            GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(6.dp)) {
                GlassField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = if (agentMode) "Give Agent mode a multi-step goal…" else "Ask anything…",
                    singleLine = false,
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp)
                ) {
                    items(presets) { p ->
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(Flow.Indigo.copy(alpha = 0.12f))
                                .clickable { query = p }
                                .padding(horizontal = 11.dp, vertical = 6.dp)
                        ) { Text(p, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Flow.Indigo) }
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
                Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.Center) {
                    CircularProgressIndicator(color = Flow.Indigo, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                }
            }
        }

        if (responses.isEmpty() && !isAsking) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    LogoMark(Modifier.align(Alignment.CenterHorizontally), size = 64.dp)
                    Spacer(Modifier.height(8.dp))
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
                    Spacer(Modifier.width(8.dp))
                    Text(resp.provider.tag, style = MaterialTheme.typography.titleSmall, color = pc, modifier = Modifier.weight(1f))
                    Text(resp.provider.displayName, fontSize = 11.sp, color = c.muted)
                }
                Spacer(Modifier.height(8.dp))
                SelectionContainer {
                    Text(
                        if (resp.isError) AnnotatedString(resp.content) else simpleMarkdown(resp.content),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (resp.isError) Flow.Rose else c.text
                    )
                }
            }
        }
    }
}
