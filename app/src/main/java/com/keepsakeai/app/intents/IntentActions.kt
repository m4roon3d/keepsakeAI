package com.keepsakeai.app.intents

import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.ai.CaptureIntent
import com.keepsakeai.app.context.DeviceContext
import com.keepsakeai.app.data.preferences.EntityStore
import com.keepsakeai.app.data.preferences.TodoStore
import com.keepsakeai.app.explore.Explorations
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What trove does with each intent found in a capture. Runs in the background after the memory is saved. */
object IntentActions {
    const val WHERE_TO_WATCH = "Where to watch"
    const val WHERE_TO_GET = "Where to get it"
    const val TRIP_PLAN = "Trip plan"
    const val HOW_TO_CANCEL = "How to cancel"
    const val ON_MAPS = "On the map"

    private fun today() = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date())

    /** "a mall", "any pharmacy", "the nearest ATM": a kind of place rather than one place. */
    fun isGenericPlace(p: String): Boolean =
        Regex("^(?:a|an|any|some|the nearest|nearest|the closest|closest)\\s", RegexOption.IGNORE_CASE).containsMatchIn(p.trim())

    /** Location line as saved in memory text, replacing an existing one. */
    fun withLocation(raw: String, lat: Double, lng: Double, name: String): String {
        val line = String.format(Locale.US, "Location: %.5f, %.5f (near %s)", lat, lng, name)
        val existing = Regex("(?m)^Location: .*$")
        return if (existing.containsMatchIn(raw)) raw.replace(existing, line) else raw.trimEnd() + "\n\n" + line
    }

    fun mapsLink(q: String) = "[Google Maps](https://www.google.com/maps/search/?api=1&query=${URLEncoder.encode(q, "UTF-8")})"

    suspend fun apply(app: MindSpaceApp, memoryId: Long, intents: List<CaptureIntent>, near: String?, productHandled: Boolean, findWhereToBuy: suspend (String) -> Unit) {
        val now = System.currentTimeMillis()
        intents.forEach { i ->
            runCatching {
                when (i.type) {
                    "gift" -> {
                        i.person?.let { EntityStore(app).addMention("person", it, "gift idea: ${i.subject}", memoryId, now) }
                        addTags(app, memoryId, "gift idea")
                        if (!productHandled) findWhereToBuy(i.subject)
                    }
                    "owe" -> {
                        val person = i.person ?: return@runCatching
                        val toMe = i.direction != "i_owe"
                        OwedStore(app).add(Owed(now + memoryId + person.hashCode(), person, i.amount, toMe, i.subject, memoryId, now))
                        val amt = i.amount?.let { "₹" + java.text.NumberFormat.getIntegerInstance(Locale("en", "IN")).format(Math.round(it)) } ?: "money"
                        EntityStore(app).addMention("person", person, if (toMe) "owes you $amt" else "you owe them $amt", memoryId, now)
                        addTags(app, memoryId, "money owed")
                    }
                    "watch", "read" -> {
                        updateMemory(app, memoryId) { it.copy(collectionName = "Watch Later") }
                        addTags(app, memoryId, if (i.type == "watch") "to watch" else "to read")
                        app.mindPilotClient.intentLookup(i.type, i.subject, near)?.let {
                            saveLookup(app, memoryId, if (i.type == "watch") WHERE_TO_WATCH else WHERE_TO_GET, i.subject, it)
                        }
                    }
                    "trip" -> {
                        addTags(app, memoryId, "trip")
                        app.mindPilotClient.intentLookup("trip", i.subject, near, i.place)?.let { saveLookup(app, memoryId, TRIP_PLAN, i.subject, it) }
                    }
                    "cancel" -> {
                        TodoStore(app).add("Cancel ${i.subject}", null, memoryId)
                        app.mindPilotClient.intentLookup("cancel", i.subject, near)?.let { saveLookup(app, memoryId, HOW_TO_CANCEL, i.subject, it) }
                    }
                    "try_place" -> {
                        val query = listOfNotNull(i.subject, i.place ?: near).joinToString(", ")
                        val at = DeviceContext.coordsFor(app, query)
                        updateMemory(app, memoryId) { m ->
                            val raw = if (at != null) withLocation(m.rawContent, at.first, at.second, i.subject) else m.rawContent
                            m.copy(rawContent = Explorations.setLookup(raw, today(), ON_MAPS, i.subject, mapsLink(query)), lat = at?.first ?: m.lat, lng = at?.second ?: m.lng)
                        }
                        EntityStore(app).addMention("place", i.subject, "want to try", memoryId, now)
                        addTags(app, memoryId, "to try")
                    }
                    "place_task" -> {
                        val where = i.place ?: return@runCatching
                        val todo = TodoStore(app).add("${i.subject.replaceFirstChar { it.uppercase() }} (at $where)", null, memoryId)
                        val spots = resolveSpots(app, where, near)
                        PlaceTaskStore(app).add(PlaceTask(now + memoryId, i.subject, where, spots, todo?.id, memoryId))
                        PlaceAlerts.sync(app)
                        addTags(app, memoryId, "errand")
                    }
                }
            }
        }
    }

    /** Real spots for an errand: the named place, or the 2-3 nearest of a kind ("a mall") near where you are. */
    private suspend fun resolveSpots(app: MindSpaceApp, where: String, near: String?): List<TaskSpot> {
        if (!isGenericPlace(where)) {
            return listOfNotNull(DeviceContext.coordsFor(app, listOfNotNull(where, near).joinToString(", "))?.let { TaskSpot(where, it.first, it.second) })
        }
        val here = near ?: DeviceContext.currentLatLng(app)?.let { (la, ln) -> DeviceContext.placeNameAt(app, la, ln) } ?: return emptyList()
        val json = app.mindPilotClient.intentLookup("nearby", where.replace(Regex("^(?:a|an|any|some|the nearest|nearest|the closest|closest)\\s+", RegexOption.IGNORE_CASE), ""), here)
            ?: return emptyList()
        val arr = runCatching { JSONArray(json.substring(json.indexOf('['), json.lastIndexOf(']') + 1)) }.getOrNull() ?: return emptyList()
        return List(arr.length()) { k -> arr.optJSONObject(k) }.filterNotNull().take(3).mapNotNull { o ->
            val name = o.optString("name").trim().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            DeviceContext.coordsFor(app, "$name, ${o.optString("area")}")?.let { TaskSpot(name, it.first, it.second) }
        }
    }

    private suspend fun updateMemory(app: MindSpaceApp, id: Long, change: (com.keepsakeai.app.data.local.MemoryEntity) -> com.keepsakeai.app.data.local.MemoryEntity) {
        val dao = app.database.dao()
        val m = dao.observeMemory(id).first() ?: return
        val u = change(m)
        val vec = app.vectorSearch.embedText(Explorations.embedText(u.title, u.aiSummary, u.rawContent, u.tagsCsv))
        dao.updateMemory(u.copy(embeddingCsv = app.vectorSearch.encodeVector(vec)))
    }

    private suspend fun addTags(app: MindSpaceApp, id: Long, vararg tags: String) = updateMemory(app, id) { m ->
        m.copy(tagsCsv = (m.tagsCsv.split(",").map { it.trim() }.filter { it.isNotBlank() } + tags).distinct().joinToString(","))
    }

    private suspend fun saveLookup(app: MindSpaceApp, id: Long, label: String, subject: String, answer: String) = updateMemory(app, id) { m ->
        m.copy(rawContent = Explorations.setLookup(m.rawContent, today(), label, subject, Explorations.clean(com.keepsakeai.app.ai.PlaceMarkers.parse(answer).text)))
    }
}
