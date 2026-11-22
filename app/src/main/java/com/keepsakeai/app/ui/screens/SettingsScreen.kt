package com.keepsakeai.app.ui.screens

import android.Manifest
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.runtime.collectAsState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.glance.appwidget.updateAll
import com.keepsakeai.app.widget.MindSpaceWidget
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.BuildConfig
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.capture.FloatingSnapService
import com.keepsakeai.app.context.DeviceContext
import com.keepsakeai.app.data.preferences.ApiKeyStore
import com.keepsakeai.app.ui.components.DockClearance
import com.keepsakeai.app.ui.components.FlowHeader
import com.keepsakeai.app.ui.components.GlassButton
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.GlassField
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.components.LogoMark
import com.keepsakeai.app.ui.components.MarkGlyph
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.DarkMode
import com.keepsakeai.app.ui.components.Pill
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow
import com.keepsakeai.app.ui.theme.Palettes
import com.keepsakeai.app.ui.theme.ThemeMode
import com.keepsakeai.app.ui.theme.ThemeStore

private data class KeySpec(
    val name: String,
    val hint: String,
    val color: Color,
    val getKeyUrl: String,
    val read: (ApiKeyStore) -> String,
    val write: (ApiKeyStore, String) -> Unit,
)

private val keySpecs = listOf(
    KeySpec("Gemini", "Sorting, Pilot & live web search", Color(0xFF6366F1), "https://aistudio.google.com/apikey", { it.geminiKey }, { s, v -> s.geminiKey = v }),
    KeySpec("ChatGPT", "Second opinion in Pilot", Color(0xFF10B981), "https://platform.openai.com/api-keys", { it.openAiKey }, { s, v -> s.openAiKey = v }),
    KeySpec("Perplexity", "Web-sourced answers in Pilot", Color(0xFF0EA5E9), "https://www.perplexity.ai/settings/api", { it.perplexityKey }, { s, v -> s.perplexityKey = v }),
)

private fun masked(key: String) = if (key.length <= 4) "••••" else "••••" + key.takeLast(4)

