package com.keepsakeai.app.finance

import android.content.Context
import com.keepsakeai.app.data.preferences.KeepReminder
import com.keepsakeai.app.data.preferences.ReminderStore
import com.keepsakeai.app.expiry.Expiries

/** Turns a deposit-booked message into a maturity date in Expiries (and so in Coming up and the alerts). */
object Deposits {
    fun remember(context: Context, d: InvestmentMatcher.Deposit, bookedAt: Long) {
        val store = ReminderStore(context)
        val at = d.maturityFrom(bookedAt)
        if (at <= System.currentTimeMillis()) return
        val title = d.title
        if (store.all().any { it.kind == Expiries.KIND && it.title == title && kotlin.math.abs(it.atMillis - at) < 3L * 24 * 60 * 60 * 1000 }) return
        store.add(KeepReminder(id = at + title.hashCode(), title = title, atMillis = at, kind = Expiries.KIND, memoryId = 0, hasTime = false))
    }
}
