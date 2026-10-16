package com.englishquiz.app.domain.quiz

import com.englishquiz.app.data.ai.ComprehensionQuestion
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.local.SavedExpressionEntity
import com.englishquiz.app.domain.game.GrowthStage
import com.englishquiz.app.domain.review.ExpressionState
import kotlin.random.Random

/**
 * Builds one quiz session (요구사항 14, 백로그 010) from today's content and the learner's saved
 * expressions. Pure Kotlin: no AI calls, no I/O, no system clock or global randomness, so the
 * same inputs always produce the same [QuizSet] (see QuizBuilderTest).
 *
 * Quiz targets are exactly [todayExpressions], de-duplicated. The caller resolves them from
 * `LearningRepository.findDueExpressions`, which covers all three groups of 요구사항 14.1: the
 * review expressions the passage was generated around (due by definition), expressions saved
 * today (their next review is still null) and older expressions that came due. [todayContent]
 * never adds a target: a saved expression that merely appears in the passage again is not due
 * again, and the same passage now comes back on every same-day entry (백로그 021), so asking it
 * would advance its review schedule several times in one day.
 *
 * The shape of a question follows the expression's growth stage (백로그 045), one step harder per
 * stage: never quizzed → a card to read; 씨앗 → two meanings; 새싹 → four; 잎 → the sentence with
 * a blank and expressions to choose from; 꽃 → the blank typed. Without a context sentence the
 * 잎 step falls back to four meanings, and from 꽃 the expression is typed from its Korean meaning
 * (백로그 057): mastery must be reached by recalling the words, never by recognising a meaning.
 *
 * Wrong options are built by [QuizOptions] from the learner's other saved expressions and the
 * remaining phrases of [todayContent]: phrases of the answer's kind first (백로그 059), short
 * glosses never mixed with full meanings (백로그 046).
 */
object QuizBuilder {
    const val DEFAULT_MAX_QUESTIONS = 10

    /** Expressions never quizzed before are asked at most this many per quiz (백로그 048). */
    const val NEW_PER_DAY = 5

    /** Consecutive correct answers (the 꽃 stage) from which a blank must be typed (백로그 043/045). */
    const val TYPED_AFTER_CORRECT = 3
    private const val FEW_OPTIONS = 2
    private const val MANY_OPTIONS = 4

    fun build(
        todayContent: LearningContent?,
        todayExpressions: List<SavedExpressionEntity>,
        distractorPool: List<SavedExpressionEntity>,
        seed: Long,
        maxQuestions: Int = DEFAULT_MAX_QUESTIONS,
        /**
         * False for the weekend boss (백로그 041): an expression never asked before is then a
         * 씨앗-stage question rather than a card, and the daily cap on first meetings is off.
         */
        askCards: Boolean = true,
        /** Cards already shown today (백로그 048), so a second entry does not show five more. */
        firstMeetingsShownToday: Int = 0,
    ): QuizSet {
        val random = Random(seed)
        val distractors = QuizOptions.candidates(todayContent, distractorPool)
        // The passage's own questions come first (백로그 042): they are about what was just
        // read, and they cost nothing to ask. They are not part of the expression cap.
        val comprehension = todayContent?.comprehension.orEmpty().map { comprehensionQuestion(it, random) }
        val candidates = todayExpressions.distinctBy { normalizeQuizText(it.displayExpression) }
        if (candidates.isEmpty()) return QuizSet(comprehension)

        // Two queues (백로그 055): reviews first, the most overdue at the front so a backlog drains;
        // then at most a few expressions from the new-expression queue (백로그 048), the rest
        // waiting for another day. Only then shuffle, so the order within one session still varies.
        val byDue = compareBy<SavedExpressionEntity>({ it.nextReviewAtEpochMillis ?: it.firstSavedAtEpochMillis }, { it.id })
        val (fresh, reviews) = candidates.partition { askCards && ExpressionState.isNew(it) }
        val newToday = (NEW_PER_DAY - firstMeetingsShownToday).coerceAtLeast(0)
        val selected = (reviews.sortedWith(byDue) + fresh.sortedWith(byDue).take(newToday))
            .take(maxQuestions)
            .shuffled(random)
        return QuizSet(comprehension + selected.mapNotNull { buildQuestion(it, distractors, random, askCards) })
    }

