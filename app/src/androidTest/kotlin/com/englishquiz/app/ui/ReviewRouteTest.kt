package com.englishquiz.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.ui.review.ReviewRoute
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith

/**
 * Covers 백로그 009's data path: what the reader saved while reading shows up in 복습함, and it is
 * still there after the database is closed and reopened, which is what "앱을 다시 켜도 남아 있다"
 * means for this screen.
 */
@RunWith(AndroidJUnit4::class)
class ReviewRouteTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: LearningDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    private fun openRepository(): LearningRepository {
        val opened = LearningDatabase.create(context, DATABASE_NAME)
        database = opened
        return LearningRepository(opened)
    }

    private fun mount(repository: LearningRepository) {
        compose.setContent {
            EnglishQuizTheme { ReviewRoute(repository, onBack = {}) }
        }
    }

    private fun awaitText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun savedExpressionsFromReadingAppearInTheReviewList() {
        val repository = openRepository()
        runBlocking {
            repository.saveExpression("sketchy", "수상한", 1_000L, "That sounds sketchy.")
            repository.saveExpression("pull it off", "해내다", 2_000L, "I'll totally pull it off.")
        }

        mount(repository)

        awaitText("pull it off")
        compose.onNodeWithText("pull it off").assertIsDisplayed()
        compose.onNodeWithText("해내다").assertIsDisplayed()
        compose.onNodeWithText("sketchy").assertIsDisplayed()
        compose.onNodeWithText("수상한").assertIsDisplayed()
        compose.onNodeWithText("2개 · 최근 저장순").assertIsDisplayed()
    }

    @Test
    fun theTappedSeedFilterReadsTheTapCountFromTheStore() {
        val repository = openRepository()
        runBlocking {
            // "sketchy" was looked up twice while reading; "pull it off" only enrolled from a passage.
            repository.saveExpression("sketchy", "수상한", 1_000L, "That sounds sketchy.")
            repository.saveExpression("sketchy", "수상한", 1_500L, "That sounds sketchy.")
            repository.saveExpressionIfNew("pull it off", "해내다", 2_000L, "I'll totally pull it off.")
        }

        mount(repository)
        awaitText("2개 · 최근 저장순")
        compose.onNodeWithText("탭했는데 아직 씨앗").performClick()

        // 백로그 044: the filter is applied in the route, on the rows as stored.
        awaitText("1개 · 많이 탭한 순")
        compose.onNodeWithText("sketchy").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("pull it off").fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("전체").performClick()
        awaitText("2개 · 최근 저장순")
    }

    @Test
    fun savedExpressionsSurviveClosingAndReopeningTheDatabase() {
        val first = openRepository()
        runBlocking { first.saveExpression("hummed", "웅웅거리다", 1_000L, "\"Again?\" it hummed.") }
        database?.close()

        val reopened = openRepository()
        mount(reopened)

        awaitText("hummed")
        compose.onNodeWithText("hummed").assertIsDisplayed()
        compose.onNodeWithText("웅웅거리다").assertIsDisplayed()
    }

    @Test
    fun anEmptyStoreShowsTheGuidanceInsteadOfABlankList() {
        val repository = openRepository()

        mount(repository)

        awaitText("아직 저장한 표현이 없어요. Conversation이나 Story에서 모르는 표현의 뜻을 확인하면 자동으로 저장돼요.")
    }

    private companion object {
        const val DATABASE_NAME = "test-review-route.db"
    }
}
