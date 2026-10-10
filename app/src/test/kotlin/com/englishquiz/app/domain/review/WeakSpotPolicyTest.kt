package com.englishquiz.app.domain.review

import com.englishquiz.app.data.local.SavedExpressionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class WeakSpotPolicyTest {
    private val fresh = expression("fresh", savedAt = 5)
    private val missedOnce = expression("missed once", savedAt = 4, incorrect = 1, tapped = 1)
    private val missedTwice = expression("missed twice", savedAt = 1, incorrect = 2, correctRun = 1)
    private val tappedTwice = expression("tapped twice", savedAt = 2, tapped = 2)
    private val tappedButKnown = expression("tapped but known", savedAt = 3, tapped = 3, correctRun = 1)
    private val tappedMastered = expression("tapped mastered", savedAt = 0, tapped = 1, mastered = true)
    private val all = listOf(fresh, missedOnce, missedTwice, tappedTwice, tappedButKnown, tappedMastered)

    @Test
    fun allKeepsTheListAsItCame() {
        assertEquals(all, WeakSpotPolicy.apply(ReviewFilter.ALL, all))
    }

    @Test
    fun oftenWrongListsEveryMissedExpressionMostMissedFirst() {
        assertEquals(listOf(missedTwice, missedOnce), WeakSpotPolicy.apply(ReviewFilter.OFTEN_WRONG, all))
    }

    @Test
    fun oftenWrongAddsTheMissesLoggedByTheBossAndThePractice() {
        // 백로그 056: "fresh" was never missed in the daily quiz but twice elsewhere; "missed once"
        // gains one more, so it now outranks "missed twice" (2 + 0) by the tie-break on date.
        val logged = mapOf(fresh.id to 2, missedOnce.id to 1)
        assertEquals(listOf(fresh, missedOnce, missedTwice), WeakSpotPolicy.apply(ReviewFilter.OFTEN_WRONG, all, logged))
    }

    @Test
    fun tappedSeedIsWhatWasLookedUpAndStillNeverAnsweredRight() {
        // Known (a correct run) and mastered expressions drop out even though they were tapped.
        assertEquals(listOf(tappedTwice, missedOnce), WeakSpotPolicy.apply(ReviewFilter.TAPPED_SEED, all))
    }

    private fun expression(
        name: String,
        savedAt: Long,
        incorrect: Int = 0,
        tapped: Int = 0,
        correctRun: Int = 0,
        mastered: Boolean = false,
    ) = SavedExpressionEntity(
        id = savedAt,
        normalizedExpression = name,
        displayExpression = name,
        contextMeaning = "",
        firstSavedAtEpochMillis = savedAt,
        consecutiveCorrectCount = correctRun,
        incorrectCount = incorrect,
        isMastered = mastered,
        tapCount = tapped,
    )
}
