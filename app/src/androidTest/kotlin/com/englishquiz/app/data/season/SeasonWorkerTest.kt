package com.englishquiz.app.data.season

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.ListenableWorker
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import androidx.work.workDataOf
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.domain.season.SeasonWork
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The season chain as WorkManager runs it (백로그 075 follow-up): one step per job, the next step
 * appended by the job that finished, a failed step retried, and nothing more once the season is
 * complete. The pipeline is the test's own, over its own database and fake generators.
 */
@RunWith(AndroidJUnit4::class)
class SeasonWorkerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtures = SeasonFixtures(context)
    private val en = Language.ENGLISH

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(context, Configuration.Builder().setExecutor(SynchronousExecutor()).build())
        SeasonWorker.pipelineOf = { fixtures.pipeline }
    }

    @After
    fun tearDown() {
        SeasonWorker.pipelineOf = { null }
        fixtures.close()
    }

    private suspend fun runOnce(attempt: Int = 1): ListenableWorker.Result =
        TestListenableWorkerBuilder<SeasonWorker>(context).setInputData(workDataOf(SeasonWorker.KEY_LANGUAGE to en.wireValue)).setRunAttemptCount(attempt).build().doWork()

    @Test
    fun eachJobDoesOneStepAndAppendsTheNextUntilTheSeasonIsComplete() = runBlocking {
        val manager = WorkManager.getInstance(context)
        // The first job designs the season and appends the next step to the chain.
        assertEquals(ListenableWorker.Result.success(workDataOf(SeasonWorker.KEY_STEP to SeasonWork.DesignSeason(1).toString())), runOnce())
        assertEquals(1, fixtures.seasonRequests.size)
        val chained = manager.getWorkInfosForUniqueWorkFlow(SeasonScheduler.chainName(en)).first()
        assertEquals(1, chained.size)
        assertEquals(WorkInfo.State.ENQUEUED, chained.single().state)
        assertTrue(chained.single().tags.contains(SeasonScheduler.TAG))
        // Each following job makes one episode; after the seventh there is nothing to make and no next job.
        (1..7).forEach { number ->
            assertEquals(ListenableWorker.Result.success(workDataOf(SeasonWorker.KEY_STEP to SeasonWork.MakeEpisode(1, number).toString())), runOnce())
        }
        assertEquals(7, fixtures.episodeRequests.size)
        assertEquals(ListenableWorker.Result.success(workDataOf(SeasonWorker.KEY_STEP to SeasonWork.Nothing.toString())), runOnce())
        assertEquals(7, fixtures.episodeRequests.size)
        assertEquals(7, fixtures.seasons.observeUnreadCount(en).first())
    }

    @Test
    fun aFailedStepAsksForARetryAndGivesUpAfterTheLastAttempt() = runBlocking {
        runOnce()
        fixtures.failOnce += 1
        assertEquals(ListenableWorker.Result.retry(), runOnce(attempt = 1))
        // The retry re-plans the same piece and succeeds.
        assertEquals(ListenableWorker.Result.success(workDataOf(SeasonWorker.KEY_STEP to SeasonWork.MakeEpisode(1, 1).toString())), runOnce(attempt = 2))
        fixtures.failOnce += 2
        val last = runOnce(attempt = SeasonWorker.MAX_ATTEMPTS)
        assertTrue(last is ListenableWorker.Result.Failure)
        assertEquals("provider hiccup", last.outputData.getString(SeasonWorker.KEY_ERROR))
        assertEquals("provider hiccup", fixtures.pipeline.status.value[en]?.error)
    }
}
