package com.englishquiz.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
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
    }

    private fun openRepository(): LearningRepository {
        val opened = LearningDatabase.create(context, DATABASE_NAME)
        database = opened
        return LearningRepository(opened)
    }

    private fun home(repository: LearningRepository): @Composable () -> Unit = {
        EnglishQuizTheme {
            LearningHome(repository = repository, aiClient = null, difficulty = 2)
        }
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
