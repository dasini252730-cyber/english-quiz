package com.englishquiz.app.domain.game

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Streak shields (백로그 040): a day the learner missed can be covered by a shield bought with
 * points, and a covered day counts as a learning day for `StreakPolicy`. Pure Kotlin; the caller
 * supplies today so the result is deterministic.
 */
object StreakShieldPolicy {
    const val PRICE_POINTS = 300

    /**
     * Yesterday, when it is the one missed day between the day before it and today, or null when
     * there is nothing to save: yesterday was learned or already shielded, or the day before it
     * was not (a shield covers one day, so a longer gap is gone). Only yesterday is ever named:
     * today is still open for learning, and an older gap was yesterday once, when it either got
     * its shield or there was none to give. That also keeps one visit from spending several
     * shields on several old gaps.
     */
    fun dayToShield(
        learningDates: Collection<String>,
        shieldedDates: Collection<String>,
        todayIso: String,
    ): String? {
        val today = parse(todayIso) ?: return null
        val covered = (learningDates + shieldedDates).mapNotNullTo(HashSet(), ::parse)
        val yesterday = today.minusDays(1)
        if (covered.contains(yesterday) || !covered.contains(yesterday.minusDays(1))) return null
        return yesterday.toString()
    }

    private fun parse(value: String): LocalDate? =
        try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
}
