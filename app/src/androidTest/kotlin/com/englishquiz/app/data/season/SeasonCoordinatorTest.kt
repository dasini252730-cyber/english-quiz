package com.englishquiz.app.data.season

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.repository.findDailyContent
import com.englishquiz.app.data.repository.listLibrary
import com.englishquiz.app.data.season.SeasonFixtures.Companion.DAY
import com.englishquiz.app.data.season.SeasonFixtures.Companion.date
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reading side (백로그 063/075): an episode is consumed as a unit on the day either passage is
 * opened, both passages become the day's, the same day never advances, and nothing is generated
 * here: with no episode ready the caller gets null and the pipeline is asked.
 */
@RunWith(AndroidJUnit4::class)
class SeasonCoordinatorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtures = SeasonFixtures(context)
    private val en = Language.ENGLISH

    @After
    fun tearDown() = fixtures.close()

    @Test
    fun openingTheConversationFirstConsumesTheSameEpisodeAsTheStoryAndTheDayNeverAdvances() = runBlocking {
        fixtures.pipeline.runUntilSettled(en)
        val calls = fixtures.episodeRequests.size
        // Conversation first: episode 1 is the day's, and its story is already the day's stored story (the library has it
        // even if the story is never opened), and follows on the same day.
        assertEquals("S1E1 talk", fixtures.seasons.passageFor(en, ContentMode.CONVERSATION, date(1), fixtures.now)?.title)
        assertEquals("S1E1", fixtures.repository.findDailyContent(en, date(1), ContentMode.STORY)?.title)
        assertEquals(2, fixtures.repository.listLibrary(en).size)
        assertEquals("S1E1", fixtures.seasons.passageFor(en, ContentMode.STORY, date(1), fixtures.now + 1_000)?.title)
        assertEquals("시즌 1 · 1/7", fixtures.seasons.labelFor(en, date(1)))
        // A second entry on the same date is the stored passage; the next day moves to episode 2, story first this time.
        assertEquals("S1E1 talk", fixtures.seasons.passageFor(en, ContentMode.CONVERSATION, date(1), fixtures.now + 2_000)?.title)
        assertEquals("S1E2", fixtures.seasons.passageFor(en, ContentMode.STORY, date(2), fixtures.now + DAY)?.title)
        assertEquals("S1E2 talk", fixtures.seasons.passageFor(en, ContentMode.CONVERSATION, date(2), fixtures.now + DAY)?.title)
        assertEquals("시즌 1 · 2/7", fixtures.seasons.labelFor(en, date(2)))
        assertEquals(calls, fixtures.episodeRequests.size)
        // Both passages of each day are the day's stored passages: the library lists four.
        assertEquals(4, fixtures.repository.listLibrary(en).size)
        assertNotNull(fixtures.repository.findDailyContent(en, date(1), ContentMode.STORY))
        assertNotNull(fixtures.repository.findDailyContent(en, date(2), ContentMode.CONVERSATION))
        assertEquals(5, fixtures.seasons.observeUnreadCount(en).first())
    }

    @Test
    fun withNoEpisodeReadyNothingIsGeneratedAndThePipelineIsAsked() = runBlocking {
        fixtures.autoEnsure = true
        assertNull(fixtures.seasons.passageFor(en, ContentMode.STORY, date(1), fixtures.now))
        assertNull(fixtures.seasons.passageFor(en, ContentMode.CONVERSATION, date(1), fixtures.now))
        assertEquals(listOf(en, en), fixtures.workRequests)
        assertNull(fixtures.seasons.labelFor(en, date(1)))
        assertEquals(false, fixtures.seasons.hasUnread(en))
        // The pipeline was started by those asks: it fills the queue in the background.
        withTimeout(20_000) { fixtures.seasons.observeUnreadCount(en).first { it == 7 } }
        assertEquals("S1E1", fixtures.seasons.passageFor(en, ContentMode.STORY, date(1), fixtures.now)?.title)
    }

    @Test
    fun eachLanguageReadsItsOwnSeason() = runBlocking {
        fixtures.pipeline.runUntilSettled(en)
        fixtures.pipeline.runUntilSettled(Language.CHINESE)
        assertEquals(listOf(en to 1, Language.CHINESE to 1), fixtures.seasonRequests.map { it.language to it.seasonNumber })
        assertEquals("S1E1", fixtures.seasons.passageFor(en, ContentMode.STORY, date(1), fixtures.now)?.title)
        assertEquals("S2E1", fixtures.seasons.passageFor(Language.CHINESE, ContentMode.STORY, date(1), fixtures.now)?.title)
        assertEquals(Language.CHINESE, fixtures.seasons.passageFor(Language.CHINESE, ContentMode.CONVERSATION, date(1), fixtures.now)?.language)
        assertEquals(6, fixtures.seasons.observeUnreadCount(en).first())
        assertEquals(6, fixtures.seasons.observeUnreadCount(Language.CHINESE).first())
        assertEquals(listOf("en"), fixtures.repository.listLibrary(en).map { it.language }.distinct())
        assertEquals(listOf("zh"), fixtures.repository.listLibrary(Language.CHINESE).map { it.language }.distinct())
    }
}
