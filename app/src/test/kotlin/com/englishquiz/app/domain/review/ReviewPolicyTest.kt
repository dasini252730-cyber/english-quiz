package com.englishquiz.app.domain.review

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewPolicyTest {
    @Test
    fun correctAnswersAdvanceIntervalsAndMasterAtFour() {
        val first = ReviewPolicy.recordAnswer(ReviewProgress(), true, EVALUATED_AT)
        val second = ReviewPolicy.recordAnswer(first, true, first.nextReviewAtEpochMillis!!)
        val third = ReviewPolicy.recordAnswer(second, true, second.nextReviewAtEpochMillis!!)
        val fourth = ReviewPolicy.recordAnswer(third, true, third.nextReviewAtEpochMillis!!)
        val fifth = ReviewPolicy.recordAnswer(fourth, true, fourth.nextReviewAtEpochMillis!!)

        assertEquals(EVALUATED_AT + DAY, first.nextReviewAtEpochMillis)
        assertEquals(1, first.consecutiveCorrectCount)
        assertEquals(EVALUATED_AT + DAY + 3 * DAY, second.nextReviewAtEpochMillis)
        assertEquals(2, second.consecutiveCorrectCount)
        assertEquals(3, third.consecutiveCorrectCount)
        assertFalse(third.isMastered)
        assertEquals(second.nextReviewAtEpochMillis!! + 7 * DAY, third.nextReviewAtEpochMillis)
        assertEquals(4, fourth.consecutiveCorrectCount)
        assertTrue(fourth.isMastered)
        assertEquals(third.nextReviewAtEpochMillis!! + 30 * DAY, fourth.nextReviewAtEpochMillis)
        assertTrue(fifth.isMastered)
        assertEquals(fourth.nextReviewAtEpochMillis!! + 30 * DAY, fifth.nextReviewAtEpochMillis)
    }

    @Test
    fun aCardSeenComesBackTomorrow() {
        // 백로그 045: "모르겠어요" schedules, it does not judge.
        assertEquals(1_000L + 24 * 60 * 60 * 1000L, ReviewPolicy.seenAgainAt(1_000L))
    }

    @Test
    fun incorrectAnswerResetsMasteryAndSchedulesOneDay() {
        val mastered = ReviewProgress(
            consecutiveCorrectCount = 4,
            incorrectCount = 2,
            isMastered = true,
        )

        val result = ReviewPolicy.recordAnswer(mastered, false, EVALUATED_AT)

        assertEquals(0, result.consecutiveCorrectCount)
        assertEquals(3, result.incorrectCount)
        assertFalse(result.isMastered)
        assertEquals(EVALUATED_AT + DAY, result.nextReviewAtEpochMillis)
    }

    private companion object {
        const val EVALUATED_AT = 1_000_000L
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
