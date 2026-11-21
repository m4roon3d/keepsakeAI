package com.keepsakeai.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.ui.MindSpaceViewModel

@Composable
fun HomeFeedScreen(
    viewModel: MindSpaceViewModel,
    onTriggerScreenSnap: () -> Unit,
    onOpenPilot: () -> Unit,
    modifier: Modifier = Modifier
) {
    val memories by viewModel.filteredMemories.collectAsState()
    val collections by viewModel.collections.collectAsState()
    val selectedCol by viewModel.selectedCollection.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()
    val isCapturing by viewModel.isCapturing.collectAsState()

    var quickNote by remember { mutableStateOf("") }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { viewModel.captureImageUri(it, CaptureSource.CAMERA_SCAN) }
    }

    val voiceLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                viewModel.captureText(spoken, CaptureSource.VOICE_MEMO)
            }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "keepsake AI",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                FilledTonalButton(onClick = onTriggerScreenSnap) {
                    Icon(Icons.Default.Screenshot, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Screen Snap")
                }
            }
        }

        // AI Mind Suggest Daily Card
        item {
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("AI Mind Suggest", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(6.dp))
                    val latest = suggestions.firstOrNull()
                    Text(
                        text = latest?.body ?: "Tap refresh to synthesize your saved memories into today's brief.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { viewModel.refreshDailySuggest() }) {
                            Text("Refresh Digest")
                        }
                        TextButton(onClick = onOpenPilot) {
                            Text("Ask Mind Pilot")
                        }
                    }
                }
            }
        }

        // Quick Capture Box
        item {
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quickNote,
                        onValueChange = { quickNote = it },
                        label = { Text("Snap a thought, link, or expense...") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { imagePicker.launch("image/*") }) {
                                Icon(Icons.Default.ImageSearch, contentDescription = "Scan Image / Receipt")
                            }
                            IconButton(onClick = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                }
                                runCatching { voiceLauncher.launch(intent) }
                            }) {
                                Icon(Icons.Default.Mic, contentDescription = "Voice Memo")
                            }
                        }
                        Button(
                            enabled = quickNote.isNotBlank() && !isCapturing,
                            onClick = {
                                viewModel.captureText(quickNote)
                                quickNote = ""
                            }
                        ) {
                            Text(if (isCapturing) "Analyzing..." else "Save to keepsake")
                        }
                    }
                }
            }
        }

        // Smart Collections Filter Chips
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedCol == null,
                    onClick = { viewModel.selectedCollection.value = null },
                    label = { Text("All") }
                )
                collections.forEach { col ->
                    FilterChip(
                        selected = selectedCol == col,
                        onClick = { viewModel.selectedCollection.value = col },
                        label = { Text(col) }
                    )
                }
            }
        }

        // Saved Memory Cards
        if (memories.isEmpty()) {
            item {
                Text(
                    text = "No memories yet. Share a link from any app, snap a receipt, or type a note above.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(memories, key = { it.id }) { mem ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AssistChip(onClick = {}, label = { Text(mem.collectionName) })
                            IconButton(onClick = { viewModel.deleteMemory(mem) }) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = "Delete")
                            }
                        }
                        Text(mem.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(mem.aiSummary, style = MaterialTheme.typography.bodyMedium)
                        if (mem.tagsCsv.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = mem.tagsCsv.split(",").joinToString("  ") { "#$it" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}
