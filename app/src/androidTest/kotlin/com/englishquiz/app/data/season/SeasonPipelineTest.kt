package com.englishquiz.app.data.season

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.season.SeasonFixtures.Companion.DAY
import com.englishquiz.app.data.season.SeasonFixtures.Companion.date
import com.englishquiz.app.domain.season.SeasonWork
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The background pipeline (백로그 075): a season is designed, its episodes made one call at a time
 * with their conversations, each stored as it arrives; the next season starts when two episodes
 * wait unread; a failure stops the run where it is and the next run resumes there.
 */
@RunWith(AndroidJUnit4::class)
class SeasonPipelineTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtures = SeasonFixtures(context)
    private val en = Language.ENGLISH

    @After
    fun tearDown() = fixtures.close()

    @Test
    fun aFreshLanguageGetsItsDesignAndSevenEpisodesAndTheFirstIsReadableAtOnce() = runBlocking {
        val done = fixtures.pipeline.runUntilSettled(en)
        assertEquals(listOf<SeasonWork>(SeasonWork.DesignSeason(1)) + (1..7).map { SeasonWork.MakeEpisode(1, it) }, done)
        assertEquals(listOf(1), fixtures.seasonRequests.map { it.seasonNumber })
        // Each call asks for one episode, with every earlier episode's full text and the levels of both modes.
        assertEquals((1..7).map { listOf(it) }, fixtures.episodeRequests.map { it.episodes })
        assertEquals((0..6).toList(), fixtures.episodeRequests.map { it.previousEpisodes.size })
        assertEquals("Narrator: We can pull it off together in S1E1.", fixtures.episodeRequests[1].previousEpisodes.single().text)
        assertEquals(2 to 3, fixtures.episodeRequests[0].let { it.storyDifficulty to it.conversationDifficulty })
        assertNull(fixtures.episodeRequests[0].story)
        // The canon of episode 1 reaches episode 2's request.
        assertEquals(listOf("Event of episode 1"), fixtures.episodeRequests[1].canon.events)
        // Both passages of episode 1 are stored and read without another call; the season is complete.
        assertEquals("S1E1", fixtures.seasons.passageFor(en, ContentMode.STORY, date(1), fixtures.now)?.title)
        assertEquals("S1E1 talk", fixtures.seasons.passageFor(en, ContentMode.CONVERSATION, date(1), fixtures.now)?.title)
        assertEquals(7, fixtures.episodeRequests.size)
        assertEquals(6, fixtures.seasons.observeUnreadCount(en).first())
        assertEquals(SeasonWork.Nothing, fixtures.pipeline.plan(en))
    }

    @Test
    fun theNextSeasonIsDesignedTheMomentTwoEpisodesWaitUnread() = runBlocking {
        fixtures.pipeline.runUntilSettled(en)
        // Reading episodes 1 to 4 leaves three unread: nothing to make.
        (1..4).forEach { day ->
            fixtures.now = SeasonFixtures.NOW + day * DAY
            fixtures.seasons.passageFor(en, ContentMode.STORY, date(day), fixtures.now)
            assertEquals(SeasonWork.Nothing, fixtures.pipeline.plan(en))
        }
        // Reading the fifth leaves two: the next season's design is the next piece of work.
        fixtures.seasons.passageFor(en, ContentMode.STORY, date(5), fixtures.now + DAY)
        assertEquals(SeasonWork.DesignSeason(2), fixtures.pipeline.plan(en))
        // Every read asked the pipeline to look; the coordinator never generated anything itself.
        assertEquals(5, fixtures.workRequests.size)
        val done = fixtures.pipeline.runUntilSettled(en)
        assertEquals(listOf<SeasonWork>(SeasonWork.DesignSeason(2)) + (1..7).map { SeasonWork.MakeEpisode(2, it) }, done)
        assertEquals(listOf("Season 1 bible"), fixtures.seasonRequests[1].previousTitles)
        // Season 1's remaining episodes are still read first; season 2 waits behind them.
        assertEquals("S1E6", fixtures.seasons.passageFor(en, ContentMode.STORY, date(6), fixtures.now + 2 * DAY)?.title)
        assertEquals(1 + 7, fixtures.seasons.observeUnreadCount(en).first())
    }

    @Test
    fun aFailedCallKeepsTheEpisodesBeforeItAndTheNextRunResumesAtTheSameNumber() = runBlocking {
        fixtures.failOnce += 3
        val failed = runCatching { fixtures.pipeline.runUntilSettled(en) }
        assertTrue(failed.isFailure)
        assertEquals(2, fixtures.dao.listEpisodes(checkNotNull(fixtures.dao.latestSeason(en.wireValue)).id).size)
        assertEquals(SeasonWork.MakeEpisode(1, 3), fixtures.pipeline.plan(en))
        // Episode 1 is readable meanwhile.
        assertEquals("S1E1", fixtures.seasons.passageFor(en, ContentMode.STORY, date(1), fixtures.now)?.title)
        val resumed = fixtures.pipeline.runUntilSettled(en)
        assertEquals((3..7).map { SeasonWork.MakeEpisode(1, it) }, resumed)
        assertEquals(listOf(1, 2, 3, 3, 4, 5, 6, 7), fixtures.episodeRequests.map { it.episodes.single() })
    }

    @Test
    fun aConversationTheServerDroppedOrAnOlderEpisodeNeverHadIsMadeWithTheStoryGiven() = runBlocking {
        // An episode from before 075: a story, no conversation. The conversation comes first, with the story given.
        fixtures.storeLegacyEpisode(en, seasonNumber = 1, number = 1)
        assertEquals(SeasonWork.MakeConversation(1, 1), fixtures.pipeline.plan(en))
        assertNull(fixtures.seasons.passageFor(en, ContentMode.CONVERSATION, date(1), fixtures.now))
        assertEquals(listOf(en), fixtures.workRequests)
        fixtures.dropConversation += 3
        val done = fixtures.pipeline.runUntilSettled(en)
        assertEquals(
            listOf<SeasonWork>(SeasonWork.MakeConversation(1, 1), SeasonWork.MakeEpisode(1, 2), SeasonWork.MakeEpisode(1, 3), SeasonWork.MakeConversation(1, 3)) +
                (4..7).map { SeasonWork.MakeEpisode(1, it) },
            done,
        )
        val given = fixtures.episodeRequests.filter { it.story != null }
        assertEquals(listOf(1, 3), given.map { it.episodes.single() })
        assertEquals("S1E1", given[0].story?.title)
        // The fake names a season by how many designs it made; this season was stored by hand, so its episodes read "S0".
        assertEquals("We can pull it off together in S0E3.", given[1].story?.segments?.single()?.text)
        // Both conversations are now stored and the day's conversation opens without a call.
        assertEquals("S0E1 talk", fixtures.seasons.passageFor(en, ContentMode.CONVERSATION, date(1), fixtures.now)?.title)
        assertNotNull(fixtures.dao.findEpisode(checkNotNull(fixtures.dao.latestSeason(en.wireValue)).id, 3)?.conversationJson)
    }

    @Test
    fun ensureRunsInTheBackgroundOncePerLanguageAndFillsTheUnreadQueue() = runBlocking {
        fixtures.pipeline.ensure(en)
        fixtures.pipeline.ensure(en)
        withTimeout(20_000) { fixtures.seasons.observeUnreadCount(en).first { it == 7 } }
        // Two ensures, one run: one design and seven episodes.
        assertEquals(1, fixtures.seasonRequests.size)
        assertEquals(7, fixtures.episodeRequests.size)
        assertTrue(fixtures.seasons.observeEpisodeState(en).first() >= 14)
        // Chinese has its own seasons and nothing was made for it.
        assertEquals(0, fixtures.seasons.observeUnreadCount(Language.CHINESE).first())
        assertEquals(SeasonWork.DesignSeason(1), fixtures.pipeline.plan(Language.CHINESE))
    }
}
