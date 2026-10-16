package com.englishquiz.app.data.season

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.ai.CanonState
import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentGenerationRequest
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.EpisodePlan
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.SeasonBible
import com.englishquiz.app.data.ai.SeasonCharacter
import com.englishquiz.app.data.ai.SeasonRequest
import com.englishquiz.app.data.ai.SpeakerGender
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.data.repository.findDailyContent
import com.englishquiz.app.data.repository.listLibrary
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Episodes are numbered, read on a date, and never skipped or repeated (백로그 063). Every test
 * drives the coordinator the way the session route and the prefetcher do, with fake generators
 * that record what they were asked.
 */
@RunWith(AndroidJUnit4::class)
class SeasonCoordinatorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: LearningDatabase? = null
    private val storyRequests = mutableListOf<ContentGenerationRequest>()
    private val seasonRequests = mutableListOf<SeasonRequest>()
    private var failNextStory = false

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    private fun coordinator(): Pair<SeasonCoordinator, LearningRepository> {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)
        val coordinator = SeasonCoordinator(
            repository,
            generateContent = { request ->
                storyRequests += request
                if (failNextStory) {
                    failNextStory = false
                    error("provider hiccup")
                }
                episode(request)
            },
            generateSeason = { request -> seasonRequests += request; bible(request.seasonNumber) },
        )
        return coordinator to repository
    }

    @Test
    fun aNewSeasonStartsAtEpisodeOneAndEpisodesFollowInOrderIntoTheNextSeason() = runBlocking {
        val (seasons, repository) = coordinator()
        // Test 1 and 2: eight days of reading give season 1's seven episodes, then season 2 episode 1.
        (1..8).forEach { day ->
            val content = seasons.storyFor(date(day), 2, emptyList(), NOW + day * DAY)
            val expected = if (day <= 7) "S1E$day" else "S2E1"
            assertEquals(expected, content.title)
            assertEquals(if (day <= 7) "시즌 1 · $day/7" else "시즌 2 · 1/7", seasons.labelFor(date(day)))
        }
        assertEquals(listOf(1, 2), seasonRequests.map { it.seasonNumber })
        // Test 6: the second season was designed knowing the first's title.
        assertEquals(listOf("Season 1 bible"), seasonRequests[1].previousTitles)
        assertEquals((1..7).toList() + 1, storyRequests.map { it.season!!.episodeNumber })
        // Each read day's story is also the day's stored passage, so the library has eight stories.
        assertEquals(8, repository.listLibrary().count { it.mode == "story" })
    }

    @Test
    fun theSameDayNeverAdvancesAndAPrefetchedEpisodeIsReadNotSkipped() = runBlocking {
        val (seasons, repository) = coordinator()
        // Test 3: a second entry on the same date is the stored episode, no generation.
        val first = seasons.storyFor(date(1), 2, emptyList(), NOW)
        val again = seasons.storyFor(date(1), 2, emptyList(), NOW + 1_000)
        assertEquals(first, again)
        assertEquals(1, storyRequests.size)

        // Test 4: tonight's prefetch makes episode 2; it waits unread, so a second prefetch makes
        // nothing, and the next read day (even two days later) opens episode 2, not episode 3.
        assertTrue(seasons.prefetchNext(2, emptyList(), NOW + HOUR))
        assertFalse(seasons.prefetchNext(2, emptyList(), NOW + 2 * HOUR))
        assertTrue(seasons.hasUnread())
        assertEquals(2, storyRequests.size)
        // The level moved to 5 in between: the episode already made keeps level 2, no regeneration.
        val dayThree = seasons.storyFor(date(3), 5, emptyList(), NOW + 2 * DAY)
        assertEquals("S1E2", dayThree.title)
        assertEquals(2, storyRequests.size)
        assertEquals(listOf(2, 2), storyRequests.map { it.difficulty })
        // The episode after it is the first at the new level.
        assertEquals("S1E3", seasons.storyFor(date(4), 5, emptyList(), NOW + 3 * DAY).title)
        assertEquals(5, storyRequests.last().difficulty)
        assertFalse(seasons.hasUnread())
        assertNull(repository.findDailyContent(date(2), ContentMode.STORY))
        assertNotNull(repository.findDailyContent(date(3), ContentMode.STORY))
    }

    @Test
    fun aFailedGenerationLeavesTheEpisodeNumberForTheRetry() = runBlocking {
        val (seasons, _) = coordinator()
        seasons.storyFor(date(1), 2, emptyList(), NOW)
        // Test 5: the provider fails once; the retry asks for the same episode 2.
        failNextStory = true
        val failed = runCatching { seasons.prefetchNext(2, emptyList(), NOW + HOUR) }
        assertTrue(failed.isFailure)
        assertFalse(seasons.hasUnread())
        val retried = seasons.storyFor(date(2), 2, emptyList(), NOW + DAY)
        assertEquals("S1E2", retried.title)
        assertEquals(listOf(1, 2, 2), storyRequests.map { it.season!!.episodeNumber })
    }

    @Test
    fun theSummaryAndCanonOfOneEpisodeReachTheNextRequest() = runBlocking {
        val (seasons, _) = coordinator()
        seasons.storyFor(date(1), 2, emptyList(), NOW)
        seasons.storyFor(date(2), 2, emptyList(), NOW + DAY)
        seasons.storyFor(date(3), 2, emptyList(), NOW + 2 * DAY)
        // Test 7: episode 3's brief carries episode 2's synopsis, the merged canon and the plan.
        val third = storyRequests[2].season!!
        assertEquals("Synopsis of S1E2.", third.previousSummary)
        assertEquals(listOf("Event of S1E1", "Event of S1E2"), third.canon.events)
        assertEquals(listOf("Open after S1E2"), third.canon.openQuestions)
        assertEquals("Plan 3", third.episodePlan)
        assertEquals("Season 1 bible", third.title)
        assertEquals("Mina (owner; wants: keep the hostel; hides: owes money; cousin of Rui); Rui (driver; wants: quiet life; hides: saw it; cousin of Mina)", third.characters)
        // 백로그 065: the bible's engine and two lists travel with every episode, each in its own field.
        assertEquals("A. Clever mystery", third.engine)
        assertEquals(listOf("True: Mina sent the guest away."), third.whatIsTrue)
        assertEquals((1..7).map { "Believed after $it" }, third.readerBelieves)
        // The first episode saw the bible's own open questions and no previous summary.
        assertEquals("", storyRequests[0].season!!.previousSummary)
        assertEquals(listOf("Who?"), storyRequests[0].season!!.canon.openQuestions)
    }

    @Test
    fun theConversationAndTheLibraryReadTheSeasonWithoutMovingIt() = runBlocking {
        val (seasons, repository) = coordinator()
        seasons.storyFor(date(1), 2, emptyList(), NOW)
        seasons.prefetchNext(2, emptyList(), NOW + HOUR)
        val before = storyRequests.size
        // Test 8: a conversation hears today's episode with its people and place; nothing changes.
        val companion = checkNotNull(seasons.companionFor(date(1)))
        assertEquals("S1E1", companion.title)
        assertEquals("Mina (owner); Rui (driver)", companion.people)
        assertEquals("Lisbon", companion.place)
        assertEquals("S1E2", seasons.companionForNextRead()?.title)
        // Test 9: re-reading the stored passage (what the library does) touches no episode.
        assertNotNull(repository.findDailyContent(date(1), ContentMode.STORY))
        assertTrue(seasons.hasUnread())
        assertEquals(before, storyRequests.size)
        assertEquals("S1E2", seasons.storyFor(date(2), 2, emptyList(), NOW + DAY).title)
    }

    private fun episode(request: ContentGenerationRequest): LearningContent {
        val season = checkNotNull(request.season)
        val name = "S${seasonRequests.size}E${season.episodeNumber}"
        val sentence = "We can pull it off together in $name."
        return LearningContent(
            title = name,
            mode = ContentMode.STORY,
            segments = listOf(ContentSegment("Narrator", sentence)),
            expressions = listOf(ContentExpression("pull it off", "해내다", 0, 7, 18)),
            synopsis = "Synopsis of $name.",
            canon = CanonState(events = listOf("Event of $name"), openQuestions = listOf("Open after $name")),
        )
    }

    private fun bible(number: Int) = SeasonBible(
        title = "Season $number bible",
        setting = "Lisbon",
        conflict = "Who is sending the guests away?",
        characters = listOf(
            SeasonCharacter("Mina", SpeakerGender.FEMALE, "owner", "keep the hostel", "owes money", "cousin of Rui"),
            SeasonCharacter("Rui", SpeakerGender.MALE, "driver", "quiet life", "saw it", "cousin of Mina"),
        ),
        episodes = (1..7).map { EpisodePlan(it, "Episode $it", "Plan $it") },
        openQuestions = listOf("Who?"),
        engine = "A. Clever mystery",
        whatIsTrue = listOf("True: Mina sent the guest away."),
        readerBelieves = (1..7).map { "Believed after $it" },
    )

    private fun date(day: Int) = "2026-10-%02d".format(day)

    private companion object {
        const val DATABASE_NAME = "test-season-coordinator.db"
        const val NOW = 1_791_201_600_000L
        const val HOUR = 60 * 60 * 1000L
        const val DAY = 24 * HOUR
    }
}
