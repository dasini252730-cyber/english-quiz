package com.englishquiz.app.domain.game

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.IsoFields

/** What home says about the weekend boss (백로그 041) today. */
sealed interface BossStatus {
    /** It is the weekend and this week's boss has not been beaten yet. */
    data object Open : BossStatus

    /** Beaten already this week; back next Saturday. */
    data object Cleared : BossStatus

    /** A weekday: [daysUntil] days to Saturday. */
    data class Waiting(val daysUntil: Int) : BossStatus
}

/**
 * Pure Kotlin. The boss opens on Saturday and Sunday and can be cleared once per ISO week; the
 * record of a clear is the boss session itself (mode [SESSION_MODE]), so nothing else has to be
 * stored. [MAX_QUESTIONS] and [POINTS_MULTIPLIER] are what the quiz route is handed.
 */
object BossPolicy {
    const val SESSION_MODE = "boss"
    const val MAX_QUESTIONS = 15
    const val POINTS_MULTIPLIER = 2

    /** [bossDates] are the learning dates of earlier boss sessions. */
    fun status(todayIso: String, bossDates: Collection<String>): BossStatus {
        val today = parse(todayIso) ?: return BossStatus.Waiting(daysUntil = 0)
        val weekend = today.dayOfWeek == DayOfWeek.SATURDAY || today.dayOfWeek == DayOfWeek.SUNDAY
        if (!weekend) {
            return BossStatus.Waiting((DayOfWeek.SATURDAY.value - today.dayOfWeek.value + 7) % 7)
        }
        val clearedThisWeek = bossDates.mapNotNull(::parse).any { sameIsoWeek(it, today) }
        return if (clearedThisWeek) BossStatus.Cleared else BossStatus.Open
    }

    private fun sameIsoWeek(a: LocalDate, b: LocalDate): Boolean =
        a.get(IsoFields.WEEK_BASED_YEAR) == b.get(IsoFields.WEEK_BASED_YEAR) &&
            a.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR) == b.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)

    private fun parse(value: String): LocalDate? =
        try {
            LocalDate.parse(value)
        } catch (_: DateTimeParseException) {
            null
        }
}
