package com.keepsakeai.app.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.BuildConfig
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.capture.FloatingSnapService
import com.keepsakeai.app.context.DeviceContext
import com.keepsakeai.app.ui.components.DockClearance
import com.keepsakeai.app.ui.components.FlowHeader
import com.keepsakeai.app.ui.components.GlassButton
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.GlassField
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.components.Pill
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = LocalFlow.current
    val keyStore = (context.applicationContext as MindSpaceApp).apiKeyStore

    var geminiKey by remember { mutableStateOf(keyStore.geminiKey) }
    var openAiKey by remember { mutableStateOf(keyStore.openAiKey) }
    var perplexityKey by remember { mutableStateOf(keyStore.perplexityKey) }
    var currency by remember { mutableStateOf(keyStore.defaultCurrency.ifBlank { "INR" }) }
    var saved by remember { mutableStateOf(false) }
    var hasLocation by remember { mutableStateOf(DeviceContext.hasLocationPermission(context)) }

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasLocation = it.values.any { g -> g }
    }

    fun openAppInfo() = runCatching {
        context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")))
    }

    Column(
        modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = DockClearance),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FlowHeader(eyebrow = "Privacy first · keys stay on device", title = "Settings")

        GlassCard(Modifier.fillMaxWidth()) {
            CardTitle(Icons.Rounded.Key, "Your AI keys", Flow.Indigo)
            Spacer(Modifier.height(4.dp))
            Text("Stored encrypted on this phone. Only the keys you add are used.", fontSize = 12.sp, color = c.muted)
            Spacer(Modifier.height(10.dp))
            GlassField(geminiKey, { geminiKey = it; saved = false }, "Gemini API key", Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
            Spacer(Modifier.height(8.dp))
            GlassField(openAiKey, { openAiKey = it; saved = false }, "OpenAI API key", Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
            Spacer(Modifier.height(8.dp))
            GlassField(perplexityKey, { perplexityKey = it; saved = false }, "Perplexity API key", Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
            Spacer(Modifier.height(12.dp))
            Text("CURRENCY", style = MaterialTheme.typography.labelSmall, color = c.muted)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("INR" to "₹ INR", "USD" to "$ USD", "EUR" to "€ EUR").forEach { (code, label) ->
                    Pill(label, currency == code, { currency = code; saved = false }, Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(12.dp))
            GradientButton(
                if (saved) "Saved ✓" else "Save keys",
                onClick = {
                    keyStore.geminiKey = geminiKey
                    keyStore.openAiKey = openAiKey
                    keyStore.perplexityKey = perplexityKey
                    keyStore.defaultCurrency = currency
                    saved = true
                },
                modifier = Modifier.fillMaxWidth()
            )
        }

        GlassCard(Modifier.fillMaxWidth()) {
            CardTitle(Icons.Rounded.Security, "Permissions", Flow.Pink)
            Spacer(Modifier.height(10.dp))
            PermissionRow(
                Icons.Rounded.LocationOn, "Location", if (hasLocation) "On · used for weather & nearby answers" else "Off",
                hasLocation, Flow.Pink
            ) {
                if (hasLocation) openAppInfo()
                else locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }
            PermissionRow(
                Icons.Rounded.NotificationsActive, "UPI auto-detect", "Reads payment notifications from banks & UPI apps",
                null, Flow.Emerald
            ) { runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } }
            Spacer(Modifier.height(6.dp))
            Text(
                "Greyed-out switch? Open App info → ⋮ → Allow restricted settings, then try again.",
                fontSize = 11.sp, color = c.muted
            )
            Spacer(Modifier.height(8.dp))
            GlassButton("Open App info", onClick = { openAppInfo() }, modifier = Modifier.fillMaxWidth())
        }

        GlassCard(Modifier.fillMaxWidth()) {
            CardTitle(Icons.Rounded.Layers, "Floating Snap key", Flow.Purple)
            Spacer(Modifier.height(4.dp))
            Text("An iridescent capture pill on the edge of any app. Also try the \"Snap to keepsake AI\" Quick Settings tile.", fontSize = 12.sp, color = c.muted)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton(
                    "Start",
                    brush = Flow.auroraSweep,
                    onClick = {
                        if (!Settings.canDrawOverlays(context)) {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                        } else {
                            context.startService(Intent(context, FloatingSnapService::class.java))
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                GlassButton("Stop", onClick = { context.stopService(Intent(context, FloatingSnapService::class.java)) }, modifier = Modifier.weight(1f))
            }
        }

        Text(
            "keepsake AI ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            fontSize = 11.sp, color = c.muted,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

@Composable
private fun CardTitle(icon: ImageVector, title: String, tint: Color) {
    val c = LocalFlow.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(32.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(17.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text)
    }
}

@Composable
private fun PermissionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    on: Boolean?,
    tint: Color,
    onClick: () -> Unit
) {
    val c = LocalFlow.current
    GlassCard(Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
                Text(subtitle, fontSize = 11.sp, color = c.muted)
            }
            Text(
                when (on) { true -> "ON"; false -> "Turn on"; null -> "Open" },
                fontSize = 12.sp,
                color = if (on == true) Flow.Emerald else Flow.Indigo,
                style = MaterialTheme.typography.labelMedium
            )
        }
    }
}
