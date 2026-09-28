package com.englishquiz.app.domain.library

import com.englishquiz.app.data.local.LibraryItem
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** A library row with what the list needs to say about it: how old it is, and whether it is due a re-read. */
data class LibraryEntry(
    val item: LibraryItem,
    val daysAgo: Long,
    /** Two to three weeks after the first read is when reading a passage again pays off most. */
    val isSweetSpot: Boolean,
)

/**
 * Orders the library for the "read again" list (백로그 026). Only past days are offered — today's
 * passage is reached from the home cards, and a day generated ahead of time (백로그 025) is not
 * to be read early. Passages in the two-to-three-week window come first, then the rest newest
 * first. Pure Kotlin, so the rule is proved on the JVM.
 */
object LibraryPolicy {
    val SWEET_SPOT_DAYS: LongRange = 14L..21L

    fun arrange(items: List<LibraryItem>, today: LocalDate): List<LibraryEntry> = items
        .mapNotNull { item ->
            val date = runCatching { LocalDate.parse(item.learningDate) }.getOrNull() ?: return@mapNotNull null
            val daysAgo = ChronoUnit.DAYS.between(date, today)
            if (daysAgo <= 0) null else LibraryEntry(item, daysAgo, daysAgo in SWEET_SPOT_DAYS)
        }
        .sortedWith(compareByDescending<LibraryEntry> { it.isSweetSpot }.thenBy { it.daysAgo }.thenBy { it.item.mode })
}
