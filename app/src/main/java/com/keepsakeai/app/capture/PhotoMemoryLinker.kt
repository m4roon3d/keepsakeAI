package com.keepsakeai.app.capture

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Automatically finds local device photos taken within +/- 24 hours of a captured memory
 * (or matching the memory's collection/trip folder) so keepsake AI can render rich Photo + Note cards.
 */
object PhotoMemoryLinker {

    suspend fun findMatchingPhotoUris(
        context: Context,
        timestampMillis: Long,
        keyword: String = "",
        maxPhotos: Int = 3
    ): List<String> = withContext(Dispatchers.IO) {
        val uris = mutableListOf<String>()
        val windowStart = timestampMillis - 24 * 3600_000L
        val windowEnd = timestampMillis + 24 * 3600_000L
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN
        )
        runCatching {
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Images.Media.DATE_TAKEN} >= ? AND ${MediaStore.Images.Media.DATE_TAKEN} <= ?",
                arrayOf(windowStart.toString(), windowEnd.toString()),
                "${MediaStore.Images.Media.DATE_TAKEN} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(MediaStore.Images.Media._ID)
                val nameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                val bucketIdx = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                val q = keyword.lowercase()
                while (cursor.moveToNext() && uris.size < maxPhotos) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else continue
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty().lowercase() else ""
                    val bucket = if (bucketIdx >= 0) cursor.getString(bucketIdx).orEmpty().lowercase() else ""
                    if (q.isBlank() || name.contains(q) || bucket.contains(q) || uris.isEmpty()) {
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            id
                        )
                        uris.add(contentUri.toString())
                    }
                }
            }
        }
        uris
    }
}
