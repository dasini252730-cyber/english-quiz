package com.englishquiz.app.domain.review

data class ReviewProgress(
    val lastReviewedAtEpochMillis: Long? = null,
    val nextReviewAtEpochMillis: Long? = null,
    val consecutiveCorrectCount: Int = 0,
    val incorrectCount: Int = 0,
    val isMastered: Boolean = false,
)

object ReviewPolicy {
    private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    private const val MASTERED_AFTER_CORRECT = 4

    /** When a card answered "모르겠어요" (백로그 045) comes back: tomorrow, with no mark against it. */
    fun seenAgainAt(seenAtEpochMillis: Long): Long = seenAtEpochMillis + DAY_MILLIS

    fun recordAnswer(
        current: ReviewProgress,
        wasCorrect: Boolean,
        evaluatedAtEpochMillis: Long,
    ): ReviewProgress {
        val nextStreak = if (wasCorrect) {
            current.consecutiveCorrectCount.inc()
        } else {
            0
        }
        val intervalDays = when {
            !wasCorrect -> 1
            nextStreak == 1 -> 1
            nextStreak == 2 -> 3
            nextStreak == 3 -> 7
            else -> 30
        }

        return current.copy(
            lastReviewedAtEpochMillis = evaluatedAtEpochMillis,
            nextReviewAtEpochMillis = evaluatedAtEpochMillis + intervalDays * DAY_MILLIS,
            consecutiveCorrectCount = nextStreak,
            incorrectCount = if (wasCorrect) current.incorrectCount else current.incorrectCount.inc(),
            isMastered = wasCorrect && nextStreak >= MASTERED_AFTER_CORRECT,
        )
    }

}
