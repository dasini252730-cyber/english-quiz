package com.englishquiz.app.domain.streak

import org.junit.Assert.assertEquals
import org.junit.Test

class StreakPolicyTest {
    @Test
    fun countsConsecutiveDaysEndingToday() {
        val dates = listOf("2026-09-20", "2026-09-19", "2026-09-18")

        val streak = StreakPolicy.currentStreakDays(dates, TODAY)

        assertEquals(3, streak)
    }

    @Test
    fun stopsAtAGapInTheHistory() {
        val dates = listOf("2026-09-20", "2026-09-19", "2026-09-16")

        val streak = StreakPolicy.currentStreakDays(dates, TODAY)

        assertEquals(2, streak)
    }

    @Test
    fun countsFromYesterdayWhenTodayHasNoSession() {
        val dates = listOf("2026-09-19", "2026-09-18")

        val streak = StreakPolicy.currentStreakDays(dates, TODAY)

        assertEquals(2, streak)
    }

    @Test
    fun isZeroWhenNeitherTodayNorYesterdayHaveASession() {
        val dates = listOf("2026-09-17", "2026-09-16")

        val streak = StreakPolicy.currentStreakDays(dates, TODAY)

        assertEquals(0, streak)
    }

    @Test
    fun countsARepeatedDateOnce() {
        val dates = listOf("2026-09-20", "2026-09-20", "2026-09-20", "2026-09-19")

        val streak = StreakPolicy.currentStreakDays(dates, TODAY)

        assertEquals(2, streak)
    }

    @Test
    fun ignoresDatesAfterToday() {
        val dates = listOf("2026-09-21", "2026-09-22", "2026-09-20", "2026-09-19")

        val streak = StreakPolicy.currentStreakDays(dates, TODAY)

        assertEquals(2, streak)
    }

    @Test
    fun ignoresMalformedDateStringsWithoutThrowing() {
        val dates = listOf("2026-09-20", "not-a-date", "", "2026-13-40", "2026-09-19")

        val streak = StreakPolicy.currentStreakDays(dates, TODAY)

        assertEquals(2, streak)
    }

    @Test
    fun isZeroForAnEmptyList() {
        val streak = StreakPolicy.currentStreakDays(emptyList(), TODAY)

        assertEquals(0, streak)
    }

    private companion object {
        const val TODAY = "2026-09-20"
    }
}
