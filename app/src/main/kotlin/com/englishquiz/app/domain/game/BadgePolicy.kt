package com.englishquiz.app.domain.game

/** The ten achievements (백로그 039), each with the line the badge screen shows under it. */
enum class Badge(val emoji: String, val title: String, val description: String) {
    FIRST_SESSION("🏁", "첫 걸음", "첫 학습을 끝냈어요"),
    STREAK_7("🔥", "일주일 연속", "7일 연속으로 학습했어요"),
    STREAK_30("🌟", "한 달 연속", "30일 연속으로 학습했어요"),
    SAVED_50("📚", "표현 수집가", "표현 50개를 저장했어요"),
    SAVED_100("🏛", "표현 도서관", "표현 100개를 저장했어요"),
    MASTERED_10("🍎", "첫 수확", "표현 10개를 암기 완료했어요"),
    MASTERED_50("🧺", "풍년", "표현 50개를 암기 완료했어요"),
    PERFECT_QUIZ("💯", "만점", "5문제 이상 퀴즈에서 전부 맞혔어요"),
    COMBO_10("⚡", "10 콤보", "한 퀴즈에서 10문제 연속 정답"),
    BOTH_MODES_ONE_DAY("🎭", "두 마리 토끼", "하루에 대화와 스토리를 모두 학습했어요"),
}

/** Everything the badges are judged on, gathered by the caller from the learning records. */
data class BadgeFacts(
    val sessionCount: Int = 0,
    /** The longest run of consecutive learning days ever, not just the current one. */
    val longestStreakDays: Int = 0,
    val savedCount: Int = 0,
    val masteredCount: Int = 0,
    /** True when some session with at least [BadgePolicy.PERFECT_MIN_QUESTIONS] questions had no wrong answer. */
    val hasPerfectQuiz: Boolean = false,
    val bestCombo: Int = 0,
    val bothModesInOneDay: Boolean = false,
)

/**
 * Pure Kotlin. Which badges the facts earn is re-derived every time, so nothing has to be
 * written when one is won; only which badges the learner has already looked at is stored
 * (`GameProgressRepository`), to tell a new badge from an old one.
 */
object BadgePolicy {
    const val PERFECT_MIN_QUESTIONS = 5

    fun earned(facts: BadgeFacts): Set<Badge> = Badge.entries.filterTo(LinkedHashSet()) { badge ->
        when (badge) {
            Badge.FIRST_SESSION -> facts.sessionCount >= 1
            Badge.STREAK_7 -> facts.longestStreakDays >= 7
            Badge.STREAK_30 -> facts.longestStreakDays >= 30
            Badge.SAVED_50 -> facts.savedCount >= 50
            Badge.SAVED_100 -> facts.savedCount >= 100
            Badge.MASTERED_10 -> facts.masteredCount >= 10
            Badge.MASTERED_50 -> facts.masteredCount >= 50
            Badge.PERFECT_QUIZ -> facts.hasPerfectQuiz
            Badge.COMBO_10 -> facts.bestCombo >= 10
            Badge.BOTH_MODES_ONE_DAY -> facts.bothModesInOneDay
        }
    }
}
