package com.englishquiz.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.Language
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 백로그 066: the settings carry the levels of the selected language. English keeps the keys and
 * the diagnosis from before; Chinese has no diagnosis, starts at 3 and keeps its own windows.
 */
@RunWith(AndroidJUnit4::class)
class AppSettingsLanguageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val handles = mutableListOf<Pair<Job, File>>()

    @After
    fun releaseDataStores() {
        // Join before deleting: a write still in flight would otherwise leave the file behind.
        handles.forEach { (job, file) ->
            runBlocking { job.cancelAndJoin() }
            file.delete()
        }
        handles.clear()
    }

    @Test
    fun englishIsTheDefaultAndChineseStartsAtThreeWithoutADiagnosis() = runBlocking {
        val repository = AppSettingsRepository(newDataStore())
        assertEquals(Language.ENGLISH, repository.settings.first().language)
        repository.saveAssessmentResult(2)
        assertEquals(mapOf(ContentMode.CONVERSATION to ModeLevel(3), ContentMode.STORY to ModeLevel(3)), repository.settings.first().levels)

        repository.setLanguage(Language.CHINESE)

        val chinese = repository.settings.first()
        assertEquals(Language.CHINESE, chinese.language)
        // Both modes at 3, nothing counted, nothing suggested: the diagnosis was English only.
        assertEquals(mapOf(ContentMode.CONVERSATION to ModeLevel(3), ContentMode.STORY to ModeLevel(3)), chinese.levels)
        assertEquals(true, chinese.isAssessmentComplete)

        // Even before any diagnosis, Chinese has its levels: there is no Chinese assessment to wait
        // for. The flag itself belongs to the English diagnosis and stays unset until that is done.
        val fresh = AppSettingsRepository(newDataStore())
        fresh.setLanguage(Language.CHINESE)
        assertEquals(mapOf(ContentMode.CONVERSATION to ModeLevel(3), ContentMode.STORY to ModeLevel(3)), fresh.settings.first().levels)
        assertEquals(false, fresh.settings.first().isAssessmentComplete)
    }

    @Test
    fun aChineseLevelMoveLeavesTheEnglishLevelWhereItWas() = runBlocking {
        val repository = AppSettingsRepository(newDataStore())
        repository.saveAssessmentResult(2)

        repository.setLevel(ContentMode.STORY, 5, Language.CHINESE)

        // Home still shows English: its story level is the diagnosed 3.
        assertEquals(3, repository.settings.first().level(ContentMode.STORY))
        repository.setLanguage(Language.CHINESE)
        assertEquals(5, repository.settings.first().level(ContentMode.STORY))
        assertEquals(3, repository.settings.first().level(ContentMode.CONVERSATION))
        // And back: switching languages loses nothing on either side.
        repository.setLanguage(Language.ENGLISH)
        assertEquals(ModeLevel(level = 3), repository.settings.first().levels[ContentMode.STORY])
    }

    @Test
    fun aFinishedChineseSessionCountsOnlyTowardsTheChineseWindow() = runBlocking {
        val repository = AppSettingsRepository(newDataStore())
        repository.saveAssessmentResult(2)

        repository.applyFinishedSession(ContentMode.STORY, sessionCompletedAtEpochMillis = 1L, language = Language.CHINESE) { current, _ -> current }
        repository.applyFinishedSession(ContentMode.STORY, sessionCompletedAtEpochMillis = 2L, language = Language.CHINESE) { current, _ -> current + 1 }

        // The English window did not move and got no suggestion.
        assertEquals(ModeLevel(level = 3), repository.settings.first().levels[ContentMode.STORY])
        repository.setLanguage(Language.CHINESE)
        assertEquals(ModeLevel(level = 3, sessionsSinceChange = 2, suggestedLevel = 4), repository.settings.first().levels[ContentMode.STORY])
        assertEquals(ModeLevel(level = 3), repository.settings.first().levels[ContentMode.CONVERSATION])

        // The same session id in English is another session: the replay guard is per language.
        repository.applyFinishedSession(ContentMode.STORY, sessionCompletedAtEpochMillis = 1L, language = Language.ENGLISH) { current, _ -> current }
        repository.setLanguage(Language.ENGLISH)
        assertEquals(ModeLevel(level = 3, sessionsSinceChange = 1), repository.settings.first().levels[ContentMode.STORY])

        // Accepting the Chinese suggestion moves the Chinese level only.
        repository.acceptSuggestion(ContentMode.STORY, Language.CHINESE)
        assertEquals(ModeLevel(level = 3, sessionsSinceChange = 1), repository.settings.first().levels[ContentMode.STORY])
        repository.setLanguage(Language.CHINESE)
        assertEquals(ModeLevel(level = 4), repository.settings.first().levels[ContentMode.STORY])
    }

    private fun newDataStore(): DataStore<Preferences> {
        val file = File(context.filesDir, "app-settings-language-${System.nanoTime()}.preferences_pb")
        val job = SupervisorJob()
        handles += job to file
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { file },
        )
    }
}
