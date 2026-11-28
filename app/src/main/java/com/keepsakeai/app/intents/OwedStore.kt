package com.keepsakeai.app.intents

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Money lent or borrowed between you and someone: "Lent Ravi ₹2,000", "I owe Meera 500". */
data class Owed(
    val id: Long,
    val person: String,
    val amount: Double?,
    /** True when they owe you; false when you owe them. */
    val owedToMe: Boolean,
    val what: String,
    val memoryId: Long,
    val at: Long,
    val settled: Boolean = false,
)

object OwedRules {
    const val NUDGE_DAYS = 14
    private const val DAY = 24L * 60 * 60 * 1000

    fun open(all: List<Owed>): List<Owed> = all.filter { !it.settled }.sortedBy { it.at }

    fun daysOpen(o: Owed, now: Long): Int = ((now - o.at) / DAY).toInt()

    /** Open items old enough for a gentle nudge. */
    fun dueForNudge(all: List<Owed>, now: Long): List<Owed> = open(all).filter { daysOpen(it, now) >= NUDGE_DAYS }

    /** Net per person (positive: they owe you), skipping amounts that weren't given. */
    fun netByPerson(all: List<Owed>): Map<String, Double> =
        open(all).filter { it.amount != null }.groupBy { it.person.lowercase() }
            .mapValues { (_, l) -> l.sumOf { if (it.owedToMe) it.amount!! else -it.amount!! } }
}

class OwedStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("owed", Context.MODE_PRIVATE)

    fun all(): List<Owed> = runCatching {
        val arr = JSONArray(prefs.getString("items", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Owed(
                o.getLong("id"), o.getString("person"), o.optDouble("amount", Double.NaN).takeIf { !it.isNaN() },
                o.optBoolean("toMe", true), o.optString("what"), o.optLong("memoryId"), o.optLong("at"), o.optBoolean("settled")
            )
        }
    }.getOrDefault(emptyList())

    private fun save(items: List<Owed>) {
        val arr = JSONArray()
        items.takeLast(300).forEach { o ->
            arr.put(JSONObject().put("id", o.id).put("person", o.person).put("amount", o.amount ?: Double.NaN)
                .put("toMe", o.owedToMe).put("what", o.what).put("memoryId", o.memoryId).put("at", o.at).put("settled", o.settled))
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    fun add(o: Owed) {
        val items = all()
        // The same capture processed twice should not add twice.
        if (items.any { it.memoryId == o.memoryId && it.person.equals(o.person, true) && it.amount == o.amount }) return
        save(items + o)
    }

    fun setSettled(id: Long, settled: Boolean = true) = save(all().map { if (it.id == id) it.copy(settled = settled) else it })
}
