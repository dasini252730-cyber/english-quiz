package com.englishquiz.app.domain.game

import com.englishquiz.app.domain.session.LearningSessionSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyMissionPolicyTest {
    @Test
    fun noSessionsMeansNothingDoneAndNoBonus() {
        val missions = DailyMissionPolicy.missions(emptyList())

        assertEquals(listOf(false, false, false), missions.map { it.done })
        assertEquals(0, DailyMissionPolicy.bonusPoints(emptyList()))
    }

    @Test
    fun oneAccurateSessionFinishesTheFirstTwoMissions() {
        val sessions = listOf(session(correct = 8, questions = 10, newlySaved = 2))

        val missions = DailyMissionPolicy.missions(sessions)

        assertEquals(listOf(true, true, false), missions.map { it.done })
        assertEquals(50, DailyMissionPolicy.bonusPoints(sessions))
    }

    @Test
    fun savedExpressionsAddUpAcrossTheDaysSessions() {
        val sessions = listOf(
            session(correct = 1, questions = 4, newlySaved = 2),
            session(correct = 2, questions = 4, newlySaved = 1),
        )

        val missions = DailyMissionPolicy.missions(sessions)

        assertEquals(listOf(true, false, true), missions.map { it.done })
        assertEquals(40, DailyMissionPolicy.bonusPoints(sessions))
    }

    @Test
    fun aTinyQuizDoesNotCountForTheAccuracyMission() {
        val sessions = listOf(session(correct = 2, questions = 2, newlySaved = 0))

        val missions = DailyMissionPolicy.missions(sessions)

        assertEquals(false, missions[1].done)
    }

    private fun session(correct: Int, questions: Int, newlySaved: Int) = LearningSessionSummary(
        quizCorrectCount = correct,
        quizQuestionCount = questions,
        newlySavedExpressionCount = newlySaved,
    )
}
