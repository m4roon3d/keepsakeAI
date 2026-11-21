package com.keepsakeai.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.speech.RecognizerIntent
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Screenshot
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.ui.MindSpaceViewModel
import com.keepsakeai.app.ui.components.DockClearance
import com.keepsakeai.app.ui.components.FlowHeader
import com.keepsakeai.app.ui.components.GlassButton
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.GlassField
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.components.GradientCard
import com.keepsakeai.app.ui.components.LogoMark
import com.keepsakeai.app.ui.components.Pill
import com.keepsakeai.app.ui.components.SectionLabel
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow

/** Accent per Smart Collection, used for card stripes and labels. */
fun collectionAccent(name: String): Pair<Color, Color> = when (name) {
    "Travel" -> Flow.Sky to Flow.Cyan
    "Study Notes" -> Flow.Indigo to Flow.Violet
    "Events" -> Flow.Pink to Flow.Purple
    "Personal Projects" -> Flow.Purple to Flow.Indigo
    "Receipts" -> Flow.Emerald to Flow.Teal
    "Watch Later" -> Flow.Amber to Flow.Pink
    else -> Flow.Indigo to Flow.Sky
}

@Composable
fun HomeFeedScreen(
    viewModel: MindSpaceViewModel,
    onTriggerScreenSnap: () -> Unit,
    onOpenPilot: () -> Unit,
    onOpenSnapsake: () -> Unit,
    onOpenSpend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val c = LocalFlow.current
    val memories by viewModel.filteredMemories.collectAsState()
    val allMemories by viewModel.allMemories.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val selectedCol by viewModel.selectedCollection.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()
    val pendingUpi by viewModel.pendingUpi.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val semantic by viewModel.semanticResults.collectAsState()

    var quickNote by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { viewModel.captureImageUri(it, CaptureSource.CAMERA_SCAN) }
    }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) viewModel.captureText(spoken, CaptureSource.VOICE_MEMO)
        }
    }

    val searching = searchQuery.isNotBlank()
    val cards: List<Pair<MemoryEntity, Float?>> =
        if (searching) semantic.map { it.memory to it.similarity } else memories.map { it to null }

    LazyVerticalStaggeredGrid(
        columns = StaggeredGridCells.Fixed(2),
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = DockClearance),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalItemSpacing = 10.dp
    ) {
        item(span = StaggeredGridItemSpan.FullLine) {
            FlowHeader(
                eyebrow = "Your memory hub",
                title = "keepsake",
                gradientSuffix = "AI",
                leading = { LogoMark(size = 46.dp) }
            ) {
                GlassButton("Snap", onClick = onTriggerScreenSnap, icon = Icons.Rounded.Screenshot)
            }
        }

        item(span = StaggeredGridItemSpan.FullLine) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassField(
                    value = searchQuery,
                    onValueChange = { viewModel.runSemanticSearch(it) },
                    placeholder = "Search memories by meaning…",
                    leadingIcon = Icons.Rounded.Search,
                    modifier = Modifier.weight(1f)
                )
                if (searching) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(44.dp).clip(CircleShape).background(c.glass)
                            .clickable { viewModel.runSemanticSearch("") },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Close, "Clear search", tint = c.muted) }
                }
            }
        }

        if (!searching) {
            // keepsake Suggest aurora card
            item(span = StaggeredGridItemSpan.FullLine) {
                GradientCard(Flow.suggestGradient, Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("KEEPSAKE SUGGEST", color = Color.White.copy(alpha = 0.92f), style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) { Text("AI Highlight", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold) }
                    }
                    Spacer(Modifier.height(8.dp))
                    val body = when {
                        pendingUpi.isNotEmpty() -> "${pendingUpi.size} UPI payment${if (pendingUpi.size > 1) "s are" else " is"} waiting for your approval."
                        suggestions.firstOrNull() != null -> suggestions.first().body
                        allMemories.isEmpty() -> "Share a link, snap a receipt or jot a thought. keepsake sorts it into Smart Collections for you."
                        else -> "${allMemories.size} memories organised into ${collections.size} Smart Collections. Ask Pilot to plan, summarise or find anything."
                    }
                    Text(body, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WhiteChip("Ask Pilot", solid = true, onClick = onOpenPilot)
                        if (pendingUpi.isNotEmpty()) WhiteChip("Review ${pendingUpi.size} UPI", onClick = onOpenSpend)
                        else WhiteChip("Refresh brief", onClick = { viewModel.refreshDailySuggest() })
                    }
                }
            }

            // Quick capture composer
            item(span = StaggeredGridItemSpan.FullLine) {
                GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 10.dp)) {
                    GlassField(
                        value = quickNote,
                        onValueChange = { quickNote = it },
                        placeholder = "Capture a thought, link or receipt…",
                        singleLine = false,
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    HorizontalDivider(color = c.divider, modifier = Modifier.padding(horizontal = 10.dp))
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 6.dp)) {
                        ComposerIcon(Icons.Rounded.CenterFocusWeak, "Snapsake", Flow.Indigo, onOpenSnapsake)
                        ComposerIcon(Icons.Rounded.Mic, "Voice note", Flow.Pink) {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                            }
                            runCatching { voiceLauncher.launch(intent) }
                        }
                        ComposerIcon(Icons.Rounded.AddPhotoAlternate, "Read text from a photo", Flow.Emerald) {
                            imagePicker.launch("image/*")
                        }
                        Spacer(Modifier.weight(1f))
                        GradientButton(
                            text = if (isCapturing) "Saving…" else "Save",
                            enabled = quickNote.isNotBlank() && !isCapturing,
                            onClick = {
                                viewModel.captureText(quickNote)
                                quickNote = ""
                            }
                        )
                    }
                }
            }

            item(span = StaggeredGridItemSpan.FullLine) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SectionLabel("Smart Collections", trailing = "${allMemories.size} items")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            Pill("All (${allMemories.size})", selectedCol == null, { viewModel.selectedCollection.value = null })
                        }
                        items(collections) { col ->
                            val n = allMemories.count { it.collectionName == col }
                            Pill("$col ($n)", selectedCol == col, { viewModel.selectedCollection.value = col })
                        }
                    }
                }
            }
        } else {
            item(span = StaggeredGridItemSpan.FullLine) {
                SectionLabel("Closest matches", trailing = "${cards.size} found")
            }
        }

        if (cards.isEmpty()) {
            item(span = StaggeredGridItemSpan.FullLine) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text(
                        if (searching) "Nothing close to that yet." else "Your space is empty. Share anything to keepsake from any app, or type a note above.",
                        color = c.muted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(cards, key = { it.first.id }) { (mem, score) ->
                MemoryCard(mem, score, onDelete = { viewModel.deleteMemory(mem) })
            }
        }
    }
}

