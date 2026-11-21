package com.keepsakeai.app.agent

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.keepsakeai.app.data.local.MemoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class LocalMediaMatch(
    val displayName: String,
    val bucketName: String,
    val dateTaken: Long
)

data class AgentActionResult(
    val matchedPhotos: List<LocalMediaMatch>,
    val exportedHtmlPath: String?,
    val summary: String
)

/**
 * Executes real on-device Agent Mode actions:
 * 1. Indexes local photos via Android MediaStore matching a keyword or date window.
 * 2. Exports a clean, standalone HTML itinerary/briefing from matched trove memories into Downloads.
 */
object DeviceAgentExecutor {

    suspend fun findLocalPhotos(context: Context, keyword: String, limit: Int = 20): List<LocalMediaMatch> =
        withContext(Dispatchers.IO) {
            val results = mutableListOf<LocalMediaMatch>()
            val projection = arrayOf(
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATE_TAKEN
            )
            runCatching {
                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    null,
                    null,
                    "${MediaStore.Images.Media.DATE_TAKEN} DESC"
                )?.use { cursor ->
                    val nameIdx = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
                    val bucketIdx = cursor.getColumnIndex(MediaStore.Images.Media.BUCKET_DISPLAY_NAME)
                    val dateIdx = cursor.getColumnIndex(MediaStore.Images.Media.DATE_TAKEN)
                    val q = keyword.lowercase()
                    while (cursor.moveToNext() && results.size < limit) {
                        val name = if (nameIdx >= 0) cursor.getString(nameIdx).orEmpty() else ""
                        val bucket = if (bucketIdx >= 0) cursor.getString(bucketIdx).orEmpty() else ""
                        val dt = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                        if (q.isBlank() || name.lowercase().contains(q) || bucket.lowercase().contains(q)) {
                            results.add(LocalMediaMatch(name, bucket, dt))
                        }
                    }
                }
            }
            results
        }

    suspend fun exportMemoriesToHtmlBrief(
        context: Context,
        title: String,
        memories: List<MemoryEntity>,
        agentPlanText: String
    ): String? = withContext(Dispatchers.IO) {
        val safeName = title.lowercase().replace(Regex("[^a-z0-9]+"), "_").take(32) + "_brief.html"
        val cardsHtml = memories.joinToString("\\n") { m ->
            """
            <div style="background:#fff;border-radius:16px;padding:16px;margin-bottom:12px;box-shadow:0 2px 10px rgba(0,0,0,.06)">
              <div style="font-size:11px;font-weight:700;color:#4f46e5;text-transform:uppercase">${m.collectionName}</div>
              <h3 style="margin:4px 0 6px">${m.title}</h3>
              <p style="margin:0;color:#475569;font-size:14px">${m.aiSummary}</p>
            </div>
            """.trimIndent()
        }
        val html = """
        <!DOCTYPE html>
        <html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>$title — trove</title></head>
        <body style="font-family:system-ui,sans-serif;background:#f4f6ff;color:#0f172a;max-width:720px;margin:24px auto;padding:16px">
          <h1>$title</h1>
          <div style="background:#eef2ff;border-radius:16px;padding:16px;margin-bottom:20px;white-space:pre-line">$agentPlanText</div>
          <h2>Grounded trove Memories (${memories.size})</h2>
          $cardsHtml
        </body></html>
        """.trimIndent()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, safeName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/html")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/keepsakeAI")
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(html.toByteArray()) }
                    return@runCatching "Downloads/keepsakeAI/$safeName"
                }
            }
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), safeName)
            file.writeText(html)
            file.absolutePath
        }.getOrNull()
    }
}
