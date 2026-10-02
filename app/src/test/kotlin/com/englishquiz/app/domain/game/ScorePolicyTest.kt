package com.englishquiz.app.domain.game

import org.junit.Assert.assertEquals
import org.junit.Test

class ScorePolicyTest {
    @Test
    fun pointsGrowWithTheComboAndStopAtThirty() {
        assertEquals(listOf(10, 15, 20, 25, 30, 30, 30), (1..7).map { ScorePolicy.pointsFor(it) })
    }

    @Test
    fun aRunOfCorrectAnswersAccumulatesPointsAndCombo() {
        var score = QuizScore()
        repeat(3) { score = ScorePolicy.answer(score, wasCorrect = true) }

        assertEquals(QuizScore(points = 45, combo = 3, maxCombo = 3, lastEarned = 20), score)
    }

    @Test
    fun aWrongAnswerEndsTheRunButKeepsThePointsAndTheBestRun() {
        var score = QuizScore()
        repeat(2) { score = ScorePolicy.answer(score, wasCorrect = true) }
        score = ScorePolicy.answer(score, wasCorrect = false)

        assertEquals(QuizScore(points = 25, combo = 0, maxCombo = 2, lastEarned = 0), score)

        // The next correct answer starts over at the base value.
        score = ScorePolicy.answer(score, wasCorrect = true)
        assertEquals(QuizScore(points = 35, combo = 1, maxCombo = 2, lastEarned = 10), score)
    }

    @Test
    fun theMultiplierScalesEveryAnswer() {
        val score = ScorePolicy.answer(QuizScore(combo = 1, points = 20, maxCombo = 1), true, multiplier = 2)

        assertEquals(QuizScore(points = 50, combo = 2, maxCombo = 2, lastEarned = 30), score)
    }
}
