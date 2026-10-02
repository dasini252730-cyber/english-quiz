package com.englishquiz.app.domain.quiz

import com.englishquiz.app.data.ai.ComprehensionQuestion
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.local.SavedExpressionEntity
import com.englishquiz.app.domain.game.GrowthStage
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
 * Wrong options come from the learner's other saved expressions and, when those run out, from the
 * remaining phrases of [todayContent] (요구사항 22: their meanings are already in hand, so this
 * costs no extra AI call). Without that second source a learner who has saved only one expression
 * gets no question at all and is told there is nothing to review, which is exactly what the first
 * session looks like.
 *
 * Passage phrases supply options for fill-in-the-blank questions only. Measured against the
 * deployed function, a saved meaning is a full explanatory sentence (~70 characters) while a
 * passage annotation is a short phrase (~8-19), so offering both as meanings would let the learner
 * pick the answer by its length without knowing the expression. Expression text carries no such
 * tell, so the blank questions can draw from either source.
 */
object QuizBuilder {
    const val DEFAULT_MAX_QUESTIONS = 10

    /** Consecutive correct answers after which a blank must be typed rather than chosen (백로그 043). */
    const val TYPED_AFTER_CORRECT = 1
    private const val MAX_OPTIONS = 4
    private const val MIN_OPTIONS = 2

    fun build(
        todayContent: LearningContent?,
        todayExpressions: List<SavedExpressionEntity>,
        distractorPool: List<SavedExpressionEntity>,
        seed: Long,
        maxQuestions: Int = DEFAULT_MAX_QUESTIONS,
    ): QuizSet {
        val random = Random(seed)
        val distractors = distractorCandidates(todayContent, distractorPool)
        // Only what the caller says is due is asked. Today's passage supplies wrong options and
        // context, never candidates: a saved expression that merely appears in it again is not
        // due again, and asking it anyway would advance its review schedule twice in one day —
        // which is exactly what re-reading the day's stored passage would do (백로그 021).
        // The passage's own questions come first (백로그 042): they are about what was just
        // read, and they cost nothing to ask. They are not part of the expression cap.
        val comprehension = todayContent?.comprehension.orEmpty().map(::comprehensionQuestion)
        val candidates = todayExpressions.distinctBy { normalizeQuizText(it.displayExpression) }
        if (candidates.isEmpty()) return QuizSet(comprehension)

        // Take the most overdue expressions first so a long review backlog actually drains; only
        // then shuffle, so the order within one session still varies.
        val selected = candidates
            .sortedWith(compareBy({ it.nextReviewAtEpochMillis ?: it.firstSavedAtEpochMillis }, { it.id }))
            .take(maxQuestions)
            .shuffled(random)
        return QuizSet(comprehension + selected.mapNotNull { buildQuestion(it, distractors, random) })
    }

    private fun comprehensionQuestion(item: ComprehensionQuestion) = QuizQuestion(
        expression = "",
        type = QuizQuestionType.COMPREHENSION,
        questionText = item.question,
        options = item.options.mapIndexed { index, option -> QuizOption(option, index == item.answerIndex) },
        explanation = item.explanation.ifBlank { "지문의 흐름을 떠올려 보세요." },
    )

    private fun buildQuestion(
        expression: SavedExpressionEntity,
        distractors: List<Distractor>,
        random: Random,
    ): QuizQuestion? {
        val blanked = blankSentence(expression)
        val type = when {
            blanked == null -> QuizQuestionType.MULTIPLE_CHOICE
            // Once recognised right at least once, the expression has to be produced (백로그 043).
            expression.consecutiveCorrectCount >= TYPED_AFTER_CORRECT -> QuizQuestionType.TYPED_BLANK
            else -> QuizQuestionType.FILL_IN_BLANK
        }
        if (type == QuizQuestionType.TYPED_BLANK) {
            return QuizQuestion(
                expression = expression.displayExpression,
                type = type,
                questionText = checkNotNull(blanked),
                options = listOf(QuizOption(expression.displayExpression, true)),
                explanation = explanationFor(expression),
                growthBefore = GrowthStage.of(expression.consecutiveCorrectCount, expression.isMastered),
            )
        }
        // A fill-in-the-blank asks which expression fits the sentence (요구사항 14.2), so its
        // options are expressions; the meaning question's options are meanings.
        val answerOf: (Distractor) -> String = when (type) {
            QuizQuestionType.FILL_IN_BLANK -> Distractor::expression
            else -> Distractor::meaning
        }
        val answer = Distractor(expression.displayExpression, expression.contextMeaning, true)
        val usable = when (type) {
            QuizQuestionType.FILL_IN_BLANK -> distractors
            else -> distractors.filter { it.fromSaved }
        }
        val options = buildOptions(answer, usable, random, answerOf) ?: return null
        return QuizQuestion(
            expression = expression.displayExpression,
            type = type,
            questionText = blanked ?: expression.displayExpression,
            options = options,
            explanation = explanationFor(expression),
            growthBefore = GrowthStage.of(expression.consecutiveCorrectCount, expression.isMastered),
        )
    }

    /** Null when no honest question can be built: a blank answer, or fewer than [MIN_OPTIONS]. */
    private fun buildOptions(
        answer: Distractor,
        distractors: List<Distractor>,
        random: Random,
        answerOf: (Distractor) -> String,
    ): List<QuizOption>? {
        val correctAnswer = answerOf(answer).trim()
        if (correctAnswer.isEmpty()) return null
        val wrongAnswers = distractors
            .filter { normalizeQuizText(it.expression) != normalizeQuizText(answer.expression) }
            .map { answerOf(it).trim() }
            .filter { it.isNotEmpty() && normalizeQuizText(it) != normalizeQuizText(correctAnswer) }
            .distinct()
            .shuffled(random)
            .take(MAX_OPTIONS - 1)
        if (wrongAnswers.size + 1 < MIN_OPTIONS) return null
        return (wrongAnswers.map { QuizOption(it, false) } + QuizOption(correctAnswer, true))
            .shuffled(random)
    }

    /** Replaces the expression in `contextSentence` with a blank, or null if that isn't possible. */
    private fun blankSentence(expression: SavedExpressionEntity): String? {
        val sentence = expression.contextSentence
        if (sentence.isBlank()) return null
        val regex = Regex(Regex.escape(expression.displayExpression.trim()), RegexOption.IGNORE_CASE)
        if (!regex.containsMatchIn(sentence)) return null
        return regex.replace(sentence, "____")
    }

    /**
     * The meaning comes back from the model as a finished sentence, so wrapping it in another one
     * reads as a double ending ("...뜻입니다.' 라는 뜻이에요."). It gets its own line instead.
     */
    private fun explanationFor(expression: SavedExpressionEntity): String =
        "\"${expression.displayExpression}\"의 뜻이에요.\n${expression.contextMeaning}"

    /** One possible option: an expression and the meaning shown for it. */
    private data class Distractor(
        val expression: String,
        val meaning: String,
        /** False for a phrase that only appeared in today's passage. */
        val fromSaved: Boolean,
    )

    /**
     * The saved expressions first, then today's phrases. Order matters only for de-duplication:
     * the learner's own saved meaning wins over the one the passage happened to give.
     */
    private fun distractorCandidates(
        content: LearningContent?,
        pool: List<SavedExpressionEntity>,
    ): List<Distractor> {
        val saved = pool.map { Distractor(it.displayExpression, it.contextMeaning, true) }
        val fromContent = content?.expressions.orEmpty()
            .map { Distractor(it.text, it.meaning, false) }
        return (saved + fromContent).distinctBy { normalizeQuizText(it.expression) }
    }
}
