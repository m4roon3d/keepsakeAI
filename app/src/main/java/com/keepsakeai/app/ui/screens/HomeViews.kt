package com.keepsakeai.app.ui.screens

import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Receipt
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.data.local.ExpenseEntity
import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.data.local.PersonalFactEntity
import com.keepsakeai.app.data.preferences.KeepReminder
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.GlassField
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.components.SectionLabel
import com.keepsakeai.app.ui.spend.DAY
import com.keepsakeai.app.ui.spend.dayLabel
import com.keepsakeai.app.ui.spend.startOfDay
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class HubView(val label: String) {
    KEEPSAKES("finds"), TODOS("to-dos"), PEOPLE("people & places"), THREADS("threads"), TIMELINE("timeline"), FACTS("about me"), IMPORTS("imports")
}

private fun rupees(v: Double) = "₹" + String.format(Locale.US, "%,.0f", v)

@Composable
fun HubSwitcher(current: HubView, factCount: Int, onPick: (HubView) -> Unit) {
    val c = LocalFlow.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(c.glass).border(1.dp, c.glassBorder, RoundedCornerShape(50)).padding(4.dp)
    ) {
        HubView.entries.forEach { v ->
            val sel = v == current
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(50)).background(if (sel) c.chipSelected else Color.Transparent)
                    .clickable { onPick(v) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (v == HubView.FACTS && factCount > 0) "facts ($factCount)" else v.label,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (sel) c.onChipSelected else c.muted, maxLines = 1
                )
            }
        }
    }
}

// ── On this day ──────────────────────────────────────────────────────

internal data class Echo(val label: String, val memories: List<MemoryEntity>, val spent: Double)

internal fun echoes(memories: List<MemoryEntity>, expenses: List<ExpenseEntity>, now: Long = System.currentTimeMillis()): List<Echo> {
    fun dayBack(field: Int, n: Int): Long = startOfDay(Calendar.getInstance().apply { timeInMillis = now; add(field, -n) }.timeInMillis)
    return listOf(
        "a week ago today" to dayBack(Calendar.DAY_OF_YEAR, 7),
        "a month ago today" to dayBack(Calendar.MONTH, 1),
        "a year ago today" to dayBack(Calendar.YEAR, 1),
    ).mapNotNull { (label, start) ->
        val m = memories.filter { it.createdAt in start until start + DAY }
        val s = expenses.filter { it.timestamp in start until start + DAY }.sumOf { it.amount }
        if (m.isEmpty()) null else Echo(label, m, s)
    }
}

@Composable
fun OnThisDayCard(memories: List<MemoryEntity>, expenses: List<ExpenseEntity>, onOpen: (Long) -> Unit = {}) {
    val list = remember(memories, expenses) { echoes(memories, expenses) }
    if (list.isEmpty()) return
    val c = LocalFlow.current
    GlassCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.History, null, tint = Flow.Purple, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("on this day", style = MaterialTheme.typography.titleSmall, color = c.text)
        }
        list.forEach { e ->
            Spacer(Modifier.height(8.dp))
            Text(e.label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Flow.Purple)
            e.memories.take(2).forEach { m ->
                Text(
                    "• ${m.title}", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { onOpen(m.id) }.padding(vertical = 3.dp)
                )
            }
            if (e.memories.size > 2) Text("+${e.memories.size - 2} more", fontSize = 11.sp, color = c.muted)
            if (e.spent > 0) Text("and you spent ${rupees(e.spent)} that day", fontSize = 11.sp, color = c.muted)
        }
    }
}

// ── Reminders found in captures ──────────────────────────────────────

@Composable
fun ReminderSuggestionCard(r: KeepReminder, onRemind: () -> Unit, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalFlow.current
    val context = LocalContext.current
    val whenText = SimpleDateFormat(if (r.hasTime) "EEE d MMM, h:mm a" else "EEE d MMM", Locale.ENGLISH).format(Date(r.atMillis))
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Flow.Indigo.copy(alpha = 0.16f), Flow.Sky.copy(alpha = 0.10f))))
            .border(1.dp, Flow.Indigo.copy(alpha = 0.35f), shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(if (r.kind == "event") Icons.Rounded.Event else Icons.Rounded.CalendarMonth, null, tint = Flow.Indigo, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(r.title, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text((if (r.kind == "due") "due " else if (r.kind == "deadline") "deadline " else "") + whenText.lowercase(), fontSize = 12.sp, color = c.muted)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (r.status == "scheduled") {
                Text("reminder set ✓", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Flow.Emerald, modifier = Modifier.padding(end = 8.dp))
            } else {
                GradientButton("remind me", onClick = onRemind)
            }
            Spacer(Modifier.width(6.dp))
            Text(
                "add to calendar", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Flow.Indigo,
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable {
                    val i = Intent(Intent.ACTION_INSERT).setData(CalendarContract.Events.CONTENT_URI)
                        .putExtra(CalendarContract.Events.TITLE, r.title)
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, r.atMillis)
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, r.atMillis + 60 * 60 * 1000)
                        .putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, !r.hasTime)
                    runCatching { context.startActivity(i) }
                }.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            Spacer(Modifier.weight(1f))
            Text(
                "✕", fontSize = 14.sp, color = c.muted,
                modifier = Modifier.clip(CircleShape).clickable(onClick = onDismiss).padding(8.dp)
            )
        }
    }
}

