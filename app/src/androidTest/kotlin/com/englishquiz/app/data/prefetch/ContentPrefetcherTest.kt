package com.englishquiz.app.data.prefetch

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentGenerationRequest
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.PassageSummary
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.preferences.AppSettingsRepository
import com.englishquiz.app.data.repository.LearningRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import com.englishquiz.app.data.repository.findDailyContent
import com.englishquiz.app.data.repository.listLibrary

/** 백로그 025: tomorrow's passages are made once, for both modes, at tomorrow's level and due list. */
@RunWith(AndroidJUnit4::class)
class ContentPrefetcherTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val settingsJob = SupervisorJob()
    private val settingsFile = File(context.filesDir, "test-prefetch-settings.preferences_pb")
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var database: LearningDatabase? = null

    @After
    fun cleanUp() {
        // JUnit needs a void method here, so the suspending part is wrapped rather than returned.
        runBlocking { settingsJob.cancelAndJoin() }
        backgroundScope.cancel()
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
        settingsFile.delete()
    }

    private fun openRepository(): LearningRepository {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        return LearningRepository(db)
    }

    private fun settings() = AppSettingsRepository(
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(settingsJob + Dispatchers.IO),
            produceFile = { settingsFile },
        ),
    )

    private fun passage(request: ContentGenerationRequest) = LearningContent(
        title = "Tomorrow ${request.mode.wireValue} at ${request.difficulty}",
        mode = request.mode,
        segments = listOf(ContentSegment("Alex", "We can pull it off together.")),
        expressions = listOf(ContentExpression("pull it off", "해내다", 0, 7, 18)),
        synopsis = "Alex pulled it off in a ${request.mode.wireValue}. What it cost is left open.",
        language = request.language,
    )

    @Test
    fun bothModesAreMadeForTomorrowOnceAtTheAdjustedLevelAndAFailureIsContained() = runBlocking {
        val repository = openRepository()
        val settings = settings()
        settings.saveAssessmentResult(assessmentLevel = 3)
        // Story is what the learner turned up (백로그 034); tomorrow's story must be made at that level.
        settings.setLevel(ContentMode.STORY, 5)
        // A phrase that comes due just after midnight belongs in tomorrow's passage.
        repository.saveExpression(Language.ENGLISH, "hang out", "놀다", 1L, "Let's hang out.")
        repository.saveReviewProgress(Language.ENGLISH, "hang out", 1L, TOMORROW_START + 1, 1, 0, false)
        val requests = mutableListOf<ContentGenerationRequest>()
        var storyFails = true
        val prefetcher = ContentPrefetcher(
            repository = repository,
            settings = settings,
            generate = { request ->
                requests += request
                if (request.mode == ContentMode.STORY && storyFails) error("provider hiccup")
                passage(request)
            },
            scope = this,
            zoneId = ZONE,
            nowEpochMillis = { TONIGHT },
        )

        assertEquals(listOf(ContentMode.CONVERSATION), prefetcher.prefetchTomorrowNow())

        // The story goes first (백로그 054), so the conversation can be set in its world.
        assertEquals(2, requests.size)
        assertEquals(listOf(5, 4), requests.map { it.difficulty })
        assertEquals(listOf("hang out"), requests[0].reviewExpressions)
        // The story failed, so the conversation had no companion to practise.
        assertNull(requests[1].companion)
        assertNotNull(repository.findDailyContent(Language.ENGLISH, TOMORROW, ContentMode.CONVERSATION))
        assertNull(repository.findDailyContent(Language.ENGLISH, TOMORROW, ContentMode.STORY))
        // Tonight's row is untouched: tomorrow's passage is not today's.
        assertNull(repository.findDailyContent(Language.ENGLISH, TODAY, ContentMode.CONVERSATION))

        // Run again: the conversation is not made twice, the failed story is retried.
        storyFails = false
        assertEquals(listOf(ContentMode.STORY), prefetcher.prefetchTomorrowNow())
        assertEquals(3, requests.size)
        // 백로그 054: the retried story hears of the conversation already made for tomorrow; there is
        // no earlier story to go on from.
        assertEquals(ContentMode.STORY, requests[2].mode)
        assertNull(requests[2].previousStory)
        assertEquals(
            PassageSummary(ContentMode.CONVERSATION, "Tomorrow conversation at 4", "Alex pulled it off in a conversation. What it cost is left open."),
            requests[2].companion,
        )
        assertEquals(
            listOf(ContentMode.CONVERSATION, ContentMode.STORY),
            repository.listLibrary(Language.ENGLISH).filter { it.learningDate == TOMORROW }.map { ContentMode.valueOf(it.mode.uppercase()) },
        )
    }

    @Test
    fun theBackgroundStartRunsOnceEvenWhenAskedTwiceAndOutlivesTheCaller() = runBlocking {
        val repository = openRepository()
        val settings = settings()
        val calls = AtomicInteger()
        val started = CountDownLatch(1)
        val release = CompletableDeferred<Unit>()
        val prefetcher = ContentPrefetcher(
            repository = repository,
            settings = settings,
            generate = { request ->
                calls.incrementAndGet()
                started.countDown()
                release.await() // hold the first run open while the second start is attempted
                passage(request)
            },
            scope = backgroundScope,
            zoneId = ZONE,
            nowEpochMillis = { TONIGHT },
        )

        prefetcher.prefetchTomorrow()
        assertTrue(started.await(5, TimeUnit.SECONDS))
        prefetcher.prefetchTomorrow() // ignored: one is already running
        release.complete(Unit)

        withTimeout(10_000) {
            while (repository.listLibrary(Language.ENGLISH).count { it.learningDate == TOMORROW } < 2) delay(50)
        }
        // Two modes, one run: the second start did not add a third or fourth generation.
        assertEquals(2, calls.get())

        // With both rows in place, a fresh start has nothing to do and asks for nothing.
        prefetcher.prefetchTomorrow()
        delay(500)
        assertEquals(2, calls.get())
    }

    @Test
    fun onlyTheSelectedLanguageIsMadeAheadAndItsRequestsCarryIt() = runBlocking {
        val repository = openRepository()
        val settings = settings()
        settings.saveAssessmentResult(assessmentLevel = 3)
        // 백로그 066: home is on Chinese tonight, with its story turned up to 5.
        settings.setLanguage(Language.CHINESE)
        settings.setLevel(ContentMode.STORY, 5, Language.CHINESE)
        settings.setLevel(ContentMode.CONVERSATION, 2, Language.CHINESE)
        // Both languages have a phrase due tomorrow; only the Chinese one belongs in a Chinese passage.
        repository.saveExpression(Language.ENGLISH, "hang out", "놀다", 1L, "Let's hang out.")
        repository.saveReviewProgress(Language.ENGLISH, "hang out", 1L, TOMORROW_START + 1, 1, 0, false)
        repository.saveExpression(Language.CHINESE, "打电话", "전화하다", 1L, "我们明天打电话。", pinyin = "dǎ diànhuà")
        repository.saveReviewProgress(Language.CHINESE, "打电话", 1L, TOMORROW_START + 1, 1, 0, false)
        val requests = mutableListOf<ContentGenerationRequest>()
        val prefetcher = ContentPrefetcher(
            repository = repository,
            settings = settings,
            generate = { request -> requests += request; passage(request) },
            scope = this,
            zoneId = ZONE,
            nowEpochMillis = { TONIGHT },
        )

        assertEquals(listOf(ContentMode.STORY, ContentMode.CONVERSATION), prefetcher.prefetchTomorrowNow())

        assertEquals(listOf(Language.CHINESE, Language.CHINESE), requests.map { it.language })
        // The Chinese levels the learner set — not English's diagnosed 4, nor the prefetcher's fallback 3.
        assertEquals(listOf(5, 2), requests.map { it.difficulty })
        assertEquals(listOf(listOf("打电话"), listOf("打电话")), requests.map { it.reviewExpressions })
        assertEquals(ContentMode.STORY, requests[1].companion?.mode)
        val tomorrow = repository.listLibrary(Language.CHINESE).filter { it.learningDate == TOMORROW }
        assertEquals(setOf("story", "conversation"), tomorrow.map { it.mode }.toSet())
        assertEquals(listOf("zh", "zh"), tomorrow.map { it.language })
        // Nothing English was made: that language is generated on its first tap instead.
        assertTrue(repository.listLibrary(Language.ENGLISH).isEmpty())
        assertNull(repository.findDailyContent(Language.ENGLISH, TOMORROW, ContentMode.STORY))
        assertNotNull(repository.findDailyContent(Language.CHINESE, TOMORROW, ContentMode.CONVERSATION))
    }

    private companion object {
        const val DATABASE_NAME = "test-content-prefetcher.db"
        val ZONE: ZoneId = ZoneId.of("Asia/Seoul")
        const val TODAY = "2026-09-26"
        const val TOMORROW = "2026-09-27"
        /** 2026-09-26 22:00 in Seoul: the learner finishing the day's session. */
        val TONIGHT: Long = ZonedDateTime.of(2026, 9, 26, 22, 0, 0, 0, ZONE).toInstant().toEpochMilli()
        /** The first moment of 2026-09-27 in Seoul. */
        val TOMORROW_START: Long = ZonedDateTime.of(2026, 9, 27, 0, 0, 0, 0, ZONE).toInstant().toEpochMilli()
    }
}
