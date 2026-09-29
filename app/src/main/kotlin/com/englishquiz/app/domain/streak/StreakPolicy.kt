package com.englishquiz.app.domain.streak

import java.time.LocalDate
import java.time.format.DateTimeParseException

/**
 * Counts the current streak of consecutive local-midnight learning days. Pure Kotlin: no AI, no
 * reads of the system clock. The caller supplies "today" so the result stays deterministic and
 * testable, and stays consistent across app restarts and date changes.
 */
object StreakPolicy {
    /**
     * [learningDates] are `yyyy-MM-dd` strings (duplicates and unordered input are both fine; a
     * date repeated across several sessions still counts once). [todayIso] is the reference date
     * in the same format. Dates after [todayIso] are ignored (clock rollback guard), and any
     * string that fails to parse as a date is ignored rather than thrown.
     *
     * The streak runs backward from today if today has a session, otherwise from yesterday if
     * yesterday has one. If neither has a session, the streak is 0.
     */
    fun currentStreakDays(learningDates: Collection<String>, todayIso: String): Int {
        val today = parseDate(todayIso) ?: return 0
        val distinctDates = learningDates.mapNotNull(::parseDate)
            .filterTo(HashSet()) { !it.isAfter(today) }
        if (distinctDates.isEmpty()) return 0

        var cursor = when {
            distinctDates.contains(today) -> today
            distinctDates.contains(today.minusDays(1)) -> today.minusDays(1)
            else -> return 0
        }

        var streak = 0
        while (distinctDates.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    private fun parseDate(value: String): LocalDate? =
        try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
}
