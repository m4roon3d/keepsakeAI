package com.keepsakeai.app.notify

import android.content.Context
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.data.preferences.MoneyPrefs
import com.keepsakeai.app.ui.spend.BudgetState
import com.keepsakeai.app.ui.spend.OVERALL
import com.keepsakeai.app.ui.spend.SpendPeriod
import com.keepsakeai.app.ui.spend.budgetStatuses
import com.keepsakeai.app.ui.spend.detectRecurring
import com.keepsakeai.app.ui.spend.inRange
import com.keepsakeai.app.ui.spend.periodRange
import kotlinx.coroutines.flow.first
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Budget warnings (80% and over) and renewal heads-ups, each sent once. */
object MoneyWatch {

    suspend fun check(context: Context) {
        val app = context.applicationContext as MindSpaceApp
        val prefs = MoneyPrefs(app)
        val all = app.database.dao().observeAllExpenses().first()
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance()
        val monthKey = SimpleDateFormat("yyyy-MM", Locale.US).format(Date(now))
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)
        val month = all.inRange(periodRange(SpendPeriod.THIS_MONTH, all, now))
        val sym = if (app.apiKeyStore.defaultCurrency.equals("USD", true)) "$" else "₹"
        fun r(v: Double) = sym + String.format(Locale.US, "%,.0f", v)

        budgetStatuses(month, prefs.budgets, daysInMonth - day + 1).forEach { b ->
            val label = if (b.name == OVERALL) "your monthly budget" else "your ${b.name.lowercase()} budget"
            when {
                b.state(day, daysInMonth) == BudgetState.OVER && prefs.firstTime("over:$monthKey:${b.name}") ->
                    Notifier.show(app, Notifier.MONEY, ("over" + b.name).hashCode(), "Over $label",
                        "${r(b.spent)} spent of ${r(b.limit)} — ${r(-b.remaining)} over with ${b.daysLeft} days to go.", openTab = "spend")
                b.fraction >= 0.8 && b.spent <= b.limit && prefs.firstTime("80:$monthKey:${b.name}") ->
                    Notifier.show(app, Notifier.MONEY, ("80" + b.name).hashCode(), "80% of $label used",
                        "${r(b.remaining)} left for ${b.daysLeft} days — about ${r(b.perDayLeft)} a day.", openTab = "spend")
            }
        }

        if (prefs.renewalReminders) {
            fun whenText(days: Int) = when (days) { 0 -> "today"; 1 -> "tomorrow"; else -> "in $days days" }
            // A heads-up three days out, and again on the day.
            all.detectRecurring(now).filter { it.daysUntilDue(now) in 0..3 }.forEach { s ->
                val d = s.daysUntilDue(now)
                val dueKey = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(s.nextDue))
                val stage = if (d == 0) "day" else "soon"
                if (prefs.firstTime("renew:${s.name.lowercase()}:$dueKey:$stage")) {
                    Notifier.show(app, Notifier.MONEY, ("renew" + s.name + stage).hashCode(), "${s.name} renews ${whenText(d)}",
                        "Expected ${r(s.amount)} (${s.cadenceLabel}). Cancel now if you don't need it.", openTab = "spend")
                }
            }
            // Bills found in your mail and messages, with a due date.
            com.keepsakeai.app.data.preferences.ReminderStore(app).all()
                .filter { it.kind == "due" && it.status != "dismissed" }
                .forEach { b ->
                    val d = Math.floorDiv(com.keepsakeai.app.ui.spend.startOfDay(b.atMillis) - com.keepsakeai.app.ui.spend.startOfDay(now), com.keepsakeai.app.ui.spend.DAY).toInt()
                    if (d !in 0..3) return@forEach
                    val stage = if (d == 0) "day" else "soon"
                    if (prefs.firstTime("bill:${b.id}:$stage")) {
                        Notifier.show(app, Notifier.MONEY, ("bill" + b.id + stage).hashCode(), "${b.title}: due ${whenText(d)}",
                            "Open trove to see what's coming up.", openTab = "spend")
                    }
                }
        }
        ExpiryWatch.check(context)
    }
}
