package com.englishquiz.app.domain.review

import com.englishquiz.app.data.local.SavedExpressionEntity

/** The three views of the review box (백로그 044): everything, or one of the two weak-spot cuts. */
enum class ReviewFilter(val label: String, val orderLabel: String) {
    ALL("전체", "최근 저장순"),
    OFTEN_WRONG("자주 틀리는", "오답 많은 순"),
    TAPPED_SEED("탭했는데 아직 씨앗", "많이 탭한 순"),
}

/**
 * Pure Kotlin. "자주 틀리는" is every expression answered wrong at least once, the most-missed
 * first. "탭했는데 아직 씨앗" is what the learner looked up while reading (`tapCount`, 백로그 044)
 * and has not answered right since — the clearest sign of a phrase that still is not known.
 */
object WeakSpotPolicy {
    fun apply(filter: ReviewFilter, expressions: List<SavedExpressionEntity>): List<SavedExpressionEntity> =
        when (filter) {
            ReviewFilter.ALL -> expressions
            ReviewFilter.OFTEN_WRONG -> expressions
                .filter { it.incorrectCount >= 1 }
                .sortedWith(compareByDescending<SavedExpressionEntity> { it.incorrectCount }.thenByDescending { it.firstSavedAtEpochMillis })
            ReviewFilter.TAPPED_SEED -> expressions
                .filter { it.tapCount > 0 && it.consecutiveCorrectCount == 0 && !it.isMastered }
                .sortedWith(compareByDescending<SavedExpressionEntity> { it.tapCount }.thenByDescending { it.firstSavedAtEpochMillis })
        }
}
