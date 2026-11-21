package com.keepsakeai.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.capture.FloatingSnapService

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val keyStore = (context.applicationContext as MindSpaceApp).apiKeyStore

    var geminiKey by remember { mutableStateOf(keyStore.geminiKey) }
    var openAiKey by remember { mutableStateOf(keyStore.openAiKey) }
    var perplexityKey by remember { mutableStateOf(keyStore.perplexityKey) }
    var currency by remember { mutableStateOf(keyStore.defaultCurrency) }
    var savedBanner by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings & System Hooks", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        OutlinedTextField(
            value = geminiKey,
            onValueChange = { geminiKey = it },
            label = { Text("Gemini API Key (Primary + Capture OCR)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = openAiKey,
            onValueChange = { openAiKey = it },
            label = { Text("OpenAI API Key (Mind Pilot @ChatGPT)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = perplexityKey,
            onValueChange = { perplexityKey = it },
            label = { Text("Perplexity API Key (Mind Pilot @Perplexity)") },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = currency,
            onValueChange = { currency = it },
            label = { Text("Default Currency (e.g. INR, USD)") },
            modifier = Modifier.fillMaxWidth()
        )

        Button(
            onClick = {
                keyStore.geminiKey = geminiKey
                keyStore.openAiKey = openAiKey
                keyStore.perplexityKey = perplexityKey
                keyStore.defaultCurrency = currency
                savedBanner = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Save Encrypted Keys")
        }

        if (savedBanner) {
            Text("Saved securely to EncryptedSharedPreferences.", color = MaterialTheme.colorScheme.primary)
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("OS-Level Snap Key Overlay", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Enable Display Over Other Apps to float a quick Snap button on your screen, or add 'Snap to MindSpace' from Android Quick Settings.",
            style = MaterialTheme.typography.bodySmall
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = {
                    if (!Settings.canDrawOverlays(context)) {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${context.packageName}")
                        )
                        context.startActivity(intent)
                    } else {
                        context.startService(Intent(context, FloatingSnapService::class.java))
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Start Snap Overlay")
            }
            OutlinedButton(
                onClick = {
                    context.stopService(Intent(context, FloatingSnapService::class.java))
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Stop Overlay")
            }
        }
    }
}
