package com.keepsakeai.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.finance.ImportSection
import com.keepsakeai.app.finance.ImportSections
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.Pill
import com.keepsakeai.app.ui.components.SectionLabel
import com.keepsakeai.app.ui.spend.dayLabel
import com.keepsakeai.app.ui.spend.startOfDay
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow

/**
 * Everything that came in from email and SMS, sorted by kind. These don't get home-feed cards; this is where to browse them
 * (search also finds them).
 */
fun LazyStaggeredGridScope.importItems(memories: List<MemoryEntity>, onOpen: (Long) -> Unit) {
    val imports = memories.filter { ImportSections.isImport(it) }
    val grouped = imports.groupBy { ImportSections.of(it) ?: ImportSection.OTHER }

    item(span = StaggeredGridItemSpan.FullLine, key = "importsBody") {
        val c = LocalFlow.current
        var picked by rememberSaveable { mutableStateOf(ImportSection.UPI.name) }
        var shown by remember(picked) { mutableIntStateOf(30) }
        val section = ImportSection.entries.firstOrNull { it.name == picked } ?: ImportSection.UPI
        val list = grouped[section].orEmpty().sortedByDescending { it.createdAt }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (imports.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Text("Nothing imported yet. Connect a mailbox in Settings or allow the SMS listener.", color = c.muted, style = MaterialTheme.typography.bodyMedium)
                }
                return@Column
            }
            SectionLabel("imported from email and SMS", trailing = "${imports.size} kept")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ImportSection.entries.toList()) { s ->
                    Pill("${s.label} (${grouped[s]?.size ?: 0})", section == s, { picked = s.name })
                }
            }
            if (list.isEmpty()) {
                GlassCard(Modifier.fillMaxWidth()) { Text("Nothing here.", color = c.muted) }
            } else {
                GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 4.dp)) {
                    list.take(shown).forEach { m ->
                        Column(
                            Modifier.fillMaxWidth().clickable { onOpen(m.id) }.padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(m.title, color = c.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(dayLabel(startOfDay(m.createdAt)), fontSize = 11.sp, color = c.muted, modifier = Modifier.padding(start = 8.dp))
                            }
                            if (m.aiSummary.isNotBlank()) {
                                Text(m.aiSummary, fontSize = 11.sp, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (list.size > shown) {
                        Text(
                            "show more (${list.size - shown} left)", color = Flow.Indigo, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                            modifier = Modifier.fillMaxWidth().clickable { shown += 30 }.padding(14.dp)
                        )
                    }
                }
            }
        }
    }
}
