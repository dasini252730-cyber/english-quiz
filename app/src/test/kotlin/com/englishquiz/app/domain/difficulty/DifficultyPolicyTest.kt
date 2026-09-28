package com.englishquiz.app.domain.difficulty

import com.englishquiz.app.domain.session.LearningSessionSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class DifficultyPolicyTest {
    private fun session(
        correct: Int,
        questions: Int,
        newlySaved: Int = 5,
    ) = LearningSessionSummary(
        learnedExpressionCount = questions,
        newlySavedExpressionCount = newlySaved,
        quizCorrectCount = correct,
        quizQuestionCount = questions,
    )

    private fun next(
        current: Int,
        sessions: List<LearningSessionSummary>,
        sessionsSinceLastChange: Int = 99,
    ) = DifficultyPolicy.nextDifficulty(current, sessions, sessionsSinceLastChange)

    @Test
    fun `strong answers with little new vocabulary move the level up one step`() {
        val sessions = List(3) { session(correct = 9, questions = 10, newlySaved = 1) }

        assertEquals(3, next(current = 2, sessions = sessions))
    }

    @Test
    fun `weak answers move the level down one step`() {
        val sessions = List(3) { session(correct = 4, questions = 10, newlySaved = 5) }

        assertEquals(1, next(current = 2, sessions = sessions))
    }

    @Test
    fun `saving a flood of expressions lowers the level even when answers are strong`() {
        // The quiz only asks about what was saved, so a learner can score well on a passage that
        // was still far too hard to read. The save count is what catches that.
        val sessions = List(3) { session(correct = 10, questions = 10, newlySaved = 14) }

        assertEquals(2, next(current = 3, sessions = sessions))
    }

    @Test
    fun `strong answers with plenty of new vocabulary hold the level`() {
        val sessions = List(3) { session(correct = 10, questions = 10, newlySaved = 7) }

        assertEquals(2, next(current = 2, sessions = sessions))
    }

    @Test
    fun `a middling correct rate holds the level`() {
        val sessions = List(3) { session(correct = 7, questions = 10, newlySaved = 4) }

        assertEquals(2, next(current = 2, sessions = sessions))
    }

    @Test
    fun `the level never moves more than one step at a time`() {
        val sessions = List(3) { session(correct = 0, questions = 10, newlySaved = 20) }

        assertEquals(2, next(current = 3, sessions = sessions))
    }

    @Test
    fun `the level holds until a full window has passed since the last change`() {
        val sessions = List(3) { session(correct = 10, questions = 10, newlySaved = 0) }

        assertEquals(2, next(current = 2, sessions = sessions, sessionsSinceLastChange = 0))
        assertEquals(2, next(current = 2, sessions = sessions, sessionsSinceLastChange = 2))
        assertEquals(3, next(current = 2, sessions = sessions, sessionsSinceLastChange = 3))
    }

    @Test
    fun `too few sessions hold the level`() {
        val strong = session(correct = 10, questions = 10, newlySaved = 0)

        assertEquals(2, next(current = 2, sessions = emptyList()))
        assertEquals(2, next(current = 2, sessions = listOf(strong)))
        assertEquals(2, next(current = 2, sessions = listOf(strong, strong)))
    }

    @Test
    fun `only the most recent sessions count`() {
        val strong = session(correct = 10, questions = 10, newlySaved = 0)
        val weak = session(correct = 0, questions = 10, newlySaved = 0)
        // Newest first: three strong sessions decide it, however bad the older ones were.
        val sessions = listOf(strong, strong, strong, weak, weak)

        assertEquals(3, next(current = 2, sessions = sessions))
    }

    // The four thresholds are inclusive on the side that triggers. Without a case sitting exactly
    // on each one, flipping any of the four comparisons would still pass the rest of this file.

    @Test
    fun `exactly the raise threshold raises`() {
        // 17 of 20 pooled questions is exactly 85 percent, and 6 saved over 3 sessions is exactly 2.
        val sessions = listOf(
            session(correct = 6, questions = 7, newlySaved = 2),
            session(correct = 6, questions = 7, newlySaved = 2),
            session(correct = 5, questions = 6, newlySaved = 2),
        )
        assertEquals(85, sessions.sumOf { it.quizCorrectCount } * 100 / sessions.sumOf { it.quizQuestionCount })

        assertEquals(3, next(current = 2, sessions = sessions))
    }

    @Test
    fun `one point under the raise threshold holds`() {
        val sessions = listOf(
            session(correct = 6, questions = 7, newlySaved = 2),
            session(correct = 6, questions = 7, newlySaved = 2),
            session(correct = 4, questions = 6, newlySaved = 2),
        )
        assertEquals(80, sessions.sumOf { it.quizCorrectCount } * 100 / sessions.sumOf { it.quizQuestionCount })

        assertEquals(2, next(current = 2, sessions = sessions))
    }

    @Test
    fun `exactly the lower threshold holds because lowering needs to fall below it`() {
        val sessions = List(3) { session(correct = 5, questions = 10, newlySaved = 5) }

        assertEquals(2, next(current = 2, sessions = sessions))
    }

    @Test
    fun `one point under the lower threshold lowers`() {
        val sessions = listOf(
            session(correct = 5, questions = 10, newlySaved = 5),
            session(correct = 5, questions = 10, newlySaved = 5),
            session(correct = 4, questions = 10, newlySaved = 5),
        )

        assertEquals(1, next(current = 2, sessions = sessions))
    }

    @Test
    fun `exactly the easy save average still counts as easy`() {
        val sessions = listOf(
            session(correct = 10, questions = 10, newlySaved = 3),
            session(correct = 10, questions = 10, newlySaved = 2),
            session(correct = 10, questions = 10, newlySaved = 1),
        )

        assertEquals(3, next(current = 2, sessions = sessions))
    }

    @Test
    fun `just over the easy save average holds`() {
        val sessions = listOf(
            session(correct = 10, questions = 10, newlySaved = 3),
            session(correct = 10, questions = 10, newlySaved = 2),
            session(correct = 10, questions = 10, newlySaved = 2),
        )

        assertEquals(2, next(current = 2, sessions = sessions))
    }

    @Test
    fun `exactly the hard save average still counts as hard`() {
        val sessions = listOf(
            session(correct = 10, questions = 10, newlySaved = 13),
            session(correct = 10, questions = 10, newlySaved = 12),
            session(correct = 10, questions = 10, newlySaved = 11),
        )

        assertEquals(1, next(current = 2, sessions = sessions))
    }

    @Test
    fun `just under the hard save average does not lower`() {
        val sessions = listOf(
            session(correct = 10, questions = 10, newlySaved = 13),
            session(correct = 10, questions = 10, newlySaved = 12),
            session(correct = 10, questions = 10, newlySaved = 10),
        )

        assertEquals(2, next(current = 2, sessions = sessions))
    }

    @Test
    fun `a window that asked no questions holds the level`() {
        // 오늘 복습할 표현이 없으면 퀴즈가 비어 있다. Saving alone must not move the level.
        val sessions = List(3) { session(correct = 0, questions = 0, newlySaved = 20) }

        assertEquals(2, next(current = 2, sessions = sessions))
    }

    @Test
    fun `a window mixing an empty quiz with answered ones pools the answered questions`() {
        val sessions = listOf(
            session(correct = 0, questions = 0, newlySaved = 1),
            session(correct = 10, questions = 10, newlySaved = 1),
            session(correct = 9, questions = 10, newlySaved = 1),
        )

        assertEquals(3, next(current = 2, sessions = sessions))
    }

    @Test
    fun `a single quiz day cannot decide the window`() {
        // One perfect quiz plus two days that asked nothing used to read as "too easy", because
        // the empty days also diluted the save average. Two days of quiz evidence are required.
        val sessions = listOf(
            session(correct = 10, questions = 10, newlySaved = 6),
            session(correct = 0, questions = 0, newlySaved = 0),
            session(correct = 0, questions = 0, newlySaved = 0),
        )

        assertEquals(2, next(current = 2, sessions = sessions))
    }

    @Test
    fun `the level stays inside its bounds`() {
        val strong = List(3) { session(correct = 10, questions = 10, newlySaved = 0) }
        val weak = List(3) { session(correct = 0, questions = 10, newlySaved = 0) }

        assertEquals(3, next(current = 3, sessions = strong))
        assertEquals(1, next(current = 1, sessions = weak))
    }

    @Test
    fun `a difficulty stored outside the scale is clamped before it is used`() {
        val holding = List(3) { session(correct = 7, questions = 10, newlySaved = 4) }

        assertEquals(3, next(current = 9, sessions = holding))
        assertEquals(1, next(current = 0, sessions = holding))
    }
}
