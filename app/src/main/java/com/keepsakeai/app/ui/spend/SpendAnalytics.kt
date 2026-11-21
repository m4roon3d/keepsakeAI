package com.keepsakeai.app.ui.spend

import com.keepsakeai.app.data.local.ExpenseEntity
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** Pure spend maths for the Spend dashboard. No Android dependencies, so it's unit-tested. */

enum class SpendPeriod(val label: String) {
    THIS_MONTH("this month"),
    LAST_MONTH("last month"),
    LAST_90("90 days"),
    ALL("all time"),
}

data class TimeRange(val start: Long, val end: Long) {
    operator fun contains(t: Long) = t in start until end
    val days: Int get() = ((end - start + DAY - 1) / DAY).toInt().coerceAtLeast(1)
}

const val DAY = 24L * 60 * 60 * 1000

private fun cal(now: Long, tz: TimeZone) = Calendar.getInstance(tz).apply { timeInMillis = now }

private fun startOfMonth(now: Long, tz: TimeZone, monthsBack: Int = 0): Long = cal(now, tz).apply {
    add(Calendar.MONTH, -monthsBack)
    set(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun startOfDay(t: Long, tz: TimeZone = TimeZone.getDefault()): Long = cal(t, tz).apply {
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun periodRange(p: SpendPeriod, list: List<ExpenseEntity>, now: Long = System.currentTimeMillis(), tz: TimeZone = TimeZone.getDefault()): TimeRange {
    val tomorrow = startOfDay(now, tz) + DAY
    return when (p) {
        SpendPeriod.THIS_MONTH -> TimeRange(startOfMonth(now, tz), tomorrow)
        SpendPeriod.LAST_MONTH -> TimeRange(startOfMonth(now, tz, 1), startOfMonth(now, tz))
        SpendPeriod.LAST_90 -> TimeRange(tomorrow - 90 * DAY, tomorrow)
        SpendPeriod.ALL -> TimeRange(startOfDay(list.minOfOrNull { it.timestamp } ?: now, tz), tomorrow)
    }
}

/** The window to compare against: same point last month, the month before, or the 90 days before. */
fun previousRange(p: SpendPeriod, now: Long = System.currentTimeMillis(), tz: TimeZone = TimeZone.getDefault()): TimeRange? = when (p) {
    SpendPeriod.THIS_MONTH -> {
        val lastStart = startOfMonth(now, tz, 1)
        val elapsed = now - startOfMonth(now, tz)
        TimeRange(lastStart, minOf(lastStart + elapsed, startOfMonth(now, tz)))
    }
    SpendPeriod.LAST_MONTH -> TimeRange(startOfMonth(now, tz, 2), startOfMonth(now, tz, 1))
    SpendPeriod.LAST_90 -> {
        val end = startOfDay(now, tz) + DAY - 90 * DAY
        TimeRange(end - 90 * DAY, end)
    }
    SpendPeriod.ALL -> null
}

/** "UPI · ICICI Bank" → UPI; legacy "UPI (ICICI Bank)" → UPI; "Manual" → Manual; captures → Receipt. */
fun channelOf(sourceMethod: String): String = when {
    " · " in sourceMethod -> sourceMethod.substringBefore(" · ").trim()
    sourceMethod.startsWith("UPI (") -> "UPI"
    sourceMethod.equals("Manual", ignoreCase = true) -> "Manual"
    else -> "Receipt"
}

/** Bank / app part of the source, e.g. "ICICI Bank". */
fun accountOf(sourceMethod: String): String? = when {
    " · " in sourceMethod -> sourceMethod.substringAfter(" · ").trim()
    sourceMethod.startsWith("UPI (") -> sourceMethod.removePrefix("UPI (").removeSuffix(")")
    else -> null
}

data class SpendFilter(
    val categories: Set<String> = emptySet(),
    val channels: Set<String> = emptySet(),
    val merchant: String? = null,
    val query: String = "",
) {
    val isActive get() = categories.isNotEmpty() || channels.isNotEmpty() || merchant != null || query.isNotBlank()
}

fun merchantKey(m: String) = m.trim().lowercase(Locale.ROOT)

fun List<ExpenseEntity>.inRange(r: TimeRange) = filter { it.timestamp in r }

fun List<ExpenseEntity>.applyFilter(f: SpendFilter): List<ExpenseEntity> = filter { e ->
    (f.categories.isEmpty() || e.category in f.categories) &&
        (f.channels.isEmpty() || channelOf(e.sourceMethod) in f.channels) &&
        (f.merchant == null || merchantKey(e.merchant) == merchantKey(f.merchant)) &&
        (f.query.isBlank() || e.merchant.contains(f.query.trim(), ignoreCase = true) ||
            e.category.contains(f.query.trim(), ignoreCase = true) || e.notes.contains(f.query.trim(), ignoreCase = true))
}

/** Totals by a key, largest first. */
fun List<ExpenseEntity>.breakdown(key: (ExpenseEntity) -> String): List<Pair<String, Double>> =
    groupBy(key).map { (k, v) -> k to v.sumOf { it.amount } }.sortedByDescending { it.second }

/** Top merchants: display name of the most recent spelling, total, count. */
data class MerchantTotal(val name: String, val total: Double, val count: Int)

fun List<ExpenseEntity>.topMerchants(n: Int = 5): List<MerchantTotal> =
    groupBy { merchantKey(it.merchant) }
        .map { (_, v) -> MerchantTotal(v.maxBy { it.timestamp }.merchant, v.sumOf { it.amount }, v.size) }
        .sortedByDescending { it.total }
        .take(n)

/** Bars for the trend chart: daily for ranges up to 45 days, weekly beyond. */
data class Bucket(val start: Long, val total: Double)

fun List<ExpenseEntity>.buckets(r: TimeRange, tz: TimeZone = TimeZone.getDefault()): List<Bucket> {
    val step = if (r.days <= 45) DAY else 7 * DAY
    val start = startOfDay(r.start, tz)
    val count = (((r.end - start) + step - 1) / step).toInt().coerceIn(1, 120)
    val sums = DoubleArray(count)
    forEach { e ->
        if (e.timestamp in r) {
            val i = ((e.timestamp - start) / step).toInt()
            if (i in 0 until count) sums[i] += e.amount
        }
    }
    return List(count) { Bucket(start + it * step, sums[it]) }
}

enum class GroupBy(val label: String) {
    CATEGORY("category"),
    CHANNEL("paid via"),
    MERCHANT("merchant"),
    DAY("day"),
}

data class SpendGroup(val key: String, val label: String, val total: Double, val items: List<ExpenseEntity>)

fun List<ExpenseEntity>.grouped(by: GroupBy, tz: TimeZone = TimeZone.getDefault()): List<SpendGroup> {
    val sorted = sortedByDescending { it.timestamp }
    return when (by) {
        GroupBy.DAY -> sorted.groupBy { startOfDay(it.timestamp, tz) }
            .map { (d, v) -> SpendGroup(d.toString(), dayLabel(d, tz), v.sumOf { it.amount }, v) }
            .sortedByDescending { it.key.toLong() }
        GroupBy.CATEGORY -> sorted.groupBy { it.category }
            .map { (k, v) -> SpendGroup(k, k, v.sumOf { it.amount }, v) }.sortedByDescending { it.total }
        GroupBy.CHANNEL -> sorted.groupBy { channelOf(it.sourceMethod) }
            .map { (k, v) -> SpendGroup(k, k, v.sumOf { it.amount }, v) }.sortedByDescending { it.total }
        GroupBy.MERCHANT -> sorted.groupBy { merchantKey(it.merchant) }
            .map { (k, v) -> SpendGroup(k, v.first().merchant, v.sumOf { it.amount }, v) }.sortedByDescending { it.total }
    }
}

fun dayLabel(dayStart: Long, tz: TimeZone = TimeZone.getDefault(), now: Long = System.currentTimeMillis()): String {
    val today = startOfDay(now, tz)
    return when (dayStart) {
        today -> "today"
        today - DAY -> "yesterday"
        else -> java.text.SimpleDateFormat("EEE, d MMM", Locale.ENGLISH).apply { timeZone = tz }.format(java.util.Date(dayStart)).lowercase(Locale.ENGLISH)
    }
}

/** Percentage change, or null when there's nothing to compare with. */
fun percentChange(current: Double, previous: Double): Int? =
    if (previous <= 0.0) null else (((current - previous) / previous) * 100).toInt()
