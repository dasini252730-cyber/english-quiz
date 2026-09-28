package com.englishquiz.app.domain.library

import com.englishquiz.app.data.local.LibraryItem
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class LibraryPolicyTest {
    private val today = LocalDate.of(2026, 9, 26)

    private fun item(date: String, mode: String = "conversation", title: String = date) =
        LibraryItem(learningDate = date, mode = mode, title = title, createdAtEpochMillis = 0)

    @Test
    fun onlyPastDaysAreOfferedAndTheSweetSpotComesFirst() {
        val arranged = LibraryPolicy.arrange(
            listOf(
                item("2026-09-27"), // tomorrow, generated ahead: not for reading early
                item("2026-09-26"), // today: reached from the home cards
                item("2026-09-25"), // yesterday
                item("2026-09-10"), // 16 days: the window
                item("2026-08-30"), // 27 days: past the window
                item("2026-09-05"), // 21 days: still the window
            ),
            today,
        )

        assertEquals(
            listOf("2026-09-10", "2026-09-05", "2026-09-25", "2026-08-30"),
            arranged.map { it.item.learningDate },
        )
        assertEquals(listOf(true, true, false, false), arranged.map { it.isSweetSpot })
        assertEquals(16L, arranged.first().daysAgo)
    }

    @Test
    fun theTwoModesOfOneDayAreListedTogetherInAStableOrder() {
        val arranged = LibraryPolicy.arrange(
            listOf(item("2026-09-20", mode = "story"), item("2026-09-20", mode = "conversation")),
            today,
        )

        assertEquals(listOf("conversation", "story"), arranged.map { it.item.mode })
    }

    @Test
    fun aRowWithAnUnreadableDateIsLeftOutRatherThanCrashingTheList() {
        val arranged = LibraryPolicy.arrange(listOf(item("not-a-date"), item("2026-09-20")), today)

        assertEquals(listOf("2026-09-20"), arranged.map { it.item.learningDate })
    }
}
