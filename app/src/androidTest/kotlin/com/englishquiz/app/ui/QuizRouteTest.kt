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
import com.englishquiz.app.domain.quiz.QuizMode
import com.englishquiz.app.domain.session.LearningSessionSummary
import com.englishquiz.app.ui.quiz.QuizRoute
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
        repository.markReviewed("sketchy")
        repository.markReviewed("hang out")

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
        // One answer was wrong, so the missed question comes back once (백로그 047) before the result.
        awaitText("틀린 문제 다시 풀기").performScrollTo().performClick()
        awaitText("3 / 3").assertIsDisplayed()
        awaitText("놀다").performScrollTo().performClick()
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
        // 백로그 056: the daily quiz also leaves its answers in the log, as "daily".
        val logged = EXPRESSIONS.flatMap { repository.listQuizAnswers(it) }
        assertEquals(listOf("daily", "daily"), logged.map { it.mode })
        assertEquals(1, logged.count { it.isCorrect })
    }

    @Test
    fun twoCorrectAnswersInARowAccumulateTheComboBonus() = runBlocking {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)
        repository.saveExpression("sketchy", "수상한", NOW - 1_000)
        repository.saveExpression("hang out", "놀다", NOW - 900)
        repository.markReviewed("sketchy")
        repository.markReviewed("hang out")

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
        // At the 꽃 stage the blank is typed (백로그 043/045); the other is just past its card.
        repository.markReviewed("hang out", correctRun = 3)
        repository.markReviewed("sketchy")

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
            runBlocking { db.learningDao().findSavedExpression("hang out")?.consecutiveCorrectCount == 4 }
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
    fun aMissedQuestionComesBackOnceAtTheEndWithoutScoring() = runBlocking {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)
        repository.saveExpression("sketchy", "수상한", NOW - 1_000)
        repository.saveExpression("hang out", "놀다", NOW - 900)
        repository.markReviewed("sketchy")
        repository.markReviewed("hang out")

        var summary: LearningSessionSummary? = null
        compose.setContent {
            EnglishQuizTheme {
                QuizRoute(repository = repository, todayContent = null, onFinished = { summary = it }, now = { NOW }, seed = { SEED })
            }
        }

        // Both wrong on purpose: "놀다" for sketchy, "수상한" for hang out.
        repeat(2) { number ->
            awaitText("${number + 1} / 2")
            val askingSketchy = compose.onAllNodesWithText("sketchy").fetchSemanticsNodes().isNotEmpty()
            compose.onNodeWithText(if (askingSketchy) "놀다" else "수상한").performScrollTo().performClick()
            awaitText("아쉬워요, 오답이에요.")
            compose.onNodeWithText(if (number == 0) "다음 문제" else "틀린 문제 다시 풀기").performScrollTo().performClick()
        }

        // 백로그 047: the retry round — the counter grew, the questions say so, and a right answer
        // now pays nothing and changes no record.
        awaitText("3 / 4")
        compose.onNodeWithText("다시 풀기 · ", substring = true).assertIsDisplayed()
        repeat(2) { number ->
            val askingSketchy = compose.onAllNodesWithText("sketchy").fetchSemanticsNodes().isNotEmpty()
            compose.onNodeWithText(if (askingSketchy) "수상한" else "놀다").performScrollTo().performClick()
            awaitText("정답이에요!")
            assertTrue(compose.onAllNodesWithText("+10점").fetchSemanticsNodes().isEmpty())
            compose.onNodeWithText(if (number == 0) "다음 문제" else "결과 보기").performScrollTo().performClick()
        }
        compose.waitUntil(TIMEOUT_MILLIS) { summary != null }
        val finished = checkNotNull(summary)
        assertEquals(2, finished.quizQuestionCount)
        assertEquals(0, finished.quizCorrectCount)
        assertEquals(0, finished.score)
        EXPRESSIONS.forEach { assertEquals(0, db.learningDao().findSavedExpression(it)?.consecutiveCorrectCount) }
    }

    @Test
    fun aFirstMeetingIsACardWhoseAnswersScheduleButNeverScore() = runBlocking {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)
        repository.saveExpression("sketchy", "수상한", NOW - 1_000, "That sounds sketchy.", shortMeaning = "수상한")
        repository.saveExpression("hang out", "놀다", NOW - 900)

        var summary: LearningSessionSummary? = null
        compose.setContent {
            EnglishQuizTheme {
                QuizRoute(repository = repository, todayContent = null, onFinished = { summary = it }, now = { NOW }, seed = { SEED })
            }
        }

        // 백로그 045/055: two cards, each read and moved past with the one button; both only
        // bring the expression back tomorrow, judged by nobody.
        awaitText("처음 보는 표현이에요. 뜻과 문장을 읽어 보세요.")
        if (compose.onAllNodesWithText("sketchy").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("That sounds sketchy.").assertIsDisplayed()
        }
        compose.onNodeWithText("뜻 확인 완료").performScrollTo().performClick()
        awaitText("2 / 2")
        compose.onNodeWithText("뜻 확인 완료").performScrollTo().performClick()

        compose.waitUntil(TIMEOUT_MILLIS) { summary != null }
        val finished = checkNotNull(summary)
        assertEquals(0, finished.quizQuestionCount)
        assertEquals(2, finished.learnedExpressionCount)
        assertEquals(0, finished.score)
        compose.waitUntil(TIMEOUT_MILLIS) {
            runBlocking { EXPRESSIONS.all { db.learningDao().findSavedExpression(it)?.lastReviewedAtEpochMillis == NOW } }
        }
        EXPRESSIONS.forEach {
            val seen = checkNotNull(db.learningDao().findSavedExpression(it))
            assertEquals(0, seen.incorrectCount)
            assertEquals(0, seen.consecutiveCorrectCount)
            assertEquals(NOW + DAY, seen.nextReviewAtEpochMillis)
        }
    }

    @Test
    fun cardsAlreadyShownTodayCountAgainstTheDaysFive() = runBlocking {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)
        (1..8).forEach { repository.saveExpression("new$it", "뜻$it", NOW - 1_000 - it) }
        // Four cards were seen earlier today (백로그 048): only one more fits.
        (1..4).forEach { repository.markSeen("new$it", NOW - 60_000) }

        compose.setContent {
            EnglishQuizTheme {
                QuizRoute(repository = repository, todayContent = null, onFinished = {}, now = { NOW }, seed = { SEED })
            }
        }

        awaitText("1 / 1")
        compose.onNodeWithText("뜻 확인 완료").assertIsDisplayed()
        Unit
    }

    @Test
    fun practiceLogsEveryAnswerMovesNoScheduleAndEndsOnRecreation() = runBlocking {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)
        repository.saveExpression("sketchy", "수상한", NOW - 1_000)
        repository.saveExpression("hang out", "놀다", NOW - 900)
        // Not due at all, but asked before: practice still asks them.
        listOf("sketchy", "hang out").forEach { repository.saveReviewProgress(it, NOW - DAY, NOW + 5 * DAY, 2, 0, false) }
        val restoration = StateRestorationTester(compose)
        var summary: LearningSessionSummary? = null
        restoration.setContent {
            EnglishQuizTheme {
                QuizRoute(
                    repository = repository,
                    todayContent = null,
                    onFinished = { summary = it },
                    now = { NOW },
                    seed = { SEED },
                    enrolExpressions = false,
                    questionSource = { repo, _ -> repo.listSavedExpressions() },
                    mode = QuizMode.PRACTICE,
                )
            }
        }

        // 백로그 052: "놀다" is wrong for sketchy and right for hang out — one of each, whichever first.
        awaitText("1 / 2").assertIsDisplayed()
        assertTrue(compose.onAllNodesWithText("뜻 확인 완료").fetchSemanticsNodes().isEmpty())
        awaitText("놀다").performScrollTo().performClick()
        awaitText("다음 문제").performScrollTo().performClick()
        awaitText("2 / 2").assertIsDisplayed()
        awaitText("놀다").performScrollTo().performClick()
        // 백로그 056: both answers are logged as practice, and neither row's schedule moved.
        compose.waitUntil(TIMEOUT_MILLIS) {
            runBlocking { repository.listQuizAnswers("sketchy").size == 1 && repository.listQuizAnswers("hang out").size == 1 }
        }
        assertEquals(listOf("practice" to false), repository.listQuizAnswers("sketchy").map { it.mode to it.isCorrect })
        assertEquals(listOf("practice" to true), repository.listQuizAnswers("hang out").map { it.mode to it.isCorrect })
        listOf("sketchy", "hang out").forEach {
            val row = checkNotNull(db.learningDao().findSavedExpression(it))
            assertEquals(0, row.incorrectCount)
            assertEquals(2, row.consecutiveCorrectCount)
            assertEquals(NOW - DAY, row.lastReviewedAtEpochMillis)
            assertEquals(NOW + 5 * DAY, row.nextReviewAtEpochMillis)
        }

        // Nothing dropped out of the list, so a recreation ends the practice instead of re-asking.
        restoration.emulateSavedInstanceStateRestore()
        compose.waitUntil(TIMEOUT_MILLIS) { summary != null }
        assertEquals(1, checkNotNull(summary).quizCorrectCount)
        assertEquals(0, checkNotNull(summary).score)
    }

    @Test
    fun practiceMeetsANeverAskedExpressionWithItsCardInsteadOfFailingIt() = runBlocking {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)
        repository.saveExpression("sketchy", "수상한", NOW - 1_000, "That sounds sketchy.", shortMeaning = "수상한")
        var summary: LearningSessionSummary? = null
        compose.setContent {
            EnglishQuizTheme {
                QuizRoute(
                    repository = repository,
                    todayContent = null,
                    onFinished = { summary = it },
                    now = { NOW },
                    seed = { SEED },
                    enrolExpressions = false,
                    questionSource = { repo, _ -> repo.listSavedExpressions() },
                    mode = QuizMode.PRACTICE,
                )
            }
        }

        // 백로그 052: a first meeting is shown as its card, never asked as a question it cannot know,
        // and the card is only read: the row stays untouched for the daily quiz to meet first.
        awaitText("처음 보는 표현이에요. 뜻과 문장을 읽어 보세요.")
        compose.onNodeWithText("뜻 확인 완료").performScrollTo().performClick()
        compose.waitUntil(TIMEOUT_MILLIS) { summary != null }
        assertEquals(0, checkNotNull(summary).quizQuestionCount)
        val untouched = checkNotNull(db.learningDao().findSavedExpression("sketchy"))
        assertEquals(0, untouched.incorrectCount)
        assertNull(untouched.lastReviewedAtEpochMillis)
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
        // "hang out" was tapped while reading and saved with the meaning the Reader found, and
        // has been asked once since; "sketchy" is met for the first time today.
        repository.saveExpression("hang out", "놀다", NOW - 900, "Let's hang out.")
        // At the 잎 stage its sentence becomes a blank to fill by choice (백로그 045).
        repository.markReviewed("hang out", correctRun = 2)
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

        // The new "sketchy" is a card to read (백로그 045); "hang out" is a blank to fill. Either
        // may come first; the card is met but not counted as a question.
        val expectedLearned = 2
        repeat(2) { number ->
            awaitText("${number + 1} / 2")
            if (compose.onAllNodesWithText("뜻 확인 완료").fetchSemanticsNodes().isNotEmpty()) {
                compose.onNodeWithText("뜻 확인 완료").performScrollTo().performClick()
            } else {
                compose.onNodeWithText("hang out").performScrollTo().performClick()
                awaitText("정답이에요!")
                compose.onNodeWithText(if (number == 0) "다음 문제" else "결과 보기").performScrollTo().performClick()
            }
        }
        compose.waitUntil(TIMEOUT_MILLIS) { summary != null }
        assertEquals(1, checkNotNull(summary).quizQuestionCount)
        assertEquals(1, checkNotNull(summary).quizCorrectCount)
        assertEquals(expectedLearned, checkNotNull(summary).learnedExpressionCount)

        // Both answers reached the review schedule, so neither is asked again today.
        compose.waitUntil(TIMEOUT_MILLIS) {
            runBlocking {
                EXPRESSIONS.all { db.learningDao().findSavedExpression(it)?.lastReviewedAtEpochMillis == NOW }
            }
        }
        repository.listSavedExpressions().forEach { assertReviewScheduled(it) }
    }

    /** Puts [expression] past its first meeting (백로그 045) so the quiz asks it rather than showing a card. */
    private suspend fun LearningRepository.markReviewed(expression: String, correctRun: Int = 0) {
        saveReviewProgress(expression, NOW - DAY, NOW - 1, consecutiveCorrectCount = correctRun, incorrectCount = 0, isMastered = false)
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
        const val DAY = 24 * 60 * 60 * 1000L
        const val SEED = 42L
        const val TIMEOUT_MILLIS = 5_000L
        val EXPRESSIONS = listOf("sketchy", "hang out")
    }
}
