package com.keepsakeai.app.capture

import android.Manifest
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.location.Geocoder
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Where and when a photo or video was taken. */
data class GeoTag(val lat: Double?, val lng: Double?, val takenAt: Long?, val place: String? = null) {
    val hasLocation get() = lat != null && lng != null
}

/** Reads location and time from photos and videos, and turns a video into a 3-frame strip Gemini can look at. */
object MediaMeta {

    fun canReadPhotoLocation(context: Context) = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) == PackageManager.PERMISSION_GRANTED

    /**
     * Android hides GPS in photos from other apps unless we hold ACCESS_MEDIA_LOCATION and open the
     * MediaStore item with setRequireOriginal. Gallery documents are mapped back to their MediaStore item first.
     */
    fun originalUri(context: Context, uri: Uri): Uri {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || uri.scheme != ContentResolver.SCHEME_CONTENT) return uri
        if (!canReadPhotoLocation(context)) return uri
        val media = when {
            uri.authority == MediaStore.AUTHORITY && !uri.path.orEmpty().contains("/picker") -> uri
            DocumentsContract.isDocumentUri(context, uri) -> runCatching { MediaStore.getMediaUri(context, uri) }.getOrNull()
            else -> null
        } ?: return uri
        val original = MediaStore.setRequireOriginal(media)
        // Only use it if we're actually allowed to open it.
        return if (runCatching { context.contentResolver.openInputStream(original)?.close(); true }.getOrDefault(false)) original else uri
    }

    fun readPhoto(file: File): GeoTag? = runCatching {
        val exif = ExifInterface(file)
        val ll = exif.latLong
        val taken = exif.dateTimeOriginal ?: exif.dateTime
        GeoTag(ll?.getOrNull(0), ll?.getOrNull(1), taken)
    }.getOrNull()?.takeIf { it.hasLocation || it.takenAt != null }

    /** Location and date from a video's metadata (ISO 6709 like "+12.9716+077.5946/"). */
    fun readVideo(context: Context, uri: Uri): Pair<GeoTag?, Long?> {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, originalUri(context, uri))
            val loc = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION)
            val (lat, lng) = parseIso6709(loc)
            val date = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)?.let { parseVideoDate(it) }
            val dur = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            GeoTag(lat, lng, date).takeIf { it.hasLocation || it.takenAt != null } to dur
        } catch (e: Exception) {
            null to null
        } finally {
            runCatching { r.release() }
        }
    }

    fun parseIso6709(s: String?): Pair<Double?, Double?> {
        val m = Regex("""([+-]\d+(?:\.\d+)?)([+-]\d+(?:\.\d+)?)""").find(s ?: return null to null) ?: return null to null
        val lat = m.groupValues[1].toDoubleOrNull()
        val lng = m.groupValues[2].toDoubleOrNull()
        return if (lat == null || lng == null || (lat == 0.0 && lng == 0.0)) null to null else lat to lng
    }

    fun parseVideoDate(s: String): Long? = listOf("yyyyMMdd'T'HHmmss.SSS'Z'", "yyyyMMdd'T'HHmmss'Z'", "yyyyMMdd'T'HHmmss")
        .firstNotNullOfOrNull { f ->
            runCatching { SimpleDateFormat(f, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(s)?.time }.getOrNull()
        }?.takeIf { it > 946684800000L } // phones write 1904/1970 when unset

    /** Three frames (start, middle, end) side by side, saved as a JPEG in the snaps folder. */
    fun videoStrip(context: Context, uri: Uri): File? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(context, uri)
            val durUs = (r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L) * 1000
            val frames = listOf(0.12, 0.5, 0.88).mapNotNull { f ->
                r.getFrameAtTime((durUs * f).toLong(), MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            }.map { b ->
                val h = 540
                val w = (b.width * h / b.height.toFloat()).toInt().coerceAtLeast(1)
                Bitmap.createScaledBitmap(b, w, h, true).also { if (it != b) b.recycle() }
            }
            if (frames.isEmpty()) return null
            val gap = 8
            val strip = Bitmap.createBitmap(frames.sumOf { it.width } + gap * (frames.size - 1), 540, Bitmap.Config.RGB_565)
            val canvas = Canvas(strip)
            var x = 0f
            frames.forEach { canvas.drawBitmap(it, x, 0f, null); x += it.width + gap; it.recycle() }
            val out = ImagePrep.newSnapFile(context)
            out.outputStream().use { strip.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            strip.recycle()
            out
        } catch (e: Exception) {
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** Street-level address for coordinates (on-device geocoder; no venue names). */
    @Suppress("DEPRECATION")
    fun address(context: Context, lat: Double, lng: Double): String? = runCatching {
        if (!Geocoder.isPresent()) return null
        val a = Geocoder(context, Locale.ENGLISH).getFromLocation(lat, lng, 1)?.firstOrNull() ?: return null
        listOfNotNull(a.featureName?.takeIf { f -> f.any { it.isLetter() } && f != a.thoroughfare }, a.thoroughfare, a.subLocality, a.locality)
            .distinct().joinToString(", ").ifBlank { a.getAddressLine(0) }
    }.getOrNull()

    fun describe(tag: GeoTag): String = buildString {
        tag.takenAt?.let { append("Taken: ").append(SimpleDateFormat("EEE d MMM yyyy, h:mm a", Locale.ENGLISH).format(Date(it))).append('\n') }
        if (tag.hasLocation) {
            append(String.format(Locale.US, "Location: %.5f, %.5f", tag.lat, tag.lng))
            tag.place?.let { append(" (near ").append(it).append(')') }
            append('\n')
        }
    }.trim()
}
