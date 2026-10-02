package com.englishquiz.app.domain.game

/**
 * The running score of one quiz (백로그 035). [combo] is the current run of correct answers,
 * [maxCombo] the longest run so far, [lastEarned] what the most recent answer was worth (0 for a
 * wrong one) so the feedback card can show it.
 */
data class QuizScore(
    val points: Int = 0,
    val combo: Int = 0,
    val maxCombo: Int = 0,
    val lastEarned: Int = 0,
)

/**
 * Pure Kotlin scoring (요구사항 18절 게임 요소). A correct answer earns [BASE_POINTS] plus
 * [COMBO_STEP] for every earlier answer in the current run, up to [MAX_BONUS_STEPS] steps: 10,
 * 15, 20, 25 and then 30 for every further answer. A wrong answer earns nothing and ends the run;
 * it never takes points away, because a learner with nothing to lose keeps answering honestly.
 */
object ScorePolicy {
    const val BASE_POINTS = 10
    const val COMBO_STEP = 5
    const val MAX_BONUS_STEPS = 4

    /** What the [combo]-th correct answer in a row is worth (combo starts at 1). */
    fun pointsFor(combo: Int, multiplier: Int = 1): Int {
        require(combo >= 1) { "콤보는 1 이상이어야 합니다." }
        val steps = (combo - 1).coerceAtMost(MAX_BONUS_STEPS)
        return (BASE_POINTS + COMBO_STEP * steps) * multiplier
    }

    /** [multiplier] lets a special quiz (the weekend boss, 백로그 041) pay more per answer. */
    fun answer(current: QuizScore, wasCorrect: Boolean, multiplier: Int = 1): QuizScore {
        if (!wasCorrect) return current.copy(combo = 0, lastEarned = 0)
        val combo = current.combo + 1
        val earned = pointsFor(combo, multiplier)
        return QuizScore(
            points = current.points + earned,
            combo = combo,
            maxCombo = maxOf(current.maxCombo, combo),
            lastEarned = earned,
        )
    }
}
