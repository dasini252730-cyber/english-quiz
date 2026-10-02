package com.englishquiz.app.domain.quiz

import com.englishquiz.app.data.local.SavedExpressionEntity

/** Shared builders for the QuizBuilder test classes, which are split by what they assert on. */
internal object QuizFixtures {
    const val NOW = 1_700_000_000_000L
    const val SEED = 42L
    const val DAY = 24 * 60 * 60 * 1000L

    fun expression(
        display: String,
        meaning: String,
        contextSentence: String = "",
        nextReviewAt: Long? = null,
        consecutiveCorrect: Int = 0,
    ) = SavedExpressionEntity(
        id = display.hashCode().toLong(),
        normalizedExpression = display.lowercase(),
        displayExpression = display,
        contextMeaning = meaning,
        firstSavedAtEpochMillis = NOW,
        nextReviewAtEpochMillis = nextReviewAt,
        contextSentence = contextSentence,
        consecutiveCorrectCount = consecutiveCorrect,
    )

    /** Deliberately disjoint from every expression the tests use as a target or as content. */
    val DISTRACTORS = listOf(
        expression("call it a day", "그만하다"),
        expression("under the weather", "몸이 안 좋은"),
        expression("on the fence", "결정을 못 한"),
    )
}
