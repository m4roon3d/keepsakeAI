package com.keepsakeai.app.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.ui.MindSpaceViewModel
import com.keepsakeai.app.ui.components.FlowHeader
import com.keepsakeai.app.ui.components.GlassButton
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.LogoMark
import com.keepsakeai.app.ui.components.MarkGlyph
import com.keepsakeai.app.ui.components.SectionLabel
import com.keepsakeai.app.ui.spend.DAY
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Every call trove made to an AI: who, why, when and exactly what was sent. */
@Composable
fun AiLogScreen(viewModel: MindSpaceViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalFlow.current
    val context = LocalContext.current
    val logs by viewModel.aiLogs.collectAsState()
    val week = logs.count { it.timestamp > System.currentTimeMillis() - 7 * DAY }
    val fmt = remember { SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH) }

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            FlowHeader(
                eyebrow = "what was sent, and when",
                title = "ai ",
                gradientSuffix = "activity",
                joinSuffix = true,
                leading = { LogoMark(size = 46.dp, glyph = MarkGlyph.ORBIT) }
            ) { GlassButton("close", onClick = onClose, icon = Icons.Rounded.Close) }
        }
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("$week calls this week · ${logs.size} kept", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.text)
                Spacer(Modifier.height(4.dp))
                Text(
                    "trove only talks to the AIs whose keys you added. Photos and your SMS stay on your phone: only the text shown below was sent (for payments, just the payee names).",
                    fontSize = 12.sp, color = c.muted
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GlassButton("export", onClick = {
                        val text = logs.joinToString("\n\n") { "${fmt.format(Date(it.timestamp))} · ${it.provider} · ${it.feature}\n${it.promptPreview}" }
                        val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text.ifBlank { "No AI calls yet." })
                        context.startActivity(Intent.createChooser(i, "Export AI activity"))
                    }, modifier = Modifier.weight(1f))
                    GlassButton("clear log", onClick = { viewModel.clearAiLogs() }, tint = Flow.Rose, modifier = Modifier.weight(1f))
                }
            }
        }
        if (logs.isEmpty()) {
            item { SectionLabel("no AI calls yet") }
        }
        items(logs, key = { it.id }) { log ->
            var open by remember { mutableStateOf(false) }
            val color = when (log.provider) { "Gemini" -> Flow.Indigo; "ChatGPT" -> Flow.Emerald; else -> Flow.Sky }
            GlassCard(Modifier.fillMaxWidth(), onClick = { open = !open }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.15f)).padding(horizontal = 9.dp, vertical = 3.dp)) {
                        Text(log.provider, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(log.feature, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.weight(1f))
                    Text(fmt.format(Date(log.timestamp)).lowercase(), fontSize = 11.sp, color = c.muted)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    if (open) log.promptPreview else log.promptPreview.replace("\n", " ").take(140) + "…",
                    fontSize = 11.sp, color = c.muted, fontFamily = if (open) FontFamily.Monospace else FontFamily.Default
                )
            }
        }
    }
}
