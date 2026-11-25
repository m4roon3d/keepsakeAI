package com.keepsakeai.app.places

/** Reads and rewrites the place on a memory's "Location: lat, lng (near X)" line. */
object PlaceTag {
    private val loc = Regex("""Location: (-?\d+\.\d+), (-?\d+\.\d+)(?: \(near ([^)]*)\))?""")

    fun placeOf(raw: String): String? = loc.find(raw)?.groupValues?.get(3)?.takeIf { it.isNotBlank() }

    /** Sets the place name. With no Location line yet, adds one carrying the coordinates when known, else a "Place:" line. */
    fun withPlace(raw: String, name: String, lat: Double?, lng: Double?): String {
        val clean = name.replace(Regex("[()\\n]"), " ").trim()
        if (clean.isBlank()) return raw
        loc.find(raw)?.let { m ->
            return raw.replaceRange(m.range, "Location: ${m.groupValues[1]}, ${m.groupValues[2]} (near $clean)")
        }
        val line = if (lat != null && lng != null) String.format(java.util.Locale.US, "Location: %.5f, %.5f (near %s)", lat, lng, clean) else "Place: $clean"
        val rest = raw.lines().filterNot { it.startsWith("Place: ") }.joinToString("\n")
        return if (rest.isBlank()) line else "$line\n$rest"
    }

    /** "Place: X" when no coordinates were available. */
    fun plainPlace(raw: String): String? = raw.lineSequence().firstOrNull { it.startsWith("Place: ") }?.removePrefix("Place: ")?.trim()?.takeIf { it.isNotBlank() }
}
