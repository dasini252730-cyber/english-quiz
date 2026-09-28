package com.englishquiz.app.assessment

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssessmentTest {
    @Test
    fun fixedQuestionSetCoversAllRequiredTypes() {
        assertEquals(8, Assessment.questions.size)
        assertTrue(Assessment.questions.all { it.options.size == 4 })
        assertEquals(
            setOf(QuestionType.MEANING, QuestionType.NATURAL_EXPRESSION, QuestionType.FILL_IN_THE_BLANK),
            Assessment.questions.map { it.type }.toSet(),
        )
    }

    @Test
    fun scoreBoundariesSelectTheExpectedInitialLevel() {
        assertEquals(AssessmentLevel.BEGINNER, Assessment.levelFor(scoreWithCorrectAnswers(3)))
        assertEquals(AssessmentLevel.INTERMEDIATE, Assessment.levelFor(scoreWithCorrectAnswers(4)))
        assertEquals(AssessmentLevel.INTERMEDIATE, Assessment.levelFor(scoreWithCorrectAnswers(6)))
        assertEquals(AssessmentLevel.ADVANCED, Assessment.levelFor(scoreWithCorrectAnswers(7)))
        assertEquals(AssessmentLevel.ADVANCED, Assessment.levelFor(Assessment.score(correctAnswers())))
    }

    @Test
    fun unansweredQuestionsAreScoredAsIncorrect() {
        assertEquals(0, Assessment.score(List(Assessment.questions.size) { null }))
        assertEquals(AssessmentLevel.BEGINNER, Assessment.levelFor(Assessment.score(emptyList())))
    }

    private fun scoreWithCorrectAnswers(count: Int): Int {
        val answers = Assessment.questions.mapIndexed { index, question ->
            if (index < count) question.correctOption else wrongOption(question)
        }
        return Assessment.score(answers)
    }

    private fun correctAnswers(): List<Int> = Assessment.questions.map { it.correctOption }

    private fun wrongOption(question: AssessmentQuestion): Int =
        (question.correctOption + 1) % question.options.size
}
