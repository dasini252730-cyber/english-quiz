package com.englishquiz.app.domain.game

import com.englishquiz.app.data.local.LearningSessionEntity
import com.englishquiz.app.data.local.SavedExpressionEntity
import com.englishquiz.app.data.preferences.GameProgress
import org.junit.Assert.assertEquals
import org.junit.Test

class GameStatsPolicyTest {
    @Test
    fun aFreshInstallHasLevelOneNoBadgesAndAWaitingBoss() {
        val stats = GameStatsPolicy.compute(emptyList(), emptyList(), GameProgress(), FRIDAY)

        assertEquals(0, stats.totalPoints)
        assertEquals(1, stats.level.level)
        assertEquals(emptySet<Badge>(), stats.earnedBadges)
        assertEquals(0, stats.streakDays)
        assertEquals(BossStatus.Waiting(1), stats.boss)
        assertEquals(listOf(false, false, false), stats.missions.map { it.done })
    }

    @Test
    fun pointsIncludeTodaysMissionBonusAndNewBadgesExcludeTheOnesSeen() {
        val sessions = listOf(
            session(FRIDAY, "conversation", correct = 10, questions = 10, saved = 3, score = 200, combo = 10),
            session(FRIDAY, "story", correct = 5, questions = 10, score = 70),
        )
        val progress = GameProgress(badgesSeen = setOf(Badge.FIRST_SESSION.name))

        val stats = GameStatsPolicy.compute(sessions, emptyList(), progress, FRIDAY)

        // 270 from the quizzes plus all three missions (20 + 30 + 20).
        assertEquals(340, stats.totalPoints)
        assertEquals(2, stats.level.level)
        assertEquals(
            setOf(Badge.FIRST_SESSION, Badge.PERFECT_QUIZ, Badge.COMBO_10, Badge.BOTH_MODES_ONE_DAY),
            stats.earnedBadges,
        )
        assertEquals(setOf(Badge.PERFECT_QUIZ, Badge.COMBO_10, Badge.BOTH_MODES_ONE_DAY), stats.newBadges)
        assertEquals(1, stats.streakDays)
    }

    @Test
    fun shieldedDaysKeepTheStreakAndTheBalanceDeductsWhatWasSpent() {
        val sessions = listOf(
            session("2026-09-30", "story", correct = 3, questions = 5, score = 100),
            session(FRIDAY, "story", correct = 3, questions = 5, score = 100),
        )
        val progress = GameProgress(shields = 1, shieldedDates = setOf("2026-10-01"), pointsSpent = 150)

        val stats = GameStatsPolicy.compute(sessions, emptyList(), progress, FRIDAY)

        assertEquals(3, stats.streakDays)
        // 200 quiz points + 20 per day for finishing a session, minus 150 spent.
        assertEquals(240, stats.totalPoints)
        assertEquals(90, stats.balance)
        assertEquals(1, stats.shields)
    }

    @Test
    fun theBossIsClearedByABossSessionThisWeekAndMasteryCountsFromTheExpressions() {
        val sessions = listOf(session(SATURDAY, BossPolicy.SESSION_MODE, correct = 10, questions = 15, score = 300))
        val expressions = List(12) { index ->
            SavedExpressionEntity(
                id = index.toLong(),
                normalizedExpression = "e$index",
                displayExpression = "e$index",
                contextMeaning = "",
                firstSavedAtEpochMillis = 0L,
                isMastered = index < 10,
            )
        }

        val stats = GameStatsPolicy.compute(sessions, expressions, GameProgress(), SATURDAY)

        assertEquals(BossStatus.Cleared, stats.boss)
        assertEquals(setOf(Badge.FIRST_SESSION, Badge.MASTERED_10), stats.earnedBadges)
    }

    private fun session(
        date: String,
        mode: String,
        correct: Int,
        questions: Int,
        saved: Int = 0,
        score: Int = 0,
        combo: Int = 0,
    ) = LearningSessionEntity(
        learningDate = date,
        completedAtEpochMillis = 0L,
        learnedExpressionCount = questions,
        newlySavedExpressionCount = saved,
        quizCorrectCount = correct,
        quizQuestionCount = questions,
        mode = mode,
        score = score,
        maxCombo = combo,
    )

    private companion object {
        const val FRIDAY = "2026-10-02"
        const val SATURDAY = "2026-10-03"
    }
}
