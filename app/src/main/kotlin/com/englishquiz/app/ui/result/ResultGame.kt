package com.englishquiz.app.ui.result

import com.englishquiz.app.data.local.LearningSessionEntity
import com.englishquiz.app.data.local.SavedExpressionEntity
import com.englishquiz.app.data.preferences.GameProgress
import com.englishquiz.app.domain.game.Badge
import com.englishquiz.app.domain.game.DailyMission
import com.englishquiz.app.domain.game.GameStatsPolicy
import com.englishquiz.app.domain.game.LearnerLevel

/** What the result screen says about the game after this session (백로그 037~039). */
data class ResultGame(
    /** Everything this session added to the total: quiz points plus any mission bonus it completed. */
    val pointsEarned: Int,
    val level: LearnerLevel,
    val leveledUp: Boolean,
    val missions: List<DailyMission>,
    val newBadges: Set<Badge>,
    /** The streak with shielded days counted (백로그 040). */
    val streakDays: Int,
)

/**
 * Compares the picture with and without the just-recorded session. The session is told apart by
 * its completion time, so a replay of the result screen after process death still gives the same
 * answer as the first run.
 */
internal fun resultGame(
    sessions: List<LearningSessionEntity>,
    expressions: List<SavedExpressionEntity>,
    progress: GameProgress,
    todayIso: String,
    sessionCompletedAtEpochMillis: Long,
): ResultGame {
    val before = GameStatsPolicy.compute(
        sessions.filter { it.completedAtEpochMillis != sessionCompletedAtEpochMillis },
        expressions,
        progress,
        todayIso,
    )
    val after = GameStatsPolicy.compute(sessions, expressions, progress, todayIso)
    return ResultGame(
        pointsEarned = (after.totalPoints - before.totalPoints).coerceAtLeast(0),
        level = after.level,
        leveledUp = after.level.level > before.level.level,
        missions = after.missions,
        newBadges = after.newBadges,
        streakDays = after.streakDays,
    )
}
