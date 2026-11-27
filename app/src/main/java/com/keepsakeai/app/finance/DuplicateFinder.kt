package com.keepsakeai.app.finance

import com.keepsakeai.app.data.local.ExpenseEntity
import com.keepsakeai.app.data.local.TxnType
import kotlin.math.abs

/**
 * Finds payments that were saved twice because the same money event arrived by more than one route
 * (bank SMS, a UPI app notification, a bank email, a screenshot). Pure and unit-tested.
 * Entries the user typed in by hand are never touched.
 */
object DuplicateFinder {
    private const val MINUTES_5 = 5 * 60_000L
    private const val DAY = 24L * 60 * 60 * 1000

    data class Dupe(val remove: ExpenseEntity, val keep: ExpenseEntity)

    private fun isMessage(e: ExpenseEntity) = " · " in e.sourceMethod || e.sourceMethod.startsWith("UPI (")
    private fun utrOf(e: ExpenseEntity): String? = e.notes.takeIf { it.startsWith("UTR: ") }?.removePrefix("UTR: ")?.trim()?.ifBlank { null }

    /** Which copy to keep: a bank message with a UPI ref is the most exact, then any message, then email, then the rest. */
    private fun rank(e: ExpenseEntity): Int = when {
        isMessage(e) && utrOf(e) != null -> 3
        isMessage(e) -> 2
        e.sourceMethod == "Email" -> 1
        else -> 0
    }

    private fun sameEvent(a: ExpenseEntity, b: ExpenseEntity, messages: Map<Long, String>): Boolean {
        val ua = utrOf(a)
        val ub = utrOf(b)
        if (ua != null && ub != null) return ua == ub
        if (isMessage(a) && isMessage(b)) {
            val fa = messages[a.timestamp]?.let { MessageFingerprint.of(it) }
            val fb = messages[b.timestamp]?.let { MessageFingerprint.of(it) }
            // The same SMS saved again when the notification was re-posted, hours apart.
            if (fa != null && fb != null) return fa == fb && abs(a.timestamp - b.timestamp) <= 3 * DAY
        }
        val window = if (isMessage(a) && isMessage(b)) MINUTES_5 else DAY
        return abs(a.timestamp - b.timestamp) <= window
    }

    /** [messages]: the original message text of message-sourced payments, keyed by their time, where known. */
    fun find(all: List<ExpenseEntity>, messages: Map<Long, String> = emptyMap()): List<Dupe> {
        val out = mutableListOf<Dupe>()
        val gone = HashSet<Long>()
        val eligible = all.filter { !it.sourceMethod.equals("Manual", ignoreCase = true) }
        eligible.groupBy { Math.round(it.amount * 100) }.values.forEach { group ->
            val sorted = group.sortedBy { it.timestamp }
            for (i in sorted.indices) {
                val a = sorted[i]
                if (a.id in gone) continue
                for (j in i + 1 until sorted.size) {
                    val b = sorted[j]
                    if (b.id in gone || !sameEvent(a, b, messages)) continue
                    val keepA = rank(a) > rank(b) || (rank(a) == rank(b) && (a.type == TxnType.TRANSFER || b.type != TxnType.TRANSFER))
                    val (keep, drop) = if (keepA) a to b else b to a
                    gone.add(drop.id)
                    out.add(Dupe(remove = drop, keep = keep))
                    if (drop.id == a.id) break
                }
            }
        }
        return out
    }
}
