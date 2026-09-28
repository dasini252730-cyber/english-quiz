package com.englishquiz.app.ui.library

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.local.LibraryItem
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** 백로그 026: past passages are listed, the two-to-three-week ones first, and a tap hands one over. */
@RunWith(AndroidJUnit4::class)
class LibraryRouteTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: LearningDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    private fun passage(title: String, mode: ContentMode) = LearningContent(
        title = title,
        mode = mode,
        segments = listOf(ContentSegment("Alex", "We can pull it off together.")),
        expressions = listOf(ContentExpression("pull it off", "해내다", 0, 7, 18)),
    )

    @Test
    fun pastPassagesAreListedSweetSpotFirstAndATapOpensTheChosenOne() {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)
        runBlocking {
            repository.saveDailyContent("2026-09-26", passage("Today's coffee", ContentMode.CONVERSATION), 1)
            repository.saveDailyContent("2026-09-21", passage("Five days ago", ContentMode.STORY), 2)
            repository.saveDailyContent("2026-09-10", passage("Sixteen days ago", ContentMode.CONVERSATION), 3)
        }
        val opened = mutableListOf<LibraryItem>()
        compose.setContent {
            EnglishQuizTheme {
                LibraryRoute(
                    repository = repository,
                    onOpen = { opened += it },
                    onBack = {},
                    today = LocalDate.of(2026, 9, 26),
                )
            }
        }

        compose.waitUntil(5_000) { compose.onAllNodesWithText("Sixteen days ago").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Five days ago").assertIsDisplayed()
        // Today's passage belongs to the home cards, not to the library.
        assertEquals(0, compose.onAllNodesWithText("Today's coffee").fetchSemanticsNodes().size)
        // Only the sixteen-day-old one is in the re-read window.
        assertEquals(1, compose.onAllNodesWithText("다시 읽기 좋은 때").fetchSemanticsNodes().size)
        compose.onNodeWithText("16일 전 · Conversation").assertIsDisplayed()

        compose.onNodeWithText("Sixteen days ago").performClick()

        assertEquals(listOf("2026-09-10" to "conversation"), opened.map { it.learningDate to it.mode })
    }

    private companion object {
        const val DATABASE_NAME = "test-library-route.db"
    }
}
