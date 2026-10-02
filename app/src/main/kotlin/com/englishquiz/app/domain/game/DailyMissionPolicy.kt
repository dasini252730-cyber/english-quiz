package com.englishquiz.app.domain.game

import com.englishquiz.app.domain.session.LearningSessionSummary

/** One of the day's three goals (백로그 037) and whether today's sessions have met it. */
data class DailyMission(
    val kind: DailyMissionKind,
    val done: Boolean,
) {
    val title: String get() = kind.title
    val bonusPoints: Int get() = kind.bonusPoints
}

enum class DailyMissionKind(val title: String, val bonusPoints: Int) {
    FINISH_A_SESSION("오늘 학습 1회 완료", 20),
    QUIZ_80_PERCENT("퀴즈 정답률 80% 이상", 30),
    SAVE_THREE("새 표현 3개 저장", 20),
}

/**
 * Pure Kotlin: the day's missions are read off the sessions already recorded for that day, so
 * they need no store of their own and never disagree with the session history. A session must
 * have at least [MIN_QUIZ_QUESTIONS] questions to count for the percentage mission; two lucky
 * answers out of two are not a day of accuracy.
 */
object DailyMissionPolicy {
    const val MIN_QUIZ_QUESTIONS = 3
    const val TARGET_PERCENT = 80
    const val SAVE_TARGET = 3

    fun missions(todaySessions: List<LearningSessionSummary>): List<DailyMission> = listOf(
        DailyMission(DailyMissionKind.FINISH_A_SESSION, todaySessions.isNotEmpty()),
        DailyMission(
            DailyMissionKind.QUIZ_80_PERCENT,
            todaySessions.any { it.quizQuestionCount >= MIN_QUIZ_QUESTIONS && it.correctPercent >= TARGET_PERCENT },
        ),
        DailyMission(
            DailyMissionKind.SAVE_THREE,
            todaySessions.sumOf { it.newlySavedExpressionCount } >= SAVE_TARGET,
        ),
    )

    /** The bonus the day has earned so far: the sum over its finished missions. */
    fun bonusPoints(todaySessions: List<LearningSessionSummary>): Int =
        missions(todaySessions).filter { it.done }.sumOf { it.bonusPoints }
}
