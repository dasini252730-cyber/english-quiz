package com.englishquiz.app.domain.quiz

import com.englishquiz.app.domain.game.GrowthStage

/**
 * Quiz question types (요구사항 14.2): the first-meeting card (백로그 045), meaning multiple
 * choice, fill-in-the-blank by choice, the typed blank for an expression at the 꽃 stage
 * (백로그 043/045), the expression typed from its Korean meaning when there is no sentence to
 * blank (백로그 057), and a question about the passage itself (백로그 042).
 */
enum class QuizQuestionType { LEARN_CARD, MULTIPLE_CHOICE, FILL_IN_BLANK, TYPED_BLANK, TYPED_MEANING, COMPREHENSION }

/**
 * Which quiz an answer was given in (백로그 056). Only [DAILY] moves an expression's growth stage
 * and review schedule. [BOSS] pays double points and [PRACTICE] pays none; both only log the
 * answer, so the same expression cannot be promoted twice in a day or by practising.
 */
enum class QuizMode(val wireValue: String) {
    DAILY("daily"),
    BOSS("boss"),
    PRACTICE("practice"),
}

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
    /** The short gloss shown on a learn card (백로그 045/046); "" when the expression has none. */
    val shortMeaning: String = "",
    /** True in the retry round (백로그 047): asked again, scored and recorded no more. */
    val isRetry: Boolean = false,
)

data class QuizSet(
    val questions: List<QuizQuestion>,
)
