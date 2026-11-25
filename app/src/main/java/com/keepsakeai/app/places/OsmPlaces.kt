package com.keepsakeai.app.places

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** A named place near a point, from OpenStreetMap. [landmark] is true for named buildings and structures rather than businesses. */
data class Venue(val name: String, val kind: String, val meters: Int, val landmark: Boolean = false)

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

    /** Businesses and public places: restaurants, shops, hospitals, banks, temples, hotels, parks, monuments, studios... */
    private val placeKeys = listOf("amenity", "shop", "tourism", "leisure", "office", "historic", "craft", "healthcare")
    /** Named buildings and structures (apartment blocks, towers, stations, malls without a shop tag). Ranked after businesses. */
    private val structureKeys = listOf("building", "man_made", "railway", "aeroway", "public_transport")

    fun query(lat: Double, lng: Double, radius: Int): String {
        val around = "around:$radius,${"%.6f".format(java.util.Locale.US, lat)},${"%.6f".format(java.util.Locale.US, lng)}"
        val parts = (placeKeys + structureKeys).joinToString("") { "nwr($around)[\"name\"][\"$it\"];" }
        return "[out:json][timeout:10];($parts);out center 80;"
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
            val place = placeKeys.firstNotNullOfOrNull { k -> tags.optString(k).takeIf { it.isNotBlank() } }
            val structure = if (place == null) structureKeys.firstNotNullOfOrNull { k ->
                tags.optString(k).takeIf { it.isNotBlank() }?.let { v -> if (v == "yes") k else v }
            } else null
            val kind = (place ?: structure).orEmpty().replace('_', ' ')
            Venue(name, kind, VisitTracker.distanceMeters(lat, lng, la, lo).toInt(), landmark = place == null)
        }.sortedWith(compareBy<Venue>({ it.landmark }, { it.meters })).distinctBy { it.name.lowercase() }
    }

    /** Public Overpass servers, tried in order: the main one is often busy and answers with an error or an empty "timed out" reply. */
    private val endpoints = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    )

    /** True when [body] is a complete Overpass answer: JSON with an elements list and no runtime error (timeout, overload). */
    fun isCompleteReply(body: String): Boolean = runCatching {
        val o = JSONObject(body)
        o.has("elements") && !o.optString("remark").contains("runtime error", ignoreCase = true)
    }.getOrDefault(false)

    /** Named places within [radius] metres (businesses nearest first, then named buildings); null when no server gave a usable answer. */
    suspend fun nearbyOrNull(lat: Double, lng: Double, radius: Int = 60, limit: Int = 5): List<Venue>? = withContext(Dispatchers.IO) {
        val q = query(lat, lng, radius)
        for (url in endpoints) {
            val body = runCatching {
                val req = Request.Builder().url(url)
                    .header("User-Agent", "Trove/1.0 (personal notes app)")
                    .post(FormBody.Builder().add("data", q).build()).build()
                client.newCall(req).execute().use { r ->
                    if (!r.isSuccessful) null else r.body?.string().orEmpty().takeIf { isCompleteReply(it) }
                }
            }.getOrNull()
            if (body != null) return@withContext parse(body, lat, lng).take(limit)
        }
        null
    }

    /** Same as [nearbyOrNull], but empty when offline or nothing is mapped there. */
    suspend fun nearby(lat: Double, lng: Double, radius: Int = 60, limit: Int = 5): List<Venue> =
        nearbyOrNull(lat, lng, radius, limit).orEmpty()

    /** The one venue worth stamping on a photo automatically: close by, otherwise leave it to the person. */
    suspend fun best(lat: Double, lng: Double, withinMeters: Int = 40): Venue? =
        nearby(lat, lng, withinMeters, 1).firstOrNull()
}
