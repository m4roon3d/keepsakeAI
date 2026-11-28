package com.keepsakeai.app.data.preferences

import android.content.Context
import org.json.JSONObject

/**
 * Small settings that don't belong in the database (so no schema migration is needed):
 * monthly budgets, the user's UPI ID for split requests, and which alerts were already sent.
 */
class MoneyPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("money_prefs", Context.MODE_PRIVATE)

    /** Categories you chose for a merchant ("zepto" → "Food"), used to pre-select future payments. */
    fun categoryFor(merchant: String): String? =
        prefs.getString("cat:" + merchant.trim().lowercase(), null)

    fun rememberCategory(merchant: String, category: String) {
        if (merchant.isNotBlank()) prefs.edit().putString("cat:" + merchant.trim().lowercase(), category).apply()
    }

    var budgets: Map<String, Double>
        get() = runCatching {
            val o = JSONObject(prefs.getString("budgets", "{}")!!)
            o.keys().asSequence().associateWith { o.getDouble(it) }
        }.getOrDefault(emptyMap())
        set(value) {
            val o = JSONObject()
            value.filterValues { it > 0 }.forEach { (k, v) -> o.put(k, v) }
            prefs.edit().putString("budgets", o.toString()).apply()
        }

    var upiId: String
        get() = prefs.getString("upi_id", "").orEmpty()
        set(v) = prefs.edit().putString("upi_id", v.trim()).apply()

    var payeeName: String
        get() = prefs.getString("payee_name", "").orEmpty()
        set(v) = prefs.edit().putString("payee_name", v.trim()).apply()

    /** Last digits of the user's own bank accounts, learned from their debit and credit messages. */
    var ownAccounts: Set<String>
        get() = prefs.getStringSet("own_accounts", emptySet()).orEmpty()
        set(v) = prefs.edit().putStringSet("own_accounts", v).apply()

    /** Payees the user marked as transfers (lowercase merchant names). Later payments to them are filed as transfers. */
    var transferPayees: Set<String>
        get() = prefs.getStringSet("transfer_payees", emptySet()).orEmpty()
        set(v) = prefs.edit().putStringSet("transfer_payees", v).apply()

    var renewalReminders: Boolean
        get() = prefs.getBoolean("renewal_reminders", true)
        set(v) = prefs.edit().putBoolean("renewal_reminders", v).apply()

    /** True the first time a given alert key is seen; used so each alert fires once. */
    /**
     * Remembers bank-message fingerprints for a few days. Returns true if this one was already seen
     * (a re-posted notification, or the inbox scan meeting a message the listener already saved).
     */
    @Synchronized
    fun seenMessage(fingerprint: String, now: Long): Boolean {
        val keep = 4L * 24 * 60 * 60 * 1000
        val live = prefs.getStringSet("seen_msgs", emptySet()).orEmpty().mapNotNull { e ->
            val at = e.substringAfter('@', "").toLongOrNull() ?: return@mapNotNull null
            if (now - at in 0..keep) e.substringBefore('@') to at else null
        }
        if (live.any { it.first == fingerprint }) return true
        prefs.edit().putStringSet("seen_msgs", (live + (fingerprint to now)).takeLast(400).map { "${it.first}@${it.second}" }.toSet()).apply()
        return false
    }

    fun firstTime(alertKey: String): Boolean {
        val sent = prefs.getStringSet("alerts_sent", emptySet()).orEmpty()
        if (alertKey in sent) return false
        // Keep the set small: only this and last month's keys matter.
        val trimmed = sent.toMutableSet().apply { if (size > 300) clear() }
        prefs.edit().putStringSet("alerts_sent", trimmed + alertKey).apply()
        return true
    }
}
