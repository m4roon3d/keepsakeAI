package com.keepsakeai.app.ui.screens

import android.app.DatePickerDialog
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridScope
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Place
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.TravelExplore
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.data.local.ExpenseEntity
import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.data.preferences.Entity
import com.keepsakeai.app.data.preferences.Todo
import com.keepsakeai.app.data.preferences.TodoBucket
import com.keepsakeai.app.data.preferences.bucketOf
import com.keepsakeai.app.ui.MindSpaceViewModel
import com.keepsakeai.app.ui.components.GlassButton
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.GlassField
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.components.SectionLabel
import com.keepsakeai.app.ui.spend.dayLabel
import com.keepsakeai.app.ui.spend.startOfDay
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private fun rupees(v: Double) = "₹" + String.format(Locale.US, "%,.0f", v)

fun typeColor(type: String): Color = when (type) {
    "person" -> Flow.Pink
    "place" -> Flow.Sky
    "project" -> Flow.Purple
    else -> Flow.Amber
}

private fun typeLabel(type: String) = when (type) {
    "person" -> "people"; "place" -> "places"; "project" -> "projects"; else -> "organisations"
}

// ── To-dos ───────────────────────────────────────────────────────────

fun LazyStaggeredGridScope.todoItems(
    todos: List<Todo>,
    onAdd: (String, Long?) -> Unit,
    onToggle: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onRemind: (Todo) -> Unit,
    onSetDue: (Long, Long?) -> Unit,
    onOpenSource: (Long) -> Unit,
) {
    item(span = StaggeredGridItemSpan.FullLine, key = "todoInput") {
        val c = LocalFlow.current
        val context = LocalContext.current
        var draft by remember { mutableStateOf("") }
        var due by remember { mutableStateOf<Long?>(null) }
        Column {
            GlassCard(Modifier.fillMaxWidth(), padding = PaddingValues(start = 6.dp, end = 10.dp, top = 6.dp, bottom = 6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassField(draft, { draft = it }, "Add a to-do…", Modifier.weight(1f), bare = true)
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).background(if (due != null) Flow.Indigo.copy(alpha = 0.16f) else c.glass)
                            .clickable { pickDate(context, due) { due = it } },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Rounded.Event, "Due date", tint = if (due != null) Flow.Indigo else c.muted, modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(6.dp))
                    GradientButton("add", enabled = draft.isNotBlank(), onClick = { onAdd(draft, due); draft = ""; due = null })
                }
            }
            if (due != null) {
                Text("due ${dayLabel(startOfDay(due!!))}", fontSize = 11.sp, color = Flow.Indigo, modifier = Modifier.padding(start = 10.dp, top = 6.dp))
            }
            Text(
                "trove also adds to-dos from what you save: bills to pay, tickets to book, people to call.",
                fontSize = 11.sp, color = c.muted, modifier = Modifier.padding(start = 6.dp, top = 6.dp)
            )
        }
    }
    val now = System.currentTimeMillis()
    val today = startOfDay(now)
    val grouped = todos.groupBy { bucketOf(it, now, today) }
    if (todos.none { !it.done }) {
        item(span = StaggeredGridItemSpan.FullLine, key = "todoEmpty") {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("All clear. Nothing on your list.", color = LocalFlow.current.muted)
            }
        }
    }
    TodoBucket.entries.forEach { b ->
        val list = grouped[b].orEmpty().sortedWith(compareBy({ it.due ?: Long.MAX_VALUE }, { it.createdAt }))
        if (list.isEmpty()) return@forEach
        item(span = StaggeredGridItemSpan.FullLine, key = "tb${b.name}") {
            SectionLabel(
                b.label, trailing = "${list.size}",
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        val shown = if (b == TodoBucket.DONE) list.sortedByDescending { it.doneAt }.take(10) else list
        shown.forEach { t ->
            item(span = StaggeredGridItemSpan.FullLine, key = "todo${t.id}") {
                TodoRow(t, b, Modifier.animateItem(), onToggle, onDelete, onRemind, onSetDue, onOpenSource)
            }
        }
    }
}

@Composable
private fun TodoRow(
    t: Todo,
    bucket: TodoBucket,
    modifier: Modifier,
    onToggle: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onRemind: (Todo) -> Unit,
    onSetDue: (Long, Long?) -> Unit,
    onOpenSource: (Long) -> Unit,
) {
    val c = LocalFlow.current
    val context = LocalContext.current
    var confirm by remember { mutableStateOf(false) }
    val checkScale by animateFloatAsState(if (t.done) 1f else 0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "check")
    val ring by animateColorAsState(if (t.done) Flow.Emerald else if (bucket == TodoBucket.OVERDUE) Flow.Rose else c.muted, label = "ring")
    val rowAlpha by animateFloatAsState(if (t.done) 0.55f else 1f, label = "alpha")

    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("delete to-do?") },
            text = { Text(t.text) },
            confirmButton = { TextButton(onClick = { onDelete(t.id); confirm = false }) { Text("delete", color = Flow.Rose, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("cancel") } }
        )
    }

    GlassCard(
        modifier.fillMaxWidth().alpha(rowAlpha).pointerInput(t.id) { detectTapGestures(onLongPress = { confirm = true }) },
        shape = RoundedCornerShape(20.dp), padding = PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(26.dp).clip(CircleShape).border(2.dp, ring, CircleShape)
                    .background(if (t.done) Flow.Emerald.copy(alpha = 0.18f) else Color.Transparent)
                    .clickable { onToggle(t.id) },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.Check, if (t.done) "Mark not done" else "Mark done", tint = Flow.Emerald, modifier = Modifier.size(16.dp).scale(checkScale))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    t.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = c.text,
                    textDecoration = if (t.done) TextDecoration.LineThrough else TextDecoration.None, maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val due = t.due
                    if (due != null) {
                        Text(
                            "due ${dayLabel(startOfDay(due))}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                            color = if (bucket == TodoBucket.OVERDUE) Flow.Rose else Flow.Indigo,
                            modifier = Modifier.clickable { pickDate(context, due) { d -> onSetDue(t.id, d) } }
                        )
                    } else if (!t.done) {
                        Text("+ due date", fontSize = 11.sp, color = c.muted, modifier = Modifier.clickable { pickDate(context, null) { d -> onSetDue(t.id, d) } })
                    }
                    val src = t.memoryId
                    if (src != null) {
                        Text("  ·  from a memory", fontSize = 11.sp, color = c.muted, modifier = Modifier.clickable { onOpenSource(src) })
                    }
                }
            }
            if (!t.done && t.due != null) {
                Icon(
                    Icons.Rounded.NotificationsActive, "Remind me", tint = Flow.Amber,
                    modifier = Modifier.size(20.dp).clip(CircleShape).clickable { onRemind(t) }
                )
            }
        }
    }
}

