package com.englishquiz.app.domain.quiz

import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.foldTypography
import com.englishquiz.app.data.local.SavedExpressionEntity
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
    /** Matches the set the Reader strips when it turns a tapped token into a saved expression. */
    private val EDGE_PUNCTUATION = charArrayOf(
        '.', ',', '!', '?', ';', ':', '"', '\'', '(', ')', '[', ']', '\u2026', '-',
    )

    private const val MAX_QUESTIONS = 10
    private const val MAX_OPTIONS = 4
    private const val MIN_OPTIONS = 2

    fun build(
        todayContent: LearningContent?,
        todayExpressions: List<SavedExpressionEntity>,
        distractorPool: List<SavedExpressionEntity>,
        seed: Long,
    ): QuizSet {
        val random = Random(seed)
        val distractors = distractorCandidates(todayContent, distractorPool)
        // Only what the caller says is due is asked. Today's passage supplies wrong options and
        // context, never candidates: a saved expression that merely appears in it again is not
        // due again, and asking it anyway would advance its review schedule twice in one day —
        // which is exactly what re-reading the day's stored passage would do (백로그 021).
        val candidates = todayExpressions.distinctBy { normalize(it.displayExpression) }
        if (candidates.isEmpty()) return QuizSet(emptyList())

        // Take the most overdue expressions first so a long review backlog actually drains; only
        // then shuffle, so the order within one session still varies.
        val selected = candidates
            .sortedWith(compareBy({ it.nextReviewAtEpochMillis ?: it.firstSavedAtEpochMillis }, { it.id }))
            .take(MAX_QUESTIONS)
            .shuffled(random)
        return QuizSet(selected.mapNotNull { buildQuestion(it, distractors, random) })
    }

    private fun buildQuestion(
        expression: SavedExpressionEntity,
        distractors: List<Distractor>,
        random: Random,
    ): QuizQuestion? {
        val blanked = blankSentence(expression)
        val type = if (blanked != null) QuizQuestionType.FILL_IN_BLANK else QuizQuestionType.MULTIPLE_CHOICE
        // A fill-in-the-blank asks which expression fits the sentence (요구사항 14.2), so its
        // options are expressions; the meaning question's options are meanings.
        val answerOf: (Distractor) -> String = when (type) {
            QuizQuestionType.FILL_IN_BLANK -> Distractor::expression
            QuizQuestionType.MULTIPLE_CHOICE -> Distractor::meaning
        }
        val answer = Distractor(expression.displayExpression, expression.contextMeaning, true)
        val usable = when (type) {
            QuizQuestionType.FILL_IN_BLANK -> distractors
            QuizQuestionType.MULTIPLE_CHOICE -> distractors.filter { it.fromSaved }
        }
        val options = buildOptions(answer, usable, random, answerOf) ?: return null
        return QuizQuestion(
            expression = expression.displayExpression,
            type = type,
            questionText = blanked ?: expression.displayExpression,
            options = options,
            explanation = explanationFor(expression),
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
            .filter { normalize(it.expression) != normalize(answer.expression) }
            .map { answerOf(it).trim() }
            .filter { it.isNotEmpty() && normalize(it) != normalize(correctAnswer) }
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
        return (saved + fromContent).distinctBy { normalize(it.expression) }
    }

    /**
     * The key two spellings of one expression have to agree on.
     *
     * A saved expression is the text the Reader cut out of the passage with its edge punctuation
     * removed; a content annotation is the string the model sent, trimmed and nothing else. The
     * two differ in exactly the ways [foldTypography] folds — a curly apostrophe against a
     * straight one — and in trailing punctuation. Without folding both, the same phrase can be
     * offered as the correct answer and as a wrong option at once, indistinguishable on screen,
     * and tapping the wrong copy records a wrong answer against the learner's review state.
     */
    private fun normalize(text: String): String = text
        .foldTypography()
        .trim(*EDGE_PUNCTUATION)
        .trim()
        .split(Regex("""\s+"""))
        .filter { it.isNotEmpty() }
        .joinToString(" ")
}
