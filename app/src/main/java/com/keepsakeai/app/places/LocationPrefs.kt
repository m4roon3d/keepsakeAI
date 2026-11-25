package com.keepsakeai.app.places

import android.content.Context

/** Settings for places visited. Kept on the phone, never sent anywhere. */
class LocationPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("places_prefs", Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(v) = prefs.edit().putBoolean("enabled", v).apply()

    var paused: Boolean
        get() = prefs.getBoolean("paused", false)
        set(v) = prefs.edit().putBoolean("paused", v).apply()

    /** Days to keep visits; 0 means keep until deleted. */
    var retentionDays: Int
        get() = prefs.getInt("retention_days", 90)
        set(v) = prefs.edit().putInt("retention_days", v).apply()

    /** Home, work or anywhere else the user never wants recorded, as "lat,lng" strings. */
    var excluded: List<Pair<Double, Double>>
        get() = prefs.getStringSet("excluded", emptySet()).orEmpty().mapNotNull {
            val p = it.split(",")
            val a = p.getOrNull(0)?.toDoubleOrNull()
            val b = p.getOrNull(1)?.toDoubleOrNull()
            if (a != null && b != null) a to b else null
        }
        set(v) = prefs.edit().putStringSet("excluded", v.map { "${it.first},${it.second}" }.toSet()).apply()

    /** When on, a stay's approximate position is sent to OpenStreetMap's free place lookup to name the venue. */
    var venueLookup: Boolean
        get() = prefs.getBoolean("venue_lookup", true)
        set(v) = prefs.edit().putBoolean("venue_lookup", v).apply()

    /** Stays whose venue has already been looked up, so each is asked once. (v2: earlier lookups could count a busy server's empty reply as "nothing here".) */
    fun isVenueChecked(id: Long): Boolean = prefs.getStringSet("venue_checked_v2", emptySet()).orEmpty().contains(id.toString())

    fun markVenueChecked(id: Long) {
        val now = prefs.getStringSet("venue_checked_v2", emptySet()).orEmpty()
        prefs.edit().putStringSet("venue_checked_v2", now + id.toString()).apply()
    }

    fun isExcluded(lat: Double, lng: Double): Boolean =
        excluded.any { VisitTracker.distanceMeters(it.first, it.second, lat, lng) <= VisitTracker.EXCLUDE_RADIUS_M }
}
