package com.keepsakeai.app.data.preferences

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A reminder found in a capture. Status: suggested → scheduled | dismissed. */
data class KeepReminder(
    val id: Long,
    val title: String,
    val atMillis: Long,
    val kind: String,
    val memoryId: Long,
    val status: String = "suggested",
    val notifyAt: Long = 0L,
    /** False when only a date was found (e.g. a poster without a show time). */
    val hasTime: Boolean = true,
) {
    /** Date-only reminders stay visible for the whole day. */
    fun isUpcoming(now: Long = System.currentTimeMillis()) =
        status != "dismissed" && atMillis + (if (hasTime) 0L else 15L * 60 * 60 * 1000) > now
}

/** Reminders live in preferences (no database migration needed). */
class ReminderStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("reminders", Context.MODE_PRIVATE)

    fun all(): List<KeepReminder> = runCatching {
        val arr = JSONArray(prefs.getString("items", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            KeepReminder(
                o.getLong("id"), o.getString("title"), o.getLong("at"), o.optString("kind", "event"),
                o.optLong("memoryId"), o.optString("status", "suggested"), o.optLong("notifyAt"), o.optBoolean("hasTime", true)
            )
        }
    }.getOrDefault(emptyList())

    private fun save(items: List<KeepReminder>) {
        val arr = JSONArray()
        // Keep things tidy: drop anything more than 30 days past.
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        items.filter { it.atMillis > cutoff }.takeLast(200).forEach {
            arr.put(JSONObject().put("id", it.id).put("title", it.title).put("at", it.atMillis).put("kind", it.kind)
                .put("memoryId", it.memoryId).put("status", it.status).put("notifyAt", it.notifyAt).put("hasTime", it.hasTime))
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    fun add(r: KeepReminder) = save(all().filterNot { it.id == r.id } + r)
    fun update(id: Long, f: (KeepReminder) -> KeepReminder) = save(all().map { if (it.id == id) f(it) else it })
    fun remove(id: Long) = save(all().filterNot { it.id == id })
}
