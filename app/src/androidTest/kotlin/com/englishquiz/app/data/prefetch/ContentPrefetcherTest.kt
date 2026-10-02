package com.englishquiz.app.data.prefetch

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentGenerationRequest
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.LearningContent
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
    )

    @Test
    fun bothModesAreMadeForTomorrowOnceAtTheAdjustedLevelAndAFailureIsContained() = runBlocking {
        val repository = openRepository()
        val settings = settings()
        settings.saveAssessmentResult(assessmentLevel = 3)
        // Story is what the learner turned up (백로그 034); tomorrow's story must be made at that level.
        settings.setLevel(ContentMode.STORY, 5)
        // A phrase that comes due just after midnight belongs in tomorrow's passage.
        repository.saveExpression("hang out", "놀다", 1L, "Let's hang out.")
        repository.saveReviewProgress("hang out", 1L, TOMORROW_START + 1, 1, 0, false)
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

        assertEquals(2, requests.size)
        assertEquals(listOf(4, 5), requests.map { it.difficulty })
        assertEquals(listOf("hang out"), requests[0].reviewExpressions)
        assertNotNull(repository.findDailyContent(TOMORROW, ContentMode.CONVERSATION))
        assertNull(repository.findDailyContent(TOMORROW, ContentMode.STORY))
        // Tonight's row is untouched: tomorrow's passage is not today's.
        assertNull(repository.findDailyContent(TODAY, ContentMode.CONVERSATION))

        // Run again: the conversation is not made twice, the failed story is retried.
        storyFails = false
        assertEquals(listOf(ContentMode.STORY), prefetcher.prefetchTomorrowNow())
        assertEquals(3, requests.size)
        assertEquals(
            listOf(ContentMode.CONVERSATION, ContentMode.STORY),
            repository.listLibrary().filter { it.learningDate == TOMORROW }.map { ContentMode.valueOf(it.mode.uppercase()) },
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
            while (repository.listLibrary().count { it.learningDate == TOMORROW } < 2) delay(50)
        }
        // Two modes, one run: the second start did not add a third or fourth generation.
        assertEquals(2, calls.get())

        // With both rows in place, a fresh start has nothing to do and asks for nothing.
        prefetcher.prefetchTomorrow()
        delay(500)
        assertEquals(2, calls.get())
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