    /** The model tends to put the answer at one index (백로그 059), so its options are shuffled like any other question's. */
    private fun comprehensionQuestion(item: ComprehensionQuestion, random: Random) = QuizQuestion(
        expression = "",
        type = QuizQuestionType.COMPREHENSION,
        questionText = item.question,
        options = item.options.mapIndexed { index, option -> QuizOption(option, index == item.answerIndex) }.shuffled(random),
        explanation = item.explanation.ifBlank { "지문의 흐름을 떠올려 보세요." },
    )

    /**
     * Whether an honest question can be built for [expression] from the review box alone
     * (백로그 057): false when its meaning is blank, hides nothing but the expression itself, or
     * the box offers no other meaning to choose against. A card is always possible, so a new row
     * is askable. The review box marks the rest "출제 불가"; such a row is not asked from the box,
     * so it cannot be promoted by it (today's passage may still lend a blank its options).
     */
    fun isAskable(expression: SavedExpressionEntity, pool: List<SavedExpressionEntity>): Boolean =
        ExpressionState.isNew(expression) ||
            buildQuestion(expression, QuizOptions.candidates(null, pool), Random(0), askCards = false) != null

    private fun buildQuestion(
        expression: SavedExpressionEntity,
        distractors: List<Distractor>,
        random: Random,
        askCards: Boolean,
    ): QuizQuestion? {
        val stage = GrowthStage.of(expression.consecutiveCorrectCount, expression.isMastered)
        val blanked = blankSentence(expression)
        val question = QuizQuestion(
            expression = expression.displayExpression,
            type = QuizQuestionType.MULTIPLE_CHOICE,
            questionText = expression.displayExpression,
            options = emptyList(),
            explanation = explanationFor(expression),
            growthBefore = stage,
            shortMeaning = expression.shortMeaning,
        )
        if (askCards && ExpressionState.isNew(expression)) {
            // Seen for the first time: read, not tested (백로그 045).
            return question.copy(type = QuizQuestionType.LEARN_CARD, questionText = expression.contextSentence)
        }
        if (blanked != null && stage == GrowthStage.LEAF) {
            val options = QuizOptions.blankOptions(expression, distractors, random, MANY_OPTIONS) ?: return null
            return question.copy(type = QuizQuestionType.FILL_IN_BLANK, questionText = blanked, options = options)
        }
        if (stage.ordinal >= GrowthStage.FLOWER.ordinal) {
            // Typed either way: the blank in its sentence, or the expression from its meaning. A
            // meaning that quotes the expression ("call it a day"는 …) has it masked first, or the
            // answer would be on screen; one that is nothing but the expression asks nothing.
            val meaning = maskExpression(expression.shortMeaning.ifBlank { expression.contextMeaning }, expression.displayExpression)
            if (blanked == null && meaning.replace("____", "").isBlank()) return null
            return question.copy(
                type = if (blanked != null) QuizQuestionType.TYPED_BLANK else QuizQuestionType.TYPED_MEANING,
                questionText = blanked ?: meaning,
                options = listOf(QuizOption(expression.displayExpression, true)),
            )
        }
        val count = if (stage == GrowthStage.SEED) FEW_OPTIONS else MANY_OPTIONS
        val options = QuizOptions.meaningOptions(expression, distractors, random, count) ?: return null
        return question.copy(options = options)
    }

    /** Replaces the expression in `contextSentence` with a blank, or null if that isn't possible. */
    private fun blankSentence(expression: SavedExpressionEntity): String? {
        val sentence = expression.contextSentence
        if (sentence.isBlank()) return null
        val regex = Regex(Regex.escape(expression.displayExpression.trim()), RegexOption.IGNORE_CASE)
        if (!regex.containsMatchIn(sentence)) return null
        return regex.replace(sentence, "____")
    }

    /** [text] with every occurrence of [expression] blanked, case-insensitively, and trimmed. */
    private fun maskExpression(text: String, expression: String): String =
        Regex(Regex.escape(expression.trim()), RegexOption.IGNORE_CASE).replace(text, "____").trim()

    /**
     * The meaning comes back from the model as a finished sentence, so wrapping it in another one
     * reads as a double ending ("...뜻입니다.' 라는 뜻이에요."). It gets its own line instead.
     */
    private fun explanationFor(expression: SavedExpressionEntity): String =
        "\"${expression.displayExpression}\"의 뜻이에요.\n${expression.contextMeaning}"
}
