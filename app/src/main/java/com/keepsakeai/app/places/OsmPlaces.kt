package com.keepsakeai.app.places

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A named place near a point, from OpenStreetMap. */
data class Venue(val name: String, val kind: String, val meters: Int)

/**
 * Names the business a photo was taken at. The phone's geocoder only knows streets, so this asks
 * OpenStreetMap (free, no key) for named cafés, shops and sights within a few dozen metres.
 */
object OsmPlaces {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS).readTimeout(12, TimeUnit.SECONDS).build()

    /** Things with a name in OSM that aren't somewhere you'd say you "went". */
    private val skipAmenity = setOf("parking", "bench", "atm", "toilets", "bicycle_parking", "waste_basket", "drinking_water",
        "shelter", "post_box", "recycling", "parking_space", "parking_entrance", "motorcycle_parking", "charging_station")

    fun query(lat: Double, lng: Double, radius: Int): String {
        val around = "around:$radius,${"%.6f".format(java.util.Locale.US, lat)},${"%.6f".format(java.util.Locale.US, lng)}"
        val parts = listOf("amenity", "shop", "tourism", "leisure", "office").joinToString("") { "nwr($around)[\"name\"][\"$it\"];" }
        return "[out:json][timeout:10];($parts);out center 40;"
    }

    /** Named places from an Overpass reply, nearest first, without duplicates. */
    fun parse(json: String, lat: Double, lng: Double): List<Venue> {
        val els = runCatching { JSONObject(json).getJSONArray("elements") }.getOrNull() ?: return emptyList()
        return List(els.length()) { els.getJSONObject(it) }.mapNotNull { e ->
            val tags = e.optJSONObject("tags") ?: return@mapNotNull null
            val name = tags.optString("name").trim().takeIf { it.length >= 2 } ?: return@mapNotNull null
            val amenity = tags.optString("amenity")
            if (amenity in skipAmenity) return@mapNotNull null
            val c = e.optJSONObject("center")
            val la = if (e.has("lat")) e.optDouble("lat") else c?.optDouble("lat") ?: return@mapNotNull null
            val lo = if (e.has("lon")) e.optDouble("lon") else c?.optDouble("lon") ?: return@mapNotNull null
            val kind = listOf("amenity", "shop", "tourism", "leisure", "office").firstNotNullOfOrNull { k ->
                tags.optString(k).takeIf { it.isNotBlank() }
            }.orEmpty().replace('_', ' ')
            Venue(name, kind, VisitTracker.distanceMeters(lat, lng, la, lo).toInt())
        }.sortedBy { it.meters }.distinctBy { it.name.lowercase() }
    }

    /** Named places within [radius] metres, nearest first; empty when offline or nothing is mapped there. */
    suspend fun nearby(lat: Double, lng: Double, radius: Int = 60, limit: Int = 5): List<Venue> = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder().url("https://overpass-api.de/api/interpreter")
                .header("User-Agent", "Trove/1.0 (personal notes app)")
                .post(FormBody.Builder().add("data", query(lat, lng, radius)).build()).build()
            client.newCall(req).execute().use { r ->
                if (!r.isSuccessful) emptyList() else parse(r.body?.string().orEmpty(), lat, lng).take(limit)
            }
        }.getOrDefault(emptyList())
    }

    /** The one venue worth stamping on a photo automatically: close by, otherwise leave it to the person. */
    suspend fun best(lat: Double, lng: Double, withinMeters: Int = 40): Venue? =
        nearby(lat, lng, withinMeters, 1).firstOrNull()
}
