package com.englishquiz.app.domain.quiz

import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.local.SavedExpressionEntity
import kotlin.random.Random

/** One possible option: an expression and the meanings shown for it. */
internal data class Distractor(
    val expression: String,
    val meaning: String,
    val shortMeaning: String,
    /** The slot the phrase fills (백로그 059); "" when unknown. */
    val kind: String,
    /** False for a phrase that only appeared in today's passage. */
    val fromSaved: Boolean,
)

/**
 * The options of one question (요구사항 22, 백로그 046/059), from the learner's other saved
 * expressions and the remaining phrases of today's passage, whose meanings are already in hand.
 *
 * Wrong options are phrases of the answer's kind (백로그 059: a lone verb among nouns gives
 * itself away), with phrases whose kind is unknown, from before kinds were kept, admitted after
 * them; a phrase of another kind is never offered, so the question shrinks or is dropped instead.
 * Another spelling or inflection of the answer ("pulled it off") is never offered either. The
 * answer's place among the options is drawn afresh for every question from the session's seeded
 * [Random], so it never settles on one number.
 *
 * A meaning question never mixes a full saved meaning with a short gloss: the odd one out would
 * give the answer away by its length. Full meanings therefore come from saved expressions only
 * (a passage annotation is a phrase, not a sentence), while short glosses may come from today's
 * passage as well, which is what rescues an expression the box alone cannot ask about.
 */
internal object QuizOptions {
    /** Fewer than this is no choice at all. */
    private const val MIN_OPTIONS = 2

    /**
     * The saved expressions first, then today's phrases. Order matters only for de-duplication:
     * the learner's own saved meaning wins over the one the passage happened to give.
     */
    fun candidates(content: LearningContent?, pool: List<SavedExpressionEntity>): List<Distractor> {
        val saved = pool.map { Distractor(it.displayExpression, it.contextMeaning, it.shortMeaning, it.kind, true) }
        val fromContent = content?.expressions.orEmpty()
            .map { Distractor(it.text, it.meaning, it.shortMeaning, it.kind, false) }
        return (saved + fromContent).distinctBy { normalizeQuizText(it.expression) }
    }

    /** Expressions to fill a blank with: the answer and up to [count] - 1 others. */
    fun blankOptions(
        expression: SavedExpressionEntity,
        distractors: List<Distractor>,
        random: Random,
        count: Int,
    ): List<QuizOption>? = buildOptions(answerOf(expression), distractors, random, count, Distractor::expression)

    /**
     * Short glosses for every option, or full meanings for every option, whichever makes the
     * fuller question; null when neither makes an honest one.
     */
    fun meaningOptions(
        expression: SavedExpressionEntity,
        distractors: List<Distractor>,
        random: Random,
        count: Int,
    ): List<QuizOption>? {
        val answer = answerOf(expression)
        val short = if (answer.shortMeaning.isEmpty()) {
            null
        } else {
            buildOptions(answer, distractors.filter { it.shortMeaning.isNotEmpty() }, random, count, Distractor::shortMeaning)
        }
        if (short != null && short.size == count) return short
        val long = buildOptions(answer, distractors.filter { it.fromSaved }, random, count, Distractor::meaning)
        return listOfNotNull(short, long).maxByOrNull { it.size }
    }

    private fun answerOf(expression: SavedExpressionEntity) =
        Distractor(expression.displayExpression, expression.contextMeaning, expression.shortMeaning, expression.kind, true)

    /** Null when no honest question can be built: a blank answer, or fewer than [MIN_OPTIONS]. */
    private fun buildOptions(
        answer: Distractor,
        distractors: List<Distractor>,
        random: Random,
        count: Int,
        answerOf: (Distractor) -> String,
    ): List<QuizOption>? {
        val correctAnswer = answerOf(answer).trim()
        if (correctAnswer.isEmpty()) return null
        val usable = distractors
            .filter { !typedAnswerMatches(it.expression, answer.expression) && fitsKind(answer.kind, it.kind) }
            .map { it.kind to answerOf(it).trim() }
            .filter { (_, text) -> text.isNotEmpty() && normalizeQuizText(text) != normalizeQuizText(correctAnswer) }
            .distinctBy { (_, text) -> normalizeQuizText(text) }
        val (sameKind, unknownKind) = usable.partition { (kind, _) -> kind.isNotEmpty() && kind == answer.kind }
        val wrongAnswers = (sameKind.shuffled(random) + unknownKind.shuffled(random))
            .map { (_, text) -> text }
            .take(count - 1)
        if (wrongAnswers.size + 1 < MIN_OPTIONS) return null
        return (wrongAnswers.map { QuizOption(it, false) } + QuizOption(correctAnswer, true))
            .shuffled(random)
    }

    /** Whether a phrase of [kind] may stand against an answer of [answerKind]: the same kind, or either unknown. */
    private fun fitsKind(answerKind: String, kind: String): Boolean =
        answerKind.isEmpty() || kind.isEmpty() || answerKind == kind
}
