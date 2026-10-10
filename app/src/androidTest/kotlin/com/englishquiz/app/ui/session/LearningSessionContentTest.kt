package com.englishquiz.app.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.ContextualMeaning
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.ui.session.SessionRouteFixtures.EMPTY_QUIZ
import com.englishquiz.app.ui.session.SessionRouteFixtures.FIRST_CARD
import com.englishquiz.app.ui.session.SessionRouteFixtures.QUIZ_BUTTON
import com.englishquiz.app.ui.session.SessionRouteFixtures.READER_TOKEN
import com.englishquiz.app.ui.session.SessionRouteFixtures.content
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import com.englishquiz.app.data.repository.listLibrary
import com.englishquiz.app.data.repository.listRecentSessionSummaries

/**
 * Where a session's passage comes from: the day's stored one on a second entry (백로그 021) and a
 * library passage read again (백로그 026). Neither may cost a generation.
 */
@RunWith(AndroidJUnit4::class)
class LearningSessionContentTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: LearningDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
        database = null
        context.deleteDatabase(DATABASE_NAME)
    }

    private fun repository(): LearningRepository {
        val opened = LearningDatabase.create(context, DATABASE_NAME)
        database = opened
        return LearningRepository(opened)
    }

    @Test
    fun theSameDaysSecondEntryReadsTheStoredPassageInsteadOfGeneratingAgain() {
        val repository = repository()
        var attempts = 0
        // Bumping the key throws the whole route away and builds a new one: the learner went home
        // and tapped the same mode again, which used to cost a second generation (백로그 021).
        var entry by mutableIntStateOf(0)
        compose.setContent {
            EnglishQuizTheme {
                key(entry) {
                    LearningSessionRoute(
                        mode = ContentMode.CONVERSATION,
                        repository = repository,
                        difficulty = 2,
                        generateContent = {
                            attempts++
                            // The attempt number is a word of the passage, so the screen itself
                            // says which passage came back, not just how many were generated.
                            val sentence = "Attempt$attempts we can pull it off together."
                            val start = sentence.indexOf("pull it off")
                            content("Attempt $attempts").copy(
                                segments = listOf(ContentSegment("Alex", sentence)),
                                // The annotation still points at the phrase: an offset that drifted
                                // would enrol the wrong words for the quiz (백로그 031).
                                expressions = listOf(ContentExpression("pull it off", "해내다", 0, start, start + 11)),
                            )
                        },
                        explainMeaning = { ContextualMeaning("pull it off", "해내다") },
                        onExit = {},
                    )
                }
            }
        }
        compose.awaitText(READER_TOKEN)
        assertEquals(1, attempts)
        // Leave from the quiz step, as the recreation test does: tearing the Reader's lazy list
        // down in this harness can trip Compose's slot table (백로그 018), and the guard being
        // proved — no second generation, the same passage back — is the same from either step.
        compose.onNodeWithText(QUIZ_BUTTON).performClick()
        compose.awaitText(FIRST_CARD)

        compose.runOnIdle { entry++ }

        compose.awaitText(READER_TOKEN)
        assertEquals("같은 날 두 번째 진입이 지문을 다시 생성했다", 1, attempts)
        compose.onNodeWithText("Attempt1").assertIsDisplayed()
    }

    @Test
    fun aLibraryPassageIsReadAsItIsWithoutGeneratingOrStoring() {
        val repository = repository()
        var attempts = 0
        compose.setContent {
            EnglishQuizTheme {
                LearningSessionRoute(
                    mode = ContentMode.CONVERSATION,
                    repository = repository,
                    difficulty = 2,
                    generateContent = {
                        attempts++
                        content("Should never be made")
                    },
                    explainMeaning = { ContextualMeaning("pull it off", "해내다") },
                    onExit = {},
                    initialContent = content("From the library"),
                )
            }
        }

        compose.awaitText(READER_TOKEN)
        assertEquals("라이브러리 지문인데 생성을 호출했다", 0, attempts)
        // Reading an old passage again must not be mistaken for today's new one.
        assertTrue(runBlocking { repository.listLibrary() }.isEmpty())

        // Nor are its expressions enrolled for the quiz the way today's are (백로그 031): nothing
        // was tapped and nothing is due, so the quiz is empty and the review box stays empty.
        compose.onNodeWithText(QUIZ_BUTTON).performClick()
        // Nothing is enrolled from a library passage (백로그 026/031), so there is no card either.
        compose.awaitText(EMPTY_QUIZ)
        assertTrue(runBlocking { repository.listSavedExpressions() }.isEmpty())
    }

    @Test
    fun todaysPassageEnrolsItsExpressionsForTheQuizWithoutCountingThemAsNewlySaved() {
        val repository = repository()
        // An honest question needs a wrong option; this one is saved but not due, so it is not asked.
        runBlocking {
            repository.saveExpression("sketchy", "수상한", 1L)
            repository.saveReviewProgress("sketchy", 1L, Long.MAX_VALUE, 1, 0, false)
        }
        compose.setContent {
            EnglishQuizTheme {
                LearningSessionRoute(
                    mode = ContentMode.CONVERSATION,
                    repository = repository,
                    difficulty = 2,
                    generateContent = { content("Today") },
                    explainMeaning = { ContextualMeaning("pull it off", "해내다") },
                    onExit = {},
                )
            }
        }

        // Untapped, the passage's one expression still makes today's quiz (백로그 031) ...
        compose.awaitText(READER_TOKEN)
        compose.onNodeWithText(QUIZ_BUTTON).performClick()
        compose.awaitText("1 / 1")
        // Met for the first time, the passage's expression is a card, not a question (백로그 045).
        compose.onNodeWithText("pull it off").assertIsDisplayed()
        compose.onNodeWithText("뜻 확인 완료").performScrollTo().performClick()

        // ... but the recorded session - what difficulty adjustment reads (백로그 013) - counts
        // no newly saved expression: the learner tapped nothing.
        compose.awaitText("오늘 학습 완료")
        compose.waitUntil(SessionRouteFixtures.TIMEOUT_MILLIS) {
            runBlocking { repository.listRecentSessionSummaries(ContentMode.CONVERSATION, 1) }.isNotEmpty()
        }
        val recorded = runBlocking { repository.listRecentSessionSummaries(ContentMode.CONVERSATION, 1) }.single()
        assertEquals(0, recorded.quizQuestionCount)
        assertEquals(1, recorded.learnedExpressionCount)
        assertEquals(0, recorded.newlySavedExpressionCount)
        assertEquals(2, runBlocking { repository.listSavedExpressions() }.size)
    }

    private companion object {
        const val DATABASE_NAME = "test-learning-session-content.db"
    }
}