// ── Timeline: memories and payments by day ───────────────────────────

private sealed class TimelineEntry(val time: Long) {
    class Mem(val m: MemoryEntity) : TimelineEntry(m.createdAt)
    class Pay(val e: ExpenseEntity) : TimelineEntry(e.timestamp)
}

fun LazyStaggeredGridScope.timelineItems(memories: List<MemoryEntity>, expenses: List<ExpenseEntity>, onOpen: (Long) -> Unit = {}) {
    val since = System.currentTimeMillis() - 60 * DAY
    val entries = (memories.filter { it.createdAt >= since }.map { TimelineEntry.Mem(it) } +
        expenses.filter { it.timestamp >= since }.map { TimelineEntry.Pay(it) })
        .sortedByDescending { it.time }
    val byDay = entries.groupBy { startOfDay(it.time) }.toList().sortedByDescending { it.first }
    if (byDay.isEmpty()) {
        item(span = StaggeredGridItemSpan.FullLine) {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("Nothing in the last 60 days yet.", color = LocalFlow.current.muted)
            }
        }
        return
    }
    byDay.forEach { (day, list) ->
        item(span = StaggeredGridItemSpan.FullLine, key = "day$day") {
            val spent = list.filterIsInstance<TimelineEntry.Pay>().sumOf { it.e.amount }
            SectionLabel(dayLabel(day), trailing = if (spent > 0) "spent ${rupees(spent)}" else null, modifier = Modifier.padding(top = 6.dp))
        }
        item(span = StaggeredGridItemSpan.FullLine, key = "list$day") {
            val c = LocalFlow.current
            GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(vertical = 4.dp)) {
                list.forEach { entry ->
                    val time = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(entry.time)).lowercase()
                    Row(
                        Modifier.fillMaxWidth()
                            .then(if (entry is TimelineEntry.Mem) Modifier.clickable { onOpen(entry.m.id) } else Modifier)
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(time, fontSize = 11.sp, color = c.muted, modifier = Modifier.width(58.dp))
                        when (entry) {
                            is TimelineEntry.Mem -> {
                                val (a1, _) = collectionAccent(entry.m.collectionName)
                                Box(Modifier.size(8.dp).clip(CircleShape).background(a1))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(entry.m.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(entry.m.collectionName.lowercase(), fontSize = 11.sp, color = c.muted)
                                }
                            }
                            is TimelineEntry.Pay -> {
                                Icon(Icons.Rounded.Receipt, null, tint = Flow.Emerald, modifier = Modifier.size(14.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(entry.e.merchant, fontSize = 13.sp, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(rupees(entry.e.amount), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Flow.Emerald)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Facts ────────────────────────────────────────────────────────────

fun LazyStaggeredGridScope.factItems(
    facts: List<PersonalFactEntity>,
    onAdd: (String) -> Unit,
    onPin: (PersonalFactEntity) -> Unit,
    onDelete: (PersonalFactEntity) -> Unit,
) {
    item(span = StaggeredGridItemSpan.FullLine, key = "factInput") {
        val c = LocalFlow.current
        var draft by remember { mutableStateOf("") }
        Column {
            GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassField(draft, { draft = it }, "Teach trove about you…", Modifier.weight(1f), bare = true)
                    GradientButton("remember", enabled = draft.isNotBlank(), onClick = { onAdd(draft); draft = "" })
                }
            }
            Text(
                "Pilot uses these to personalise answers. trove also learns them from what you save. Pin the important ones.",
                fontSize = 11.sp, color = c.muted, modifier = Modifier.padding(start = 6.dp, top = 8.dp)
            )
        }
    }
    if (facts.isEmpty()) return
    facts.groupBy { it.category }.toList().sortedBy { it.first }.forEach { (cat, list) ->
        item(span = StaggeredGridItemSpan.FullLine, key = "fcat$cat") { SectionLabel(cat, modifier = Modifier.padding(top = 4.dp)) }
        list.forEach { f ->
            item(key = "fact${f.id}") {
                val c = LocalFlow.current
                GlassCard(padding = PaddingValues(12.dp)) {
                    Text(f.fact, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Rounded.PushPin, if (f.pinned) "Unpin" else "Pin",
                            tint = if (f.pinned) Flow.Amber else c.muted,
                            modifier = Modifier.size(18.dp).clip(CircleShape).clickable { onPin(f) }
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(Icons.Rounded.DeleteOutline, "Forget", tint = c.muted, modifier = Modifier.size(18.dp).clip(CircleShape).clickable { onDelete(f) })
                    }
                }
            }
        }
    }
}

// ── Threads ──────────────────────────────────────────────────────────

fun LazyStaggeredGridScope.threadItems(memories: List<MemoryEntity>, onAsk: (String) -> Unit, onOpen: (Long) -> Unit = {}) {
    val threads = memories.groupBy { it.threadKey.ifBlank { it.collectionName } }
        .filterValues { it.size >= 1 }
        .toList()
        .sortedByDescending { (_, v) -> v.maxOf { it.createdAt } }
    if (threads.isEmpty()) {
        item(span = StaggeredGridItemSpan.FullLine) {
            GlassCard(Modifier.fillMaxWidth()) { Text("Threads appear as you save related things.", color = LocalFlow.current.muted) }
        }
        return
    }
    threads.forEach { (name, items) ->
        item(span = StaggeredGridItemSpan.FullLine, key = "thread$name") {
            val c = LocalFlow.current
            var open by remember { mutableStateOf(false) }
            val sorted = items.sortedByDescending { it.createdAt }
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.fillMaxWidth().clickable { open = !open }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(name, style = MaterialTheme.typography.titleSmall, color = c.text)
                        Text("${items.size} linked · last ${dayLabel(startOfDay(sorted.first().createdAt))}", fontSize = 11.sp, color = c.muted)
                    }
                    Box(Modifier.clip(RoundedCornerShape(50)).background(Flow.Indigo.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
                        Text("${items.size}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Flow.Indigo)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Column(Modifier.padding(start = 6.dp)) {
                    (if (open) sorted else sorted.take(3)).forEach { m ->
                        Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().clickable { onOpen(m.id) }.padding(vertical = 4.dp)) {
                            Box(Modifier.padding(top = 5.dp).size(7.dp).clip(CircleShape).background(collectionAccent(m.collectionName).first))
                            Spacer(Modifier.width(10.dp))
                            Column {
                                Text(m.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(m.aiSummary, fontSize = 11.sp, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                    if (!open && sorted.size > 3) Text("+${sorted.size - 3} more · tap to expand", fontSize = 11.sp, color = c.muted)
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(50)).background(Flow.Indigo.copy(alpha = 0.12f))
                        .clickable { onAsk("Summarise everything I've saved about \"$name\" and suggest what I should do next.") }
                        .padding(vertical = 9.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null, tint = Flow.Indigo, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("ask pilot about $name", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Flow.Indigo, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}


// ── Memory-first home pieces ─────────────────────────────────────────

/** The day at a glance: next reminder, what you kept lately, quick ways in. */
@Composable
fun TodayCard(
    next: KeepReminder?,
    memories: List<MemoryEntity>,
    onAsk: () -> Unit,
    onTimeline: () -> Unit,
    openTodos: Int = 0,
    dueToday: Int = 0,
    onTodos: () -> Unit = {},
    onBrief: () -> Unit = {},
    onJournal: () -> Unit = {},
    nextEvent: com.keepsakeai.app.calendar.CalEvent? = null,
) {
    val now = System.currentTimeMillis()
    val today = startOfDay(now)
    val keptToday = memories.count { it.createdAt >= today }
    val keptWeek = memories.count { it.createdAt >= now - 7 * DAY }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) { in 5..11 -> "good morning"; in 12..16 -> "good afternoon"; in 17..21 -> "good evening"; else -> "late night thoughts?" }
    val date = SimpleDateFormat("EEEE d MMMM", Locale.ENGLISH).format(Date(now)).lowercase()
    com.keepsakeai.app.ui.components.GradientCard(Flow.suggestGradient, Modifier.fillMaxWidth()) {
        Text("$greeting · $date", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        val headline = when {
            memories.isEmpty() -> "Your second brain is empty. Snap, share or jot your first memory."
            nextEvent != null && (next == null || nextEvent.begin <= next.atMillis) -> {
                val day = when {
                    nextEvent.begin < today + DAY -> "today"
                    nextEvent.begin < today + 2 * DAY -> "tomorrow"
                    else -> SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(Date(nextEvent.begin)).lowercase()
                }
                "Next up: ${nextEvent.title} · $day" + if (nextEvent.allDay) "" else ", ${nextEvent.timeLabel()}"
            }
            next != null -> {
                val whenText = SimpleDateFormat(if (next.hasTime) "EEE d MMM, h:mm a" else "EEE d MMM", Locale.ENGLISH).format(Date(next.atMillis)).lowercase()
                "Next up: ${next.title} · $whenText"
            }
            dueToday > 0 -> "$dueToday to-do${if (dueToday > 1) "s" else ""} due today. Knock ${if (dueToday > 1) "them" else "it"} out?"
            keptToday > 0 -> "You kept $keptToday thing${if (keptToday > 1) "s" else ""} today. Ask Pilot to connect the dots."
            else -> "$keptWeek memories this week, ${memories.size} in all. What's on your mind?"
        }
        Text(headline, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 23.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HomeChip("ask pilot", solid = true, onClick = onAsk)
            if (hour < 12) HomeChip("brief", onClick = onBrief) else if (hour >= 18) HomeChip("journal", onClick = onJournal)
            if (openTodos > 0) HomeChip("$openTodos to-do${if (openTodos > 1) "s" else ""}", onClick = onTodos)
            else HomeChip("timeline", onClick = onTimeline)
        }
    }
}

/** What's left on the phone calendar today (and early tomorrow); tap opens the event in the calendar app. */
@Composable
fun CalendarTodayCard(events: List<com.keepsakeai.app.calendar.CalEvent>, modifier: Modifier = Modifier) {
    if (events.isEmpty()) return
    val c = LocalFlow.current
    val context = LocalContext.current
    com.keepsakeai.app.ui.components.GlassCard(modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.CalendarMonth, null, tint = Flow.Sky, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("ON YOUR CALENDAR", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.2.sp, color = c.muted)
        }
        events.forEach { e ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                    .clickable { runCatching { context.startActivity(com.keepsakeai.app.calendar.CalendarReader.openIntent(e.id)) } }
                    .padding(vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val now = System.currentTimeMillis()
                val live = !e.allDay && e.begin <= now && e.end > now
                val label = when {
                    live -> "now"
                    e.begin >= startOfDay(now) + DAY -> "tmrw " + e.timeLabel()
                    else -> e.timeLabel()
                }
                Text(
                    label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    color = if (live) Flow.Emerald else Flow.Sky, modifier = Modifier.width(78.dp)
                )
                Column(Modifier.weight(1f)) {
                    Text(e.title, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (e.location.isNotBlank()) Text(e.location, fontSize = 11.sp, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun HomeChip(text: String, solid: Boolean = false, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(if (solid) Color.White else Color.White.copy(alpha = 0.22f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(text, color = if (solid) Flow.IndigoDeep else Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
}

/** Threads you're in the middle of, newest first. */
@Composable
fun ThreadsCarousel(memories: List<MemoryEntity>, onOpen: () -> Unit) {
    val threads = memories.filter { it.threadKey.isNotBlank() && it.threadKey != "General" }
        .groupBy { it.threadKey }
        .filterValues { it.size >= 2 }
        .toList()
        .sortedByDescending { (_, v) -> v.maxOf { it.createdAt } }
        .take(6)
    if (threads.isEmpty()) return
    val c = LocalFlow.current
    Column {
        SectionLabel("pick up where you left off", modifier = Modifier.padding(bottom = 8.dp))
        androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(threads.size) { i ->
                val (name, items) = threads[i]
                val latest = items.maxBy { it.createdAt }
                val (a1, a2) = collectionAccent(latest.collectionName)
                GlassCard(Modifier.width(190.dp), onClick = onOpen, padding = PaddingValues(12.dp)) {
                    Box(Modifier.width(32.dp).height(4.dp).clip(CircleShape).background(Brush.horizontalGradient(listOf(a1, a2))))
                    Spacer(Modifier.height(8.dp))
                    Text(name, style = MaterialTheme.typography.titleSmall, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${items.size} linked · ${dayLabel(startOfDay(latest.createdAt))}", fontSize = 11.sp, color = c.muted)
                    Spacer(Modifier.height(6.dp))
                    Text(latest.title, fontSize = 12.sp, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Money as one module of the hub: this month's spend and anything to review. */
@Composable
fun MoneyRow(expenses: List<ExpenseEntity>, pending: Int, onOpen: () -> Unit) {
    if (expenses.isEmpty() && pending == 0) return
    val c = LocalFlow.current
    val monthStart = Calendar.getInstance().apply {
        set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val month = expenses.filter { it.timestamp >= monthStart }.sumOf { it.amount }
    GlassCard(Modifier.fillMaxWidth(), onClick = onOpen, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(Flow.Emerald.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Text("₹", fontWeight = FontWeight.ExtraBold, color = Flow.Emerald)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${rupees(month)} this month", style = MaterialTheme.typography.titleSmall, color = c.text)
                Text(if (pending > 0) "$pending payment${if (pending > 1) "s" else ""} to review" else "spend, budgets & subscriptions", fontSize = 11.sp, color = if (pending > 0) Flow.Amber else c.muted)
            }
            Text("spend →", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Flow.Emerald)
        }
    }
}
