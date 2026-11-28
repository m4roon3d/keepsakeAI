package com.keepsakeai.app.ui.brief

import com.keepsakeai.app.ui.components.appear
import com.keepsakeai.app.ui.theme.Ts
import com.keepsakeai.app.ui.theme.Gap
import com.keepsakeai.app.ui.theme.Display
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.data.local.ExpenseEntity
import com.keepsakeai.app.data.local.MemoryEntity
import com.keepsakeai.app.data.preferences.KeepReminder
import com.keepsakeai.app.data.preferences.MoneyPrefs
import com.keepsakeai.app.data.preferences.NudgePrefs
import com.keepsakeai.app.data.preferences.Todo
import com.keepsakeai.app.ui.MindSpaceViewModel
import com.keepsakeai.app.ui.components.FlowHeader
import com.keepsakeai.app.ui.components.GlassButton
import com.keepsakeai.app.ui.components.GlassCard
import com.keepsakeai.app.ui.components.GradientButton
import com.keepsakeai.app.ui.components.GradientCard
import com.keepsakeai.app.ui.components.LogoMark
import com.keepsakeai.app.ui.components.SectionLabel
import com.keepsakeai.app.ui.spend.BudgetState
import com.keepsakeai.app.ui.spend.DAY
import com.keepsakeai.app.ui.spend.Recurring
import com.keepsakeai.app.ui.spend.SpendPeriod
import com.keepsakeai.app.ui.spend.budgetStatuses
import com.keepsakeai.app.ui.spend.detectRecurring
import com.keepsakeai.app.ui.spend.inRange
import com.keepsakeai.app.ui.spend.periodRange
import com.keepsakeai.app.ui.spend.startOfDay
import com.keepsakeai.app.ui.theme.Flow
import com.keepsakeai.app.ui.theme.LocalFlow
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** One line in the brief; [memoryId] or [tab] say where tapping it goes. */
data class BriefLine(val text: String, val sub: String? = null, val memoryId: Long? = null, val tab: String? = null, val urgent: Boolean = false, val eventId: Long? = null)

data class Brief(
    val today: List<BriefLine>,
    val money: List<BriefLine>,
    val revisit: List<BriefLine>,
    val onThisDay: List<BriefLine>,
    val people: List<BriefLine> = emptyList(),
) {
    val isEmpty get() = today.isEmpty() && money.isEmpty() && revisit.isEmpty() && onThisDay.isEmpty() && people.isEmpty()

    /** One-line summary for the notification. */
    fun summary(): String = (today.take(2) + money.take(1)).joinToString(" · ") { it.text }.ifBlank {
        revisit.firstOrNull()?.let { "Revisit: ${it.text}" } ?: "Nothing urgent today. Enjoy it."
    }
}

private fun rupees(v: Double) = "₹" + String.format(Locale.US, "%,.0f", v)

