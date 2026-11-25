package com.keepsakeai.app.ui.spend

import com.keepsakeai.app.data.preferences.KeepReminder
import com.keepsakeai.app.data.preferences.Todo
import java.util.Locale

enum class DueKind(val label: String) { BILL("bill"), RENEWAL("renewal"), TODO("to-do") }

/** One thing you will have to pay, in date order. [refId] is the reminder or to-do id (0 for a renewal). */
data class DueItem(
    val title: String,
    val dueAt: Long,
    val amount: Double?,
    val kind: DueKind,
    val memoryId: Long?,
    val refId: Long = 0L,
) {
    fun daysUntil(now: Long): Int = Math.floorDiv(startOfDay(dueAt) - startOfDay(now), DAY).toInt()
}

/** Bills, renewals and money to-dos in one list. Pure and unit-tested. */
object ComingUp {
    /** A bill still counts this long after its due date, so a missed one stays in view until it is marked paid. */
    const val OVERDUE_DAYS = 7
    const val AHEAD_DAYS = 30

    private val moneyWords = Regex(
        "\\b(pay|bill|rent|emi|premium|renew|renewal|recharge|instal+ments?|tax|fees?|invoice|subscription|loan|credit card)\\b"
    )
    private val amountRe = Regex("(?:₹|\\$|rs\\.?)\\s?([0-9][0-9,]*(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)

    /** The first amount written in [text] ("Pay BESCOM bill ₹1,240"), or null. */
    fun amountIn(text: String): Double? =
        amountRe.find(text)?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()?.takeIf { it > 0 }

    fun build(reminders: List<KeepReminder>, todos: List<Todo>, recurring: List<Recurring>, now: Long): List<DueItem> {
        val today = startOfDay(now)
        val from = today - OVERDUE_DAYS * DAY
        val to = today + (AHEAD_DAYS + 1) * DAY

        val bills = reminders
            .filter { it.kind == "due" && it.status != "dismissed" && it.atMillis in from until to }
            .map { DueItem(it.title, it.atMillis, amountIn(it.title), DueKind.BILL, it.memoryId.takeIf { m -> m > 0 }, it.id) }

        val moneyTodos = todos
            .filter { !it.done && it.due != null && it.due in from until to && moneyWords.containsMatchIn(it.text.lowercase(Locale.ROOT)) }
            // The same bill often arrives as a reminder and as a to-do from the same email: keep the reminder.
            .filterNot { t -> bills.any { b -> b.memoryId != null && b.memoryId == t.memoryId && kotlin.math.abs(b.dueAt - t.due!!) <= DAY } }
            .map { DueItem(it.text, it.due!!, amountIn(it.text), DueKind.TODO, it.memoryId, it.id) }

        val renewals = recurring
            .filter { it.nextDue in from until to }
            .filterNot { r ->
                val name = r.name.lowercase(Locale.ROOT)
                name.length >= 3 && bills.any { b -> name in b.title.lowercase(Locale.ROOT) && kotlin.math.abs(b.dueAt - r.nextDue) <= 4 * DAY }
            }
            .map { DueItem(it.name.trim(), it.nextDue, it.amount, DueKind.RENEWAL, null) }

        return (bills + moneyTodos + renewals).sortedBy { it.dueAt }
    }

}
