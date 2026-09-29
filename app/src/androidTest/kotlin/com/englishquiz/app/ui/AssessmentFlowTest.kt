package com.englishquiz.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.englishquiz.app.assessment.Assessment
import com.englishquiz.app.data.preferences.AppSettingsRepository
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class AssessmentFlowTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val handles = mutableListOf<StoreHandle>()
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @After
    fun closeStores() = runBlocking {
        handles.forEach { it.job.cancelAndJoin() }
        handles.map { it.file }.distinct().forEach(File::delete)
    }

    @Test
    fun firstRunSavesBeforeHomeAndFailedSaveCanBeRetried() {
        val handle = newStoreHandle()
        val gate = WriteGateDataStore(handle.dataStore)
        composeRule.setContent {
            EnglishQuizTheme { EnglishQuizApp(AppSettingsRepository(gate)) }
        }

        answerAllQuestions()
        runBlocking { withTimeout(5_000) { gate.writeStarted.await() } }
        composeRule.onNodeWithText("진단 결과를 저장하고 있어요…").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("오늘의 영어 학습").fetchSemanticsNodes().isEmpty())

        runBlocking { gate.allowWrite.complete(Unit) }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("8 / 8").fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithText("오늘의 영어 학습").assertIsDisplayed()
        composeRule.onNodeWithText("진단 완료 · 초기 난이도: 고급").assertIsDisplayed()
    }

    @Test
    fun failedSaveKeepsAssessmentOpenAndRetryMovesHome() {
        val failingHandle = newStoreHandle()
        val failOnce = FailFirstWriteDataStore(failingHandle.dataStore)
        composeRule.setContent {
            EnglishQuizTheme { EnglishQuizApp(AppSettingsRepository(failOnce)) }
        }
        answerAllQuestions()
        composeRule.onNodeWithText("결과를 저장하지 못했어요. 다시 시도해 주세요.").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("오늘의 영어 학습").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithText("다시 시도").performClick()
        // The retried save goes through DataStore, so home is not composed on the same frame.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("오늘의 영어 학습").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("오늘의 영어 학습").assertIsDisplayed()
    }

    @Test
    fun completedAssessmentIsSkippedAfterStoreReopens() = runBlocking {
        val firstHandle = newStoreHandle()
        AppSettingsRepository(firstHandle.dataStore).saveAssessmentResult(difficulty = 2)
        firstHandle.job.cancelAndJoin()
        val reopenedHandle = newStoreHandle(firstHandle.file)

        composeRule.setContent {
            EnglishQuizTheme {
                EnglishQuizApp(AppSettingsRepository(reopenedHandle.dataStore))
            }
        }

        composeRule.onNodeWithText("오늘의 영어 학습").assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("레벨 진단").fetchSemanticsNodes().isEmpty())
        assertTrue(AppSettingsRepository(reopenedHandle.dataStore).settings.first().isAssessmentComplete)
    }

    private fun answerAllQuestions() {
        composeRule.onNodeWithText("레벨 진단").assertIsDisplayed()
        composeRule.onNodeWithText("1 / 8").assertIsDisplayed()
        Assessment.questions.forEachIndexed { index, question ->
            composeRule.onNodeWithText(question.options[question.correctOption]).performClick()
            if (index == 0) composeRule.onNodeWithText("2 / 8").assertIsDisplayed()
        }
    }

    private fun newStoreHandle(file: File = File(context.filesDir, "assessment-${System.nanoTime()}.preferences_pb")):
        StoreHandle {
        val job = SupervisorJob()
        val store = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { file },
        )
        return StoreHandle(store, job, file).also(handles::add)
    }

    private data class StoreHandle(
        val dataStore: DataStore<Preferences>,
        val job: Job,
        val file: File,
    )

    private class WriteGateDataStore(
        private val delegate: DataStore<Preferences>,
    ) : DataStore<Preferences> by delegate {
        val writeStarted = CompletableDeferred<Unit>()
        val allowWrite = CompletableDeferred<Unit>()

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            writeStarted.complete(Unit)
            allowWrite.await()
            return delegate.updateData(transform)
        }
    }

    private class FailFirstWriteDataStore(
        private val delegate: DataStore<Preferences>,
    ) : DataStore<Preferences> by delegate {
        private var shouldFail = true

        override suspend fun updateData(
            transform: suspend (t: Preferences) -> Preferences,
        ): Preferences {
            if (shouldFail) {
                shouldFail = false
                throw IOException("simulated write failure")
            }
            return delegate.updateData(transform)
        }
    }
}
