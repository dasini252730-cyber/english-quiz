package com.englishquiz.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.local.LearningSessionEntity
import com.englishquiz.app.data.preferences.AppSettings
import com.englishquiz.app.data.preferences.GameProgressRepository
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.ui.boss.BossRoute
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * App-level navigation for 백로그 004: the home destinations really switch, the back arrow returns
 * home, the open destination survives state restoration, and no login or MVP-excluded screen is
 * reachable. Component tests cover the screens themselves; this drives [LearningHome], the switch
 * that owns the destination state.
 *
 * The AI client is deliberately absent. Conversation and Story then show their "endpoint not
 * configured" screen instead of generated content, which is the honest state until 006 can call a
 * funded provider — the navigation contract proved here does not depend on it.
 */
@RunWith(AndroidJUnit4::class)
class LearningHomeTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: LearningDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
        database = null
        context.deleteDatabase(DATABASE_NAME)
        gameStores.forEach { (job, file) ->
            job.cancel()
            file.delete()
        }
        gameStores.clear()
    }

    private fun openRepository(): LearningRepository {
        val opened = LearningDatabase.create(context, DATABASE_NAME)
        database = opened
        return LearningRepository(opened)
    }

    private fun home(
        repository: LearningRepository,
        gameRepository: GameProgressRepository? = null,
    ): @Composable () -> Unit = {
        EnglishQuizTheme {
            LearningHome(
                repository = repository,
                aiClient = null,
                settings = AppSettings(isAssessmentComplete = true),
                gameRepository = gameRepository,
            )
        }
    }

    @Test
    fun withAGameStoreHomeShowsLevelMissionsAndBadgesFromTheRecords() {
        val repository = openRepository()
        runBlocking {
            repository.recordCompletedSession(
                LearningSessionEntity(
                    learningDate = todayIso(),
                    completedAtEpochMillis = 1L,
                    learnedExpressionCount = 5,
                    newlySavedExpressionCount = 3,
                    quizCorrectCount = 5,
                    quizQuestionCount = 5,
                    mode = "story",
                    score = 100,
                    maxCombo = 5,
                ),
            )
        }
        compose.setContent(home(repository, GameProgressRepository(newGameDataStore())))

        // 100 quiz points plus all three missions (70): level 1 with 170 of 300 (백로그 037/038).
        awaitText("Lv.1 공항 도착")
        compose.onNodeWithText("170점").assertIsDisplayed()
        compose.onNodeWithText("오늘의 미션").assertIsDisplayed()
        // First session and a perfect quiz: two badges, both unseen (백로그 039).
        compose.onNodeWithText("새 배지 2").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("배지 2 / 10").performScrollTo().performClick()
        awaitText("배지 2 / 10")
        compose.onNodeWithText("첫 학습을 끝냈어요").assertIsDisplayed()

        // Opening the badge screen marks them seen, so home no longer flags them.
        compose.onNodeWithContentDescription("홈으로").performClick()
        awaitText(HOME_PROMPT)
        // The seen-write, the store's emission and the recompute take a moment on a slow device.
        compose.waitUntil(3 * TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("새 배지 2").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun theReviewBoxPracticeQuizMovesTheExpressionButRecordsNoSession() {
        val repository = openRepository()
        runBlocking {
            repository.saveExpression("sketchy", "수상한", 1_000L)
            repository.saveExpression("hang out", "놀다", 900L)
            // Past their cards, so the practice asks them (백로그 045).
            listOf("sketchy", "hang out").forEach {
                repository.saveReviewProgress(it, 2_000L, 3_000L, consecutiveCorrectCount = 0, incorrectCount = 0, isMastered = false)
            }
        }
        compose.setContent(home(repository))

        awaitText(HOME_PROMPT)
        compose.onNodeWithText("복습함").performClick()
        awaitText("2개 · 최근 저장순")
        compose.onNodeWithText("이 목록으로 퀴즈 풀기 (최대 2문제)").performClick()

        // 백로그 052: a quiz without a score row. One right, one wrong on purpose: only the wrong
        // answer reaches the expression, and the missed one comes back once at the end.
        awaitText("1 / 2")
        assertTrue(compose.onAllNodesWithText("0점").fetchSemanticsNodes().isEmpty())
        repeat(2) { number ->
            val askingSketchy = compose.onAllNodesWithText("sketchy").fetchSemanticsNodes().isNotEmpty()
            // "sketchy" answered right, "hang out" answered wrong.
            compose.onNodeWithText("수상한").performScrollTo().performClick()
            awaitText(if (askingSketchy) "정답이에요!" else "아쉬워요, 오답이에요.")
            compose.onNodeWithText(if (number == 0) "다음 문제" else "틀린 문제 다시 풀기").performScrollTo().performClick()
        }
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText("다시 풀기 · ", substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("놀다").performScrollTo().performClick()
        compose.onNodeWithText("결과 보기").performScrollTo().performClick()
        awaitText("2문제 중 1개를 맞혔어요.")
        compose.onNodeWithText("복습함으로").performClick()
        awaitText("2개 · 최근 저장순")

        compose.waitUntil(TIMEOUT_MILLIS) {
            runBlocking { repository.listSavedExpressions().single { it.displayExpression == "hang out" }.incorrectCount == 1 }
        }
        val sketchy = runBlocking { repository.listSavedExpressions() }.single { it.displayExpression == "sketchy" }
        assertEquals(0, sketchy.consecutiveCorrectCount)
        assertEquals(2_000L, sketchy.lastReviewedAtEpochMillis)
        assertTrue(runBlocking { repository.listAllSessions() }.isEmpty())
    }

    @Test
    fun theBossRecordsADoublePointSessionAndReturnsHome() {
        val repository = openRepository()
        runBlocking {
            // Never asked before: the boss still asks them as questions, never as cards (백로그 045).
            repository.saveExpression("sketchy", "수상한", 1_000L)
            repository.saveExpression("hang out", "놀다", 900L)
        }
        val recorded = mutableListOf<Long>()
        compose.setContent {
            EnglishQuizTheme {
                BossRoute(
                    repository = repository,
                    gameRepository = GameProgressRepository(newGameDataStore()),
                    onExit = {},
                    onSessionRecorded = { recorded += it },
                )
            }
        }

        awaitText("도전하기")
        compose.onNodeWithText("도전하기").performClick()
        repeat(2) { number ->
            awaitText("${number + 1} / 2")
            val askingSketchy = compose.onAllNodesWithText("sketchy").fetchSemanticsNodes().isNotEmpty()
            compose.onNodeWithText(if (askingSketchy) "수상한" else "놀다").performScrollTo().performClick()
            // 백로그 041: every answer pays double — 20, then 30 for the second in a row.
            awaitText(if (number == 0) "+20점" else "+30점")
            compose.onNodeWithText(if (number == 0) "다음 문제" else "결과 보기").performScrollTo().performClick()
        }

        awaitText("오늘 학습 완료")
        compose.waitUntil(TIMEOUT_MILLIS) { recorded.size == 1 }
        val session = runBlocking { repository.listAllSessions() }.single()
        assertEquals("boss", session.mode)
        assertEquals(50, session.score)
        assertEquals(2, session.quizQuestionCount)
    }

    @Test
    fun aBossWithNothingToAskRecordsNothing() {
        val repository = openRepository()
        compose.setContent {
            EnglishQuizTheme {
                BossRoute(repository = repository, gameRepository = null, onExit = {})
            }
        }

        awaitText("도전하기")
        compose.onNodeWithText("도전하기").performClick()
        awaitText("오늘 복습할 표현이 없어요.")
        compose.onNodeWithText("완료").performClick()

        awaitText("지금은 도전할 표현이 없어요. 복습 예정인 표현이 쌓이면 다시 열려요.")
        assertTrue(runBlocking { repository.listAllSessions() }.isEmpty())
    }

    private val gameStores = mutableListOf<Pair<Job, File>>()

    private fun newGameDataStore(): DataStore<Preferences> {
        val file = File(context.filesDir, "home-game-${System.nanoTime()}.preferences_pb")
        val job = SupervisorJob()
        gameStores += job to file
        return PreferenceDataStoreFactory.create(scope = CoroutineScope(job + Dispatchers.IO), produceFile = { file })
    }

    @Test
    fun theReviewListOpensFromHomeAndTheBackArrowReturnsHome() {
        val repository = openRepository()
        runBlocking { repository.saveExpression("sketchy", "수상한", 1_000L, "That sounds sketchy.") }
        compose.setContent(home(repository))

        awaitText(HOME_PROMPT)
        compose.onNodeWithText("복습함").performClick()

        awaitText("1개 · 최근 저장순")
        compose.onNodeWithText("sketchy").assertIsDisplayed()

        compose.onNodeWithContentDescription("홈으로").performClick()

        awaitText(HOME_PROMPT)
        compose.onNodeWithText("StoryEnglish").assertIsDisplayed()
    }

    @Test
    fun conversationAndStoryOpenTheirOwnSessionAndComeBackHome() {
        val repository = openRepository()
        compose.setContent(home(repository))
        awaitText(HOME_PROMPT)

        listOf("Conversation", "Story").forEach { mode ->
            compose.onNodeWithText(mode).performClick()

            awaitText(NO_ENDPOINT_MESSAGE)
            // The session screen carries its own mode title, so the two entries are distinguishable.
            compose.onNodeWithText(mode).assertIsDisplayed()

            compose.onNodeWithContentDescription("홈으로").performClick()
            awaitText(HOME_PROMPT)
        }
    }

    @Test
    fun noLoginOrExcludedScreenIsReachableFromHome() {
        val repository = openRepository()
        compose.setContent(home(repository))
        awaitText(HOME_PROMPT)

        listOf("로그인", "회원가입", "구독", "설정").forEach { excluded ->
            assertTrue(
                "홈에 '$excluded' 진입점이 보이면 안 된다",
                compose.onAllNodesWithText(excluded).fetchSemanticsNodes().isEmpty(),
            )
        }
    }

    @Test
    fun theOpenDestinationSurvivesStateRestoration() {
        val repository = openRepository()
        val restoration = StateRestorationTester(compose)
        restoration.setContent(home(repository))

        awaitText(HOME_PROMPT)
        compose.onNodeWithText("복습함").performClick()
        awaitText("0개 · 최근 저장순")

        restoration.emulateSavedInstanceStateRestore()

        // Recreation must not drop the learner back at home mid-session (요구사항 21절).
        awaitText("0개 · 최근 저장순")
        assertTrue(compose.onAllNodesWithText(HOME_PROMPT).fetchSemanticsNodes().isEmpty())
    }

    private fun awaitText(text: String) {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private companion object {
        const val DATABASE_NAME = "test-learning-home.db"
        const val TIMEOUT_MILLIS = 5_000L
        const val HOME_PROMPT = "오늘 뭐 할까요?"
        const val NO_ENDPOINT_MESSAGE = "AI 연결이 아직 설정되지 않았어요. 학습 내용을 만들려면 앱을 빌드할 때 " +
            "Edge Function 주소를 넣어야 해요."
    }
}
