package com.keepsakeai.app.ai

/**
 * Pilot is asked to write each real place it recommends as [[place: Name, Area, City]].
 * This turns those markers back into plain names for display and collects them for map links.
 */
object PlaceMarkers {
    private val marker = Regex("""\[\[\s*place\s*:\s*([^\]\[]{2,120}?)\s*]]""", RegexOption.IGNORE_CASE)

    data class Place(
        /** Short name shown in the text. */
        val name: String,
        /** Full "Name, Area, City" used for Google Maps and the geocoder. */
        val query: String
    )

    data class Parsed(val text: String, val places: List<Place>)

    fun parse(content: String): Parsed {
        val seen = LinkedHashMap<String, Place>()
        val text = marker.replace(content) { m ->
            val full = m.groupValues[1].trim()
            val name = full.substringBefore(',').trim().ifBlank { full }
            seen.getOrPut(full.lowercase()) { Place(name, full) }
            name
        }
        return Parsed(text, seen.values.toList())
    }

    /** Added to Pilot's prompt so it tags places in a way the app can use. */
    const val PROMPT_RULE =
        "Whenever you name a specific real place (restaurant, sight, hotel, station, shop), write it once, at its first mention, " +
            "as [[place: Name, Area, City]] with the area and city filled in, and nothing else inside the brackets. " +
            "Do not tag generic words, cities on their own, or places that are not real."
}
