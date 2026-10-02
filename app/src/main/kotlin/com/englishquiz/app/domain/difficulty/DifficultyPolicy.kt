package com.englishquiz.app.domain.difficulty

import com.englishquiz.app.domain.session.LearningSessionSummary

/**
 * Decides the difficulty the next content generation should ask for (요구사항 8절, 백로그 013).
 *
 * Pure Kotlin: no AI, no clock, no storage. The caller supplies the recent sessions and how many
 * sessions have passed since the last change, so the same input always gives the same answer and
 * the rule stays explainable — which is what the task's `done_when` asks for.
 *
 * 요구사항 8절 names four inputs. Three of them are here: quiz correct rate, recent learning
 * performance (the window of sessions this reads), and how often the learner saves expressions.
 * The fourth — the difficulty of the expressions inside the content — has no measurement in this
 * app: nothing scores an expression, and the level of what gets generated is itself set by the
 * number this function returns. Saved-expression frequency stands in for it: a passage that
 * forces many lookups was too hard, one that forces almost none was too easy.
 */
object DifficultyPolicy {
    /** Five levels per mode (백로그 034); the Edge Function's rubric names each one. */
    const val MIN_DIFFICULTY = 1
    const val MAX_DIFFICULTY = 5

    /**
     * Where the diagnosis (`AssessmentLevel.storedValue`: 1 초급, 2 중급, 3 고급) puts a learner on
     * the five-step scale: 2, 3 and 4, so every start leaves room to move in both directions.
     */
    fun initialLevel(assessmentLevel: Int): Int = (assessmentLevel + 1).coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

    /** How many of the most recent sessions count as "recent performance". */
    const val SESSIONS_CONSIDERED = 3

    /**
     * How many of those sessions must actually have asked quiz questions. A window where only one
     * day had a quiz would let a single day's score decide the level, and the level would likely
     * swing back once the next window filled in — the oscillation this policy exists to avoid.
     */
    const val MIN_SESSIONS_WITH_QUIZ = 2

    /** Pooled correct percent at or above which the content is treated as too easy. */
    const val RAISE_CORRECT_PERCENT = 85

    /** Pooled correct percent below which the content is treated as too hard. */
    const val LOWER_CORRECT_PERCENT = 50

    /** Average newly saved expressions per session at or below which little was unfamiliar. */
    const val EASY_SAVE_COUNT = 2.0

    /** Average newly saved expressions per session at or above which too much was unfamiliar. */
    const val HARD_SAVE_COUNT = 12.0

    /**
     * [recentSessions] is newest-first; only the first [SESSIONS_CONSIDERED] are read.
     * [sessionsSinceLastChange] is how many sessions the learner has finished since difficulty
     * last moved — the caller persists it, and passes a large value when it has never moved.
     *
     * Returns the difficulty to use next, clamped to [MIN_DIFFICULTY]..[MAX_DIFFICULTY]. It moves
     * by at most one step, only when a full window of sessions has accumulated since the last
     * move, and only when enough of that window actually ran a quiz; together those rules are what
     * keeps the level from oscillating.
     */
    fun nextDifficulty(
        currentDifficulty: Int,
        recentSessions: List<LearningSessionSummary>,
        sessionsSinceLastChange: Int,
    ): Int {
        val current = currentDifficulty.coerceIn(MIN_DIFFICULTY, MAX_DIFFICULTY)

        // A level that just moved needs a fresh window of evidence before it may move again.
        if (sessionsSinceLastChange < SESSIONS_CONSIDERED) return current

        val window = recentSessions.take(SESSIONS_CONSIDERED)
        if (window.size < SESSIONS_CONSIDERED) return current

        // 오늘 복습할 표현이 없는 날은 퀴즈가 비어 있다(백로그 010). Judging a learner on a window
        // that barely asked anything would be guessing, so the level holds.
        if (window.count { it.quizQuestionCount > 0 } < MIN_SESSIONS_WITH_QUIZ) return current

        val askedQuestions = window.sumOf { it.quizQuestionCount }

        val correctPercent = window.sumOf { it.quizCorrectCount } * 100 / askedQuestions
        val averageSaved = window.sumOf { it.newlySavedExpressionCount }.toDouble() / window.size

        // Lowering wins over raising: leaving a learner stuck is worse than moving them on late.
        return when {
            correctPercent < LOWER_CORRECT_PERCENT || averageSaved >= HARD_SAVE_COUNT ->
                (current - 1).coerceAtLeast(MIN_DIFFICULTY)
            correctPercent >= RAISE_CORRECT_PERCENT && averageSaved <= EASY_SAVE_COUNT ->
                (current + 1).coerceAtMost(MAX_DIFFICULTY)
            else -> current
        }
    }
}
