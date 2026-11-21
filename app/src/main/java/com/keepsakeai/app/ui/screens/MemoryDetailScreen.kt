package com.keepsakeai.app.ui.screens

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

    val m = memory
    LaunchedEffect(m?.id, m?.title) { if (m != null) related = viewModel.relatedTo(m) }
    LaunchedEffect(id) { viewModel.markViewed(id) }
    if (m == null) return

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

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // Top bar
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(c.glass).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = c.text)
                }
                Spacer(Modifier.weight(1f))
                if (!editing) {
                    IconPill(Icons.Rounded.Edit, "edit") {
                        title = m.title; summary = m.aiSummary; body = m.rawContent; editing = true
                    }
                    Spacer(Modifier.width(8.dp))
                    IconPill(Icons.Rounded.IosShare, "share") {
                        val text = "${m.title}\n\n${m.aiSummary}" + (m.sourceUrl?.let { "\n\n$it" } ?: "")
                        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share memory"))
                    }
                    Spacer(Modifier.width(8.dp))
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
                Spacer(Modifier.height(10.dp))
                if (editing) {
                    GlassField(title, { title = it }, "Title", Modifier.fillMaxWidth())
                } else {
                    Text(m.title, style = MaterialTheme.typography.headlineSmall, color = c.text)
                }
                Spacer(Modifier.height(6.dp))
                val date = SimpleDateFormat("EEE d MMM yyyy, h:mm a", Locale.ENGLISH).format(Date(m.createdAt)).lowercase()
                Text("$date · ${sourceLabel(m)}", fontSize = 12.sp, color = c.muted)
            }
        }
        item {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("thread · ${m.threadKey}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Flow.Indigo)
                }
            }
        }

        // Summary + full content
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("summary")
                Spacer(Modifier.height(6.dp))
                if (editing) GlassField(summary, { summary = it }, "Summary", Modifier.fillMaxWidth(), singleLine = false, minLines = 2)
                else Text(m.aiSummary, style = MaterialTheme.typography.bodyLarge, color = c.text)
                if (m.tagsCsv.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(m.tagsCsv.split(",").filter { it.isNotBlank() }.joinToString("  ") { "#${it.trim()}" }, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Flow.Sky)
                }
            }
        }
        m.sourceUrl?.takeIf { it.isNotBlank() }?.let { url ->
            item {
                GlassCard(Modifier.fillMaxWidth(), onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Link, null, tint = Flow.Indigo, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(url, fontSize = 12.sp, color = Flow.Indigo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                SectionLabel("full text")
                Spacer(Modifier.height(6.dp))
                if (editing) GlassField(body, { body = it }, "Notes", Modifier.fillMaxWidth(), singleLine = false, minLines = 4)
                else SelectionContainer { Text(m.rawContent, style = MaterialTheme.typography.bodyMedium, color = c.text) }
            }
        }
        if (editing) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("cancel", onClick = { editing = false }, modifier = Modifier.weight(1f))
                    GradientButton("save", onClick = {
                        viewModel.updateMemory(m.copy(title = title.ifBlank { m.title }, aiSummary = summary, rawContent = body))
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
                    onClick = {
                        viewModel.pilotDraft.value = "About my saved memory \"${m.title}\": what are the key points, anything I should act on, and what else I've saved that relates to it?"
                        viewModel.closeAllMemories()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // Related by meaning
        if (related.isNotEmpty()) {
            item { SectionLabel("related memories", modifier = Modifier.padding(top = 6.dp)) }
            items(related, key = { it.id }) { r ->
                val (ra, _) = collectionAccent(r.collectionName)
                GlassCard(Modifier.fillMaxWidth(), onClick = { viewModel.openMemory(r.id) }, padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(ra))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(r.aiSummary, fontSize = 11.sp, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        if (r.threadKey == m.threadKey && m.threadKey != "General") {
                            Text("same thread", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Flow.Indigo)
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
        Modifier.clip(RoundedCornerShape(50)).background(c.glass).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(5.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.text)
    }
}
