package com.keepsakeai.app.intents

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingEvent
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.keepsakeai.app.MindSpaceApp
import com.keepsakeai.app.data.preferences.TodoStore
import com.keepsakeai.app.notify.Notifier
import org.json.JSONArray
import org.json.JSONObject

/** One spot where an errand can be done: "Phoenix Marketcity" at its coordinates. */
data class TaskSpot(val name: String, val lat: Double, val lng: Double)

/** "Buy batteries when I'm near a mall": the task, the place as said, and the real spots it was matched to. */
data class PlaceTask(
    val id: Long,
    val task: String,
    val placeAsSaid: String,
    val spots: List<TaskSpot>,
    val todoId: Long?,
    val memoryId: Long,
    val done: Boolean = false,
)

class PlaceTaskStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("place_tasks", Context.MODE_PRIVATE)

    fun all(): List<PlaceTask> = runCatching {
        val arr = JSONArray(prefs.getString("items", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            val sp = o.optJSONArray("spots") ?: JSONArray()
            PlaceTask(
                o.getLong("id"), o.getString("task"), o.optString("place"),
                List(sp.length()) { j -> sp.getJSONObject(j).let { s -> TaskSpot(s.getString("name"), s.getDouble("lat"), s.getDouble("lng")) } },
                o.optLong("todoId").takeIf { it != 0L }, o.optLong("memoryId"), o.optBoolean("done")
            )
        }
    }.getOrDefault(emptyList())

    fun save(items: List<PlaceTask>) {
        val arr = JSONArray()
        items.takeLast(60).forEach { t ->
            val sp = JSONArray()
            t.spots.forEach { s -> sp.put(JSONObject().put("name", s.name).put("lat", s.lat).put("lng", s.lng)) }
            arr.put(JSONObject().put("id", t.id).put("task", t.task).put("place", t.placeAsSaid).put("spots", sp)
                .put("todoId", t.todoId ?: 0L).put("memoryId", t.memoryId).put("done", t.done))
        }
        prefs.edit().putString("items", arr.toString()).apply()
    }

    fun add(t: PlaceTask) = save(all().filterNot { it.memoryId == t.memoryId && it.task.equals(t.task, true) } + t)

    fun setDone(id: Long) = save(all().map { if (it.id == id) it.copy(done = true) else it })

    /** Open errands, dropping ones whose linked to-do was ticked off. */
    fun open(context: Context): List<PlaceTask> {
        val doneTodos = TodoStore(context).all().filter { it.done }.map { it.id }.toSet()
        return all().filter { !it.done && (it.todoId == null || it.todoId !in doneTodos) }
    }
}

/** Geofences around each errand's spots; Android wakes trove when you arrive. */
object PlaceAlerts {
    const val RADIUS_M = 250f

    fun canWatch(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val bg = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine && bg
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context, 4711, Intent(context, PlaceAlertReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
    )

    /** Replaces all of trove's geofences with the open errands' spots. Safe to call often. */
    @SuppressLint("MissingPermission")
    fun sync(context: Context) {
        if (!canWatch(context)) return
        val client = LocationServices.getGeofencingClient(context)
        val fences = PlaceTaskStore(context).open(context).flatMap { t ->
            t.spots.mapIndexed { i, s ->
                Geofence.Builder()
                    .setRequestId("${t.id}:$i")
                    .setCircularRegion(s.lat, s.lng, RADIUS_M)
                    .setExpirationDuration(60L * 24 * 60 * 60 * 1000)
                    .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER)
                    .build()
            }
        }.take(90)
        client.removeGeofences(pending(context)).addOnCompleteListener {
            if (fences.isNotEmpty()) runCatching {
                client.addGeofences(
                    GeofencingRequest.Builder().setInitialTrigger(0).addGeofences(fences).build(), pending(context)
                )
            }
        }
    }
}

class PlaceAlertReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val event = GeofencingEvent.fromIntent(intent) ?: return
        if (event.hasError() || event.geofenceTransition != Geofence.GEOFENCE_TRANSITION_ENTER) return
        val app = context.applicationContext as? MindSpaceApp ?: return
        val store = PlaceTaskStore(app)
        val open = store.open(app).associateBy { it.id }
        event.triggeringGeofences.orEmpty().forEach { g ->
            val (taskId, spotIdx) = g.requestId.split(":").let { it[0].toLongOrNull() to it.getOrNull(1)?.toIntOrNull() }
            val t = open[taskId] ?: return@forEach
            val spot = spotIdx?.let { t.spots.getOrNull(it) }?.name ?: t.placeAsSaid
            Notifier.show(app, Notifier.REMINDERS, ("place" + g.requestId).hashCode(), "You're near $spot", t.task.replaceFirstChar { it.uppercase() }, openTab = "home")
        }
    }
}
