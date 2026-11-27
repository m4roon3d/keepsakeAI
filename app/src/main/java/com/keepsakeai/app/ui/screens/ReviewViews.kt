package com.keepsakeai.app.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import com.keepsakeai.app.ui.components.ProgressRing
import com.keepsakeai.app.ui.components.appear
import com.keepsakeai.app.ui.theme.Display
import com.keepsakeai.app.ui.theme.Ts
import com.keepsakeai.app.ui.theme.Gap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.spend.dayLabel
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow

/** The two-minute pass over what you saved in the last few days: keep it, make it a to-do, or let it go. */
fun LazyStaggeredGridScope.reviewItems(
    pending: List<MemoryEntity>,
    total: Int,
    onKeep: (MemoryEntity) -> Unit,
    onToDo: (MemoryEntity) -> Unit,
    onDelete: (MemoryEntity) -> Unit,
    onOpen: (Long) -> Unit,
) {
    item(span = StaggeredGridItemSpan.FullLine, key = "reviewHead") {
        val c = LocalFlow.current
        val done = (total - pending.size).coerceAtLeast(0)
        GlassCard(Modifier.fillMaxWidth().appear(), padding = PaddingValues(Gap.m)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProgressRing(if (total == 0) 1f else done.toFloat() / total, Flow.Emerald, size = 56.dp, stroke = 6.dp) {
                    Text(if (pending.isEmpty()) "✓" else "${pending.size}", fontSize = Ts.lead, fontFamily = Display, fontWeight = FontWeight.Bold, color = c.text)
                }
                Spacer(Modifier.width(Gap.m))
                Column(Modifier.weight(1f)) {
                    Text(if (pending.isEmpty()) "All caught up" else "${pending.size} waiting", fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text)
                    Text(
                        if (pending.isEmpty()) "Nothing from the last few days needs a decision."
                        else "$done of $total from the last few days sorted. Keep it, make it a to-do, or delete it.",
                        fontSize = Ts.meta, color = c.muted
                    )
                }
            }
        }
    }
    pending.forEach { m ->
        item(span = StaggeredGridItemSpan.FullLine, key = "review${m.id}") {
            val c = LocalFlow.current
            GlassCard(Modifier.animateItem().fillMaxWidth(), padding = PaddingValues(Gap.m)) {
                Column(Modifier.clickable { onOpen(m.id) }) {
                    Text(m.title, fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val body = m.aiSummary.ifBlank { m.rawContent }
                    if (body.isNotBlank()) {
                        Text(body, fontSize = Ts.meta, color = c.muted, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = Gap.xs))
                    }
                    Text(dayLabel(com.keepsakeai.app.ui.spend.startOfDay(m.createdAt)) + " · " + m.collectionName, fontSize = Ts.meta, color = c.muted, modifier = Modifier.padding(top = Gap.xs))
                }
                Row(Modifier.fillMaxWidth().padding(top = Gap.s), horizontalArrangement = Arrangement.spacedBy(Gap.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text("keep", fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = Flow.Amber, modifier = Modifier.clickable { onKeep(m) }.padding(Gap.s))
                    Text("to-do", fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text, modifier = Modifier.clickable { onToDo(m) }.padding(Gap.s))
                    Text("delete", fontSize = Ts.body, color = c.soft, modifier = Modifier.clickable { onDelete(m) }.padding(Gap.s))
                }
            }
        }
    }
}
