package com.englishquiz.app.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.ai.ComprehensionQuestion
import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.local.SavedExpressionEntity
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.domain.session.LearningSessionSummary
import com.englishquiz.app.ui.quiz.QuizRoute
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the real [QuizRoute] against Room, so the acceptance criteria that live in the route
 * itself are proven: every answer reaches the learning record exactly once, and finishing the
 * quiz hands on a summary that matches what the learner actually answered (백로그 010).
 */
@RunWith(AndroidJUnit4::class)
class QuizRouteTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: LearningDatabase? = null

    @After
    fun removeDatabase() {
        database?.close()
        database = null
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun everyAnswerReachesTheLearningRecordExactlyOnce() = runBlocking {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)
        repository.saveExpression("sketchy", "수상한", NOW - 1_000)
        repository.saveExpression("hang out", "놀다", NOW - 900)

        var summary: LearningSessionSummary? = null
        compose.setContent {
            EnglishQuizTheme {
                QuizRoute(
                    repository = repository,
                    todayContent = null,
                    onFinished = { summary = it },
                    now = { NOW },
                    seed = { SEED },
                )
            }
        }

        // Both questions offer the same two meanings, and "수상한" is the right answer for
        // exactly one of the two expressions, so tapping it on both questions scores exactly one
        // correct answer whichever order the shuffle picks.
        awaitText("1 / 2").assertIsDisplayed()
        awaitText("수상한").performScrollTo().performClick()
        awaitText("다음 문제").performScrollTo().performClick()

        awaitText("2 / 2").assertIsDisplayed()
        awaitText("수상한").performScrollTo().performClick()
        // The final question offers the result screen rather than another question.
        awaitText("결과 보기").performScrollTo().performClick()

        compose.waitUntil(TIMEOUT_MILLIS) { summary != null }
        val finished = checkNotNull(summary)
        assertEquals(2, finished.quizQuestionCount)
        assertEquals(1, finished.quizCorrectCount)
        // One correct answer is worth the base 10 points and a best run of one (백로그 035).
        assertEquals(10, finished.score)
        assertEquals(1, finished.maxCombo)

        // onFinished fires as soon as the last answer is dispatched, but the answer itself is
        // written on Room's own executor, which Compose's idling does not track. Wait for the
        // record before reading it, or a slow device reads the row from before the last answer.
        compose.waitUntil(TIMEOUT_MILLIS) {
            runBlocking {
                EXPRESSIONS.all {
                    db.learningDao().findSavedExpression(it)?.lastReviewedAtEpochMillis == NOW
                }
            }
        }

        val answered = listOf(
            checkNotNull(db.learningDao().findSavedExpression("sketchy")),
            checkNotNull(db.learningDao().findSavedExpression("hang out")),
        )
        // Each expression was evaluated once: one right, one wrong, neither counted twice.
        answered.forEach { assertEquals(NOW, it.lastReviewedAtEpochMillis) }
        assertEquals(1, answered.count { it.consecutiveCorrectCount == 1 })
        assertEquals(1, answered.count { it.consecutiveCorrectCount == 0 })
        assertEquals(1, answered.count { it.incorrectCount == 1 })
        assertEquals(1, answered.count { it.incorrectCount == 0 })
        answered.forEach { assertReviewScheduled(it) }
    }

    @Test
    fun twoCorrectAnswersInARowAccumulateTheComboBonus() = runBlocking {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)
        repository.saveExpression("sketchy", "수상한", NOW - 1_000)
        repository.saveExpression("hang out", "놀다", NOW - 900)

        var summary: LearningSessionSummary? = null
        compose.setContent {
            EnglishQuizTheme {
                QuizRoute(
                    repository = repository,
                    todayContent = null,
                    onFinished = { summary = it },
                    now = { NOW },
                    seed = { SEED },
                )
            }
        }

        // Each question shows its expression, so the right meaning can be picked whichever order
        // the shuffle produced.
        repeat(2) { number ->
            awaitText("${number + 1} / 2").assertIsDisplayed()
            val askingSketchy = compose.onAllNodesWithText("sketchy").fetchSemanticsNodes().isNotEmpty()
            awaitText(if (askingSketchy) "수상한" else "놀다").performScrollTo().performClick()
            // 백로그 035: the second answer in a row pays 15, shown on the card.
            awaitText(if (number == 0) "+10점" else "+15점").performScrollTo().assertIsDisplayed()
            awaitText(if (number == 0) "다음 문제" else "결과 보기").performScrollTo().performClick()
        }

        compose.waitUntil(TIMEOUT_MILLIS) { summary != null }
        val finished = checkNotNull(summary)
        assertEquals(2, finished.quizCorrectCount)
        assertEquals(25, finished.score)
        assertEquals(2, finished.maxCombo)
    }

    @Test
    fun aKnownExpressionIsTypedAndJudgedOnTheNormalisedText() = runBlocking {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)
        repository.saveExpression("hang out", "놀다", NOW - 900, "Let's hang out.")
        repository.saveExpression("sketchy", "수상한", NOW - 1_000)
        // One right answer already: from here on the blank is typed (백로그 043).
        repository.saveReviewProgress("hang out", NOW - 500, NOW - 100, consecutiveCorrectCount = 1, incorrectCount = 0, isMastered = false)

        var summary: LearningSessionSummary? = null
        compose.setContent {
            EnglishQuizTheme {
                QuizRoute(repository = repository, todayContent = null, onFinished = { summary = it }, now = { NOW }, seed = { SEED })
            }
        }

        repeat(2) { number ->
            awaitText("${number + 1} / 2").assertIsDisplayed()
            if (compose.onAllNodesWithText("Let's ____.").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNode(hasSetTextAction()).performTextInput("Hang Out!")
                compose.onNodeWithText("제출").performScrollTo().performClick()
            } else {
                awaitText("수상한").performScrollTo().performClick()
            }
            awaitText("정답이에요!")
            awaitText(if (number == 0) "다음 문제" else "결과 보기").performScrollTo().performClick()
        }

        compose.waitUntil(TIMEOUT_MILLIS) { summary != null }
        assertEquals(2, checkNotNull(summary).quizCorrectCount)
        compose.waitUntil(TIMEOUT_MILLIS) {
            runBlocking { db.learningDao().findSavedExpression("hang out")?.consecutiveCorrectCount == 2 }
        }
    }

    @Test
    fun aPassageQuestionOpensTheQuizAndRecordsNothing() = runBlocking {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)
        repository.saveExpression("sketchy", "수상한", NOW - 1_000)
        repository.saveExpression("hang out", "놀다", NOW - 900)
        val content = LearningContent(
            title = "A cafe",
            mode = ContentMode.CONVERSATION,
            segments = listOf(ContentSegment("Emma", "That sounds sketchy.")),
            expressions = listOf(ContentExpression("sketchy", "수상한", 0, 12, 19)),
            comprehension = listOf(ComprehensionQuestion("Emma는 왜?", listOf("의심스러워서", "기뻐서"), 0, "수상하다고 했다.")),
        )

        var summary: LearningSessionSummary? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            EnglishQuizTheme {
                QuizRoute(
                    repository = repository,
                    todayContent = content,
                    onFinished = { summary = it },
                    now = { NOW },
                    seed = { SEED },
                    enrolExpressions = false,
                )
            }
        }

        // 백로그 042: the passage question is first and counts in the total; it touches no expression.
        awaitText("1 / 3").assertIsDisplayed()
        awaitText("의심스러워서").performScrollTo().performClick()
        awaitText("+10점")
        // Answered but not yet moved on: a recreation must not offer the same question (and its
        // points) a second time; the quiz resumes at the next question with the score kept.
        restoration.emulateSavedInstanceStateRestore()
        awaitText("2 / 3").assertIsDisplayed()
        compose.onNodeWithText("10점").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Emma는 왜?").fetchSemanticsNodes().isEmpty())
        assertEquals(null, db.learningDao().findSavedExpression("sketchy")?.lastReviewedAtEpochMillis)
        assertEquals(null, db.learningDao().findSavedExpression("hang out")?.lastReviewedAtEpochMillis)
        assertEquals(null, summary)
    }

    @Test
    fun thePassageQuestionsAreSkippedWhenTheCallerSaysSo() = runBlocking {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)
        repository.saveExpression("sketchy", "수상한", NOW - 1_000)
        repository.saveExpression("hang out", "놀다", NOW - 900)
        val content = LearningContent(
            title = "A cafe",
            mode = ContentMode.CONVERSATION,
            segments = listOf(ContentSegment("Emma", "That sounds sketchy.")),
            expressions = listOf(ContentExpression("sketchy", "수상한", 0, 12, 19)),
            comprehension = listOf(ComprehensionQuestion("Emma는 왜?", listOf("의심스러워서", "기뻐서"), 0, "")),
        )
        compose.setContent {
            EnglishQuizTheme {
                QuizRoute(
                    repository = repository,
                    todayContent = content,
                    onFinished = {},
                    now = { NOW },
                    seed = { SEED },
                    enrolExpressions = false,
                    skipComprehension = { true },
                )
            }
        }

        // 백로그 042: a second quiz on the same passage asks only the expressions.
        awaitText("1 / 2").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("Emma는 왜?").fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun quizWithNothingDueFinishesWithAnEmptySummaryInsteadOfAnError() = runBlocking {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)

        var summary: LearningSessionSummary? = null
        compose.setContent {
            EnglishQuizTheme {
                QuizRoute(
                    repository = repository,
                    todayContent = null,
                    onFinished = { summary = it },
                    now = { NOW },
                    seed = { SEED },
                )
            }
        }

        awaitText("오늘 복습할 표현이 없어요.").performScrollTo().assertIsDisplayed()
        awaitText("완료").performScrollTo().performClick()

        compose.waitUntil(TIMEOUT_MILLIS) { summary != null }
        val finished = checkNotNull(summary)
        assertEquals(0, finished.quizQuestionCount)
        assertEquals(0, finished.quizCorrectCount)
    }

    @Test
    fun todaysAnnotatedExpressionsAreQuizzedWithoutBeingTappedAndEnterTheReviewBox() = runBlocking {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)
        // "hang out" was tapped while reading and saved with the meaning the Reader found.
        repository.saveExpression("hang out", "놀다", NOW - 900, "Let's hang out.")
        val content = LearningContent(
            title = "A cafe",
            mode = ContentMode.CONVERSATION,
            segments = listOf(
                ContentSegment("Emma", "That sounds sketchy."),
                ContentSegment("Sam", "Let's hang out."),
            ),
            expressions = listOf(
                // The model's annotation keeps the full stop; the Reader's token does not.
                ContentExpression("sketchy.", "수상한", 0, 12, 20),
                ContentExpression("hang out", "어울리다", 1, 6, 14),
            ),
        )
        var enrolled = -1
        var summary: LearningSessionSummary? = null
        compose.setContent {
            EnglishQuizTheme {
                QuizRoute(
                    repository = repository,
                    todayContent = content,
                    onFinished = { summary = it },
                    now = { NOW },
                    seed = { SEED },
                    onExpressionsEnrolled = { enrolled = it },
                )
            }
        }

        // The untapped "sketchy" joins the tapped "hang out" (백로그 031): a two-question quiz. It
        // is saved under the Reader's key (no full stop) with the passage's meaning, the tapped
        // row keeps the meaning it already had, and only the one new row is reported.
        awaitText("1 / 2").assertIsDisplayed()
        assertEquals(1, enrolled)
        val saved = repository.listSavedExpressions()
        assertEquals(
            setOf("sketchy" to "수상한", "hang out" to "놀다"),
            saved.map { it.displayExpression to it.contextMeaning }.toSet(),
        )
        assertEquals("That sounds sketchy.", saved.single { it.displayExpression == "sketchy" }.contextSentence)

        // Both rows carry their sentence, so both are fill-in-the-blank questions whose options
        // are the expressions themselves; "hang out" fits exactly one of the two blanks.
        awaitText("hang out").performScrollTo().performClick()
        awaitText("다음 문제").performScrollTo().performClick()
        awaitText("2 / 2").assertIsDisplayed()
        awaitText("hang out").performScrollTo().performClick()
        awaitText("결과 보기").performScrollTo().performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { summary != null }
        assertEquals(2, checkNotNull(summary).quizQuestionCount)

        // Both answers reached the review schedule, so neither is asked again today.
        compose.waitUntil(TIMEOUT_MILLIS) {
            runBlocking {
                EXPRESSIONS.all { db.learningDao().findSavedExpression(it)?.lastReviewedAtEpochMillis == NOW }
            }
        }
        repository.listSavedExpressions().forEach { assertReviewScheduled(it) }
    }

    private fun assertReviewScheduled(expression: SavedExpressionEntity) {
        assertEquals(true, (expression.nextReviewAtEpochMillis ?: 0L) > NOW)
    }

    /**
     * Waits for [text] to appear and hands back its node. Scrolling is left to the caller: the
     * question counter lives in the fixed header, which has no scrollable parent, so scrolling to
     * it throws instead of being a harmless no-op.
     */
    private fun awaitText(text: String): SemanticsNodeInteraction {
        compose.waitUntil(TIMEOUT_MILLIS) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
        return compose.onNodeWithText(text)
    }

    private companion object {
        const val DATABASE_NAME = "test-quiz-route.db"
        const val NOW = 1_700_000_000_000L
        const val SEED = 42L
        const val TIMEOUT_MILLIS = 5_000L
        val EXPRESSIONS = listOf("sketchy", "hang out")
    }
}
