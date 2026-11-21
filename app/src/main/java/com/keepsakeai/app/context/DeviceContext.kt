package com.keepsakeai.app.context

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.coroutines.resume

/**
 * Live device context (date, time, approximate place) that Mind Pilot adds to every
 * question so answers like "weather tomorrow where I live" work.
 * Uses the platform LocationManager + Geocoder, no Play Services dependency.
 */
object DeviceContext {

    @Volatile private var cachedPlace: String? = null
    @Volatile private var cachedAt: Long = 0L

    fun hasLocationPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    fun nowLine(): String {
        val fmt = SimpleDateFormat("EEEE, d MMMM yyyy, h:mm a", Locale.ENGLISH)
        val tz = TimeZone.getDefault()
        return "${fmt.format(Date())} (${tz.id})"
    }

    /** Human-readable place, e.g. "Indiranagar, Bengaluru, Karnataka, India", or null. */
    suspend fun currentPlace(context: Context): String? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) return@withContext null
        val fresh = System.currentTimeMillis() - cachedAt < 15 * 60 * 1000
        if (fresh && cachedPlace != null) return@withContext cachedPlace
        val loc = lastKnown(context) ?: currentFix(context) ?: return@withContext null
        val place = reverseGeocode(context, loc)
            ?: String.format(Locale.US, "lat %.3f, lon %.3f", loc.latitude, loc.longitude)
        cachedPlace = place
        cachedAt = System.currentTimeMillis()
        place
    }

    /** Block of text added to AI prompts. */
    suspend fun promptBlock(context: Context): String {
        val place = currentPlace(context)
        return buildString {
            append("Current date and time: ").append(nowLine())
            append("\nUser's current location: ").append(place ?: "unknown (location permission not granted)")
        }
    }

    @SuppressLint("MissingPermission")
    private fun lastKnown(context: Context): Location? {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return runCatching {
            lm.getProviders(true)
                .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
                .maxByOrNull { it.time }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentFix(context: Context): Location? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) } ?: return null
        return withTimeoutOrNull(8_000) {
            suspendCancellableCoroutine { cont ->
                val signal = CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                runCatching {
                    lm.getCurrentLocation(provider, signal, context.mainExecutor) { loc ->
                        if (cont.isActive) cont.resume(loc)
                    }
                }.onFailure { if (cont.isActive) cont.resume(null) }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(context: Context, loc: Location): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        val a = Geocoder(context, Locale.ENGLISH).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            ?: return null
        listOfNotNull(a.subLocality, a.locality ?: a.subAdminArea, a.adminArea, a.countryName)
            .distinct()
            .joinToString(", ")
            .ifBlank { null }
    }.getOrNull()
}
