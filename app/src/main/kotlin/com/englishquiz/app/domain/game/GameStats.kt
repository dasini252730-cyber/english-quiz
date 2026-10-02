package com.englishquiz.app.domain.game

import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.local.LearningSessionEntity
import com.englishquiz.app.data.local.SavedExpressionEntity
import com.englishquiz.app.data.preferences.GameProgress
import com.englishquiz.app.domain.session.LearningSessionSummary
import com.englishquiz.app.domain.streak.StreakPolicy

/** Everything the game shows at once, derived from the learning records (백로그 037~041). */
data class GameStats(
    val totalPoints: Int,
    val level: LearnerLevel,
    val missions: List<DailyMission>,
    val earnedBadges: Set<Badge>,
    /** Earned badges the learner has not opened the badge screen on yet. */
    val newBadges: Set<Badge>,
    /** Consecutive learning days, with shielded days counted (백로그 040). */
    val streakDays: Int,
    val balance: Int,
    val shields: Int,
    val boss: BossStatus,
)

/**
 * Pure Kotlin. The whole picture is recomputed from the rows each time it is needed, so a crash
 * between two writes can never leave the level, the badges and the records disagreeing.
 */
object GameStatsPolicy {
    fun compute(
        sessions: List<LearningSessionEntity>,
        expressions: List<SavedExpressionEntity>,
        progress: GameProgress,
        todayIso: String,
    ): GameStats {
        val byDate = sessions.groupBy({ it.learningDate }, ::summaryOf)
        val totalPoints = LearnerLevelPolicy.totalXp(byDate)
        val learningDates = byDate.keys
        // Shielded days count for the streak badges as they do for the streak itself.
        val earned = BadgePolicy.earned(badgeFacts(sessions, expressions, learningDates + progress.shieldedDates, todayIso))
        return GameStats(
            totalPoints = totalPoints,
            level = LearnerLevelPolicy.of(totalPoints),
            missions = DailyMissionPolicy.missions(byDate[todayIso].orEmpty()),
            earnedBadges = earned,
            newBadges = earned.filterTo(LinkedHashSet()) { it.name !in progress.badgesSeen },
            streakDays = StreakPolicy.currentStreakDays(learningDates + progress.shieldedDates, todayIso),
            balance = progress.balance(totalPoints),
            shields = progress.shields,
            boss = BossPolicy.status(todayIso, sessions.filter { it.mode == BossPolicy.SESSION_MODE }.map { it.learningDate }),
        )
    }

    private fun badgeFacts(
        sessions: List<LearningSessionEntity>,
        expressions: List<SavedExpressionEntity>,
        learningDates: Collection<String>,
        todayIso: String,
    ) = BadgeFacts(
        sessionCount = sessions.size,
        longestStreakDays = StreakPolicy.longestStreakDays(learningDates, todayIso),
        savedCount = expressions.size,
        masteredCount = expressions.count { it.isMastered },
        hasPerfectQuiz = sessions.any {
            it.quizQuestionCount >= BadgePolicy.PERFECT_MIN_QUESTIONS && it.quizCorrectCount == it.quizQuestionCount
        },
        bestCombo = sessions.maxOfOrNull { it.maxCombo } ?: 0,
        bothModesInOneDay = sessions.groupBy { it.learningDate }.values.any { day ->
            day.map { it.mode }.containsAll(ContentMode.entries.map { it.wireValue })
        },
    )

    private fun summaryOf(session: LearningSessionEntity) = LearningSessionSummary(
        learnedExpressionCount = session.learnedExpressionCount,
        newlySavedExpressionCount = session.newlySavedExpressionCount,
        quizCorrectCount = session.quizCorrectCount,
        quizQuestionCount = session.quizQuestionCount,
        score = session.score,
        maxCombo = session.maxCombo,
    )
}
