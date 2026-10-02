package com.englishquiz.app.domain.game

import org.junit.Assert.assertEquals
import org.junit.Test

class BossPolicyTest {
    @Test
    fun opensOnSaturdayAndSundayWhenNotYetCleared() {
        assertEquals(BossStatus.Open, BossPolicy.status("2026-10-03", emptyList()))
        assertEquals(BossStatus.Open, BossPolicy.status("2026-10-04", emptyList()))
    }

    @Test
    fun aClearOnSaturdayClosesSundayOfTheSameWeekOnly() {
        assertEquals(BossStatus.Cleared, BossPolicy.status("2026-10-04", listOf("2026-10-03")))
        // Last week's clear does not count for this weekend.
        assertEquals(BossStatus.Open, BossPolicy.status("2026-10-10", listOf("2026-10-03")))
    }

    @Test
    fun weekdaysCountDownToSaturday() {
        // Friday 2026-10-02 → Saturday 2026-10-03.
        assertEquals(BossStatus.Waiting(1), BossPolicy.status("2026-10-02", emptyList()))
        assertEquals(BossStatus.Waiting(5), BossPolicy.status("2026-10-05", emptyList()))
    }
}
