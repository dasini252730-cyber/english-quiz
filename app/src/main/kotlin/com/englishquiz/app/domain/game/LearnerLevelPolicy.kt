package com.englishquiz.app.domain.game

import com.englishquiz.app.domain.session.LearningSessionSummary

/**
 * Where the learner stands (백로그 038). [xpIntoLevel] is how far into the level the total has
 * come and [xpForNextLevel] what the level needs in all, or null at the top.
 */
data class LearnerLevel(
    val level: Int,
    val title: String,
    val xpIntoLevel: Int,
    val xpForNextLevel: Int?,
) {
    /** 0..1 along the level's bar; a full bar at the top level. */
    val progress: Float
        get() = xpForNextLevel?.let { xpIntoLevel.toFloat() / it }?.coerceIn(0f, 1f) ?: 1f
}

/**
 * Pure Kotlin. Experience is the sum of every session's quiz score plus each day's mission bonus
 * (백로그 037), re-derived from the session history on every read, so it can never drift from the
 * records it summarises. Each level costs [STEP_BASE] more than the last plus [STEP_GROWTH] per
 * level already climbed: 300, 450, 600 … 1650 points, about five weeks of daily sessions to the
 * top. The titles follow the goal of 요구사항 2.1 — small talk with locals on a trip.
 */
object LearnerLevelPolicy {
    const val MAX_LEVEL = 10
    const val STEP_BASE = 300
    const val STEP_GROWTH = 150

    val TITLES: List<String> = listOf(
        "공항 도착",
        "입국 심사 통과",
        "호텔 체크인",
        "카페 첫 주문",
        "길 묻기 성공",
        "동네 단골",
        "바에서 농담 한마디",
        "파티 초대",
        "현지인 친구",
        "스몰토크 마스터",
    )

    /** What it takes to go from [level] to the next. */
    fun stepFrom(level: Int): Int = STEP_BASE + STEP_GROWTH * (level - 1)

    /** Sessions grouped by their learning date; the key is the `yyyy-MM-dd` string the row stores. */
    fun totalXp(sessionsByDate: Map<String, List<LearningSessionSummary>>): Int =
        sessionsByDate.values.sumOf { day -> day.sumOf { it.score } + DailyMissionPolicy.bonusPoints(day) }

    fun of(totalXp: Int): LearnerLevel {
        var level = 1
        var remaining = totalXp.coerceAtLeast(0)
        while (level < MAX_LEVEL && remaining >= stepFrom(level)) {
            remaining -= stepFrom(level)
            level++
        }
        return LearnerLevel(
            level = level,
            title = TITLES[level - 1],
            xpIntoLevel = remaining,
            xpForNextLevel = if (level < MAX_LEVEL) stepFrom(level) else null,
        )
    }
}