@Composable
fun SettingsScreen(modifier: Modifier = Modifier, viewModel: com.keepsakeai.app.ui.MindSpaceViewModel? = null) {
    val context = LocalContext.current
    val c = LocalFlow.current
    val keyStore = (context.applicationContext as MindSpaceApp).apiKeyStore

    var currency by remember { mutableStateOf(keyStore.defaultCurrency.ifBlank { "INR" }) }
    var hasLocation by remember { mutableStateOf(DeviceContext.hasLocationPermission(context)) }
    var expandedKey by remember { mutableStateOf<String?>(null) }
    var notifOn by remember { mutableStateOf(com.keepsakeai.app.notify.Notifier.canNotify(context)) }
    val scope = rememberCoroutineScope()

    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        hasLocation = it.values.any { g -> g }
    }
    var hasMediaLocation by remember { mutableStateOf(com.keepsakeai.app.capture.MediaMeta.canReadPhotoLocation(context)) }
    val mediaLocationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasMediaLocation = it }
    var hasCalendar by remember { mutableStateOf(com.keepsakeai.app.calendar.CalendarReader.hasPermission(context)) }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCalendar = granted
        if (granted) viewModel?.refreshCalendar()
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
        FlowHeader(
            eyebrow = "make it yours",
            title = "",
            gradientSuffix = "settings",
            joinSuffix = true,
            leading = { LogoMark(size = 46.dp, glyph = MarkGlyph.TUNE) }
        ) {
            val next = ThemeMode.entries[(ThemeStore.mode.ordinal + 1) % ThemeMode.entries.size]
            GlassButton(
                ThemeStore.mode.label.lowercase(),
                onClick = { ThemeStore.setMode(context, next) },
                icon = when (ThemeStore.mode) {
                    ThemeMode.SYSTEM -> Icons.Rounded.BrightnessAuto
                    ThemeMode.LIGHT -> Icons.Rounded.LightMode
                    ThemeMode.DARK -> Icons.Rounded.DarkMode
                }
            )
        }

        // ── Look & feel ────────────────────────────────────────────
        GlassCard(Modifier.fillMaxWidth()) {
            CardTitle(Icons.Rounded.Palette, "Theme", Flow.Purple)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Palettes.all.forEach { p ->
                    val selected = ThemeStore.palette.id == p.id
                    val shape = RoundedCornerShape(20.dp)
                    Column(
                        Modifier
                            .width(78.dp)
                            .clip(shape)
                            .background(if (selected) p.primary.copy(alpha = 0.12f) else Color.Transparent)
                            .border(if (selected) 2.dp else 1.dp, if (selected) p.primary else c.glassBorder, shape)
                            .clickable {
                                ThemeStore.setPalette(context, p)
                                scope.launch { runCatching { MindSpaceWidget().updateAll(context) } }
                            }
                            .padding(vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(52.dp).clip(RoundedCornerShape(16.dp))
                                .background(if (c.dark) p.darkBase else Color(0xFF111827)),
                            contentAlignment = Alignment.Center
                        ) { LogoMark(size = 46.dp, palette = p) }
                        Spacer(Modifier.height(6.dp))
                        Text(p.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (selected) p.primary else c.text)
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(ThemeStore.palette.tagline, fontSize = 11.sp, color = c.muted, modifier = Modifier.padding(start = 2.dp))
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    Pill(m.label, ThemeStore.mode == m, { ThemeStore.setMode(context, m) }, Modifier.weight(1f))
                }
            }
        }

        // ── AI keys: one slim row each, tap to edit ────────────────
        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
            CardTitle(Icons.Rounded.Key, "AI models", Flow.Indigo, trailing = "Encrypted on device")
            Spacer(Modifier.height(6.dp))
            keySpecs.forEachIndexed { i, spec ->
                if (i > 0) HorizontalDivider(color = c.divider)
                KeyRow(
                    spec = spec,
                    store = keyStore,
                    expanded = expandedKey == spec.name,
                    onToggle = { expandedKey = if (expandedKey == spec.name) null else spec.name },
                    onDone = { expandedKey = null }
                )
            }
            HorizontalDivider(color = c.divider)
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Currency", style = MaterialTheme.typography.titleSmall, color = c.text, modifier = Modifier.weight(1f))
                listOf("INR" to "₹", "USD" to "$", "EUR" to "€").forEach { (code, sym) ->
                    val sel = currency == code
                    Box(
                        Modifier.padding(start = 6.dp).size(36.dp).clip(CircleShape)
                            .background(if (sel) c.chipSelected else c.glass)
                            .border(1.dp, if (sel) Color.Transparent else c.glassBorder, CircleShape)
                            .clickable { currency = code; keyStore.defaultCurrency = code },
                        contentAlignment = Alignment.Center
                    ) { Text(sym, fontWeight = FontWeight.Bold, color = if (sel) c.onChipSelected else c.text) }
                }
            }
        }

        // ── Permissions ────────────────────────────────────────────
        GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
            CardTitle(Icons.Rounded.Security, "Permissions", Flow.Pink)
            Spacer(Modifier.height(4.dp))
            SlimRow(Icons.Rounded.LocationOn, Flow.Pink, "Location", if (hasLocation) "Weather & nearby answers" else "Off", if (hasLocation) "On" else "Turn on", hasLocation) {
                if (hasLocation) openAppInfo()
                else locationLauncher.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION))
            }
            HorizontalDivider(color = c.divider)
            SlimRow(Icons.Rounded.CalendarMonth, Flow.Sky, "Calendar", if (hasCalendar) "Events in your brief, home and Pilot" else "Off", if (hasCalendar) "On" else "Turn on", hasCalendar) {
                if (hasCalendar) openAppInfo() else calendarLauncher.launch(Manifest.permission.READ_CALENDAR)
            }
            HorizontalDivider(color = c.divider)
            SlimRow(Icons.Rounded.Place, Flow.Amber, "Photo locations", if (hasMediaLocation) "Names the café or place in your photos" else "Off", if (hasMediaLocation) "On" else "Turn on", hasMediaLocation) {
                if (hasMediaLocation) openAppInfo() else mediaLocationLauncher.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
            }
            HorizontalDivider(color = c.divider)
            SlimRow(Icons.Rounded.NotificationsActive, Flow.Emerald, "UPI auto-detect", "Reads bank & UPI payment alerts", "Open", null) {
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            }
            HorizontalDivider(color = c.divider)
            SlimRow(
                Icons.Rounded.Layers, Flow.Purple, "Floating Snap key", "Capture pill over any app", "Start", null,
                secondary = "Stop" to { context.stopService(Intent(context, FloatingSnapService::class.java)) }
            ) {
                if (!Settings.canDrawOverlays(context)) {
                    context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${context.packageName}")))
                } else {
                    context.startService(Intent(context, FloatingSnapService::class.java))
                }
            }
            Text(
                "Switch greyed out? App info → ⋮ → Allow restricted settings.",
                fontSize = 11.sp, color = c.muted,
                modifier = Modifier.padding(top = 6.dp).clickable { openAppInfo() }
            )
        }

        // ── Insights ───────────────────────────────────────────────
        if (viewModel != null) {
            val logs by viewModel.aiLogs.collectAsState()
            GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
                CardTitle(Icons.Rounded.Insights, "Insights", Flow.Indigo)
                Spacer(Modifier.height(4.dp))
                val nudges = remember { com.keepsakeai.app.data.preferences.NudgePrefs(context) }
                var briefOn by remember { mutableStateOf(nudges.morningBrief) }
                var journalOn by remember { mutableStateOf(nudges.eveningJournal) }
                SlimRow(Icons.Rounded.WbSunny, Flow.Amber, "Morning brief", "8am: today, money, worth revisiting", if (briefOn) "On" else "Off", briefOn,
                    secondary = "open" to { viewModel.overlay.value = "brief" }) {
                    briefOn = !briefOn; nudges.morningBrief = briefOn
                }
                HorizontalDivider(color = c.divider)
                SlimRow(Icons.Rounded.Mic, Flow.Pink, "Evening journal nudge", "9:30pm: talk for a minute about your day", if (journalOn) "On" else "Off", journalOn,
                    secondary = "record" to { viewModel.overlay.value = "journal" }) {
                    journalOn = !journalOn; nudges.eveningJournal = journalOn
                }
                HorizontalDivider(color = c.divider)
                SlimRow(Icons.Rounded.CalendarViewWeek, Flow.Indigo, "Weekly review", "Spend, highlights and one insight", "Open", null) {
                    viewModel.overlay.value = "review"
                }
                HorizontalDivider(color = c.divider)
                SlimRow(Icons.Rounded.Visibility, Flow.Sky, "AI activity", "${logs.size} calls logged · see exactly what was sent", "Open", null) {
                    viewModel.overlay.value = "ai_log"
                }
                HorizontalDivider(color = c.divider)
                val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifOn = it }
                SlimRow(
                    Icons.Rounded.NotificationsActive, Flow.Amber, "Notifications",
                    "Budgets, renewals, reminders, weekly review", if (notifOn) "On" else "Turn on", notifOn
                ) {
                    if (!notifOn && android.os.Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else openAppInfo()
                }
            }
        }

        // ── Email ──────────────────────────────────────────────────
        if (viewModel != null) EmailCard(viewModel)

        // ── Privacy & backup ───────────────────────────────────────
        PrivacyCard(viewModel)

        // ── Footer ─────────────────────────────────────────────────
        Column(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LogoMark(size = 40.dp, halo = false)
            Spacer(Modifier.height(6.dp))
            Text("trove", style = MaterialTheme.typography.titleSmall, color = c.text)
            Text("v${BuildConfig.VERSION_NAME} · build ${BuildConfig.VERSION_CODE}", fontSize = 11.sp, color = c.muted)
        }
    }
}

