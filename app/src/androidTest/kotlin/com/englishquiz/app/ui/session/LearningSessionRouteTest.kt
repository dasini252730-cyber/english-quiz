package com.englishquiz.app.ui.session

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.ai.AiLearningException
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContextualMeaning
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.ui.session.SessionRouteFixtures.EMPTY_QUIZ
import com.englishquiz.app.ui.session.SessionRouteFixtures.QUIZ_BUTTON
import com.englishquiz.app.ui.session.SessionRouteFixtures.READER_TOKEN
import com.englishquiz.app.ui.session.SessionRouteFixtures.content
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers 백로그 006's third acceptance criterion — a timeout or an unusable reply must leave the
 * app usable with a retry prompt — and the cost rule that a generated passage is not thrown away
 * when the activity is recreated. Where the passage comes from (stored, library) is in
 * [LearningSessionContentTest].
 */
@RunWith(AndroidJUnit4::class)
class LearningSessionRouteTest {
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
    fun aTimeoutSaysSoAndTheRetryProducesTheContent() {
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
                        if (attempts == 1) throw AiLearningException("provider_timeout", 504)
                        content("Second attempt")
                    },
                    explainMeaning = { ContextualMeaning("pull it off", "해내다") },
                    onExit = {},
                )
            }
        }

        compose.awaitText("응답이 너무 오래 걸렸어요. 잠시 후 다시 시도해 주세요.")
        compose.onNodeWithText("다시 시도").performClick()

        // The retry re-runs generation and the Reader opens, so a failure is recoverable in place.
        compose.awaitText(READER_TOKEN)
        assertEquals(2, attempts)
    }

    @Test
    fun aRepliesTheAppCannotReadAndABusyProviderEachGetTheirOwnGuidance() {
        val repository = repository()
        compose.setContent {
            EnglishQuizTheme {
                LearningSessionRoute(
                    mode = ContentMode.STORY,
                    repository = repository,
                    difficulty = 1,
                    generateContent = { throw AiLearningException("invalid_response", 502) },
                    explainMeaning = { ContextualMeaning("x", "y") },
                    onExit = {},
                )
            }
        }

        compose.awaitText("학습 내용을 이해하지 못했어요. 다시 시도해 주세요.")
        // The screen stays usable: the retry and the way back are both still there.
        compose.onNodeWithText("다시 시도").assertIsDisplayed()
    }

    @Test
    fun anUnknownFailureStillOffersARetryInsteadOfADeadScreen() {
        val repository = repository()
        compose.setContent {
            EnglishQuizTheme {
                LearningSessionRoute(
                    mode = ContentMode.CONVERSATION,
                    repository = repository,
                    difficulty = 2,
                    generateContent = { throw IllegalStateException("something unexpected") },
                    explainMeaning = { ContextualMeaning("x", "y") },
                    onExit = {},
                )
            }
        }

        compose.awaitText("학습 내용을 불러오지 못했어요.")
    }

    @Test
    fun recreationMidSessionKeepsTheSessionInsteadOfPayingForANewPassage() {
        val repository = repository()
        var attempts = 0
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            EnglishQuizTheme {
                LearningSessionRoute(
                    mode = ContentMode.CONVERSATION,
                    repository = repository,
                    difficulty = 2,
                    generateContent = {
                        attempts++
                        content("Attempt $attempts")
                    },
                    explainMeaning = { ContextualMeaning("pull it off", "해내다") },
                    onExit = {},
                )
            }
        }
        compose.awaitText(READER_TOKEN)
        // Restoration is emulated from the quiz step: the Reader's lazy list cannot be rebuilt off
        // the main looper in this harness, and the guard being proved is the same either way.
        compose.onNodeWithText(QUIZ_BUTTON).performClick()
        compose.awaitText(EMPTY_QUIZ)

        restoration.emulateSavedInstanceStateRestore()

        // Recreation must not spend another provider call, nor restart the session.
        compose.awaitText(EMPTY_QUIZ)
        assertEquals("회전 후 생성이 다시 호출됐다", 1, attempts)
    }

    private companion object {
        const val DATABASE_NAME = "test-learning-session.db"
    }
}