private fun pickDate(context: android.content.Context, current: Long?, onPick: (Long?) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = current ?: System.currentTimeMillis() }
    DatePickerDialog(context, { _, y, m, d ->
        onPick(Calendar.getInstance().apply { set(y, m, d, 9, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis)
    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
}

// ── People, places & projects ────────────────────────────────────────

fun LazyStaggeredGridScope.peopleItems(
    entities: List<Entity>,
    unscanned: Int,
    backfilling: Boolean,
    onBackfill: () -> Unit,
    onOpen: (String) -> Unit,
) {
    if (unscanned > 0 || backfilling) {
        item(span = StaggeredGridItemSpan.FullLine, key = "backfill") {
            val c = LocalFlow.current
            GlassCard(Modifier.fillMaxWidth(), onClick = if (backfilling) null else onBackfill) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (backfilling) CircularProgressIndicator(Modifier.size(20.dp), color = Flow.Indigo, strokeWidth = 2.5.dp)
                    else Icon(Icons.Rounded.TravelExplore, null, tint = Flow.Indigo, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (backfilling) "reading your memories…" else "find people & places in older memories", style = MaterialTheme.typography.titleSmall, color = c.text)
                        Text(if (backfilling) "this takes a few seconds" else "$unscanned memories not scanned yet · uses Gemini", fontSize = 11.sp, color = c.muted)
                    }
                }
            }
        }
    }
    val visible = entities.filterNot { it.hidden }
    if (visible.isEmpty()) {
        item(span = StaggeredGridItemSpan.FullLine, key = "peopleEmpty") {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("People, places and projects you mention will gather here, each with their own page.", color = LocalFlow.current.muted)
            }
        }
        return
    }
    listOf("person", "place", "project", "org").forEach { type ->
        val list = visible.filter { it.type == type }.sortedByDescending { it.lastSeen }
        if (list.isEmpty()) return@forEach
        item(span = StaggeredGridItemSpan.FullLine, key = "et$type") { SectionLabel(typeLabel(type), trailing = "${list.size}", modifier = Modifier.padding(top = 6.dp)) }
        list.forEach { e ->
            item(key = "e${e.key}") {
                val c = LocalFlow.current
                val col = typeColor(e.type)
                GlassCard(Modifier.animateItem(), onClick = { onOpen(e.key) }, padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(Brush.linearGradient(listOf(col, col.copy(alpha = 0.55f)))),
                            contentAlignment = Alignment.Center
                        ) { Text(e.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.ExtraBold) }
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(e.name, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${e.memoryIds.size} memor${if (e.memoryIds.size == 1) "y" else "ies"}", fontSize = 11.sp, color = c.muted)
                        }
                    }
                    e.notes.lastOrNull()?.let {
                        Spacer(Modifier.height(6.dp))
                        Text(it.note, fontSize = 11.sp, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** A person, place, project or organisation: what you know, memories, payments and to-dos. */
@Composable
fun EntityScreen(viewModel: MindSpaceViewModel, key: String, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val c = LocalFlow.current
    val entities by viewModel.entities.collectAsState()
    val memories by viewModel.allMemories.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val todos by viewModel.todos.collectAsState()
    val e = entities.firstOrNull { it.key == key }
    if (e == null) {
        androidx.compose.runtime.LaunchedEffect(key) { onClose() }
        return
    }
    val col = typeColor(e.type)
    val mems = memories.filter { it.id in e.memoryIds }.sortedByDescending { it.createdAt }
    val first = e.name.split(" ").first().lowercase()
    val payments: List<ExpenseEntity> = if (e.type == "person" || e.type == "org" || e.type == "place")
        expenses.filter { x -> first.length >= 3 && x.merchant.lowercase().split(" ", ".", "-").any { it == first } } else emptyList()
    val related = todos.filter { t -> !t.done && (t.memoryId in e.memoryIds || t.text.lowercase().contains(first)) }

    LazyColumn(modifier, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(c.glass).clickable(onClick = onClose), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.ArrowBack, "Back", tint = c.text)
                }
                Spacer(Modifier.weight(1f))
                GlassButton("hide", onClick = { viewModel.hideEntity(e.key) }, icon = Icons.Rounded.VisibilityOff, tint = c.muted)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(listOf(col, Flow.Indigo))),
                    contentAlignment = Alignment.Center
                ) { Text(e.name.take(1).uppercase(), color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold) }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(e.name, style = MaterialTheme.typography.headlineSmall, color = c.text)
                    Text("${e.type} · ${mems.size} memories" + (if (e.lastSeen > 0) " · last ${dayLabel(startOfDay(e.lastSeen))}" else ""), fontSize = 12.sp, color = c.muted)
                }
            }
        }
        if (e.notes.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionLabel("what you know")
                    Spacer(Modifier.height(6.dp))
                    e.notes.sortedByDescending { it.at }.take(12).forEach { n ->
                        Row(Modifier.fillMaxWidth().clickable { viewModel.openMemory(n.memoryId) }.padding(vertical = 4.dp)) {
                            Text("•", color = col, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text(n.note, fontSize = 13.sp, color = c.text, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
        if (e.type == "place") {
            item {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                GradientButton(
                    "directions to ${e.name}", brush = Flow.spendGradient, icon = Icons.Rounded.Place, modifier = Modifier.fillMaxWidth(),
                    onClick = { com.keepsakeai.app.maps.Directions.open(ctx, e.name) }
                )
            }
        }
        item {
            GradientButton(
                "ask pilot about ${e.name}", brush = Flow.auroraSweep, icon = Icons.Rounded.AutoAwesome, modifier = Modifier.fillMaxWidth(),
                onClick = {
                    viewModel.pilotDraft.value = "Tell me everything I've saved about ${e.name} (${e.type}): key facts, history, anything pending, and money involved."
                    onClose()
                }
            )
        }
        if (related.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    SectionLabel("open to-dos")
                    related.forEach { t ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(18.dp).clip(CircleShape).border(2.dp, c.muted, CircleShape).clickable { viewModel.toggleTodo(t.id) })
                            Spacer(Modifier.width(10.dp))
                            Text(t.text, fontSize = 13.sp, color = c.text, modifier = Modifier.weight(1f))
                            t.due?.let { Text(dayLabel(startOfDay(it)), fontSize = 11.sp, color = Flow.Indigo) }
                        }
                    }
                }
            }
        }
        if (payments.isNotEmpty()) {
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("money", modifier = Modifier.weight(1f))
                    }
                    Text("${rupees(payments.sumOf { it.amount })} across ${payments.size} payment${if (payments.size > 1) "s" else ""}", style = MaterialTheme.typography.titleSmall, color = Flow.Emerald)
                    Spacer(Modifier.height(6.dp))
                    payments.sortedByDescending { it.timestamp }.take(5).forEach { p ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(dayLabel(startOfDay(p.timestamp)), fontSize = 12.sp, color = c.muted, modifier = Modifier.width(96.dp))
                            Text(p.merchant, fontSize = 12.sp, color = c.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(rupees(p.amount), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = c.text)
                        }
                    }
                }
            }
        }
        if (mems.isNotEmpty()) {
            item { SectionLabel("memories", trailing = "${mems.size}", modifier = Modifier.padding(top = 4.dp)) }
            items(mems, key = { it.id }) { m ->
                val (a1, _) = collectionAccent(m.collectionName)
                GlassCard(Modifier.fillMaxWidth().animateItem(), onClick = { viewModel.openMemory(m.id) }, padding = PaddingValues(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(a1))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.title, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(m.aiSummary, fontSize = 11.sp, color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                        Text(dayLabel(startOfDay(m.createdAt)), fontSize = 10.sp, color = c.muted)
                    }
                }
            }
        }
    }
}

