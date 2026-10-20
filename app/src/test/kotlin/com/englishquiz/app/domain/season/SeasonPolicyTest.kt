package com.englishquiz.app.domain.season

import org.junit.Assert.assertEquals
import org.junit.Test

/** 백로그 063/075: episodes are numbered 1..7 per season, and the pipeline's plan says what to make next. */
class SeasonPolicyTest {
    /** 백로그 075: what the pipeline makes next, from the latest season's episodes. */
    @Test
    fun thePlanDesignsEpisodesFillsConversationsThenDesignsTheNextSeasonNearTheEnd() {
        val today = "2026-10-18"
        fun made(count: Int, read: Int) = (1..count).map { EpisodeStatus(it, if (it <= read) "2026-10-%02d".format(it) else null, hasConversation = true) }

        assertEquals(SeasonWork.DesignSeason(1), SeasonPolicy.nextWork(null, emptyList(), today))
        assertEquals(SeasonWork.MakeEpisode(1, 1), SeasonPolicy.nextWork(1, emptyList(), today))
        assertEquals(SeasonWork.MakeEpisode(1, 3), SeasonPolicy.nextWork(1, made(2, read = 2), today))
        // A complete season with more than two unread episodes needs nothing yet.
        assertEquals(SeasonWork.Nothing, SeasonPolicy.nextWork(1, made(7, read = 4), today))
        // Two unread (the fifth was just read): the next season is designed now.
        assertEquals(SeasonWork.DesignSeason(2), SeasonPolicy.nextWork(1, made(7, read = 5), today))
        assertEquals(SeasonWork.DesignSeason(2), SeasonPolicy.nextWork(1, made(7, read = 7), today))
        // A conversation an unread or today's episode lacks comes before anything else; a past day's does not.
        val legacy = listOf(EpisodeStatus(1, "2026-10-01", hasConversation = false), EpisodeStatus(2, today, hasConversation = false), EpisodeStatus(3, null, hasConversation = false))
        assertEquals(SeasonWork.MakeConversation(1, 2), SeasonPolicy.nextWork(1, legacy, today))
        assertEquals(SeasonWork.MakeConversation(1, 3), SeasonPolicy.nextWork(1, legacy.drop(2), today))
        assertEquals(SeasonWork.MakeEpisode(1, 2), SeasonPolicy.nextWork(1, legacy.take(1), today))
        assertEquals(2, SeasonPolicy.PREFETCH_WHEN_UNREAD_AT_MOST)
    }

    @Test
    fun theLabelNamesSeasonAndEpisode() {
        assertEquals("시즌 2 · 3/7", SeasonPolicy.label(2, 3))
    }
}
