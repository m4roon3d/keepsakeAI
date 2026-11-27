package com.keepsakeai.app.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.unit.ColorProvider
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.R
import com.keepsakeai.app.calendar.CalendarReader
import com.keepsakeai.app.data.preferences.EntityStore
import com.keepsakeai.app.data.preferences.MoneyPrefs
import com.keepsakeai.app.data.preferences.ReminderStore
import com.keepsakeai.app.data.preferences.TodoStore
import com.keepsakeai.app.expiry.Expiries
import com.keepsakeai.app.ui.brief.BriefLine
import com.keepsakeai.app.ui.brief.buildBrief
import com.keepsakeai.app.ui.spend.ComingUp
import com.keepsakeai.app.ui.spend.DAY
import com.keepsakeai.app.ui.spend.DueKind
import com.keepsakeai.app.ui.spend.detectRecurring
import com.keepsakeai.app.ui.spend.startOfDay
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// ── Today ────────────────────────────────────────────────────────────

/** Today at a glance: calendar, reminders, to-dos due and birthdays, straight from the morning brief. */
class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(150.dp, 150.dp), DpSize(260.dp, 150.dp), DpSize(260.dp, 230.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = widgetColors(context, id)
        val app = context.applicationContext as MindSpaceApp
        val now = System.currentTimeMillis()
        val start = startOfDay(now)
        val lines = runCatching {
            val events = runCatching { CalendarReader.events(app, start, start + DAY) }.getOrDefault(emptyList())
            buildBrief(
                ReminderStore(app).all(), TodoStore(app).all(), app.database.dao().getRecentMemories(200),
                emptyList(), 0, emptyMap(), emptySet(), now, events = events, entities = EntityStore(app).all()
            ).today
        }.getOrDefault(emptyList())
        provideContent { TodayContent(c, lines) }
    }
}

class MindSpaceWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}

@Composable
private fun TodayContent(c: WidgetColors, lines: List<BriefLine>) {
    val size = LocalSize.current
    val wide = size.width >= 250.dp
    val rows = when { size.height >= 220.dp -> 5; wide -> 3; else -> 3 }
    val now = Date()
    WidgetSurface(c, openApp("home", "brief")) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(SimpleDateFormat("EEEE", Locale.ENGLISH).format(now).lowercase(), style = ts(c.text, 17, FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            Text(SimpleDateFormat("d MMM", Locale.ENGLISH).format(now).lowercase(), style = ts(c.soft, 12, FontWeight.Medium))
        }
        Spacer(GlanceModifier.height(10.dp))
        if (lines.isEmpty()) {
            Text("Nothing planned.", style = ts(c.text, 14, FontWeight.Medium))
            Text("A clear day. Enjoy it.", style = ts(c.soft, 12))
        } else {
            lines.take(rows).forEach { l ->
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.size(6.dp).cornerRadius(3.dp).background(ColorProvider(if (l.urgent) c.alert else c.accent))) {}
                    Spacer(GlanceModifier.width(8.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(l.text, style = ts(c.text, 13, FontWeight.Medium), maxLines = 1)
                        if (wide && l.sub != null) Text(l.sub, style = ts(c.soft, 11), maxLines = 1)
                    }
                }
            }
            if (lines.size > rows) {
                Spacer(GlanceModifier.defaultWeight())
                Text("+${lines.size - rows} more", style = ts(c.soft, 11, FontWeight.Medium))
            }
        }
    }
}

// ── Quick capture ────────────────────────────────────────────────────

/** One strip of buttons: note, voice, photo, ask Pilot. */
class CaptureWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = widgetColors(context, id)
        provideContent { CaptureContent(c) }
    }
}

class CaptureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CaptureWidget()
}

