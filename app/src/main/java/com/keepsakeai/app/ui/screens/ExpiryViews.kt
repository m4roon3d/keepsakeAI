package com.keepsakeai.app.ui.screens

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
                fontSize = 11.sp, color = c.muted, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp)
            )
            GradientButton(if (scanning) "looking…" else "look through older saves", enabled = !scanning, onClick = onScan)
        }
    }
    if (list.isEmpty()) {
        item(span = StaggeredGridItemSpan.FullLine, key = "expiryEmpty") {
            Text("Nothing is expiring yet.", fontSize = 13.sp, color = LocalFlow.current.muted, modifier = Modifier.padding(6.dp))
        }
        return
    }
    val fmt = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)
    list.groupBy { Expiries.bucket(Expiries.daysLeft(it, now)) }.toList().sortedBy { it.first.ordinal }.forEach { (bucket, items) ->
        item(span = StaggeredGridItemSpan.FullLine, key = "expiryB${bucket.name}") { SectionLabel(bucket.label, modifier = Modifier.padding(top = 4.dp)) }
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
                GlassCard(Modifier.fillMaxWidth().clickable(enabled = r.memoryId > 0) { onOpen(r.memoryId) }, padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(Modifier.weight(1f)) {
                            Text(r.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                            Text(
                                fmt.format(Date(r.atMillis)) + " · " + left, fontSize = 11.sp,
                                color = if (d in 0..7) Flow.Amber else c.muted
                            )
                        }
                        Text("done", fontSize = 12.sp, color = c.muted, modifier = Modifier.clickable { onDismiss(r) }.padding(6.dp))
                    }
                }
            }
        }
    }
}
