package com.englishquiz.app.domain.season

import org.junit.Assert.assertEquals
import org.junit.Test

/** 백로그 063: episodes are numbered 1..7 per season; the eighth story opens the next season. */
class SeasonPolicyTest {
    @Test
    fun theFirstStoryEverOpensSeasonOneAtEpisodeOne() {
        assertEquals(NextEpisode(1, 1, startsNewSeason = true), SeasonPolicy.nextEpisode(null, 0))
    }

    @Test
    fun episodesAreAssignedInOrderInsideASeason() {
        (0 until SeasonPolicy.EPISODES_PER_SEASON).forEach { have ->
            assertEquals(NextEpisode(3, have + 1, startsNewSeason = false), SeasonPolicy.nextEpisode(3, have))
        }
    }

    @Test
    fun afterEpisodeSevenTheNextSeasonStartsAtEpisodeOne() {
        assertEquals(NextEpisode(2, 1, startsNewSeason = true), SeasonPolicy.nextEpisode(1, 7))
        assertEquals(NextEpisode(2, 1, startsNewSeason = true), SeasonPolicy.nextEpisode(1, 9))
    }

    @Test
    fun theLabelNamesSeasonAndEpisode() {
        assertEquals("시즌 2 · 3/7", SeasonPolicy.label(2, 3))
    }
}