@Composable
private fun CaptureContent(c: WidgetColors) {
    val showLabels = LocalSize.current.height >= 72.dp
    WidgetSurface(c, onClick = null, padding = 8.dp) {
        Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            CaptureButton(c, R.drawable.ic_glyph_note, "note", "note", showLabels)
            Spacer(GlanceModifier.width(6.dp))
            CaptureButton(c, R.drawable.ic_glyph_mic, "voice", "journal", showLabels)
            Spacer(GlanceModifier.width(6.dp))
            CaptureButton(c, R.drawable.ic_glyph_camera, "photo", "camera", showLabels)
            Spacer(GlanceModifier.width(6.dp))
            CaptureButton(c, R.drawable.ic_glyph_spark, "ask", "pilot", showLabels, accent = true)
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.CaptureButton(c: WidgetColors, icon: Int, label: String, tab: String, showLabel: Boolean, accent: Boolean = false) {
    Column(
        GlanceModifier.defaultWeight().fillMaxHeight().cornerRadius(18.dp)
            .background(ColorProvider(if (accent) c.accent.copy(alpha = 0.9f) else c.chip))
            .clickable(openApp(tab)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val fg = if (accent) Color.White else c.text
        Image(ImageProvider(icon), label, GlanceModifier.size(22.dp), colorFilter = ColorFilter.tint(ColorProvider(fg)))
        if (showLabel) {
            Spacer(GlanceModifier.height(4.dp))
            Text(label, style = ts(fg, 12, FontWeight.Medium))
        }
    }
}

// ── Money ────────────────────────────────────────────────────────────

private data class MoneySnap(val spent: Double, val budget: Double, val pending: Int, val currency: String, val lastMonthSoFar: Double)

/** This month's spend against your budget, and payments waiting for review. */
class MoneyWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = widgetColors(context, id)
        val app = context.applicationContext as MindSpaceApp
        val dao = app.database.dao()
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val monthStart = startOfDay(Calendar.getInstance().apply { set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis)
        val lastStart = startOfDay(Calendar.getInstance().apply { add(Calendar.MONTH, -1); set(Calendar.DAY_OF_MONTH, 1) }.timeInMillis)
        val lastSameDay = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }.timeInMillis.coerceAtMost(monthStart)
        val snap = runCatching {
            val all = dao.observeAllExpenses().first().filter { it.type == com.keepsakeai.app.data.local.TxnType.EXPENSE }
            MoneySnap(
                spent = all.filter { it.timestamp >= monthStart && it.timestamp <= now }.sumOf { it.amount },
                budget = MoneyPrefs(app).budgets.values.sum(),
                pending = dao.countPendingUpi(),
                currency = app.apiKeyStore.defaultCurrency.ifBlank { "INR" },
                lastMonthSoFar = all.filter { it.timestamp in lastStart until lastSameDay }.sumOf { it.amount },
            )
        }.getOrDefault(MoneySnap(0.0, 0.0, 0, "INR", 0.0))
        cal.timeInMillis = now
        provideContent { MoneyContent(c, snap, cal.get(Calendar.DAY_OF_MONTH), cal.getActualMaximum(Calendar.DAY_OF_MONTH)) }
    }
}

class MoneyWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MoneyWidget()
}

@Composable
private fun MoneyContent(c: WidgetColors, s: MoneySnap, day: Int, daysInMonth: Int) {
    WidgetSurface(c, openApp("spend")) {
        Text("spent this month", style = ts(c.soft, 12, FontWeight.Medium))
        Spacer(GlanceModifier.height(2.dp))
        Text(money(s.currency, s.spent), style = ts(c.text, 24, FontWeight.Bold), maxLines = 1)
        Spacer(GlanceModifier.height(10.dp))
        if (s.budget > 0) {
            val f = (s.spent / s.budget).toFloat()
            val pace = day.toFloat() / daysInMonth
            val col = when { f > 1f -> c.alert; f > pace + 0.1f -> c.warn; else -> c.good }
            LinearProgressIndicator(
                progress = f.coerceIn(0f, 1f), modifier = GlanceModifier.fillMaxWidth().height(6.dp).cornerRadius(3.dp),
                color = ColorProvider(col), backgroundColor = ColorProvider(c.chip)
            )
            Spacer(GlanceModifier.height(6.dp))
            Text(
                if (f > 1f) "over budget by ${money(s.currency, s.spent - s.budget)}" else "${(f * 100).toInt()}% of ${money(s.currency, s.budget)}",
                style = ts(if (f > 1f) c.alert else c.soft, 11, FontWeight.Medium), maxLines = 1
            )
        } else if (s.lastMonthSoFar > 0) {
            val diff = s.spent - s.lastMonthSoFar
            Text(
                (if (diff >= 0) "${money(s.currency, diff)} more" else "${money(s.currency, -diff)} less") + " than last month by now",
                style = ts(if (diff > 0) c.warn else c.good, 11, FontWeight.Medium), maxLines = 2
            )
        }
        Spacer(GlanceModifier.defaultWeight())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.size(6.dp).cornerRadius(3.dp).background(ColorProvider(if (s.pending > 0) c.warn else c.good))) {}
            Spacer(GlanceModifier.width(6.dp))
            Text(
                if (s.pending > 0) "${s.pending} payment${if (s.pending == 1) "" else "s"} to review" else "all payments sorted",
                style = ts(c.text, 12, FontWeight.Medium), maxLines = 1
            )
        }
    }
}