/** Builds the morning brief from what trove knows. Pure, unit-tested. */
fun buildBrief(
    reminders: List<KeepReminder>,
    todos: List<Todo>,
    memories: List<MemoryEntity>,
    expenses: List<ExpenseEntity>,
    pendingUpi: Int,
    budgets: Map<String, Double>,
    viewed: Set<Long>,
    now: Long = System.currentTimeMillis(),
    events: List<com.keepsakeai.app.calendar.CalEvent> = emptyList(),
    entities: List<com.keepsakeai.app.data.preferences.Entity> = emptyList(),
    owed: List<com.keepsakeai.app.intents.Owed> = emptyList(),
    errands: List<com.keepsakeai.app.intents.PlaceTask> = emptyList(),
): Brief {
    val today = startOfDay(now)
    val tomorrow = today + DAY
    val fmtTime = SimpleDateFormat("h:mm a", Locale.ENGLISH)

    val todayLines = mutableListOf<BriefLine>()
    // Calendar events today, merged in time order with trove's own reminders (skipping a reminder the calendar already has).
    val todaysEvents = events.filter { it.begin < tomorrow && it.end > today }
    fun sameThing(a: String, b: String): Boolean {
        val x = a.lowercase().trim(); val y = b.lowercase().trim()
        return x == y || (x.length > 4 && y.contains(x)) || (y.length > 4 && x.contains(y))
    }
    val timed = mutableListOf<Pair<Long, BriefLine>>()
    todaysEvents.forEach { e ->
        val sub = "calendar · " + e.timeLabel() + if (e.location.isNotBlank()) " · ${e.location}" else ""
        timed += (if (e.allDay) today else e.begin) to BriefLine(e.title, sub, eventId = e.id)
    }
    reminders.filter { it.status != "dismissed" && it.atMillis in today until tomorrow + DAY }
        .filterNot { r -> events.any { e -> sameThing(e.title, r.title) && e.begin < startOfDay(r.atMillis) + DAY && e.end > startOfDay(r.atMillis) } }
        .forEach { r ->
            val whenText = (if (r.atMillis < tomorrow) "today" else "tomorrow") + (if (r.hasTime) " · " + fmtTime.format(Date(r.atMillis)).lowercase() else "")
            timed += r.atMillis to BriefLine(r.title, whenText, memoryId = r.memoryId.takeIf { it > 0 }, urgent = r.atMillis < tomorrow)
        }
    timed.sortedBy { it.first }.forEach { todayLines += it.second }
    val open = todos.filter { !it.done }
    val overdue = open.filter { it.due != null && it.due < today }
    val dueToday = open.filter { it.due != null && it.due in today until tomorrow }
    if (overdue.isNotEmpty()) todayLines += BriefLine("${overdue.size} overdue to-do${if (overdue.size > 1) "s" else ""}", overdue.take(2).joinToString(", ") { it.text }, tab = "todos", urgent = true)
    dueToday.take(4).forEach { todayLines += BriefLine(it.text, "to-do due today", tab = "todos") }

    val moneyLines = mutableListOf<BriefLine>()
    if (pendingUpi > 0) moneyLines += BriefLine("$pendingUpi payment${if (pendingUpi > 1) "s" else ""} to review", "approve or dismiss in spend", tab = "spend")
    expenses.detectRecurring(now).filter { it.daysUntilDue(now) in 0..1 }.forEach { s: Recurring ->
        moneyLines += BriefLine("${s.name} renews ${if (s.daysUntilDue(now) == 0) "today" else "tomorrow"}", "${rupees(s.amount)} · ${s.cadenceLabel}", tab = "spend")
    }
    if (budgets.isNotEmpty()) {
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val dim = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = expenses.inRange(periodRange(SpendPeriod.THIS_MONTH, expenses, now))
        budgetStatuses(month, budgets, dim - day + 1).filter { it.state(day, dim) != BudgetState.OK }.take(2).forEach { b ->
            moneyLines += BriefLine(
                if (b.spent > b.limit) "${b.name.lowercase()} budget is over" else "${b.name.lowercase()} budget at ${(b.fraction * 100).toInt()}%",
                if (b.spent > b.limit) "over by ${rupees(-b.remaining)}" else "${rupees(b.perDayLeft)}/day for the rest of the month",
                tab = "spend", urgent = b.spent > b.limit
            )
        }
    }

    // Things you saved to read/watch and never opened, oldest first.
    val revisit = memories.filter { m ->
        m.id !in viewed && m.createdAt < now - 2 * DAY && m.source != CaptureSource.UPI_MESSAGE &&
            (m.collectionName == "Watch Later" || m.collectionName == "Study Notes" || m.source == CaptureSource.LINK_CAPTURE || m.sourceUrl != null)
    }.sortedBy { it.createdAt }.take(2).map { m ->
        val days = ((now - m.createdAt) / DAY).toInt()
        BriefLine(m.title, "saved $days days ago, never opened", memoryId = m.id)
    }

    val echoes = com.keepsakeai.app.ui.screens.echoes(memories, expenses, now).flatMap { e ->
        e.memories.take(1).map { BriefLine(it.title, e.label, memoryId = it.id) }
    }

    // Birthdays today or tomorrow go in today's list (and the notification); the rest of the week and a catch-up go under people.
    val peopleLines = mutableListOf<BriefLine>()
    com.keepsakeai.app.people.PeopleNudges.birthdays(entities, now).forEach { b ->
        val whenText = when (b.daysAway) { 0 -> "today"; 1 -> "tomorrow"; else -> "in ${b.daysAway} days" }
        // A gift idea you noted for them, if any.
        val gift = b.entity.notes.sortedByDescending { it.at }.firstOrNull { it.note.startsWith("gift idea: ") }
        val sub = whenText + (gift?.let { " · gift idea: " + it.note.removePrefix("gift idea: ") } ?: "")
        val line = BriefLine("${b.entity.name}'s birthday", sub, memoryId = (gift?.memoryId ?: b.memoryId).takeIf { it > 0 }, urgent = b.daysAway == 0)
        if (b.daysAway <= 1) todayLines += line else peopleLines += line
    }
    com.keepsakeai.app.people.PeopleNudges.catchUpToday(entities, now)?.let { c ->
        peopleLines += BriefLine("Catch up with ${c.entity.name}", "last in your notes ${c.daysQuiet} days ago", memoryId = c.memoryId.takeIf { it > 0 })
    }

    // Money lent or borrowed a while ago and still open.
    com.keepsakeai.app.intents.OwedRules.dueForNudge(owed, now).take(2).forEach { o ->
        val amt = o.amount?.let { rupees(it) } ?: "money"
        moneyLines += BriefLine(
            if (o.owedToMe) "${o.person} owes you $amt" else "You owe ${o.person} $amt",
            "${com.keepsakeai.app.intents.OwedRules.daysOpen(o, now)} days ago" + (o.what.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
            tab = "spend"
        )
    }
    // Errands waiting for the right place.
    errands.take(2).forEach { t ->
        todayLines += BriefLine(t.task.replaceFirstChar { it.uppercase() }, "when you're at ${t.spots.firstOrNull()?.name ?: t.placeAsSaid}", memoryId = t.memoryId.takeIf { it > 0 })
    }

    return Brief(todayLines, moneyLines, revisit, echoes, peopleLines)
}

@Composable
fun MorningBriefScreen(viewModel: MindSpaceViewModel, onClose: () -> Unit, onOpenTab: (String) -> Unit, modifier: Modifier = Modifier) {
    val c = LocalFlow.current
    val context = LocalContext.current
    val reminders by viewModel.reminders.collectAsState()
    val todos by viewModel.todos.collectAsState()
    val memories by viewModel.allMemories.collectAsState()
    val expenses by viewModel.expenses.collectAsState()
    val pending by viewModel.pendingUpi.collectAsState()
    val events by viewModel.calendarEvents.collectAsState()
    val entities by viewModel.entities.collectAsState()
    var calendarOn by remember { mutableStateOf(com.keepsakeai.app.calendar.CalendarReader.hasPermission(context)) }
    val calendarPermission = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted ->
        calendarOn = granted
        if (granted) viewModel.refreshCalendar()
    }
    val brief = remember(reminders, todos, memories, expenses, pending, events, entities) {
        buildBrief(reminders, todos, memories, expenses, pending.size, MoneyPrefs(context).budgets, NudgePrefs(context).viewed, events = events, entities = entities,
            owed = com.keepsakeai.app.intents.OwedStore(context).all(), errands = com.keepsakeai.app.intents.PlaceTaskStore(context).open(context))
    }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greet = when (hour) { in 5..11 -> "good morning"; in 12..16 -> "good afternoon"; else -> "good evening" }
    val date = SimpleDateFormat("EEEE d MMMM", Locale.ENGLISH).format(Date()).lowercase()

    fun tap(l: BriefLine) {
        when {
            l.eventId != null -> runCatching { context.startActivity(com.keepsakeai.app.calendar.CalendarReader.openIntent(l.eventId)) }
            l.memoryId != null -> viewModel.openMemory(l.memoryId)
            l.tab != null -> { onOpenTab(l.tab); onClose() }
        }
    }

    LazyColumn(modifier, contentPadding = PaddingValues(Gap.l), verticalArrangement = Arrangement.spacedBy(Gap.m)) {
        item {
            FlowHeader(eyebrow = date, title = "your ", gradientSuffix = "brief", joinSuffix = true, leading = { LogoMark(size = 46.dp) }) {
                GlassButton("close", onClick = onClose, icon = Icons.Rounded.Close)
            }
        }
        item {
            GradientCard(Flow.suggestGradient, Modifier.fillMaxWidth()) {
                Text(greet, color = Color.White.copy(alpha = 0.85f), fontSize = Ts.title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(Gap.xs))
                Text(
                    if (brief.isEmpty) "A clear day. Nothing needs you — keep something worth remembering."
                    else brief.summary(),
                    color = Color.White, fontSize = Ts.section, fontFamily = Display, fontWeight = FontWeight.ExtraBold
                )
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Gap.s)) {
                listOf(
                    Triple("today", brief.today.size, Flow.Indigo),
                    Triple("money", brief.money.size, Flow.Emerald),
                    Triple("people", brief.people.size, Flow.Rose),
                ).forEachIndexed { i, (label, n, col) ->
                    GlassCard(Modifier.weight(1f).appear(i + 1), padding = PaddingValues(Gap.m)) {
                        val shown by androidx.compose.animation.core.animateIntAsState(n, androidx.compose.animation.core.tween(600), label = "tile")
                        Text("$shown", fontSize = Ts.heading, fontFamily = Display, fontWeight = FontWeight.ExtraBold, color = if (n > 0) col else c.muted)
                        Text(label, fontSize = Ts.meta, color = c.muted)
                    }
                }
            }
        }
        if (!calendarOn) item {
            GlassCard(Modifier.fillMaxWidth()) {
                Text("see your meetings and plans here too", color = c.text, fontSize = Ts.title, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(Gap.xs))
                Text("Connect your phone's calendar and trove adds today's events to the brief, and Pilot can answer \"am I free on Saturday?\"", color = c.muted, fontSize = Ts.meta)
                Spacer(Modifier.height(Gap.m))
                GlassButton("connect calendar", onClick = { calendarPermission.launch(android.Manifest.permission.READ_CALENDAR) }, icon = Icons.Rounded.CalendarMonth)
            }
        }
        section("today", brief.today, Flow.Indigo, ::tap)
        section("money", brief.money, Flow.Emerald, ::tap)
        section("worth revisiting", brief.revisit, Flow.Amber, ::tap)
        section("people", brief.people, Flow.Rose, ::tap)
        section("on this day", brief.onThisDay, Flow.Purple, ::tap)
        item {
            GradientButton("plan my day with pilot", brush = Flow.auroraSweep, icon = Icons.Rounded.AutoAwesome, modifier = Modifier.fillMaxWidth(), onClick = {
                viewModel.pilotDraft.value = "Plan my day: use my reminders, to-dos, money alerts and anything worth revisiting. Keep it short and prioritised."
                onClose()
            })
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(title: String, lines: List<BriefLine>, color: Color, onTap: (BriefLine) -> Unit) {
    if (lines.isEmpty()) return
    item {
        val c = LocalFlow.current
        GlassCard(Modifier.fillMaxWidth().appear(2)) {
            SectionLabel(title)
            Spacer(Modifier.height(Gap.xs))
            lines.forEach { l ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onTap(l) }.padding(vertical = Gap.s, horizontal = Gap.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (l.urgent) Flow.Rose else color))
                    Spacer(Modifier.width(Gap.m))
                    Column(Modifier.weight(1f)) {
                        Text(l.text, fontSize = Ts.title, fontWeight = FontWeight.SemiBold, color = c.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        l.sub?.let { Text(it, fontSize = Ts.meta, color = if (l.urgent) Flow.Rose else c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    if (l.memoryId != null || l.tab != null) Text("→", color = c.muted, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
