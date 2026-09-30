package com.englishquiz.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.ai.ContentMode
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
    fun diagnosisStartsBothModesOnTheSameLevelWithAFreshWindow() = runBlocking {
        val repository = AppSettingsRepository(newDataStore())

        repository.saveAssessmentResult(3)

        val settings = repository.settings.first()
        assertEquals(true, settings.isAssessmentComplete)
        // 고급 starts at 4 of 5 (백로그 034): room to move either way.
        assertEquals(ModeLevel(level = 4), settings.levels[ContentMode.CONVERSATION])
        assertEquals(ModeLevel(level = 4), settings.levels[ContentMode.STORY])
    }

    @Test
    fun aLevelStoredBeforePerModeLevelsIsReadAsBothModesStartingLevel() = runBlocking {
        val dataStore = newDataStore()
        dataStore.edit { preferences ->
            preferences[booleanPreferencesKey("assessment_complete")] = true
            preferences[intPreferencesKey("current_difficulty")] = 1
        }

        val settings = AppSettingsRepository(dataStore).settings.first()

        assertEquals(2, settings.level(ContentMode.CONVERSATION))
        assertEquals(2, settings.level(ContentMode.STORY))
    }

    @Test
    fun theLearnerMovesOneModeAndTheOtherStaysWithinTheScale() = runBlocking {
        val repository = AppSettingsRepository(newDataStore())
        repository.saveAssessmentResult(2)

        repository.setLevel(ContentMode.STORY, 5)
        repository.setLevel(ContentMode.CONVERSATION, 0)

        val settings = repository.settings.first()
        assertEquals(5, settings.level(ContentMode.STORY))
        assertEquals(1, settings.level(ContentMode.CONVERSATION))
    }

    @Test
    fun aFinishedSessionCountsForItsModeAndARecommendationBecomesASuggestionNotAMove() = runBlocking {
        val repository = AppSettingsRepository(newDataStore())
        repository.saveAssessmentResult(2)

        // Holding the level keeps counting, so the window can fill up — for this mode only.
        repository.applyFinishedSession(ContentMode.STORY, sessionCompletedAtEpochMillis = 1L) { current, _ -> current }
        repository.applyFinishedSession(ContentMode.STORY, sessionCompletedAtEpochMillis = 2L) { current, _ -> current }
        var settings = repository.settings.first()
        assertEquals(ModeLevel(level = 3, sessionsSinceChange = 2), settings.levels[ContentMode.STORY])
        assertEquals(ModeLevel(level = 3, sessionsSinceChange = 0), settings.levels[ContentMode.CONVERSATION])

        // The decision sees the count it is about to be judged on; its answer is only suggested.
        var seenSessionsSinceChange = -1
        repository.applyFinishedSession(ContentMode.STORY, sessionCompletedAtEpochMillis = 3L) { current, sessionsSinceChange ->
            seenSessionsSinceChange = sessionsSinceChange
            current + 1
        }
        assertEquals(3, seenSessionsSinceChange)
        settings = repository.settings.first()
        assertEquals(ModeLevel(level = 3, sessionsSinceChange = 3, suggestedLevel = 4), settings.levels[ContentMode.STORY])

        // Accepting moves the level and restarts the window; the suggestion is gone.
        repository.acceptSuggestion(ContentMode.STORY)
        assertEquals(ModeLevel(level = 4), repository.settings.first().levels[ContentMode.STORY])
    }

    @Test
    fun aLaterVerdictToHoldWithdrawsAnUnansweredSuggestion() = runBlocking {
        val repository = AppSettingsRepository(newDataStore())
        repository.saveAssessmentResult(2)
        repository.applyFinishedSession(ContentMode.STORY, sessionCompletedAtEpochMillis = 1L) { current, _ -> current + 1 }
        assertEquals(4, repository.settings.first().levels.getValue(ContentMode.STORY).suggestedLevel)

        repository.applyFinishedSession(ContentMode.STORY, sessionCompletedAtEpochMillis = 2L) { current, _ -> current }

        assertEquals(ModeLevel(level = 3, sessionsSinceChange = 2), repository.settings.first().levels[ContentMode.STORY])
    }

    @Test
    fun dismissingASuggestionKeepsTheLevelAndRestartsTheWindow() = runBlocking {
        val repository = AppSettingsRepository(newDataStore())
        repository.saveAssessmentResult(2)
        repository.applyFinishedSession(ContentMode.CONVERSATION, sessionCompletedAtEpochMillis = 1L) { current, _ -> current - 1 }
        assertEquals(2, repository.settings.first().levels.getValue(ContentMode.CONVERSATION).suggestedLevel)

        repository.dismissSuggestion(ContentMode.CONVERSATION)

        assertEquals(ModeLevel(level = 3), repository.settings.first().levels[ContentMode.CONVERSATION])
    }

    @Test
    fun replayingTheSameSessionDoesNotCountItTwice() = runBlocking {
        // The result screen can re-run its effect after a rotation or process death, so the same
        // finished session may arrive more than once. Counting it twice would let a suggestion
        // come on a short window, which is exactly what the anti-oscillation rule exists to prevent.
        val repository = AppSettingsRepository(newDataStore())
        repository.saveAssessmentResult(2)

        repository.applyFinishedSession(ContentMode.CONVERSATION, sessionCompletedAtEpochMillis = 7L) { current, _ -> current }
        repository.applyFinishedSession(ContentMode.CONVERSATION, sessionCompletedAtEpochMillis = 7L) { current, _ -> current }
        repository.applyFinishedSession(ContentMode.CONVERSATION, sessionCompletedAtEpochMillis = 7L) { current, _ -> current }

        assertEquals(1, repository.settings.first().levels.getValue(ContentMode.CONVERSATION).sessionsSinceChange)

        repository.applyFinishedSession(ContentMode.CONVERSATION, sessionCompletedAtEpochMillis = 8L) { current, _ -> current }

        assertEquals(2, repository.settings.first().levels.getValue(ContentMode.CONVERSATION).sessionsSinceChange)
    }

    @Test
    fun aSessionBeforeAnyDiagnosisLeavesTheLevelUnset() = runBlocking {
        val repository = AppSettingsRepository(newDataStore())
        var decided = false

        repository.applyFinishedSession(ContentMode.STORY, sessionCompletedAtEpochMillis = 1L) { _, _ ->
            decided = true
            3
        }

        val settings = repository.settings.first()
        assertEquals(false, decided)
        assertEquals(emptyMap<ContentMode, ModeLevel>(), settings.levels)
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
