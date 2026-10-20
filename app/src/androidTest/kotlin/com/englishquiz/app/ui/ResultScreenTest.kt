package com.englishquiz.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.preferences.GameProgressRepository
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.domain.session.LearningSessionSummary
import com.englishquiz.app.ui.result.ResultRoute
import com.englishquiz.app.ui.result.ResultScreen
import com.englishquiz.app.ui.result.StreakUiState
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import java.io.File
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.englishquiz.app.data.repository.findLearningSessions

@RunWith(AndroidJUnit4::class)
class ResultScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: LearningDatabase? = null

    private val gameStores = mutableListOf<Pair<Job, File>>()

    @After
    fun removeDatabase() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
        gameStores.forEach { (job, file) ->
            job.cancel()
            file.delete()
        }
        gameStores.clear()
    }

    private fun newGameDataStore(): DataStore<Preferences> {
        val file = File(context.filesDir, "result-game-${System.nanoTime()}.preferences_pb")
        val job = SupervisorJob()
        gameStores += job to file
        return PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
    }

    @Test
    fun showsSummaryNumbersAndStreakAndCallsOnDone() {
        var doneCalls = 0
        composeRule.setContent {
            EnglishQuizTheme {
                ResultScreen(
                    summary = LearningSessionSummary(
                        learnedExpressionCount = 9,
                        newlySavedExpressionCount = 5,
                        quizCorrectCount = 5,
                        quizQuestionCount = 6,
                        score = 85,
                        maxCombo = 4,
                    ),
                    streakState = StreakUiState.Ready(8),
                    onRetry = {},
                    onDone = { doneCalls++ },
                )
            }
        }

        composeRule.onNodeWithText("83%").assertIsDisplayed()
        composeRule.onNodeWithText("5개").assertIsDisplayed()
        composeRule.onNodeWithText("9개").assertIsDisplayed()
        // 백로그 035: the quiz's points and its longest run.
        composeRule.onNodeWithText("85점").assertIsDisplayed()
        composeRule.onNodeWithText("4").assertIsDisplayed()
        composeRule.onNodeWithText("8일 연속 학습 중").assertIsDisplayed()

        // The result column scrolls, so the button sits below the fold on a short viewport.
        composeRule.onNodeWithText("완료").performScrollTo().performClick()

        assertEquals(1, doneCalls)
    }

    @Test
    fun zeroQuizQuestionsShowsZeroPercent() {
        composeRule.setContent {
            EnglishQuizTheme {
                ResultScreen(
                    summary = LearningSessionSummary(
                        learnedExpressionCount = 0,
                        newlySavedExpressionCount = 0,
                        quizCorrectCount = 0,
                        quizQuestionCount = 0,
                    ),
                    streakState = StreakUiState.Ready(0),
                    onRetry = {},
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithText("0%").assertIsDisplayed()
        composeRule.onNodeWithText("0일 연속 학습 중").assertIsDisplayed()
    }

    @Test
    fun streakErrorShowsRetryAndInvokesCallback() {
        var retryCalls = 0
        composeRule.setContent {
            EnglishQuizTheme {
                ResultScreen(
                    summary = LearningSessionSummary(),
                    streakState = StreakUiState.Error,
                    onRetry = { retryCalls++ },
                    onDone = {},
                )
            }
        }

        composeRule.onNodeWithText("스트릭을 불러오지 못했어요.").assertIsDisplayed()
        composeRule.onNodeWithText("다시 시도").performClick()

        assertEquals(1, retryCalls)
    }

    @Test
    fun withAGameStoreTheResultShowsWhatTheSessionEarnedAndTheNewBadge() {
        val freshDatabase = LearningDatabase.create(context, DATABASE_NAME)
        database = freshDatabase
        val repository = LearningRepository(freshDatabase)
        val gameRepository = GameProgressRepository(newGameDataStore())

        composeRule.setContent {
            EnglishQuizTheme {
                ResultRoute(
                    repository = repository,
                    summary = LearningSessionSummary(
                        learnedExpressionCount = 10,
                        newlySavedExpressionCount = 0,
                        quizCorrectCount = 10,
                        quizQuestionCount = 10,
                        score = 250,
                        maxCombo = 10,
                    ),
                    sessionMode = ContentMode.STORY.wireValue,
                    onDone = {},
                    nowEpochMillis = { FIXED_NOW_EPOCH_MILLIS },
                    zoneId = ZoneOffset.UTC,
                    gameRepository = gameRepository,
                )
            }
        }

        // 250 quiz points plus the finished (20) and 80% (30) missions: 300, exactly level 2.
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("+300 경험치").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("레벨 업!").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Lv.2 입국 심사 통과").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("오늘의 미션 2 / 3 · 보너스 +50점").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("새 배지: ", substring = true).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("1일 연속 학습 중").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun recordsSessionOnceEvenAcrossStateRestoration() {
        val freshDatabase = LearningDatabase.create(context, DATABASE_NAME)
        database = freshDatabase
        val repository = LearningRepository(freshDatabase)
        val stateRestorationTester = StateRestorationTester(composeRule)

        stateRestorationTester.setContent {
            EnglishQuizTheme {
                ResultRoute(
                    repository = repository,
                    summary = LearningSessionSummary(
                        learnedExpressionCount = 4,
                        newlySavedExpressionCount = 2,
                        quizCorrectCount = 3,
                        quizQuestionCount = 4,
                    ),
                    sessionMode = ContentMode.CONVERSATION.wireValue,
                    onDone = {},
                    nowEpochMillis = { FIXED_NOW_EPOCH_MILLIS },
                    zoneId = ZoneOffset.UTC,
                )
            }
        }

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("1일 연속 학습 중").fetchSemanticsNodes().isNotEmpty()
        }

        stateRestorationTester.emulateSavedInstanceStateRestore()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("1일 연속 학습 중").fetchSemanticsNodes().isNotEmpty()
        }

        val sessions = runBlocking { freshDatabase.learningDao().findLearningSessions(FIXED_DATE_ISO) }
        assertEquals(1, sessions.size)
    }

    private companion object {
        const val DATABASE_NAME = "test-result-screen.db"
        const val FIXED_DATE_ISO = "2026-09-20"
        val FIXED_NOW_EPOCH_MILLIS = LocalDate.parse(FIXED_DATE_ISO)
            .atStartOfDay(ZoneOffset.UTC)
            .toInstant()
            .toEpochMilli() + 12 * 60 * 60 * 1000L
    }
}
