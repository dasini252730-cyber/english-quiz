package com.englishquiz.app.domain.game

import com.englishquiz.app.domain.session.LearningSessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LearnerLevelPolicyTest {
    @Test
    fun startsAtTheAirportWithAnEmptyBar() {
        val level = LearnerLevelPolicy.of(0)

        assertEquals(LearnerLevel(1, "공항 도착", xpIntoLevel = 0, xpForNextLevel = 300), level)
        assertEquals(0f, level.progress)
    }

    @Test
    fun eachLevelCostsMoreThanTheLast() {
        assertEquals(1, LearnerLevelPolicy.of(299).level)
        assertEquals(2, LearnerLevelPolicy.of(300).level)
        // 300 + 450 = 750 reaches level 3 with 10 to spare on a 600-point bar.
        assertEquals(LearnerLevel(3, "호텔 체크인", xpIntoLevel = 10, xpForNextLevel = 600), LearnerLevelPolicy.of(760))
    }

    @Test
    fun theTopLevelHasNoNextStepAndAFullBar() {
        val total = (1 until LearnerLevelPolicy.MAX_LEVEL).sumOf(LearnerLevelPolicy::stepFrom)

        val top = LearnerLevelPolicy.of(total + 999)

        assertEquals(LearnerLevelPolicy.MAX_LEVEL, top.level)
        assertEquals("스몰토크 마스터", top.title)
        assertNull(top.xpForNextLevel)
        assertEquals(1f, top.progress)
    }

    @Test
    fun totalXpAddsSessionScoresAndEachDaysMissionBonus() {
        val sessions = mapOf(
            // 120 + 40 points, plus the day's bonus: finished (20) + 80% (30) + 3 saved (20).
            "2026-10-01" to listOf(
                LearningSessionSummary(quizCorrectCount = 9, quizQuestionCount = 10, newlySavedExpressionCount = 3, score = 120),
                LearningSessionSummary(quizCorrectCount = 2, quizQuestionCount = 4, score = 40),
            ),
            // 50 points and only the "finished" bonus.
            "2026-10-02" to listOf(LearningSessionSummary(quizCorrectCount = 1, quizQuestionCount = 4, score = 50)),
        )

        assertEquals(160 + 70 + 50 + 20, LearnerLevelPolicy.totalXp(sessions))
    }
}
