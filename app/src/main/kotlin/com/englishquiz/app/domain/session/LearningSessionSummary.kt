package com.englishquiz.app.domain.session

/**
 * Counts one finished learning session produced. The quiz screen fills this in and the result
 * screen reads it, so neither has to know the other's internals.
 */
data class LearningSessionSummary(
    val learnedExpressionCount: Int = 0,
    val newlySavedExpressionCount: Int = 0,
    val quizCorrectCount: Int = 0,
    val quizQuestionCount: Int = 0,
    val masteredExpressionCount: Int = 0,
) {
    val correctPercent: Int
        get() = if (quizQuestionCount <= 0) 0 else quizCorrectCount * 100 / quizQuestionCount
}
