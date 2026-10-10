package com.englishquiz.app.domain.review

import com.englishquiz.app.data.local.SavedExpressionEntity
import java.time.Instant
import java.time.ZoneId

/**
 * Where a saved expression stands in the learning cycle (백로그 055). Saved, first learned and
 * due for review are three different things, and the quiz treats each queue on its own:
 *
 * - [NEW]: saved, but its card has not been read yet. At most a few enter the quiz per day
 *   (`QuizBuilder.NEW_PER_DAY`); the rest wait their turn, oldest first.
 * - [FIRST_REVIEW_PENDING]: the card was read; the first graded question comes tomorrow.
 * - [DUE]: its review date has come or passed, so it is asked today, the most overdue first.
 *   A mastered expression whose 30-day check has come is due like any other.
 * - [SCHEDULED]: answered before and not due yet.
 * - [MASTERED]: at the 열매 stage, waiting for its 30-day check.
 */
enum class ExpressionState(val label: String) {
    NEW("새 표현 대기"),
    FIRST_REVIEW_PENDING("첫 복습 대기"),
    DUE("복습 예정"),
    SCHEDULED("복습 대기"),
    MASTERED("숙달"),
    ;

    companion object {
        /** True until the card has been read: the one state that needs no clock. */
        fun isNew(expression: SavedExpressionEntity): Boolean = expression.lastReviewedAtEpochMillis == null

        fun of(expression: SavedExpressionEntity, nowEpochMillis: Long): ExpressionState {
            val next = expression.nextReviewAtEpochMillis
            return when {
                isNew(expression) -> NEW
                next != null && next <= nowEpochMillis -> DUE
                expression.isMastered -> MASTERED
                expression.consecutiveCorrectCount == 0 && expression.incorrectCount == 0 -> FIRST_REVIEW_PENDING
                else -> SCHEDULED
            }
        }
    }
}

/**
 * How many expressions sit in each queue right now, for the home card and the quiz notice
 * (백로그 060). [firstMeetingsShownToday] is how many cards today already used of the day's
 * allowance (백로그 048), so the plan matches what the quiz will actually build.
 */
data class ReviewQueue(
    val due: Int,
    val newWaiting: Int,
    val firstReviewPending: Int,
    val mastered: Int,
    val firstMeetingsShownToday: Int = 0,
) {
    /** What the next daily quiz will ask: due reviews first, then what is left of the new allowance. */
    fun todayPlan(maxQuestions: Int, newPerDay: Int): TodayPlan {
        val reviews = minOf(due, maxQuestions)
        val fresh = minOf(newWaiting, (newPerDay - firstMeetingsShownToday).coerceAtLeast(0), maxQuestions - reviews)
        return TodayPlan(reviews, fresh, maxQuestions)
    }

    companion object {
        fun of(
            expressions: List<SavedExpressionEntity>,
            nowEpochMillis: Long,
            zoneId: ZoneId = ZoneId.systemDefault(),
        ): ReviewQueue {
            val states = expressions.groupingBy { ExpressionState.of(it, nowEpochMillis) }.eachCount()
            val day = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
            val dayStart = day.atStartOfDay(zoneId).toInstant().toEpochMilli()
            val dayEnd = day.plusDays(1).atStartOfDay(zoneId).toInstant().toEpochMilli()
            return ReviewQueue(
                due = states[ExpressionState.DUE] ?: 0,
                newWaiting = states[ExpressionState.NEW] ?: 0,
                firstReviewPending = states[ExpressionState.FIRST_REVIEW_PENDING] ?: 0,
                mastered = states[ExpressionState.MASTERED] ?: 0,
                firstMeetingsShownToday = expressions.count { isFirstMeetingShown(it, dayStart, dayEnd) },
            )
        }

        /** True for a row a card left behind today: seen, but neither right nor wrong yet (백로그 045). */
        fun isFirstMeetingShown(expression: SavedExpressionEntity, dayStartEpochMillis: Long, dayEndEpochMillis: Long): Boolean {
            val seenAt = expression.lastReviewedAtEpochMillis ?: return false
            return seenAt in dayStartEpochMillis until dayEndEpochMillis &&
                expression.consecutiveCorrectCount == 0 && expression.incorrectCount == 0 && !expression.isMastered
        }
    }
}

/** Today's quiz as the learner is told about it (백로그 060): "오늘의 학습 N/10 · 밀린 복습 X개 · 새 표현 대기 Y개". */
data class TodayPlan(val reviews: Int, val fresh: Int, val maxQuestions: Int) {
    val total: Int get() = reviews + fresh

    /** The one-line notice, or null when there is nothing to say (an empty box). */
    fun notice(queue: ReviewQueue): String? {
        if (queue.due == 0 && queue.newWaiting == 0) return null
        return "오늘의 학습 $total / $maxQuestions · 밀린 복습 ${queue.due}개 · 새 표현 대기 ${queue.newWaiting}개"
    }

    /** Why no new expression comes today, when a backlog of reviews fills the quiz; null otherwise. */
    fun reason(queue: ReviewQueue): String? =
        if (queue.due > maxQuestions) "오늘은 오래된 복습을 먼저 진행해요. 남은 복습은 다음 세션에서 이어집니다." else null
}
