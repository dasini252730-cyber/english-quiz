package com.englishquiz.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

/** 백로그 073: online voices are off until the learner switches them on, and the switch is stored. */
@RunWith(AndroidJUnit4::class)
class AppSettingsNetworkVoicesTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val handles = mutableListOf<Pair<Job, File>>()

    @After
    fun releaseDataStores() {
        handles.forEach { (job, file) ->
            runBlocking { job.cancelAndJoin() }
            file.delete()
        }
        handles.clear()
    }

    @Test
    fun onlineVoicesAreOffByDefaultAndTheSwitchIsStored() = runBlocking {
        val file = File(context.filesDir, "network_voices_${System.nanoTime()}.preferences_pb")
        val repository = AppSettingsRepository(newDataStore(file))
        assertEquals(false, repository.settings.first().allowNetworkVoices)

        repository.setNetworkVoices(true)
        assertEquals(true, repository.settings.first().allowNetworkVoices)
        // The other settings are untouched by the switch.
        assertEquals(false, repository.settings.first().isAssessmentComplete)

        // A second repository over the same file reads the stored value: the switch survives a
        // restart. The first store's scope is cancelled, not its file deleted.
        runBlocking { handles.first().first.cancelAndJoin() }
        val reopened = AppSettingsRepository(newDataStore(file))
        assertEquals(true, reopened.settings.first().allowNetworkVoices)
        reopened.setNetworkVoices(false)
        assertEquals(false, reopened.settings.first().allowNetworkVoices)
    }

    private fun newDataStore(file: File): DataStore<Preferences> {
        val job = SupervisorJob()
        handles += job to file
        return PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job)) { file }
    }
}
