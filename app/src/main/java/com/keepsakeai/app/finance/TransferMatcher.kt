package com.keepsakeai.app.finance

import kotlin.math.abs

/**
 * Spots money that only moved between the user's own accounts (an ICICI savings → ICICI salary transfer, a credit
 * card bill paid from the bank). Those must not be counted as spending. Pure logic, unit-tested.
 *
 * Signals, strongest first:
 *  1. The same UPI ref appears on a debit and on a credit seen on this phone.
 *  2. Same amount debited and credited within a few minutes, when a UPI ref can't tell them apart.
 *  3. The debit names one of the user's own account numbers as the destination.
 *  4. The payee is the user's own name or UPI ID.
 */
object TransferMatcher {
    const val AMOUNT_WINDOW_MS = 5 * 60_000L

    data class Debit(
        val amount: Double,
        val utrRef: String?,
        val account: String?,
        val toAccount: String?,
        val merchant: String,
        val at: Long,
    )

    data class Credit(val amount: Double, val utrRef: String?, val account: String?, val at: Long)

    fun creditMatches(d: Debit, credits: List<Credit>): Boolean = credits.any { c ->
        if (d.utrRef != null && c.utrRef != null) {
            d.utrRef == c.utrRef
        } else {
            c.amount == d.amount && abs(c.at - d.at) <= AMOUNT_WINDOW_MS &&
                // A credit into the very account that was debited is a refund or reversal, not a transfer out.
                (d.account == null || c.account == null || d.account != c.account)
        }
    }

    fun namesOwnAccount(d: Debit, ownAccounts: Set<String>): Boolean =
        d.toAccount != null && d.toAccount in ownAccounts && d.toAccount != d.account

    /** [ownNames] are the user's name and UPI ID. A payee matches when it equals one of them, or contains all words of a name. */
    fun isOwnPayee(merchant: String, ownNames: Collection<String>): Boolean {
        val m = key(merchant)
        if (m.isEmpty()) return false
        return ownNames.map { key(it.substringBefore('@')) }.filter { it.length >= 3 }.any { n ->
            m == n || n.split(' ').all { w -> w in m.split(' ') } && n.split(' ').size >= 2
        }
    }

    fun isTransfer(d: Debit, credits: List<Credit>, ownAccounts: Set<String>, ownNames: Collection<String>): Boolean =
        creditMatches(d, credits) || namesOwnAccount(d, ownAccounts) || isOwnPayee(d.merchant, ownNames)

    private fun key(s: String) = s.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()
}
