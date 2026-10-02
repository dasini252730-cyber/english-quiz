package com.englishquiz.app.domain.game

import org.junit.Assert.assertEquals
import org.junit.Test

class BadgePolicyTest {
    @Test
    fun nothingIsEarnedBeforeTheFirstSession() {
        assertEquals(emptySet<Badge>(), BadgePolicy.earned(BadgeFacts()))
    }

    @Test
    fun eachThresholdEarnsItsBadge() {
        val facts = BadgeFacts(
            sessionCount = 1,
            longestStreakDays = 7,
            savedCount = 50,
            masteredCount = 10,
            hasPerfectQuiz = true,
            bestCombo = 10,
            bothModesInOneDay = true,
        )

        assertEquals(
            setOf(
                Badge.FIRST_SESSION, Badge.STREAK_7, Badge.SAVED_50, Badge.MASTERED_10,
                Badge.PERFECT_QUIZ, Badge.COMBO_10, Badge.BOTH_MODES_ONE_DAY,
            ),
            BadgePolicy.earned(facts),
        )
    }

    @Test
    fun theBiggerThresholdsStackOnTheSmallerOnes() {
        val facts = BadgeFacts(sessionCount = 40, longestStreakDays = 30, savedCount = 100, masteredCount = 50)

        assertEquals(
            setOf(
                Badge.FIRST_SESSION, Badge.STREAK_7, Badge.STREAK_30,
                Badge.SAVED_50, Badge.SAVED_100, Badge.MASTERED_10, Badge.MASTERED_50,
            ),
            BadgePolicy.earned(facts),
        )
    }

    @Test
    fun oneShortOfAThresholdEarnsNothingForIt() {
        val facts = BadgeFacts(sessionCount = 1, longestStreakDays = 6, savedCount = 49, masteredCount = 9, bestCombo = 9)

        assertEquals(setOf(Badge.FIRST_SESSION), BadgePolicy.earned(facts))
    }
}
