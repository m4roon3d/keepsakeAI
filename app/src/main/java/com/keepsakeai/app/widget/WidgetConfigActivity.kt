package com.keepsakeai.app.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.keepsakeai.app.ui.components.GlassButton
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.theme.Display
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.Gap
import com.keepsakeai.app.ui.theme.LocalFlow
import com.keepsakeai.app.ui.theme.MindSpaceTheme
import com.keepsakeai.app.ui.theme.ThemeStore
import com.keepsakeai.app.ui.theme.Ts
import kotlinx.coroutines.launch

/** Opens when a trove widget is added (or long-pressed and reconfigured): pick how see-through it is and its colours. */
class WidgetConfigActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) { finish(); return }
        ThemeStore.load(this)
        val initial = WidgetStyleStore.get(this, id)
        setContent {
            MindSpaceTheme {
                val scope = rememberCoroutineScope()
                ConfigScreen(
                    initial,
                    onSave = { s ->
                        WidgetStyleStore.save(this, id, s)
                        scope.launch {
                            TroveWidgets.updateAll(applicationContext)
                            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
                            finish()
                        }
                    },
                    onCancel = { finish() }
                )
            }
        }
    }
}

@Composable
private fun ConfigScreen(initial: WidgetStyle, onSave: (WidgetStyle) -> Unit, onCancel: () -> Unit) {
    val c = LocalFlow.current
    var opacity by remember { mutableFloatStateOf(initial.opacity.toFloat()) }
    var tint by remember { mutableStateOf(initial.tint) }
    var light by remember { mutableStateOf(initial.lightText) }
    val style = WidgetStyle(opacity.toInt(), tint, light)
    val wc = style.colors(ThemeStore.palette)

    Column(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(c.base, c.baseBottom))).systemBarsPadding().padding(Gap.l),
        verticalArrangement = Arrangement.spacedBy(Gap.l)
    ) {
        Column {
            Text("widget", fontSize = Ts.meta, color = c.muted, fontWeight = FontWeight.SemiBold)
            Text("look and feel", style = MaterialTheme.typography.headlineMedium, color = c.text)
        }

        // Preview over a stand-in wallpaper, so "see-through" actually shows something behind it.
        Box(
            Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(28.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF6D28D9), Color(0xFFDB2777), Color(0xFFF59E0B), Color(0xFF0EA5E9))))
                .padding(Gap.xl),
            contentAlignment = Alignment.Center
        ) {
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(wc.bg).padding(Gap.l)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("friday", color = wc.text, fontSize = Ts.lead, fontFamily = Display, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("27 nov", color = wc.soft, fontSize = Ts.meta)
                }
                Spacer(Modifier.height(Gap.s))
                PreviewLine(wc.accent, "Team sync", "calendar · 11:00 am", wc)
                PreviewLine(wc.alert, "Electricity bill", "due today · ₹2,340", wc)
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(Gap.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("background", fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.weight(1f))
                Text(
                    when { opacity < 5 -> "clear"; opacity > 95 -> "solid"; else -> "${100 - opacity.toInt()}% see-through" },
                    fontSize = Ts.meta, color = c.muted
                )
            }
            Slider(
                value = opacity, onValueChange = { opacity = it }, valueRange = 0f..100f,
                colors = SliderDefaults.colors(thumbColor = Flow.Indigo, activeTrackColor = Flow.Indigo, inactiveTrackColor = c.divider)
            )
            Row {
                Text("transparent", fontSize = Ts.caption, color = c.muted, modifier = Modifier.weight(1f))
                Text("translucent", fontSize = Ts.caption, color = c.muted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text("solid", fontSize = Ts.caption, color = c.muted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
            }
        }

        Option("tint", listOf(WidgetStyle.TINT_THEME to "trove theme", WidgetStyle.TINT_BLACK to "black", WidgetStyle.TINT_WHITE to "white"), tint) { tint = it }
        Option("text", listOf("light" to "light text", "dark" to "dark text"), if (light) "light" else "dark") { light = it == "light" }
        Text(
            "Light text suits dark wallpapers, dark text suits light ones. Long-press the widget later to change this.",
            fontSize = Ts.meta, color = c.muted
        )

        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s), verticalAlignment = Alignment.CenterVertically) {
            GlassButton("cancel", onClick = onCancel)
            GradientButton("save", onClick = { onSave(style) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun PreviewLine(dot: Color, title: String, sub: String, wc: WidgetColors) {
    Row(Modifier.padding(vertical = Gap.xs), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(dot))
        Spacer(Modifier.width(Gap.s))
        Column {
            Text(title, color = wc.text, fontSize = Ts.body, fontWeight = FontWeight.Medium)
            Text(sub, color = wc.soft, fontSize = Ts.meta)
        }
    }
}

@Composable
private fun Option(label: String, choices: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) {
    val c = LocalFlow.current
    Column(verticalArrangement = Arrangement.spacedBy(Gap.s)) {
        Text(label, fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text)
        Row(horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
            choices.forEach { (key, text) ->
                val on = key == selected
                Box(
                    Modifier.clip(CircleShape).background(if (on) c.chipSelected else c.glass)
                        .border(1.dp, if (on) c.chipSelected else c.glassBorder, CircleShape)
                        .clickable { onPick(key) }.padding(horizontal = Gap.l, vertical = Gap.s)
                ) { Text(text, fontSize = Ts.body, fontWeight = FontWeight.SemiBold, color = if (on) c.onChipSelected else c.text) }
            }
        }
    }
}
