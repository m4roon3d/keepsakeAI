package com.keepsakeai.app.email

import java.util.Date
import java.util.Properties
import javax.mail.AuthenticationFailedException
import javax.mail.FetchProfile
import javax.mail.Folder
import javax.mail.Message
import javax.mail.Multipart
import javax.mail.Part
import javax.mail.Session
import javax.mail.Store
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeUtility
import javax.mail.search.ComparisonTerm
import javax.mail.search.ReceivedDateTerm

data class MailItem(val key: String, val from: String, val subject: String, val date: Long, val body: String)

class MailAuthException(message: String) : Exception(message)

/** Read-only IMAP access: opens the inbox with EXAMINE, so nothing is marked read or changed. */
object MailReader {

    private fun session(host: String, port: Int): Session {
        val p = Properties().apply {
            put("mail.store.protocol", "imaps")
            put("mail.imaps.host", host)
            put("mail.imaps.port", port.toString())
            put("mail.imaps.ssl.enable", "true")
            put("mail.imaps.ssl.checkserveridentity", "true")
            put("mail.imaps.connectiontimeout", "15000")
            put("mail.imaps.timeout", "30000")
            put("mail.imaps.peek", "true")
        }
        return Session.getInstance(p)
    }

    private fun connect(host: String, port: Int, user: String, password: String): Store {
        val store = session(host, port).getStore("imaps")
        try {
            store.connect(host, port, user, password)
        } catch (e: AuthenticationFailedException) {
            throw MailAuthException("Sign-in failed. Use an app password, not your normal password.")
        }
        return store
    }

    /** Signs in and out once to check the details. */
    fun test(host: String, port: Int, user: String, password: String) {
        connect(host, port, user, password).close()
    }

    /**
     * Newest relevant emails since [since] that aren't in [seen], up to [max].
     * Only receipts, orders, tickets, bills and renewals (by subject) are opened.
     */
    fun fetch(host: String, port: Int, user: String, password: String, since: Long, seen: Set<String>, max: Int): List<MailItem> {
        val store = connect(host, port, user, password)
        try {
            val inbox = store.getFolder("INBOX")
            inbox.open(Folder.READ_ONLY)
            try {
                val found = inbox.search(ReceivedDateTerm(ComparisonTerm.GE, Date(since)))
                inbox.fetch(found, FetchProfile().apply { add(FetchProfile.Item.ENVELOPE) })
                val picked = found
                    .filter { m ->
                        val subject = runCatching { m.subject.orEmpty() }.getOrDefault("")
                        EmailFilter.isWorthReading(subject, fromOf(m)) && keyOf(m) !in seen
                    }
                    .sortedByDescending { it.receivedDate?.time ?: it.sentDate?.time ?: 0L }
                    .take(max)
                return picked.mapNotNull { m ->
                    runCatching {
                        val text = textOf(m).orEmpty()
                        MailItem(
                            key = keyOf(m),
                            from = fromOf(m),
                            subject = m.subject.orEmpty(),
                            date = m.receivedDate?.time ?: m.sentDate?.time ?: System.currentTimeMillis(),
                            body = EmailFilter.cleanBody(text)
                        )
                    }.getOrNull()
                }.sortedBy { it.date }
            } finally {
                runCatching { inbox.close(false) }
            }
        } finally {
            runCatching { store.close() }
        }
    }

    private fun fromOf(m: Message): String = runCatching {
        (m.from?.firstOrNull() as? InternetAddress)?.let { a ->
            val name = a.personal?.let { runCatching { MimeUtility.decodeText(it) }.getOrDefault(it) }
            if (name.isNullOrBlank()) a.address else "$name <${a.address}>"
        } ?: m.from?.firstOrNull()?.toString().orEmpty()
    }.getOrDefault("")

    private fun keyOf(m: Message): String = runCatching {
        m.getHeader("Message-ID")?.firstOrNull()?.trim()
    }.getOrNull() ?: "${m.subject}|${m.receivedDate?.time ?: m.sentDate?.time}"

    /** Prefers plain text; falls back to readable text from the HTML part. */
    private fun textOf(p: Part): String? {
        if (p.isMimeType("text/plain")) return (p.content as? String)?.takeIf { it.isNotBlank() }
        if (p.isMimeType("text/html")) return (p.content as? String)?.let { EmailFilter.htmlToText(it) }
        if (p.isMimeType("multipart/alternative")) {
            val mp = p.content as Multipart
            var html: String? = null
            for (i in 0 until mp.count) {
                val part = mp.getBodyPart(i)
                if (part.isMimeType("text/plain")) (part.content as? String)?.takeIf { it.isNotBlank() }?.let { return it }
                if (html == null) html = textOf(part)
            }
            return html
        }
        if (p.isMimeType("multipart/*")) {
            val mp = p.content as Multipart
            for (i in 0 until mp.count) {
                val part = mp.getBodyPart(i)
                if (Part.ATTACHMENT.equals(part.disposition, ignoreCase = true)) continue
                textOf(part)?.let { return it }
            }
        }
        if (p.isMimeType("message/rfc822")) return (p.content as? Part)?.let { textOf(it) }
        return null
    }
}
