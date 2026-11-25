package com.keepsakeai.app.ui.screens

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
    onKeep: (MemoryEntity) -> Unit,
    onToDo: (MemoryEntity) -> Unit,
    onDelete: (MemoryEntity) -> Unit,
    onOpen: (Long) -> Unit,
) {
    item(span = StaggeredGridItemSpan.FullLine, key = "reviewHead") {
        val c = LocalFlow.current
        Text(
            if (pending.isEmpty()) "All caught up. Nothing from the last few days is waiting."
            else "${pending.size} from the last few days. Keep it, turn it into a to-do, or delete it.",
            fontSize = 12.sp, color = c.muted, modifier = Modifier.padding(start = 6.dp, bottom = 4.dp)
        )
    }
    pending.forEach { m ->
        item(span = StaggeredGridItemSpan.FullLine, key = "review${m.id}") {
            val c = LocalFlow.current
            GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(12.dp)) {
                Column(Modifier.clickable { onOpen(m.id) }) {
                    Text(m.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val body = m.aiSummary.ifBlank { m.rawContent }
                    if (body.isNotBlank()) {
                        Text(body, fontSize = 12.sp, color = c.muted, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    }
                    Text(dayLabel(com.keepsakeai.app.ui.spend.startOfDay(m.createdAt)) + " · " + m.collectionName, fontSize = 11.sp, color = c.muted, modifier = Modifier.padding(top = 4.dp))
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("keep", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Flow.Amber, modifier = Modifier.clickable { onKeep(m) }.padding(8.dp))
                    Text("to-do", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.text, modifier = Modifier.clickable { onToDo(m) }.padding(8.dp))
                    Text("delete", fontSize = 13.sp, color = c.muted, modifier = Modifier.clickable { onDelete(m) }.padding(8.dp))
                }
            }
        }
    }
}
