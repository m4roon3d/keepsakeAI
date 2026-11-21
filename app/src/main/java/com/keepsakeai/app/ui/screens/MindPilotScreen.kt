package com.keepsakeai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.keepsakeai.app.ai.AiProvider
import com.keepsakeai.app.ui.MindSpaceViewModel

@Composable
fun MindPilotScreen(
    viewModel: MindSpaceViewModel,
    modifier: Modifier = Modifier
) {
    val selectedProviders by viewModel.selectedProviders.collectAsState()
    val agentMode by viewModel.agentModeEnabled.collectAsState()
    val useMemory by viewModel.useMemoryContext.collectAsState()
    val isAsking by viewModel.isAskingPilot.collectAsState()
    val responses by viewModel.pilotResponses.collectAsState()

    var query by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("AI Mind Pilot", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // Multi-Model Selector Chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AiProvider.entries.forEach { provider ->
                FilterChip(
                    selected = provider in selectedProviders,
                    onClick = { viewModel.toggleProvider(provider) },
                    label = { Text(provider.tag) }
                )
            }
        }

        // Context + Agent Mode Toggles
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = useMemory, onCheckedChange = { viewModel.useMemoryContext.value = it })
                Spacer(Modifier.width(6.dp))
                Text("keepsake AI Context", style = MaterialTheme.typography.bodySmall)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = agentMode, onCheckedChange = { viewModel.agentModeEnabled.value = it })
                Spacer(Modifier.width(6.dp))
                Text("Agent Mode", style = MaterialTheme.typography.bodySmall)
            }
        }

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = {
                Text(
                    if (agentMode) "Give Agent Mode a multi-step goal (e.g. Plan my Bali itinerary)..."
                    else "Ask anything across your memories..."
                )
            },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Button(
            onClick = { viewModel.runMindPilot(query) },
            enabled = query.isNotBlank() && !isAsking,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isAsking) "Querying ${selectedProviders.size} model(s)..." else "Run Mind Pilot")
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(responses) { resp ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = resp.provider.displayName,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(resp.content, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
