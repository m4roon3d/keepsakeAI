package com.keepsakeai.app.finance

import com.keepsakeai.app.capture.ParsedCredit
import com.keepsakeai.app.capture.ParsedUpiTransaction
import com.keepsakeai.app.data.local.ExpenseEntity
import com.keepsakeai.app.data.local.MindSpaceDao
import com.keepsakeai.app.data.local.PendingUpiTransactionEntity
import com.keepsakeai.app.data.local.ObservedCreditEntity
import com.keepsakeai.app.data.local.TxnType
import com.keepsakeai.app.data.preferences.MoneyPrefs

/**
 * One place that decides two things for every money event, whichever channel it came from
 * (SMS inbox, live notification, bank email, approval queue):
 *  - is it a duplicate of something already saved?
 *  - is it a transfer between the user's own accounts?
 */
class FinanceGuard(private val dao: MindSpaceDao, private val prefs: MoneyPrefs) {

    /** Remember which accounts are the user's own (every debit and credit message names one). */
    fun learnAccounts(accounts: Collection<String?>) {
        val add = accounts.filterNotNull().filter { it.isNotBlank() }.toSet()
        if (add.isNotEmpty() && !prefs.ownAccounts.containsAll(add)) prefs.ownAccounts = prefs.ownAccounts + add
    }

    /** TRANSFER when the debit moved money between the user's own accounts, otherwise EXPENSE. */
    suspend fun classify(p: ParsedUpiTransaction, at: Long): String {
        val credits = dao.creditsSince(at - CREDIT_LOOKBACK).map { TransferMatcher.Credit(it.amount, it.utrRef, it.account, it.receivedAt) }
        val debit = TransferMatcher.Debit(p.amount, p.utrRef, p.account, p.toAccount, p.merchant, at)
        val own = prefs.ownAccounts
        val names = listOf(prefs.payeeName, prefs.upiId).filter { it.isNotBlank() }
        val transfer = TransferMatcher.isTransfer(debit, credits, own, names) || p.category == "Card bill" ||
            TransferMatcher.isKnownTransferPayee(p.merchant, prefs.transferPayees)
        return if (transfer) TxnType.TRANSFER else TxnType.EXPENSE
    }

    /**
     * Files a debit already recognised as a transfer between own accounts straight into Transfers, without sending it
     * through review. It stays visible in the Transfers card, where "not a transfer" puts it back.
     */
    suspend fun saveTransfer(item: PendingUpiTransactionEntity) {
        val merchant = if (item.merchant.endsWith(" payment") || item.merchant == "Bank transfer") "Own account transfer" else item.merchant
        dao.insertExpense(
            ExpenseEntity(
                merchant = merchant,
                amount = item.amount,
                currency = item.currency,
                category = item.suggestedCategory,
                notes = item.utrRef?.let { "UTR: $it" } ?: item.bankOrApp,
                sourceMethod = if (" · " in item.bankOrApp) item.bankOrApp else "UPI · ${item.bankOrApp}",
                timestamp = item.receivedAt,
                type = TxnType.TRANSFER
            )
        )
    }

    /** For payments with no account details to go on (bank emails, receipts): the user's own name, or a payee they marked. */
    fun typeForMerchant(merchant: String, category: String = ""): String {
        if (TransferMatcher.isCardBill("$merchant $category")) return TxnType.TRANSFER
        val names = listOf(prefs.payeeName, prefs.upiId).filter { it.isNotBlank() }
        return if (TransferMatcher.isOwnPayee(merchant, names) || TransferMatcher.isKnownTransferPayee(merchant, prefs.transferPayees)) TxnType.TRANSFER else TxnType.EXPENSE
    }

    fun rememberTransferPayee(merchant: String) { prefs.transferPayees = prefs.transferPayees + TransferMatcher.payeeKey(merchant) }
    fun forgetTransferPayee(merchant: String) { prefs.transferPayees = prefs.transferPayees - TransferMatcher.payeeKey(merchant) }

    /**
     * Records a credit and, if a debit it pairs with is already saved or waiting for review, turns that debit
     * into a transfer. Credits that pair with nothing are simply kept for a while in case their debit shows up later.
     */
    suspend fun noteCredit(c: ParsedCredit, at: Long) {
        learnAccounts(listOf(c.account))
        val known = dao.creditsSince(at - CREDIT_LOOKBACK).any { k ->
            if (c.utrRef != null && k.utrRef != null) k.utrRef == c.utrRef
            else k.amount == c.amount && kotlin.math.abs(k.receivedAt - at) <= TransferMatcher.AMOUNT_WINDOW_MS
        }
        if (!known) dao.insertCredit(ObservedCreditEntity(amount = c.amount, utrRef = c.utrRef, account = c.account, receivedAt = at))
        dao.pruneCredits(System.currentTimeMillis() - KEEP_CREDITS)

        val from = at - TransferMatcher.AMOUNT_WINDOW_MS
        val to = at + TransferMatcher.AMOUNT_WINDOW_MS
        if (c.utrRef != null) {
            dao.markPendingTransferByUtr(c.utrRef)
            dao.markExpenseTransferByNote("UTR: ${c.utrRef}")
        }
        val anyUtr = if (c.utrRef == null) 1 else 0
        dao.markPendingTransferByAmount(c.amount, from, to, anyUtr)
        dao.markExpenseTransferByAmount(c.amount, from, to, anyUtr)
        // A debit that was waiting for review and has now been paired with this credit goes straight to Transfers.
        dao.pendingTransfers().forEach { saveTransfer(it); dao.deletePendingUpi(it) }
    }

    /** True if an expense for this payment is already saved. Transfers count too, so they don't come back as spending. */
    suspend fun expenseExists(amount: Double, utr: String?, at: Long, wide: Boolean = false): Boolean {
        val w = if (wide) DAY else SAME_PAYMENT_WINDOW
        if (utr != null) {
            if (dao.countExpensesByNote("UTR: $utr") > 0) return true
            return dao.countExpensesByAmountWindowNoUtr(amount, at - w, at + w) > 0
        }
        return dao.countExpensesByAmountWindow(amount, at - w, at + w) > 0
    }

    /** True if this exact bank message reached trove before (checked and remembered in one step). */
    fun seenMessage(body: String, at: Long): Boolean {
        val fp = MessageFingerprint.of(body) ?: return false
        return prefs.seenMessage(fp, at)
    }

    /** True if the payment is already saved or already waiting in the review queue. */
    suspend fun isDuplicate(amount: Double, utr: String?, at: Long, wide: Boolean = false): Boolean {
        if (expenseExists(amount, utr, at, wide)) return true
        // With a UPI ref, the ref alone tells two same-amount payments apart.
        if (utr != null) return dao.countPendingByUtr(utr) > 0
        val w = if (wide) DAY else SAME_PAYMENT_WINDOW
        return dao.countPendingByAmountWindow(amount, at - w, at + w) > 0
    }

    companion object {
        const val SAME_PAYMENT_WINDOW = 5 * 60_000L
        private const val DAY = 24L * 60 * 60 * 1000
        private const val CREDIT_LOOKBACK = 30 * 60_000L
        private const val KEEP_CREDITS = 45 * DAY
    }
}
