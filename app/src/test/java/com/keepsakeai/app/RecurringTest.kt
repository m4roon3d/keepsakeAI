package com.keepsakeai.app

import com.keepsakeai.app.data.local.ExpenseEntity
import com.keepsakeai.app.ui.spend.BudgetState
import com.keepsakeai.app.ui.spend.DAY
import com.keepsakeai.app.ui.spend.OVERALL
import com.keepsakeai.app.ui.spend.budgetStatuses
import com.keepsakeai.app.ui.spend.detectRecurring
import com.keepsakeai.app.ui.spend.isMoneyQuestion
import com.keepsakeai.app.ui.spend.spendDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringTest {
    private val now = 1_795_000_000_000L
    private fun e(m: String, amt: Double, daysAgo: Number, cat: String = "Other") =
        ExpenseEntity(merchant = m, amount = amt, category = cat, sourceMethod = "UPI · HDFC Bank", timestamp = now - (daysAgo.toDouble() * DAY).toLong())

    @Test
    fun findsMonthlySubscriptionWithTwoCharges() {
        val subs = listOf(e("NETFLIX", 649.0, 40), e("Netflix", 649.0, 10)).detectRecurring(now)
        assertEquals(1, subs.size)
        assertEquals("monthly", subs[0].cadenceLabel)
        assertEquals(649.0, subs[0].monthly, 1.0)
        assertEquals(20, subs[0].daysUntilDue(now))
    }

    @Test
    fun ordinaryMerchantNeedsThreeSteadyCharges() {
        assertTrue(listOf(e("Swiggy", 400.0, 60), e("Swiggy", 410.0, 30)).detectRecurring(now).isEmpty())
        val rent = listOf(e("Landlord Kumar", 25000.0, 92), e("Landlord Kumar", 25000.0, 61), e("Landlord Kumar", 25000.0, 31), e("Landlord Kumar", 25000.0, 1))
        assertEquals("monthly", rent.detectRecurring(now).single().cadenceLabel)
    }

    @Test
    fun irregularOrVaryingPaymentsAreNotRecurring() {
        val varying = listOf(e("Zepto", 120.0, 90), e("Zepto", 800.0, 60), e("Zepto", 300.0, 30))
        assertTrue(varying.detectRecurring(now).isEmpty())
        val irregular = listOf(e("Uber", 300.0, 50), e("Uber", 300.0, 45), e("Uber", 300.0, 5))
        assertTrue(irregular.detectRecurring(now).isEmpty())
    }

    @Test
    fun staleSubscriptionIsDropped() {
        val old = listOf(e("Spotify", 119.0, 200), e("Spotify", 119.0, 170))
        assertTrue(old.detectRecurring(now).isEmpty())
    }

    @Test
    fun budgetsComputeStateAndPace() {
        val month = listOf(e("Swiggy", 5000.0, 2, "Food"), e("Uber", 1000.0, 1, "Transport"))
        val st = budgetStatuses(month, mapOf("Food" to 6000.0, OVERALL to 20000.0, "Bills" to 0.0), daysLeft = 10)
        assertEquals(OVERALL, st.first().name)
        assertEquals(2, st.size)
        val food = st.first { it.name == "Food" }
        assertEquals(100.0, food.perDayLeft, 0.01)
        assertEquals(BudgetState.WATCH, food.state(dayOfMonth = 20, daysInMonth = 30))
        assertEquals(BudgetState.OK, st.first().state(20, 30))
    }

    @Test
    fun digestHasTotalsAndOptionalRows() {
        val data = listOf(e("Swiggy", 400.0, 1, "Food"), e("Netflix", 649.0, 3, "Entertainment"))
        val short = spendDigest(data, emptyMap(), includeRows = false, now = now)
        assertTrue(short.contains("Monthly totals"))
        assertFalse(short.contains("| Swiggy |"))
        assertTrue(spendDigest(data, emptyMap(), includeRows = true, now = now).contains("| Swiggy |"))
        assertTrue(isMoneyQuestion("How much did I spend on Swiggy?"))
        assertFalse(isMoneyQuestion("What is the weather tomorrow?"))
    }
}
