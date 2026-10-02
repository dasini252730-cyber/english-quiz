package com.englishquiz.app.domain.quiz

import com.englishquiz.app.domain.game.GrowthStage

/**
 * Quiz question types (요구사항 14.2): meaning multiple choice, fill-in-the-blank by choice, the
 * typed blank for an expression already answered right once (백로그 043), and a question about
 * the passage itself (백로그 042).
 */
enum class QuizQuestionType { MULTIPLE_CHOICE, FILL_IN_BLANK, TYPED_BLANK, COMPREHENSION }

data class QuizOption(
    val text: String,
    val isCorrect: Boolean,
)

/**
 * One quiz item. For an expression question, [expression] is the saved expression's
 * `displayExpression`: the UI shows it (for [QuizQuestionType.MULTIPLE_CHOICE]) and also passes
 * it straight to `LearningRepository.recordAnswer` after the learner answers, so it must match
 * the stored value exactly. A [QuizQuestionType.COMPREHENSION] question is about the passage,
 * belongs to no expression and leaves [expression] empty: nothing is recorded for it.
 *
 * A [QuizQuestionType.TYPED_BLANK] carries the one correct option, so the answer can be shown
 * after a wrong attempt; the learner's own text arrives as a synthetic option.
 */
data class QuizQuestion(
    val expression: String,
    val type: QuizQuestionType,
    val questionText: String,
    val options: List<QuizOption>,
    val explanation: String,
    /** The expression's stage when the quiz was built (백로그 036), so feedback can show the move. */
    val growthBefore: GrowthStage = GrowthStage.SEED,
)

data class QuizSet(
    val questions: List<QuizQuestion>,
)