// ── Coming up ────────────────────────────────────────────────────────

private data class DueRow(val title: String, val days: Int, val amount: Double?, val color: Color)

/** Bills, renewals, money to-dos and things that expire in the next month, soonest first. */
class ComingUpWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val c = widgetColors(context, id)
        val app = context.applicationContext as MindSpaceApp
        val now = System.currentTimeMillis()
        val today = startOfDay(now)
        val (rows, currency) = runCatching {
            val reminders = ReminderStore(app).all()
            val due = ComingUp.build(reminders, TodoStore(app).all(), app.database.dao().observeAllExpenses().first().detectRecurring(now), now).map {
                DueRow(
                    it.title, Math.floorDiv(startOfDay(it.dueAt) - today, DAY).toInt(), it.amount,
                    when (it.kind) { DueKind.BILL -> c.warn; DueKind.RENEWAL -> c.accent; DueKind.TODO -> c.good }
                )
            }
            val ends = Expiries.list(reminders, now).map { it to Expiries.daysLeft(it, now) }.filter { it.second in 0..30 }
                .map { (r, d) -> DueRow(r.title, d, null, c.alert) }
            (due + ends).sortedBy { it.days } to app.apiKeyStore.defaultCurrency.ifBlank { "INR" }
        }.getOrDefault(emptyList<DueRow>() to "INR")
        provideContent { ComingUpContent(c, rows, currency) }
    }
}

class ComingUpWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ComingUpWidget()
}

@Composable
private fun ComingUpContent(c: WidgetColors, rows: List<DueRow>, currency: String) {
    val max = ((LocalSize.current.height.value - 52) / 34).toInt().coerceIn(1, 6)
    WidgetSurface(c, openApp("spend")) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("coming up", style = ts(c.text, 16, FontWeight.Bold))
            Spacer(GlanceModifier.defaultWeight())
            Text("next 30 days", style = ts(c.soft, 11, FontWeight.Medium))
        }
        Spacer(GlanceModifier.height(8.dp))
        if (rows.isEmpty()) {
            Text("Nothing due in the next month.", style = ts(c.soft, 13))
        } else {
            rows.take(max).forEach { r ->
                Row(GlanceModifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(GlanceModifier.size(width = 3.dp, height = 22.dp).cornerRadius(2.dp).background(ColorProvider(r.color))) {}
                    Spacer(GlanceModifier.width(10.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(r.title, style = ts(c.text, 13, FontWeight.Medium), maxLines = 1)
                        Text(daysLabel(r.days), style = ts(if (r.days < 0) c.alert else if (r.days <= 3) c.warn else c.soft, 11), maxLines = 1)
                    }
                    r.amount?.let { Text(money(currency, it), style = ts(c.text, 13, FontWeight.Bold)) }
                }
            }
            if (rows.size > max) Text("+${rows.size - max} more", style = ts(c.soft, 11, FontWeight.Medium))
        }
    }
}
