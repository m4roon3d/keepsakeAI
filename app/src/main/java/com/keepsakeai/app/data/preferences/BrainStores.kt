package com.keepsakeai.app.data.preferences

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

// ── People, places, projects, organisations ──────────────────────────

data class EntityNote(val memoryId: Long, val note: String, val at: Long)

data class Entity(
    val name: String,
    val type: String, // person | place | project | org
    val notes: List<EntityNote> = emptyList(),
    val memoryIds: List<Long> = emptyList(),
    val hidden: Boolean = false,
) {
    val key: String get() = entityKey(type, name)
    val lastSeen: Long get() = notes.maxOfOrNull { it.at } ?: 0L
}

fun entityKey(type: String, name: String) = type.lowercase(Locale.ROOT) + ":" + name.trim().lowercase(Locale.ROOT)

private val validTypes = setOf("person", "place", "project", "org")

/**
 * Finds the entity a new mention refers to: same type and the same name, or one name is a
 * longer form of the other ("Ravi" / "Ravi Kumar"). Pure, unit-tested.
 */
fun matchEntity(existing: Collection<Entity>, type: String, name: String): Entity? {
    val n = name.trim().lowercase(Locale.ROOT)
    val sameType = existing.filter { it.type == type }
    sameType.firstOrNull { it.name.lowercase(Locale.ROOT) == n }?.let { return it }
    return sameType.firstOrNull { e ->
        val en = e.name.lowercase(Locale.ROOT)
        val shorter = if (en.length < n.length) en else n
        val longer = if (en.length < n.length) n else en
        shorter.length >= 3 && (longer.startsWith("$shorter ") || longer.endsWith(" $shorter"))
    }
}

class EntityStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("entities", Context.MODE_PRIVATE)

    fun all(): List<Entity> = runCatching {
        val arr = JSONArray(prefs.getString("items", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val notes = o.optJSONArray("notes") ?: JSONArray()
            val ids = o.optJSONArray("ids") ?: JSONArray()
            Entity(
                o.getString("name"), o.getString("type"),
                List(notes.length()) { j -> notes.getJSONObject(j).let { n -> EntityNote(n.optLong("m"), n.optString("t"), n.optLong("at")) } },
                List(ids.length()) { j -> ids.getLong(j) },
                o.optBoolean("hidden")
            )
        }
    }.getOrDefault(emptyList())

    private fun save(items: List<Entity>) {
        val arr = JSONArray()
        items.forEach { e ->
            arr.put(
                JSONObject().put("name", e.name).put("type", e.type).put("hidden", e.hidden)
                    .put("notes", JSONArray(e.notes.takeLast(40).map { JSONObject().put("m", it.memoryId).put("t", it.note).put("at", it.at) }))
                    .put("ids", JSONArray(e.memoryIds.takeLast(200)))
            )
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    /** Records that [memoryId] mentions [name]; merges with an existing entity when it's the same one. */
    fun addMention(type: String, name: String, note: String, memoryId: Long, at: Long) {
        val t = type.lowercase(Locale.ROOT).takeIf { it in validTypes } ?: return
        val clean = name.trim().take(48)
        if (clean.length < 2) return
        val items = all().toMutableList()
        val match = matchEntity(items, t, clean)
        val newNote = note.trim().takeIf { it.isNotBlank() }?.let { EntityNote(memoryId, it.take(160), at) }
        if (match == null) {
            items += Entity(clean, t, listOfNotNull(newNote), listOf(memoryId))
        } else {
            val i = items.indexOf(match)
            // Prefer the fuller name ("Ravi Kumar" over "Ravi").
            val name2 = if (clean.length > match.name.length) clean else match.name
            items[i] = match.copy(
                name = name2,
                notes = (match.notes + listOfNotNull(newNote)).distinctBy { it.note.lowercase(Locale.ROOT) },
                memoryIds = (match.memoryIds + memoryId).distinct()
            )
        }
        save(items)
    }

    fun hide(key: String) = save(all().map { if (it.key == key) it.copy(hidden = true) else it })

    fun forgetMemory(memoryId: Long) = save(all().map { e ->
        e.copy(memoryIds = e.memoryIds - memoryId, notes = e.notes.filterNot { it.memoryId == memoryId })
    }.filter { it.memoryIds.isNotEmpty() })

    var backfilled: Set<Long>
        get() = prefs.getStringSet("backfilled", emptySet()).orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
        set(v) = prefs.edit().putStringSet("backfilled", v.map { it.toString() }.toSet()).apply()
}

// ── To-dos ───────────────────────────────────────────────────────────

data class Todo(
    val id: Long,
    val text: String,
    val due: Long? = null,
    val done: Boolean = false,
    val doneAt: Long? = null,
    val memoryId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

enum class TodoBucket(val label: String) { OVERDUE("overdue"), TODAY("today"), SOON("this week"), LATER("later"), SOMEDAY("someday"), DONE("done") }

/** Which group a to-do belongs in, relative to [now]. Pure, unit-tested. */
fun bucketOf(t: Todo, now: Long, startOfToday: Long): TodoBucket {
    val day = 24L * 60 * 60 * 1000
    return when {
        t.done -> TodoBucket.DONE
        t.due == null -> TodoBucket.SOMEDAY
        t.due < startOfToday -> TodoBucket.OVERDUE
        t.due < startOfToday + day -> TodoBucket.TODAY
        t.due < startOfToday + 7 * day -> TodoBucket.SOON
        else -> TodoBucket.LATER
    }
}

class TodoStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("todos", Context.MODE_PRIVATE)

    fun all(): List<Todo> = runCatching {
        val arr = JSONArray(prefs.getString("items", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            Todo(
                o.getLong("id"), o.getString("text"),
                o.optLong("due", 0L).takeIf { it > 0 }, o.optBoolean("done"),
                o.optLong("doneAt", 0L).takeIf { it > 0 }, o.optLong("memoryId", 0L).takeIf { it > 0 }, o.optLong("createdAt")
            )
        }
    }.getOrDefault(emptyList())

    private fun save(items: List<Todo>) {
        // Done items older than 30 days drop off.
        val cutoff = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        val arr = JSONArray()
        items.filterNot { it.done && (it.doneAt ?: 0) < cutoff }.takeLast(500).forEach {
            arr.put(
                JSONObject().put("id", it.id).put("text", it.text).put("due", it.due ?: 0L).put("done", it.done)
                    .put("doneAt", it.doneAt ?: 0L).put("memoryId", it.memoryId ?: 0L).put("createdAt", it.createdAt)
            )
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    fun add(text: String, due: Long? = null, memoryId: Long? = null): Todo? {
        val t = text.trim().take(140)
        if (t.isBlank()) return null
        val items = all()
        // Skip near-duplicates still open.
        if (items.any { !it.done && it.text.equals(t, ignoreCase = true) }) return null
        val todo = Todo(System.currentTimeMillis() + items.size, t, due, memoryId = memoryId)
        save(items + todo)
        return todo
    }

    fun toggle(id: Long) = save(all().map { if (it.id == id) it.copy(done = !it.done, doneAt = if (!it.done) System.currentTimeMillis() else null) else it })
    fun remove(id: Long) = save(all().filterNot { it.id == id })
    fun update(id: Long, f: (Todo) -> Todo) = save(all().map { if (it.id == id) f(it) else it })
}
