package com.keepsakeai.app.maps

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.net.URLEncoder

/** "Directions" to a place or address in Google Maps. Free: it only opens the Maps app (or the website), no API. */
object Directions {

    /** Maps URL for a destination given as a name, an address, or "lat,lng". Pure, unit-tested. */
    fun url(destination: String): String =
        "https://www.google.com/maps/dir/?api=1&destination=" + URLEncoder.encode(destination.trim(), "UTF-8").replace("+", "%20")

    fun url(lat: Double, lng: Double): String = url("$lat,$lng")

    /** Opens directions in Maps. Returns false when nothing on the phone could handle it. */
    fun open(context: Context, destination: String): Boolean {
        if (destination.isBlank()) return false
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url(destination))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }
}
