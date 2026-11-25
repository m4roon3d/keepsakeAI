package com.keepsakeai.app.review

import com.keepsakeai.app.data.local.CaptureSource
import com.keepsakeai.app.data.local.MemoryEntity

/** The two-minute pass: things you saved lately that you haven't decided about yet. Pure and unit-tested. */
object DailyReview {
    const val WINDOW_DAYS = 3
    const val MAX = 12
    private const val DAY = 24L * 60 * 60 * 1000

    /** Only things you saved yourself count; imported email and SMS have their own place. Newest first. */
    fun pending(memories: List<MemoryEntity>, reviewed: Set<Long>, now: Long): List<MemoryEntity> =
        memories.asSequence()
            .filter { it.source != CaptureSource.EMAIL && it.source != CaptureSource.UPI_MESSAGE }
            .filter { it.id !in reviewed && now - it.createdAt <= WINDOW_DAYS * DAY && it.createdAt <= now }
            .sortedByDescending { it.createdAt }
            .take(MAX)
            .toList()

    /** The count shown on the pill, without the cap. */
    fun count(memories: List<MemoryEntity>, reviewed: Set<Long>, now: Long): Int = pending(memories, reviewed, now).size
}