@Composable
private fun KeyRow(
    spec: KeySpec,
    store: ApiKeyStore,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDone: () -> Unit,
) {
    val c = LocalFlow.current
    val context = LocalContext.current
    var saved by remember { mutableStateOf(spec.read(store)) }
    var draft by remember(expanded) { mutableStateOf(if (expanded) saved else "") }
    val connected = saved.isNotBlank()

    Column {
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onToggle).padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(30.dp).clip(CircleShape).background(spec.color.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) { Text(spec.name.take(1), color = spec.color, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(spec.name, style = MaterialTheme.typography.titleSmall, color = c.text)
                Text(spec.hint, fontSize = 11.sp, color = c.muted, maxLines = 1)
            }
            Row(
                Modifier.clip(RoundedCornerShape(50))
                    .background(if (connected) Flow.Emerald.copy(alpha = 0.12f) else c.glass)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (connected) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Flow.Emerald))
                    Spacer(Modifier.width(5.dp))
                }
                Text(
                    if (connected) masked(saved) else "Add key",
                    fontSize = 11.sp, fontWeight = FontWeight.Bold,
                    color = if (connected) Flow.Emerald else Flow.Indigo
                )
            }
            Icon(
                Icons.Rounded.ExpandMore, null, tint = c.muted,
                modifier = Modifier.padding(start = 4.dp).size(20.dp).rotate(if (expanded) 180f else 0f)
            )
        }
        AnimatedVisibility(expanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(bottom = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassField(
                        draft, { draft = it.trim() }, "Paste your ${spec.name} key",
                        Modifier.weight(1f),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier.size(46.dp).clip(CircleShape).background(spec.color.copy(alpha = 0.14f))
                            .clickable {
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString()?.trim()?.let { draft = it }
                            },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.ContentPaste, "Paste", tint = spec.color, modifier = Modifier.size(19.dp)) }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.clip(RoundedCornerShape(50)).clickable {
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(spec.getKeyUrl))) }
                        }.padding(horizontal = 6.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Get a key", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = spec.color)
                        Spacer(Modifier.width(3.dp))
                        Icon(Icons.Rounded.OpenInNew, null, tint = spec.color, modifier = Modifier.size(13.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    if (connected) {
                        Text(
                            "Remove", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Flow.Rose,
                            modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                                spec.write(store, ""); saved = ""; onDone()
                            }.padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                    GradientButton(
                        "Save",
                        enabled = draft.isNotBlank() && draft != saved,
                        onClick = { spec.write(store, draft); saved = draft; onDone() }
                    )
                }
            }
        }
    }
}

@Composable
private fun CardTitle(icon: ImageVector, title: String, tint: Color, trailing: String? = null) {
    val c = LocalFlow.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(CircleShape).background(tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.width(10.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.text, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, fontSize = 11.sp, color = c.muted)
    }
}

@Composable
private fun SlimRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String,
    action: String,
    on: Boolean?,
    secondary: Pair<String, () -> Unit>? = null,
    onClick: () -> Unit
) {
    val c = LocalFlow.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = c.text)
            Text(subtitle, fontSize = 11.sp, color = c.muted, maxLines = 1)
        }
        if (secondary != null) {
            Text(
                secondary.first, style = MaterialTheme.typography.labelMedium, color = c.muted,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = secondary.second).padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
        Text(action, style = MaterialTheme.typography.labelMedium, color = if (on == true) Flow.Emerald else Flow.Indigo)
    }
}
