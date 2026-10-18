package com.englishquiz.app.ui.roleplay

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.data.repository.listWrongAnswerCounts
import com.englishquiz.app.domain.quiz.QuizMode
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 백로그 076: the role-play without a microphone. A [MicAccess] that denies stands in for the
 * system dialog, so the typed fallback and the pass/fail log can be proved on any device.
 */
@RunWith(AndroidJUnit4::class)
class RoleplayRouteTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: LearningDatabase? = null

    private val content = LearningContent(
        title = "At the station",
        mode = ContentMode.CONVERSATION,
        segments = listOf(
            ContentSegment("Emma", "Can you help me find the station?"),
            ContentSegment("Sam", "Sure, it's just around the corner."),
        ),
        expressions = listOf(ContentExpression("around the corner", "아주 가까운 곳에", 1, 16, 33)),
    )

    /** Denies every time, and remembers that it was asked. */
    private class DeniedMic : MicAccess {
        var asked = 0
        override val recognitionAvailable: Boolean = true
        override fun hasPermission(): Boolean = false
        override fun requestPermission(onResult: (Boolean) -> Unit) {
            asked++
            onResult(false)
        }
    }

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
    fun aDeniedMicrophoneShowsTheTypedInputInsteadOfTheSpeakButton() {
        val repository = repository()
        val mic = DeniedMic()
        compose.setContent {
            EnglishQuizTheme {
                RoleplayRoute(content = content, repository = repository, onBack = {}, onQuiz = {}, micAccess = mic)
            }
        }
        compose.onNodeWithText("어떤 역할을 할까요?").assertIsDisplayed()
        compose.onNodeWithText("Sam").performClick()

        // Emma's line is the device's: it is shown in full and the learner moves on.
        compose.onNodeWithText("Can you help me find the station?").assertIsDisplayed()
        compose.onNodeWithText("다음").performClick()

        // Sam's line is the learner's: the cue, not the text, and — the microphone having been
        // refused — a text field rather than a 말하기 button.
        compose.onNodeWithText(SPEAK_PROMPT).assertIsDisplayed()
        // The cue is the meaning alone: the expression's own words are what the learner has to say.
        compose.onNodeWithText("• 아주 가까운 곳에").assertIsDisplayed()
        compose.onNodeWithText("around the corner", substring = true).assertDoesNotExist()
        compose.onNodeWithText("힌트: S i j a t c").assertIsDisplayed()
        compose.onNodeWithText("Sure, it's just around the corner.").assertDoesNotExist()
        compose.onNodeWithText("말하기").assertDoesNotExist()
        compose.onNodeWithText("마이크를 쓸 수 없어 직접 입력해요.").performScrollTo().assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performScrollTo().assertIsDisplayed()
        assertEquals(1, mic.asked)
    }

    @Test
    fun aTypedLineIsGradedLoggedAsRoleplayAndKeptOutOfTheWeakSpots() {
        val repository = repository()
        runBlocking { repository.saveExpression(Language.ENGLISH, "around the corner", "아주 가까운 곳에", 1_000L) }
        var quizzed = 0
        compose.setContent {
            EnglishQuizTheme {
                RoleplayRoute(content = content, repository = repository, onBack = {}, onQuiz = { quizzed++ }, micAccess = DeniedMic(), nowEpochMillis = { 2_000L })
            }
        }
        compose.onNodeWithText("Sam").performClick()
        compose.onNodeWithText("다음").performClick()
        compose.onNodeWithText(REVEAL_LABEL).performClick()
        compose.onNodeWithText("Sure, it's just around the corner.").assertIsDisplayed()

        // Three words of six: below the line, so the turn stays open for another try.
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("sure the corner")
        compose.onNodeWithText("제출").performScrollTo().performClick()
        compose.onNodeWithText("일치 50% (3/6) · 다시 말하기 (남은 기회 2)").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("표현 'around the corner'이(가) 들리지 않았어요").performScrollTo().assertIsDisplayed()

        // The contraction is heard without its apostrophe; the words are still the same words.
        compose.onNode(hasSetTextAction()).performScrollTo().performTextInput("sure its just around the corner")
        compose.onNodeWithText("제출").performScrollTo().performClick()
        compose.onNodeWithText("일치 100% (6/6) · 통과").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("표현 'around the corner'을(를) 말했어요").performScrollTo().assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertDoesNotExist()

        compose.onNodeWithText("결과 보기").performClick()
        compose.onNodeWithText("통과 1/1").assertIsDisplayed()
        compose.onNodeWithText("퀴즈로").performClick()
        assertEquals(1, quizzed)

        val rowId = runBlocking { repository.listSavedExpressions(Language.ENGLISH).single().id }
        // The log is written off the main thread after the verdict is shown.
        compose.waitUntil(5_000L) { runBlocking { repository.learningDao.listQuizAnswers(rowId).isNotEmpty() } }
        runBlocking {
            val row = repository.listSavedExpressions(Language.ENGLISH).single()
            val answers = repository.learningDao.listQuizAnswers(row.id)
            assertEquals(1, answers.size)
            assertEquals(QuizMode.ROLEPLAY.wireValue, answers.single().mode)
            assertTrue(answers.single().isCorrect)
            assertTrue(answers.single().hintUsed)
            assertEquals(2_000L, answers.single().answeredAtEpochMillis)
            // The pass moved nothing else: no review, no "자주 틀리는".
            assertEquals(0, row.consecutiveCorrectCount)
            assertEquals(0, row.incorrectCount)
            assertFalse(repository.listWrongAnswerCounts().containsKey(row.id))
        }
    }

    private companion object {
        const val DATABASE_NAME = "test-roleplay-route.db"
    }
}