@Composable
fun HubPills(current: HubView, todoCount: Int, peopleCount: Int, factCount: Int, expiryCount: Int = 0, onPick: (HubView) -> Unit) {
    val c = LocalFlow.current
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(HubView.entries.toList()) { v ->
            val sel = v == current
            val bg by animateColorAsState(if (sel) c.chipSelected else c.glass, label = "pillBg")
            val fg by animateColorAsState(if (sel) c.onChipSelected else c.muted, label = "pillFg")
            val count = when (v) { HubView.TODOS -> todoCount; HubView.PEOPLE -> peopleCount; HubView.FACTS -> factCount; HubView.EXPIRIES -> expiryCount; else -> 0 }
            Row(
                Modifier.clip(RoundedCornerShape(50)).background(bg).border(1.dp, if (sel) Color.Transparent else c.glassBorder, RoundedCornerShape(50))
                    .clickable { onPick(v) }.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(v.label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = fg)
                if (count > 0) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        Modifier.clip(RoundedCornerShape(50)).background(if (sel) c.onChipSelected.copy(alpha = 0.15f) else Flow.Indigo.copy(alpha = 0.14f))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) { Text("$count", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = if (sel) c.onChipSelected else Flow.Indigo) }
                }
            }
        }
    }
}

@Suppress("unused")
private fun fmtDue(t: Long) = SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(Date(t)).lowercase()
