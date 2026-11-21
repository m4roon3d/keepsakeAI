package com.keepsakeai.app.ui.spend

import com.keepsakeai.app.data.local.ExpenseEntity
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** A payment that repeats on a steady cadence (subscription, rent, SIP, bill). */
data class Recurring(
    val name: String,
    val category: String,
    val amount: Double,          // typical (median) charge
    val cadenceDays: Int,
    val cadenceLabel: String,    // weekly · monthly · quarterly · yearly
    val lastPaid: Long,
    val nextDue: Long,
    val count: Int,
) {
    /** What this costs per month, whatever its cadence. */
    val monthly: Double get() = amount * 30.0 / cadenceDays
    fun daysUntilDue(now: Long): Int = Math.floorDiv(startOfDayUtcAgnostic(nextDue) - startOfDayUtcAgnostic(now), DAY).toInt()
}

private fun startOfDayUtcAgnostic(t: Long) = startOfDay(t)

private val subscriptionWords = Regex(
    "\\b(netflix|spotify|prime|hotstar|youtube|apple|icloud|jio|airtel|act fibernet|broadband|gym|cult|rent|sip|mutual fund|insurance|lic|emi|zee5|sonyliv|disney|audible|kindle|linkedin|claude|openai|chatgpt|notion|maintenance|society)\\b"
)

private fun median(xs: List<Double>): Double {
    val s = xs.sorted()
    return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
}

private fun cadenceFor(days: Double): Pair<Int, String>? = when (days) {
    in 6.0..8.5 -> 7 to "weekly"
    in 26.0..35.0 -> 30 to "monthly"
    in 84.0..97.0 -> 91 to "quarterly"
    in 350.0..380.0 -> 365 to "yearly"
    else -> null
}

/**
 * Finds steady repeat payments: same merchant, similar amount (±20%), regular gaps (±25%),
 * still active. Needs three payments, or two when the name looks like a subscription/bill.
 */
fun List<ExpenseEntity>.detectRecurring(now: Long = System.currentTimeMillis()): List<Recurring> =
    groupBy { merchantKey(it.merchant) }
        .filterKeys { k -> k.isNotBlank() && !k.endsWith(" payment") && k != "cash withdrawal" }
        .mapNotNull { (key, items) ->
            // One charge per day is enough (duplicates from SMS + notification).
            val byDay = items.sortedBy { it.timestamp }.distinctBy { startOfDay(it.timestamp) }
            val looksLikeSub = subscriptionWords.containsMatchIn(key)
            if (byDay.size < (if (looksLikeSub) 2 else 3)) return@mapNotNull null

            val amounts = byDay.map { it.amount }
            val typical = median(amounts)
            if (typical <= 0 || amounts.any { abs(it - typical) / typical > 0.20 }) return@mapNotNull null

            val gaps = byDay.zipWithNext { a, b -> (b.timestamp - a.timestamp).toDouble() / DAY }
            val gapMedian = median(gaps)
            val (cadence, label) = cadenceFor(gapMedian) ?: return@mapNotNull null
            if (gaps.any { abs(it - gapMedian) / gapMedian > 0.25 }) return@mapNotNull null

            val last = byDay.last()
            // Stale if we've missed more than one and a half cycles.
            if (now - last.timestamp > cadence * DAY * 3 / 2) return@mapNotNull null
            Recurring(
                name = last.merchant.trim(),
                category = last.category,
                amount = (typical * 100).roundToInt() / 100.0,
                cadenceDays = cadence,
                cadenceLabel = label,
                lastPaid = last.timestamp,
                nextDue = last.timestamp + (gapMedian * DAY).toLong(),
                count = byDay.size,
            )
        }
        .sortedBy { it.nextDue }

// ── Budgets ──────────────────────────────────────────────────────────

const val OVERALL = "Overall"

enum class BudgetState { OK, WATCH, OVER }

data class BudgetStatus(
    val name: String,
    val limit: Double,
    val spent: Double,
    val daysLeft: Int,
) {
    val fraction: Double get() = if (limit > 0) spent / limit else 0.0
    val remaining: Double get() = limit - spent
    /** What you can spend per day for the rest of the month and still land on budget. */
    val perDayLeft: Double get() = if (daysLeft > 0) (remaining / daysLeft).coerceAtLeast(0.0) else 0.0
    fun state(dayOfMonth: Int, daysInMonth: Int): BudgetState {
        val expectedByNow = dayOfMonth.toDouble() / daysInMonth
        return when {
            spent > limit -> BudgetState.OVER
            fraction >= 0.8 || fraction > expectedByNow + 0.15 -> BudgetState.WATCH
            else -> BudgetState.OK
        }
    }
}