@Composable
private fun WhiteChip(text: String, solid: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (solid) Color.White else Color.White.copy(alpha = 0.22f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text, color = if (solid) Flow.IndigoDeep else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ComposerIcon(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(tint.copy(alpha = 0.10f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = tint, modifier = Modifier.size(20.dp)) }
    Spacer(Modifier.width(6.dp))
}

@Composable
private fun MemoryCard(mem: MemoryEntity, score: Float?, onDelete: () -> Unit) {
    val c = LocalFlow.current
    val (a1, a2) = collectionAccent(mem.collectionName)
    val photo = mem.photoUrisCsv.split(",").firstOrNull { it.isNotBlank() }
    GlassCard(padding = PaddingValues(12.dp)) {
        if (photo != null) {
            AsyncImage(
                model = photo,
                contentDescription = mem.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(16.dp))
            )
            Spacer(Modifier.height(10.dp))
        } else {
            Box(
                Modifier.width(40.dp).height(5.dp).clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(a1, a2)))
            )
            Spacer(Modifier.height(10.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                mem.collectionName.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = a1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Rounded.DeleteOutline, "Delete",
                tint = c.muted,
                modifier = Modifier.size(18.dp).clip(CircleShape).clickable(onClick = onDelete)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(mem.title, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(mem.aiSummary, style = MaterialTheme.typography.bodySmall, color = c.muted, maxLines = 5, overflow = TextOverflow.Ellipsis)
        val tag = mem.tagsCsv.split(",").firstOrNull { it.isNotBlank() }
        if (tag != null || score != null) {
            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = c.divider)
            Spacer(Modifier.height(6.dp))
            Row {
                Text(tag?.let { "#$it" } ?: "", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Flow.Sky, modifier = Modifier.weight(1f), maxLines = 1)
                if (score != null) Text("${(score * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Flow.Emerald)
            }
        }
    }
}
