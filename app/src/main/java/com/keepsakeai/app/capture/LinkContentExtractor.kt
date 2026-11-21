package com.keepsakeai.app.capture

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class ExtractedLinkContent(
    val url: String,
    val title: String,
    val bodyText: String,
    val platform: String
)

/**
 * Fetches full article text (Long-doc Capture) or YouTube/TikTok metadata + descriptions
 * when the user shares a link into keepsake AI.
 */
object LinkContentExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val urlRegex = Regex("""https?://\\S+""", RegexOption.IGNORE_CASE)

    fun extractFirstUrl(text: String): String? = urlRegex.find(text)?.value?.trimEnd('.', ',', ')')

    suspend fun fetch(url: String): ExtractedLinkContent? = withContext(Dispatchers.IO) {
        val lower = url.lowercase()
        when {
            "youtube.com" in lower || "youtu.be" in lower -> fetchYouTubeOEmbed(url)
            "tiktok.com" in lower -> fetchTikTokOEmbed(url)
            else -> fetchArticleWithJsoup(url)
        }
    }

    private fun fetchYouTubeOEmbed(url: String): ExtractedLinkContent? {
        return runCatching {
            val encoded = URLEncoder.encode(url, "UTF-8")
            val req = Request.Builder()
                .url("https://www.youtube.com/oembed?url=$encoded&format=json")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val json = JSONObject(resp.body?.string().orEmpty())
                val title = json.optString("title", "YouTube Video")
                val author = json.optString("author_name", "Creator")
                ExtractedLinkContent(
                    url = url,
                    title = title,
                    bodyText = "YouTube video by $author: $title ($url)",
                    platform = "YouTube"
                )
            }
        }.getOrNull()
    }

    private fun fetchTikTokOEmbed(url: String): ExtractedLinkContent? {
        return runCatching {
            val encoded = URLEncoder.encode(url, "UTF-8")
            val req = Request.Builder()
                .url("https://www.tiktok.com/oembed?url=$encoded")
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val json = JSONObject(resp.body?.string().orEmpty())
                val title = json.optString("title", "TikTok Video")
                val author = json.optString("author_name", "Creator")
                ExtractedLinkContent(
                    url = url,
                    title = title.take(60),
                    bodyText = "TikTok by $author: $title ($url)",
                    platform = "TikTok"
                )
            }
        }.getOrNull()
    }

    private fun fetchArticleWithJsoup(url: String): ExtractedLinkContent? {
        return runCatching {
            val doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Linux; Android 15) keepsakeAI/1.0")
                .timeout(15000)
                .get()
            val title = doc.title().ifBlank { url }
            val paragraphs = doc.select("article p, main p, p")
                .map { it.text().trim() }
                .filter { it.length > 35 }
                .take(25)
                .joinToString("\\n\\n")
            val body = paragraphs.ifBlank { doc.body()?.text()?.take(3500).orEmpty() }
            ExtractedLinkContent(
                url = url,
                title = title,
                bodyText = body.take(4500),
                platform = "Article"
            )
        }.getOrNull()
    }
}
