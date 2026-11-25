package com.keepsakeai.app

import com.keepsakeai.app.data.preferences.KeepReminder
import com.keepsakeai.app.data.preferences.Todo
import com.keepsakeai.app.ui.spend.ComingUp
import com.keepsakeai.app.ui.spend.DAY
import com.keepsakeai.app.ui.spend.DueKind
import com.keepsakeai.app.ui.spend.Recurring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ComingUpTest {
    private val now = 1_800_000_000_000L

    private fun bill(id: Long, title: String, inDays: Long, mem: Long = 0, status: String = "suggested", kind: String = "due") =
        KeepReminder(id, title, now + inDays * DAY, kind, mem, status)

    private fun rec(name: String, inDays: Long) =
        Recurring(name, "Subscriptions", 499.0, 30, "monthly", now - 25 * DAY, now + inDays * DAY, 4)

    @Test fun readsAnAmountFromTheTitle() {
        assertEquals(1240.0, ComingUp.amountIn("Pay BESCOM bill ₹1,240")!!, 0.0)
        assertEquals(500.0, ComingUp.amountIn("Rent Rs. 500")!!, 0.0)
        assertNull(ComingUp.amountIn("Pay BESCOM bill"))
    }

    @Test fun billsRenewalsAndMoneyTodosInDateOrder() {
        val items = ComingUp.build(
            reminders = listOf(bill(1, "Pay BESCOM bill", 5), bill(2, "Concert", 3, kind = "event")),
            todos = listOf(Todo(10, "Pay society maintenance", now + 2 * DAY), Todo(11, "Buy milk", now + DAY)),
            recurring = listOf(rec("Netflix", 9)),
            now = now
        )
        assertEquals(listOf("Pay society maintenance", "Pay BESCOM bill", "Netflix"), items.map { it.title })
        assertEquals(listOf(DueKind.TODO, DueKind.BILL, DueKind.RENEWAL), items.map { it.kind })
    }

    @Test fun overdueBillsStaySevenDaysThenGo() {
        val items = ComingUp.build(listOf(bill(1, "Pay water bill", -3), bill(2, "Pay old bill", -9)), emptyList(), emptyList(), now)
        assertEquals(listOf("Pay water bill"), items.map { it.title })
    }

    @Test fun dismissedBillsFarOffBillsAndDoneTodosAreLeftOut() {
        val items = ComingUp.build(
            listOf(bill(1, "Pay gas bill", 2, status = "dismissed"), bill(2, "Pay tax", 90)),
            listOf(Todo(3, "Pay rent", now + DAY, done = true)),
            emptyList(), now
        )
        assertEquals(emptyList<String>(), items.map { it.title })
    }

    @Test fun sameBillAsReminderAndTodoShowsOnce() {
        val items = ComingUp.build(
            listOf(bill(1, "Pay BESCOM bill", 4, mem = 7)),
            listOf(Todo(2, "Pay BESCOM bill", now + 4 * DAY, memoryId = 7)),
            emptyList(), now
        )
        assertEquals(1, items.size)
        assertEquals(DueKind.BILL, items[0].kind)
    }

    @Test fun renewalAlreadyCoveredByABillIsNotListedTwice() {
        val items = ComingUp.build(listOf(bill(1, "Netflix renews", 8)), emptyList(), listOf(rec("Netflix", 9)), now)
        assertEquals(listOf(DueKind.BILL), items.map { it.kind })
    }
}
