package com.englishquiz.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppSettingsRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val handles = mutableListOf<Pair<Job, File>>()

    @After
    fun releaseDataStores() {
        // DataStore keeps a file "active" for the life of its scope, so a leaked scope makes the
        // next test on the same path throw. Cancelling here is what keeps these tests independent.
        handles.forEach { (job, file) ->
            job.cancel()
            file.delete()
        }
        handles.clear()
    }

    @Test
    fun readFailureIsNotConvertedToAssessmentIncomplete() = runBlocking {
        val repository = AppSettingsRepository(FailingDataStore())
        var failed = false

        try {
            repository.settings.first()
        } catch (_: IOException) {
            failed = true
        }

        assertTrue("DataStore errors must reach the UI error state", failed)
    }

    @Test
    fun diagnosisStoresTheLevelAndStartsAFreshAntiOscillationWindow() = runBlocking {
        val repository = AppSettingsRepository(newDataStore())

        repository.saveAssessmentResult(3)

        val settings = repository.settings.first()
        assertEquals(true, settings.isAssessmentComplete)
        assertEquals(3, settings.currentDifficulty)
        assertEquals(0, settings.sessionsSinceDifficultyChange)
    }

    @Test
    fun aFinishedSessionCountsUpAndMovingTheLevelResetsTheCount() = runBlocking {
        val repository = AppSettingsRepository(newDataStore())
        repository.saveAssessmentResult(2)

        // Holding the level keeps counting, so the window can fill up.
        repository.applyFinishedSession(sessionCompletedAtEpochMillis = 1L) { current, _ -> current }
        repository.applyFinishedSession(sessionCompletedAtEpochMillis = 2L) { current, _ -> current }
        var settings = repository.settings.first()
        assertEquals(2, settings.currentDifficulty)
        assertEquals(2, settings.sessionsSinceDifficultyChange)

        // The decision sees the count it is about to be judged on.
        var seenSessionsSinceChange = -1
        repository.applyFinishedSession(sessionCompletedAtEpochMillis = 3L) { current, sessionsSinceChange ->
            seenSessionsSinceChange = sessionsSinceChange
            current + 1
        }
        assertEquals(3, seenSessionsSinceChange)

        settings = repository.settings.first()
        assertEquals(3, settings.currentDifficulty)
        assertEquals(0, settings.sessionsSinceDifficultyChange)
    }

    @Test
    fun replayingTheSameSessionDoesNotCountItTwice() = runBlocking {
        // The result screen can re-run its effect after a rotation or process death, so the same
        // finished session may arrive more than once. Counting it twice would let the level move
        // on a short window, which is exactly what the anti-oscillation rule exists to prevent.
        val repository = AppSettingsRepository(newDataStore())
        repository.saveAssessmentResult(2)

        repository.applyFinishedSession(sessionCompletedAtEpochMillis = 7L) { current, _ -> current }
        repository.applyFinishedSession(sessionCompletedAtEpochMillis = 7L) { current, _ -> current }
        repository.applyFinishedSession(sessionCompletedAtEpochMillis = 7L) { current, _ -> current }

        assertEquals(1, repository.settings.first().sessionsSinceDifficultyChange)

        repository.applyFinishedSession(sessionCompletedAtEpochMillis = 8L) { current, _ -> current }

        assertEquals(2, repository.settings.first().sessionsSinceDifficultyChange)
    }

    @Test
    fun aSessionBeforeAnyDiagnosisLeavesTheLevelUnset() = runBlocking {
        val repository = AppSettingsRepository(newDataStore())
        var decided = false

        repository.applyFinishedSession(sessionCompletedAtEpochMillis = 1L) { _, _ ->
            decided = true
            3
        }

        val settings = repository.settings.first()
        assertEquals(false, decided)
        assertEquals(null, settings.currentDifficulty)
        assertEquals(0, settings.sessionsSinceDifficultyChange)
    }

    private fun newDataStore(): DataStore<Preferences> {
        val file = File(context.filesDir, "app-settings-${System.nanoTime()}.preferences_pb")
        val job = SupervisorJob()
        handles += job to file
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { file },
        )
    }

    private class FailingDataStore : DataStore<Preferences> {
        override val data = flow<Preferences> { throw IOException("settings unavailable") }

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences = throw IOException("settings unavailable")
    }
}
