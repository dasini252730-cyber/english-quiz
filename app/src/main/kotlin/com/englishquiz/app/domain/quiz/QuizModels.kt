package com.englishquiz.app.domain.quiz

/** MVP quiz question types (요구사항 14.2): meaning multiple choice and fill-in-the-blank. */
enum class QuizQuestionType { MULTIPLE_CHOICE, FILL_IN_BLANK }

data class QuizOption(
    val text: String,
    val isCorrect: Boolean,
)

/**
 * One quiz item built from a single saved expression.
 *
 * [expression] is the saved expression's `displayExpression`. The UI shows it (for
 * [QuizQuestionType.MULTIPLE_CHOICE]) and also passes it straight to
 * `LearningRepository.recordAnswer` after the learner answers, so it must match the stored
 * value exactly.
 */
data class QuizQuestion(
    val expression: String,
    val type: QuizQuestionType,
    val questionText: String,
    val options: List<QuizOption>,
    val explanation: String,
)

data class QuizSet(
    val questions: List<QuizQuestion>,
)
