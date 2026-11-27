package com.keepsakeai.app.finance

import java.security.MessageDigest
import java.util.Locale

/**
 * A short fingerprint of a bank message's text. Messaging apps re-post a conversation's notification when the
 * thread changes, so the same SMS can reach trove several times, hours apart. Identical text is the same event:
 * a real second charge differs somewhere (time, reference, available balance or limit).
 */
object MessageFingerprint {
    fun of(body: String): String? {
        val norm = body.lowercase(Locale.ROOT).replace(Regex("\\s+"), " ").trim()
        if (norm.length < 12) return null
        val d = MessageDigest.getInstance("SHA-256").digest(norm.toByteArray())
        return d.take(10).joinToString("") { "%02x".format(it) }
    }
}
