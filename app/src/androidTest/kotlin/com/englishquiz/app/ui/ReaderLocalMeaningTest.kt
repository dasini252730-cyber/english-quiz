package com.englishquiz.app.ui

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
import com.englishquiz.app.data.ai.ContextualMeaning
import com.englishquiz.app.data.ai.GlossaryEntry
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.ui.reader.ReaderRoute
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 백로그 024: a tap on a highlighted expression or a glossary word shows the meaning the passage
 * already carries and saves it, without a paid call. Only a word nothing local knows asks the AI.
 */
@RunWith(AndroidJUnit4::class)
class ReaderLocalMeaningTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: LearningDatabase? = null

    private val content = LearningContent(
        title = "A cafe",
        mode = ContentMode.CONVERSATION,
        segments = listOf(ContentSegment("Emma", "That sounds sketchy.")),
        expressions = listOf(ContentExpression("sketchy", "수상한", 0, 12, 19)),
        glossary = listOf(GlossaryEntry("sounds", "~처럼 들리다")),
    )

    @After
    fun closeDatabase() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    private fun awaitText(text: String) {
        compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    @Test
    fun highlightedAndGlossaryWordsNeverCallTheAiAndAnUnknownWordDoes() {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)
        val asked = mutableListOf<String>()
        compose.setContent {
            EnglishQuizTheme {
                ReaderRoute(
                    content = content,
                    repository = repository,
                    onBack = {},
                    explainMeaning = { request ->
                        asked += request.expression
                        ContextualMeaning(request.expression, "AI가 준 뜻")
                    },
                )
            }
        }

        // The annotated expression: its meaning came with the passage.
        compose.onNodeWithText("sketchy", substring = true).performClick()
        awaitText("수상한")
        compose.onNodeWithText("복습함에 저장했어요").assertIsDisplayed()
        compose.onNodeWithText("닫기").performClick()

        // A glossary word: same, and its edge punctuation does not get in the way.
        compose.onNodeWithText("sounds").performClick()
        awaitText("~처럼 들리다")
        compose.onNodeWithText("닫기").performClick()
        assertEquals("주석 표현이나 어휘표 단어에 AI를 불렀다", emptyList<String>(), asked)

        // A word the passage says nothing about: the one case that is still worth a call.
        compose.onNodeWithText("That").performClick()
        awaitText("AI가 준 뜻")
        compose.onNodeWithText("닫기").performClick()
        assertEquals(listOf("That"), asked)

        // And once it is saved, tapping it again asks nothing either.
        compose.onNodeWithText("That").performClick()
        awaitText("AI가 준 뜻")
        assertEquals(listOf("That"), asked)
    }

    @Test
    fun aPhraseSavedFromAnotherSentenceIsLookedUpAfreshBecauseItsMeaningMayDiffer() {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)
        // 요구사항 11: "book" saved as 예약하다 from a hotel line must not label "closed the book".
        runBlocking { repository.saveExpression("That", "다른 문장의 뜻", 1L, "That was a different sentence.") }
        var asked = 0
        compose.setContent {
            EnglishQuizTheme {
                ReaderRoute(
                    content = content,
                    repository = repository,
                    onBack = {},
                    explainMeaning = { request ->
                        asked++
                        ContextualMeaning(request.expression, "이 문장의 뜻")
                    },
                )
            }
        }

        compose.onNodeWithText("That").performClick()
        awaitText("이 문장의 뜻")

        assertEquals(1, asked)
    }

    private companion object {
        const val DATABASE_NAME = "test-reader-local-meaning.db"
    }
}
