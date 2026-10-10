package com.englishquiz.app.domain.review

import com.englishquiz.app.data.local.SavedExpressionEntity
import org.junit.Assert.assertEquals
import org.junit.Test

/** The queue each saved expression belongs to (백로그 055): saved, first learned and due are kept apart. */
class ExpressionStateTest {
    @Test
    fun aSavedButUnseenExpressionIsNewWhateverItsDates() {
        assertEquals(ExpressionState.NEW, ExpressionState.of(row(lastReviewedAt = null), NOW))
        // An enrolled phrase carries no schedule at all; a card not yet read is still new.
        assertEquals(ExpressionState.NEW, ExpressionState.of(row(lastReviewedAt = null, nextReviewAt = NOW - DAY), NOW))
    }

    @Test
    fun aCardReadTodayWaitsForItsFirstGradedQuestionTomorrow() {
        val seen = row(lastReviewedAt = NOW, nextReviewAt = NOW + DAY)
        assertEquals(ExpressionState.FIRST_REVIEW_PENDING, ExpressionState.of(seen, NOW))
        assertEquals(ExpressionState.DUE, ExpressionState.of(seen, NOW + DAY))
    }

    @Test
    fun anAnsweredExpressionIsScheduledUntilItsDateThenDue() {
        val answered = row(lastReviewedAt = NOW, nextReviewAt = NOW + 3 * DAY, consecutiveCorrect = 2)
        assertEquals(ExpressionState.SCHEDULED, ExpressionState.of(answered, NOW))
        assertEquals(ExpressionState.DUE, ExpressionState.of(answered, NOW + 3 * DAY))
        val missed = row(lastReviewedAt = NOW, nextReviewAt = NOW + DAY, incorrectCount = 1)
        assertEquals(ExpressionState.SCHEDULED, ExpressionState.of(missed, NOW))
    }

    @Test
    fun aMasteredExpressionRestsUntilItsThirtyDayCheck() {
        val mastered = row(lastReviewedAt = NOW, nextReviewAt = NOW + 30 * DAY, consecutiveCorrect = 4, mastered = true)
        assertEquals(ExpressionState.MASTERED, ExpressionState.of(mastered, NOW))
        assertEquals(ExpressionState.DUE, ExpressionState.of(mastered, NOW + 30 * DAY))
    }

    @Test
    fun theQueueCountsEachStateOnce() {
        val rows = listOf(
            row(lastReviewedAt = null),
            row(lastReviewedAt = null),
            row(lastReviewedAt = NOW, nextReviewAt = NOW + DAY),
            row(lastReviewedAt = NOW - DAY, nextReviewAt = NOW, consecutiveCorrect = 1),
            row(lastReviewedAt = NOW, nextReviewAt = NOW + 30 * DAY, consecutiveCorrect = 4, mastered = true),
        )
        assertEquals(ReviewQueue(due = 1, newWaiting = 2, firstReviewPending = 1, mastered = 1, firstMeetingsShownToday = 1), ReviewQueue.of(rows, NOW))
    }

    @Test
    fun todaysPlanFollowsTheQuizRulesAndSaysWhyNewExpressionsWait() {
        // 백로그 060: reviews first, then what is left of the day's five, inside ten in all.
        val backlog = ReviewQueue(due = 12, newWaiting = 3, firstReviewPending = 0, mastered = 0)
        val plan = backlog.todayPlan(maxQuestions = 10, newPerDay = 5)
        assertEquals(TodayPlan(reviews = 10, fresh = 0, maxQuestions = 10), plan)
        assertEquals("오늘의 학습 10 / 10 · 밀린 복습 12개 · 새 표현 대기 3개", plan.notice(backlog))
        assertEquals("오늘은 오래된 복습을 먼저 진행해요. 남은 복습은 다음 세션에서 이어집니다.", plan.reason(backlog))

        // Cards already read today (백로그 048) come off the allowance, as the quiz itself counts them.
        val light = ReviewQueue(due = 2, newWaiting = 9, firstReviewPending = 5, mastered = 0, firstMeetingsShownToday = 5)
        assertEquals(TodayPlan(reviews = 2, fresh = 0, maxQuestions = 10), light.todayPlan(10, 5))
        assertEquals(TodayPlan(reviews = 2, fresh = 3, maxQuestions = 10), light.copy(firstMeetingsShownToday = 2).todayPlan(10, 5))
        assertEquals(null, light.todayPlan(10, 5).reason(light))
        assertEquals(null, ReviewQueue(0, 0, 0, 0).todayPlan(10, 5).notice(ReviewQueue(0, 0, 0, 0)))
    }

    private fun row(
        lastReviewedAt: Long?,
        nextReviewAt: Long? = null,
        consecutiveCorrect: Int = 0,
        incorrectCount: Int = 0,
        mastered: Boolean = false,
    ) = SavedExpressionEntity(
        normalizedExpression = "x",
        displayExpression = "x",
        contextMeaning = "뜻",
        firstSavedAtEpochMillis = NOW - 2 * DAY,
        lastReviewedAtEpochMillis = lastReviewedAt,
        nextReviewAtEpochMillis = nextReviewAt,
        consecutiveCorrectCount = consecutiveCorrect,
        incorrectCount = incorrectCount,
        isMastered = mastered,
    )

    private companion object {
        const val NOW = 1_700_000_000_000L
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