/** Budget progress for this month. [limits] maps category (or [OVERALL]) to monthly limit. */
fun budgetStatuses(monthExpenses: List<ExpenseEntity>, limits: Map<String, Double>, daysLeft: Int): List<BudgetStatus> =
    limits.filterValues { it > 0 }.map { (name, limit) ->
        val spent = if (name == OVERALL) monthExpenses.sumOf { it.amount }
        else monthExpenses.filter { it.category.equals(name, ignoreCase = true) }.sumOf { it.amount }
        BudgetStatus(name, limit, spent, daysLeft)
    }.sortedWith(compareBy({ it.name != OVERALL }, { -it.fraction }))

// ── Money digest for Pilot ───────────────────────────────────────────

/**
 * Compact, pre-aggregated summary of the user's spending so an LLM can answer money
 * questions accurately (totals are computed here, not by the model).
 */
fun spendDigest(
    all: List<ExpenseEntity>,
    limits: Map<String, Double>,
    includeRows: Boolean,
    now: Long = System.currentTimeMillis(),
): String {
    if (all.isEmpty()) return "No payments recorded yet."
    val tz = java.util.TimeZone.getDefault()
    val fmtMonth = java.text.SimpleDateFormat("MMM yyyy", Locale.ENGLISH)
    val fmtDay = java.text.SimpleDateFormat("yyyy-MM-dd EEE", Locale.ENGLISH)
    fun r(v: Double) = "₹" + String.format(Locale.US, "%,.0f", v)
    val sb = StringBuilder()

    sb.append("Monthly totals (most recent first):\n")
    val months = all.groupBy { fmtMonth.format(java.util.Date(it.timestamp)) to java.util.Calendar.getInstance(tz).apply { timeInMillis = it.timestamp }.let { c -> c.get(java.util.Calendar.YEAR) * 12 + c.get(java.util.Calendar.MONTH) } }
        .toList().sortedByDescending { it.first.second }.take(6)
    months.forEach { (k, v) -> sb.append("- ${k.first}: ${r(v.sumOf { it.amount })} across ${v.size} payments\n") }

    val thisMonth = periodRange(SpendPeriod.THIS_MONTH, all, now, tz)
    val lastMonth = periodRange(SpendPeriod.LAST_MONTH, all, now, tz)
    for ((label, range) in listOf("This month" to thisMonth, "Last month" to lastMonth)) {
        val l = all.inRange(range)
        if (l.isEmpty()) continue
        sb.append("$label by category: ")
        sb.append(l.breakdown { it.category }.joinToString(", ") { "${it.first} ${r(it.second)}" }).append('\n')
        sb.append("$label by channel: ")
        sb.append(l.breakdown { channelOf(it.sourceMethod) }.joinToString(", ") { "${it.first} ${r(it.second)}" }).append('\n')
    }

    val last90 = all.inRange(periodRange(SpendPeriod.LAST_90, all, now, tz))
    if (last90.isNotEmpty()) {
        sb.append("Top merchants, last 90 days: ")
        sb.append(last90.topMerchants(10).joinToString(", ") { "${it.name} ${r(it.total)} (${it.count}×)" }).append('\n')
    }

    val subs = all.detectRecurring(now)
    if (subs.isNotEmpty()) {
        sb.append("Recurring payments: ")
        sb.append(subs.joinToString(", ") { "${it.name} ${r(it.amount)} ${it.cadenceLabel}, next ~${fmtDay.format(java.util.Date(it.nextDue))}" }).append('\n')
    }

    if (limits.isNotEmpty()) {
        val cal = java.util.Calendar.getInstance(tz).apply { timeInMillis = now }
        val daysLeft = cal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH) - cal.get(java.util.Calendar.DAY_OF_MONTH) + 1
        sb.append("Budgets this month: ")
        sb.append(budgetStatuses(all.inRange(thisMonth), limits, daysLeft).joinToString(", ") {
            "${it.name} ${r(it.spent)} of ${r(it.limit)}"
        }).append('\n')
    }

    if (includeRows) {
        val since = now - 120 * DAY
        val rows = all.filter { it.timestamp >= since }.sortedByDescending { it.timestamp }.take(250)
        sb.append("\nPayments, last 120 days (date | merchant | category | amount | paid via):\n")
        rows.forEach {
            sb.append("${fmtDay.format(java.util.Date(it.timestamp))} | ${it.merchant} | ${it.category} | ${r(it.amount)} | ${channelOf(it.sourceMethod)}\n")
        }
        if (all.count { it.timestamp >= since } > rows.size) sb.append("(older/extra rows omitted)\n")
    }
    return sb.toString().trim()
}

private val moneyWords = Regex(
    "(spen[dt]|₹|\\brs\\b|money|paid|pay|expense|cost|budget|subscri|bill|upi|swiggy|zomato|amazon|uber|ola|merchant|transaction|payment|how much|save|saving|afford|split)",
    RegexOption.IGNORE_CASE
)

fun isMoneyQuestion(q: String) = moneyWords.containsMatchIn(q)
