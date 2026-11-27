package com.keepsakeai.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.remember
import com.keepsakeai.app.ui.components.FillBar
import com.keepsakeai.app.ui.components.appear
import com.keepsakeai.app.ui.theme.Ts
import com.keepsakeai.app.ui.theme.Gap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.data.preferences.KeepReminder
import com.keepsakeai.app.expiry.Expiries
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.components.SectionLabel
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Warranties, insurance, memberships and other things that end, grouped by how close they are. */
fun LazyStaggeredGridScope.expiryItems(
    reminders: List<KeepReminder>,
    scanning: Boolean,
    onScan: () -> Unit,
    onOpen: (Long) -> Unit,
    onDismiss: (KeepReminder) -> Unit,
) {
    val now = System.currentTimeMillis()
    val list = Expiries.list(reminders, now)
    item(span = StaggeredGridItemSpan.FullLine, key = "expiryHead") {
        val c = LocalFlow.current
        Column {
            Text(
                "Warranties, insurance, passports, memberships. trove picks these up when you save a bill, receipt or document, and warns you a month and a week ahead.",
                fontSize = Ts.meta, color = c.muted, modifier = Modifier.padding(start = Gap.s, bottom = Gap.s)
            )
            GradientButton(if (scanning) "looking…" else "look through older saves", enabled = !scanning, onClick = onScan)
        }
    }
    if (list.isEmpty()) {
        item(span = StaggeredGridItemSpan.FullLine, key = "expiryEmpty") {
            Text("Nothing is expiring yet.", fontSize = Ts.body, color = LocalFlow.current.muted, modifier = Modifier.padding(Gap.s))
        }
        return
    }
    item(span = StaggeredGridItemSpan.FullLine, key = "expiryTimeline") {
        GlassCard(Modifier.fillMaxWidth().appear(), padding = PaddingValues(Gap.m)) { ExpiryTimeline(list, now) }
    }
    val fmt = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)
    list.groupBy { Expiries.bucket(Expiries.daysLeft(it, now)) }.toList().sortedBy { it.first.ordinal }.forEach { (bucket, items) ->
        item(span = StaggeredGridItemSpan.FullLine, key = "expiryB${bucket.name}") { SectionLabel(bucket.label, modifier = Modifier.padding(top = Gap.xs)) }
        items.forEach { r ->
            item(span = StaggeredGridItemSpan.FullLine, key = "expiry${r.id}") {
                val c = LocalFlow.current
                val d = Expiries.daysLeft(r, now)
                val left = when {
                    d < 0 -> "ended ${-d} day" + (if (d == -1) "" else "s") + " ago"
                    d == 0 -> "ends today"
                    d == 1 -> "ends tomorrow"
                    d < 60 -> "$d days left"
                    else -> "${d / 30} months left"
                }
                GlassCard(Modifier.animateItem().fillMaxWidth().clickable(enabled = r.memoryId > 0) { onOpen(r.memoryId) }, padding = PaddingValues(Gap.m)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text)
                            Text(
                                fmt.format(Date(r.atMillis)) + " · " + left, fontSize = Ts.meta,
                                color = if (d in 0..7) Flow.Amber else c.muted
                            )
                        }
                        Text("done", fontSize = Ts.meta, color = c.muted, modifier = Modifier.clickable { onDismiss(r) }.padding(Gap.s))
                    }
                    if (d in 0..30) {
                        Spacer(Modifier.height(Gap.s))
                        FillBar(1f - d / 30f, if (d <= 7) Flow.Rose else Flow.Amber, height = 4.dp)
                    }
                }
            }
        }
    }
}

/** The next twelve months on one line: a dot for each thing that ends, red if it ends within a week. */
@Composable
private fun ExpiryTimeline(list: List<KeepReminder>, now: Long) {
    val c = LocalFlow.current
    val grow = remember { androidx.compose.animation.core.Animatable(0f) }
    androidx.compose.runtime.LaunchedEffect(list.size) { grow.snapTo(0f); grow.animateTo(1f, androidx.compose.animation.core.tween(900)) }
    val ahead = list.filter { Expiries.daysLeft(it, now) in 0..365 }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("next 12 months", fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.weight(1f))
            Text("${ahead.size} ending", fontSize = Ts.meta, color = c.muted)
        }
        Spacer(Modifier.height(Gap.m))
        androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val y = size.height / 2
            val track = 3.dp.toPx()
            drawLine(c.divider, androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width, y), track, androidx.compose.ui.graphics.StrokeCap.Round)
            drawLine(Flow.Indigo.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(0f, y), androidx.compose.ui.geometry.Offset(size.width * grow.value, y), track, androidx.compose.ui.graphics.StrokeCap.Round)
            for (q in 1..3) {
                val x = size.width * q / 4f
                drawLine(c.muted.copy(alpha = 0.5f), androidx.compose.ui.geometry.Offset(x, y - 5.dp.toPx()), androidx.compose.ui.geometry.Offset(x, y + 5.dp.toPx()), 1.dp.toPx())
            }
            ahead.forEach { r ->
                val d = Expiries.daysLeft(r, now)
                val x = (size.width * d / 365f).coerceIn(6.dp.toPx(), size.width - 6.dp.toPx())
                if (x <= size.width * grow.value + 1f) {
                    val col = when { d <= 7 -> Flow.Rose; d <= 30 -> Flow.Amber; else -> Flow.Indigo }
                    drawCircle(col.copy(alpha = 0.25f), 9.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y))
                    drawCircle(col, 5.dp.toPx(), androidx.compose.ui.geometry.Offset(x, y))
                }
            }
        }
        Spacer(Modifier.height(Gap.xs))
        Row {
            Text("now", fontSize = Ts.caption, color = c.muted, modifier = Modifier.weight(1f))
            Text("6 months", fontSize = Ts.caption, color = c.muted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Text("1 year", fontSize = Ts.caption, color = c.muted, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        }
    }
}
