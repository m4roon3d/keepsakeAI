package com.keepsakeai.app.email

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class MailProvider(val label: String, val host: String, val port: Int, val appPasswordUrl: String?, val steps: String) {
    GMAIL(
        "Gmail", "imap.gmail.com", 993, "https://myaccount.google.com/apppasswords",
        "1. Turn on 2-Step Verification in your Google account.\n2. Open App passwords, name it \"trove\" and create.\n3. Paste the 16-letter code below."
    ),
    YAHOO(
        "Yahoo", "imap.mail.yahoo.com", 993, "https://login.yahoo.com/account/security",
        "1. Open Yahoo Account Security.\n2. Under External connections, tap Create app password and name it \"trove\".\n3. Paste the code below."
    ),
    OTHER(
        "Other", "", 993, null,
        "Use your provider's IMAP server and an app password if they offer one."
    );

    companion object {
        fun forAddress(address: String): MailProvider? {
            val domain = address.substringAfter('@', "").lowercase()
            return when {
                domain in setOf("gmail.com", "googlemail.com") -> GMAIL
                domain.startsWith("yahoo.") || domain in setOf("ymail.com", "rocketmail.com") -> YAHOO
                else -> null
            }
        }

        /** Outlook.com turned off app passwords for other apps in 2024. */
        fun isMicrosoft(address: String): Boolean {
            val domain = address.substringAfter('@', "").lowercase()
            return listOf("outlook.", "hotmail.", "live.", "msn.com").any { domain.startsWith(it) || domain == it }
        }
    }
}

data class EmailAccount(
    val id: String,
    val address: String,
    val provider: MailProvider,
    val host: String,
    val port: Int,
    val lastSyncAt: Long = 0,
    val kept: Int = 0,
    val lastError: String? = null,
)

/** Connected mailboxes (not secret). Passwords live in the encrypted ApiKeyStore. */
class EmailStore(context: Context) {
    private val prefs = context.getSharedPreferences("email_prefs", Context.MODE_PRIVATE)

    fun all(): List<EmailAccount> = runCatching {
        val arr = JSONArray(prefs.getString("accounts", "[]"))
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            EmailAccount(
                id = o.getString("id"),
                address = o.getString("address"),
                provider = runCatching { MailProvider.valueOf(o.getString("provider")) }.getOrDefault(MailProvider.OTHER),
                host = o.getString("host"),
                port = o.optInt("port", 993),
                lastSyncAt = o.optLong("lastSyncAt"),
                kept = o.optInt("kept"),
                lastError = o.optString("lastError").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }.getOrDefault(emptyList())

    private fun save(list: List<EmailAccount>) {
        val arr = JSONArray()
        list.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id); put("address", a.address); put("provider", a.provider.name)
                put("host", a.host); put("port", a.port); put("lastSyncAt", a.lastSyncAt); put("kept", a.kept)
                put("lastError", a.lastError ?: "")
            })
        }
        prefs.edit().putString("accounts", arr.toString()).apply()
    }

    fun upsert(a: EmailAccount) = save(all().filterNot { it.id == a.id } + a)

    fun update(id: String, change: (EmailAccount) -> EmailAccount) {
        val list = all()
        save(list.map { if (it.id == id) change(it) else it })
    }

    fun remove(id: String) {
        save(all().filterNot { it.id == id })
        prefs.edit().remove("seen_$id").apply()
    }

    fun seen(id: String): Set<String> = prefs.getStringSet("seen_$id", emptySet()) ?: emptySet()

    fun markSeen(id: String, keys: Collection<String>) {
        if (keys.isEmpty()) return
        val next = (seen(id) + keys).toList().takeLast(4000).toSet()
        prefs.edit().putStringSet("seen_$id", next).apply()
    }
}
