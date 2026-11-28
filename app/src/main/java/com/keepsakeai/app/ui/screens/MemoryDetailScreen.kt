package com.keepsakeai.app.ui.screens

import com.keepsakeai.app.ui.components.simpleMarkdown
import com.keepsakeai.app.ui.theme.Ts
import com.keepsakeai.app.ui.theme.Gap
import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.ui.MindSpaceViewModel
import com.keepsakeai.app.ui.components.GlassButton
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.GlassField
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.components.Pill
import com.keepsakeai.app.ui.components.SectionLabel
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val collectionsList = listOf("Journal", "Travel", "Study Notes", "Events", "Personal Projects", "Receipts", "Watch Later", "General")

private fun sourceLabel(m: MemoryEntity) = when (m.source.name) {
    "SHARE_SHEET" -> "shared from an app"
    "LINK_CAPTURE" -> "shared link"
    "CAMERA_SCAN" -> "lens photo"
    "SCREENSHOT_OCR" -> "screen snap"
    "VOICE_MEMO" -> "voice note"
    "UPI_MESSAGE" -> "UPI payment"
    "EMAIL" -> "from your email"
    else -> "typed note"
}

/** A single memory: everything about it, editable, with related memories by meaning. */
@Composable
fun MemoryDetailScreen(viewModel: MindSpaceViewModel, id: Long, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalFlow.current
    val context = LocalContext.current
    val memory by remember(id) { viewModel.observeMemory(id) }.collectAsState(initial = null)
    var related by remember(id) { mutableStateOf<List<MemoryEntity>>(emptyList()) }
    var editing by remember(id) { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var title by remember(id) { mutableStateOf("") }
    var summary by remember(id) { mutableStateOf("") }
    var body by remember(id) { mutableStateOf("") }

    var placeDialog by remember(id) { mutableStateOf(false) }
    var placeName by remember(id) { mutableStateOf("") }

    val m = memory
    LaunchedEffect(m?.id, m?.title) { if (m != null) related = viewModel.relatedTo(m) }
    val explored = remember(m?.rawContent) { m?.let { com.keepsakeai.app.explore.Explorations.split(it.rawContent) } }
    val refreshing by viewModel.refreshingSummary.collectAsState()
    LaunchedEffect(id) { viewModel.markViewed(id) }
    if (m == null) return
    if (placeDialog) {
        AlertDialog(
            onDismissRequest = { placeDialog = false },
            title = { Text("Where was this?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Gap.s)) {
                    GlassField(placeName, { placeName = it }, "Place name, e.g. Inya, JP Nagar", Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { viewModel.setPlace(m, placeName); placeDialog = false }, enabled = placeName.trim().length >= 2) { Text("Save") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { placeDialog = false }) { Text("Cancel") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("delete this memory?") },
            text = { Text(m.title) },
            confirmButton = {
                TextButton(onClick = { viewModel.deleteMemory(m); confirmDelete = false; onClose() }) {
                    Text("delete", color = Flow.Rose, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("cancel") } }
        )
    }

    val (a1, a2) = collectionAccent(m.collectionName)
    val photo = m.photoUrisCsv.split(",").firstOrNull { it.isNotBlank() }

    LazyColumn(modifier, contentPadding = PaddingValues(Gap.l), verticalArrangement = Arrangement.spacedBy(Gap.m)) {
        // Top bar
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(c.glass).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = c.text)
                }
                Spacer(Modifier.weight(1f))
                if (!editing) {
                    IconPill(Icons.Rounded.Edit, "edit") {
                        title = m.title; summary = m.aiSummary; body = explored?.first ?: m.rawContent; editing = true
                    }
                    Spacer(Modifier.width(Gap.s))
                    IconPill(Icons.Rounded.IosShare, "share") {
                        val text = "${m.title}\n\n${m.aiSummary}" + (m.sourceUrl?.let { "\n\n$it" } ?: "")
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share memory"))
                    }
                    Spacer(Modifier.width(Gap.s))
                    IconPill(Icons.Rounded.DeleteOutline, "delete", tint = Flow.Rose) { confirmDelete = true }
                }
            }
        }

        if (photo != null) {
            item {
                AsyncImage(
                    model = photo, contentDescription = m.title, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 340.dp).clip(RoundedCornerShape(26.dp))
                )
            }
        }

        // Title, collection, thread, date
        item {
            Column {
                Box(Modifier.width(48.dp).height(5.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(a1, a2))))
                Spacer(Modifier.height(Gap.m))
                if (editing) {
                    GlassField(title, { title = it }, "Title", Modifier.fillMaxWidth())
                } else {
                    Text(m.title, style = MaterialTheme.typography.headlineSmall, color = c.text)
                }
                Spacer(Modifier.height(Gap.s))
                val date = SimpleDateFormat("EEE d MMM yyyy, h:mm a", Locale.ENGLISH).format(Date(m.createdAt)).lowercase()
                Text("$date · ${sourceLabel(m)}", fontSize = Ts.meta, color = c.muted)
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                items(collectionsList) { col ->
                    Pill(col.lowercase(), m.collectionName == col, {
                        if (m.collectionName != col) viewModel.updateMemory(m.copy(collectionName = col))
                    })
                }
            }
        }
        if (m.threadKey.isNotBlank() && m.threadKey != m.collectionName && m.threadKey != "General") {
            item {
                Row(
                    Modifier.clip(RoundedCornerShape(50)).background(Flow.Indigo.copy(alpha = 0.12f))
                        .clickable { viewModel.pilotDraft.value = "Summarise everything I've saved about \"${m.threadKey}\" and suggest what I should do next."; viewModel.closeAllMemories() }
                        .padding(horizontal = Gap.m, vertical = Gap.s),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("thread · ${m.threadKey}", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Indigo)
                }
            }
        }

        // Summary + full content
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("summary")
                Spacer(Modifier.height(Gap.s))
                if (editing) GlassField(summary, { summary = it }, "Summary", Modifier.fillMaxWidth(), singleLine = false, minLines = 2)
                else Text(m.aiSummary, style = MaterialTheme.typography.bodyLarge, color = c.text)
                if (!editing && explored?.second?.isNotEmpty() == true) {
                    Spacer(Modifier.height(Gap.s))
                    val busy = m.id in refreshing
                    Text(
                        if (busy) "refreshing summary…" else "refresh summary with what you explored",
                        fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Indigo,
                        modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                            .clickable(enabled = !busy) { viewModel.refreshSummary(m) }.padding(vertical = Gap.xs)
                    )
                }
                if (m.tagsCsv.isNotBlank()) {
                    Spacer(Modifier.height(Gap.m))
                    Text(m.tagsCsv.split(",").filter { it.isNotBlank() }.joinToString("  ") { "#${it.trim()}" }, fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Sky)
                }
            }
        }
        // Where a photo or video was taken: open it on the map.
        Regex("""Location: (-?\d+\.\d+), (-?\d+\.\d+)(?: \(near ([^)]+)\))?""").find(m.rawContent)?.let { loc ->
            val (lat, lng, near) = loc.destructured
            item {
                GlassCard(Modifier.fillMaxWidth(), onClick = {
                    val label = Uri.encode(m.title)
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lng?q=$lat,$lng($label)"))) }
                }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Place, null, tint = Flow.Pink, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Gap.m))
                        Column(Modifier.weight(1f)) {
                            Text(near.ifBlank { "$lat, $lng" }, fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                if (m.photoUrisCsv.isBlank() && m.source != com.keepsakeai.app.data.local.CaptureSource.CAMERA_SCAN) "where you saved this · open in maps"
                                else "where this was taken · open in maps",
                                fontSize = Ts.meta, color = c.muted
                            )
                        }
                        Text(
                            "directions", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Indigo,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(Flow.Indigo.copy(alpha = 0.12f))
                                .clickable { com.keepsakeai.app.maps.Directions.open(context, "$lat,$lng") }
                                .padding(horizontal = Gap.m, vertical = Gap.s)
                        )
                    }
                }
            }
        }
        // Name (or rename) the place: nearby businesses from OpenStreetMap, or type your own.
        if (!editing) {
            item {
                val named = com.keepsakeai.app.places.PlaceTag.placeOf(m.rawContent) ?: com.keepsakeai.app.places.PlaceTag.plainPlace(m.rawContent)
                Text(
                    if (named == null) "name this place" else "change place",
                    fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Indigo,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Flow.Indigo.copy(alpha = 0.12f))
                        .clickable { placeName = ""; placeDialog = true }
                        .padding(horizontal = Gap.m, vertical = Gap.s)
                )
            }
        }
        m.sourceUrl?.takeIf { it.isNotBlank() }?.let { url ->
            item {
                GlassCard(Modifier.fillMaxWidth(), onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Link, null, tint = Flow.Indigo, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(Gap.m))
                        Text(url, fontSize = Ts.meta, color = Flow.Indigo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("full text")
                Spacer(Modifier.height(Gap.s))
                if (editing) GlassField(body, { body = it }, "Notes", Modifier.fillMaxWidth(), singleLine = false, minLines = 4)
                else SelectionContainer { Text(explored?.first ?: m.rawContent, style = MaterialTheme.typography.bodyMedium, color = c.text) }
            }
        }
        val exploredItems = explored?.second.orEmpty()
        if (exploredItems.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionLabel("explored with pilot")
                    exploredItems.forEachIndexed { i, e ->
                        var open by remember(m.id, i, e.question) { mutableStateOf(i == exploredItems.lastIndex) }
                        Spacer(Modifier.height(Gap.s))
                        Row(
                            Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp)).clickable { open = !open }
                                .padding(vertical = Gap.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.AutoAwesome, null, tint = Flow.Indigo, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(Gap.s))
                            Column(Modifier.weight(1f)) {
                                Text(e.question, style = MaterialTheme.typography.titleSmall, color = c.text)
                                Text(e.date, fontSize = Ts.meta, color = c.muted)
                            }
                            Text(if (open) "hide" else "show", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Indigo)
                        }
                        if (open) {
                            Spacer(Modifier.height(Gap.xs))
                            SelectionContainer { Text(simpleMarkdown(e.answer, citeColor = Flow.Indigo), style = MaterialTheme.typography.bodyMedium, color = c.text) }
                            Text(
                                "remove", fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = Flow.Rose,
                                modifier = Modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(50))
                                    .clickable { viewModel.removeExploration(m, i) }.padding(vertical = Gap.s)
                            )
                        }
                    }
                }
            }
        }
        if (editing) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                    GlassButton("cancel", onClick = { editing = false }, modifier = Modifier.weight(1f))
                    GradientButton("save", onClick = {
                        viewModel.updateMemory(m.copy(
                            title = title.ifBlank { m.title }, aiSummary = summary,
                            rawContent = com.keepsakeai.app.explore.Explorations.join(body, explored?.second.orEmpty())
                        ))
                        editing = false
                    }, modifier = Modifier.weight(1f))
                }
            }
        } else {
            item {
                GradientButton(
                    "ask pilot about this",
                    brush = Flow.auroraSweep,
                    icon = Icons.Rounded.AutoAwesome,
                    onClick = { viewModel.askAbout(m) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Related by meaning
        if (related.isNotEmpty()) {
            item { SectionLabel("related memories", modifier = Modifier.padding(top = Gap.s)) }
            items(related, key = { it.id }) { r ->
                val (ra, _) = collectionAccent(r.collectionName)
                GlassCard(Modifier.fillMaxWidth(), onClick = { viewModel.openMemory(r.id) }, padding = PaddingValues(Gap.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(ra))
                        Spacer(Modifier.width(Gap.m))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.aiSummary, fontSize = Ts.meta, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (r.threadKey == m.threadKey && m.threadKey != "General") {
                            Text("same thread", fontSize = Ts.caption, fontWeight = FontWeight.Bold, color = Flow.Indigo)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun IconPill(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tint: androidx.compose.ui.graphics.Color = Flow.Indigo, onClick: () -> Unit) {
    val c = LocalFlow.current
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(c.glass).clickable(onClick = onClick).padding(horizontal = Gap.m, vertical = Gap.s),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(Gap.xs))
        Text(label, fontSize = Ts.meta, fontWeight = FontWeight.SemiBold, color = c.text)
    }
}
