package com.englishquiz.app.domain.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StreakShieldPolicyTest {
    @Test
    fun yesterdayIsShieldedWhenTheDayBeforeWasLearned() {
        val day = StreakShieldPolicy.dayToShield(listOf("2026-09-18", "2026-09-17"), emptyList(), TODAY)

        assertEquals("2026-09-19", day)
    }

    @Test
    fun anOlderGapIsLeftAloneSoOneVisitSpendsAtMostOneShield() {
        // 09-18 was missed, but 09-19 was learned: that gap had its chance the day after it.
        val dates = listOf("2026-09-19", "2026-09-17", "2026-09-16")

        assertNull(StreakShieldPolicy.dayToShield(dates, emptyList(), TODAY))
    }

    @Test
    fun aYesterdayAlreadyShieldedIsNotShieldedAgain() {
        val dates = listOf("2026-09-18")

        assertNull(StreakShieldPolicy.dayToShield(dates, listOf("2026-09-19"), TODAY))
    }

    @Test
    fun aGapOfTwoDaysCannotBeSaved() {
        assertNull(StreakShieldPolicy.dayToShield(listOf("2026-09-17"), emptyList(), TODAY))
    }

    @Test
    fun noGapAndNoHistoryMeanNothingToShield() {
        assertNull(StreakShieldPolicy.dayToShield(listOf("2026-09-20", "2026-09-19"), emptyList(), TODAY))
        assertNull(StreakShieldPolicy.dayToShield(emptyList(), emptyList(), TODAY))
        // Only today learned: nothing before it to join.
        assertNull(StreakShieldPolicy.dayToShield(listOf(TODAY), emptyList(), TODAY))
    }

    @Test
    fun todayItselfIsNeverShielded() {
        // Yesterday learned, today not yet: there is no gap behind today.
        assertNull(StreakShieldPolicy.dayToShield(listOf("2026-09-19", "2026-09-18"), emptyList(), TODAY))
    }

    private companion object {
        const val TODAY = "2026-09-20"
    }
}
