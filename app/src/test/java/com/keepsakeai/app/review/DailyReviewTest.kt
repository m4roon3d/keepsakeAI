package com.keepsakeai.app.review

import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.data.local.MemoryEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyReviewTest {
    private val now = 1_800_000_000_000L
    private val day = 24L * 60 * 60 * 1000
    private fun m(id: Long, ageDays: Double, src: CaptureSource = CaptureSource.TEXT_NOTE) = MemoryEntity(
        id = id, title = "t$id", rawContent = "", aiSummary = "", collectionName = "c", source = src,
        createdAt = now - (ageDays * day).toLong()
    )

    @Test fun newestFirstAndWithinWindow() =
        assertEquals(listOf(2L, 1L), DailyReview.pending(listOf(m(1, 2.0), m(2, 0.5), m(3, 3.5)), emptySet(), now).map { it.id })

    @Test fun skipsReviewedAndImports() =
        assertEquals(listOf(1L), DailyReview.pending(
            listOf(m(1, 1.0), m(2, 1.0), m(3, 1.0, CaptureSource.EMAIL), m(4, 1.0, CaptureSource.UPI_MESSAGE)), setOf(2L), now
        ).map { it.id })

    @Test fun capsTheList() =
        assertEquals(DailyReview.MAX, DailyReview.pending((1L..30L).map { m(it, 0.1) }, emptySet(), now).size)
}
