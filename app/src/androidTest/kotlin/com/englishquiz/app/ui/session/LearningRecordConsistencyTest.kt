package com.englishquiz.app.ui.session

import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContextualMeaning
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.preferences.GameProgressRepository
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.data.repository.listAllSessions
import com.englishquiz.app.data.repository.listLearningDates
import com.englishquiz.app.domain.game.GameStatsPolicy
import com.englishquiz.app.domain.game.LearnerLevelPolicy
import com.englishquiz.app.domain.quiz.QuizMode
import com.englishquiz.app.domain.quiz.QuizOption
import com.englishquiz.app.domain.quiz.QuizQuestion
import com.englishquiz.app.domain.quiz.QuizQuestionType
import com.englishquiz.app.domain.streak.StreakPolicy
import com.englishquiz.app.ui.quiz.recordQuizAnswerFor
import com.englishquiz.app.ui.session.RecordConsistencyFixtures.DAY1_NOON
import com.englishquiz.app.ui.session.RecordConsistencyFixtures.DAY_MILLIS
import com.englishquiz.app.ui.session.RecordConsistencyFixtures.EMPTY_QUIZ_BUTTON
import com.englishquiz.app.ui.session.RecordConsistencyFixtures.MEANING_HANG
import com.englishquiz.app.ui.session.RecordConsistencyFixtures.MEANING_PULL
import com.englishquiz.app.ui.session.RecordConsistencyFixtures.PULL_IT_OFF
import com.englishquiz.app.ui.session.RecordConsistencyFixtures.RESULT_TITLE
import com.englishquiz.app.ui.session.RecordConsistencyFixtures.expectedTotalPoints
import com.englishquiz.app.ui.session.RecordConsistencyFixtures.passage
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * One learner's records over five days, driven through the real session route with an injected
 * clock (백로그 061): the session rows, the streak, the review schedule, the answer log and the
 * game's points must agree with each other after every step, including a process restart on the
 * result screen.
 */
@RunWith(AndroidJUnit4::class)
class LearningRecordConsistencyTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: LearningDatabase? = null
    private val storeJob = SupervisorJob()
    private val storeFile = File(context.filesDir, "record-consistency-${System.nanoTime()}.preferences_pb")

    /** The clock every route reads; the test moves it, never the system. */
    private var now = DAY1_NOON
    private val zone = ZoneOffset.UTC
    private val todayIso get() = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)

    @After
    fun tearDown() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
        storeJob.cancel()
        storeFile.delete()
    }

    private fun openRepository(): LearningRepository {
        database?.close()
        val opened = LearningDatabase.create(context, DATABASE_NAME)
        database = opened
        return LearningRepository(opened)
    }

    @Test
    fun fiveDaysOfSessionsKeepEveryRecordConsistent() {
        val gameRepository = GameProgressRepository(
            PreferenceDataStoreFactory.create(scope = CoroutineScope(storeJob + Dispatchers.IO), produceFile = { storeFile }),
        )
        var repository by mutableStateOf(openRepository())
        var mode by mutableStateOf(ContentMode.CONVERSATION)
        var entry by mutableIntStateOf(0)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            EnglishQuizTheme {
                key(entry) {
                    LearningSessionRoute(
                        mode = mode,
                        repository = repository,
                        difficulty = 2,
                        generateContent = { passage(it) },
                        explainMeaning = { ContextualMeaning(it.expression, "뜻") },
                        onExit = {},
                        nowEpochMillis = { now },
                        zoneId = zone,
                        gameRepository = gameRepository,
                    )
                }
            }
        }
        val day1 = todayIso

        // Day 1, conversation: the passage's two expressions are met as cards; the session lands.
        compose.readPassageAndOpenQuiz()
        compose.readTwoCards()
        assertSessions(repository, listOf(day1 to "conversation"))

        // The process dies on the result screen and comes back: a new database handle, the saved
        // UI state restored. The session must not be recorded a second time.
        repository = openRepository()
        restoration.emulateSavedInstanceStateRestore()
        compose.awaitText(RESULT_TITLE)
        compose.waitForIdle()
        assertSessions(repository, listOf(day1 to "conversation"))

        // Day 1, story, an hour later (a real clock never stamps two sessions with one millisecond,
        // which is what makes the session row's idempotency key safe): nothing is due yet, the quiz
        // is empty, and the streak is still one day.
        now += HOUR_MILLIS
        mode = ContentMode.STORY
        entry++
        compose.readPassageAndOpenQuiz()
        compose.awaitText(SessionRouteFixtures.EMPTY_QUIZ)
        compose.onNodeWithText(EMPTY_QUIZ_BUTTON).performClick()
        compose.awaitText(RESULT_TITLE)
        assertSessions(repository, listOf(day1 to "conversation", day1 to "story"))
        assertEquals(1, streakDays(repository))
        compose.awaitText("1일 연속 학습 중")

        // Days 2-5: "pull it off" is answered wrong, right, wrong, right; "hang out" the other way
        // round. Each day the missed one is retried right at the end, which must leave no record.
        val expected = mutableListOf(day1 to "conversation", day1 to "story")
        listOf(MEANING_HANG, MEANING_PULL, MEANING_HANG, MEANING_PULL).forEachIndexed { index, tap ->
            now += DAY_MILLIS
            mode = ContentMode.CONVERSATION
            entry++
            compose.readPassageAndOpenQuiz()
            compose.answerBothQuestionsWith(tap)
            expected += todayIso to "conversation"
            assertSessions(repository, expected)
            assertEquals(index + 2, streakDays(repository))

            val row = checkNotNull(runBlocking { database!!.learningDao().findSavedExpression(PULL_IT_OFF) })
            val wasRight = tap == MEANING_PULL
            assertEquals(if (wasRight) 1 else 0, row.consecutiveCorrectCount)
            assertEquals(now, row.lastReviewedAtEpochMillis)
            assertEquals(now + DAY_MILLIS, row.nextReviewAtEpochMillis)
            // One daily row per day, none for the retry: the log and the schedule moved together.
            val answers = runBlocking { repository.listQuizAnswers(PULL_IT_OFF) }
            assertEquals(index + 1, answers.size)
            assertEquals(List(index + 1) { it % 2 == 1 }, answers.map { it.isCorrect })
            assertTrue(answers.all { it.mode == "daily" })
            assertEquals(now, answers.last().answeredAtEpochMillis)
        }

        // The game layer's total is exactly the session scores plus each day's mission bonus.
        val sessions = runBlocking { repository.listAllSessions() }
        val stats = GameStatsPolicy.compute(sessions, runBlocking { repository.listSavedExpressions() }, runBlocking { gameRepository.progress.first() }, todayIso)
        assertEquals(expectedTotalPoints(sessions), stats.totalPoints)
        assertEquals(4 * 10, sessions.sumOf { it.score })
        assertEquals(LearnerLevelPolicy.of(stats.totalPoints).level, stats.level.level)
        assertEquals(5, stats.streakDays)
        compose.awaitText("5일 연속 학습 중")
    }

    @Test
    fun aFailedAnswerLogRollsTheScheduleBack() = runBlocking {
        // 백로그 056: the schedule change and the answer row are one transaction. With the log
        // table gone the write fails as a whole, so the row is exactly as it was.
        val repository = openRepository()
        repository.saveExpression(PULL_IT_OFF, MEANING_PULL, DAY1_NOON - 1_000)
        repository.saveReviewProgress(PULL_IT_OFF, DAY1_NOON - DAY_MILLIS, DAY1_NOON, 0, 0, false)
        database!!.openHelper.writableDatabase.execSQL("DROP TABLE quiz_answers")
        val question = QuizQuestion(PULL_IT_OFF, QuizQuestionType.MULTIPLE_CHOICE, PULL_IT_OFF, listOf(QuizOption(MEANING_PULL, true)), "")

        try {
            repository.recordQuizAnswerFor(question, wasCorrect = true, mode = QuizMode.DAILY, hintUsed = false, nowEpochMillis = DAY1_NOON)
            fail("the write should have failed without the answer table")
        } catch (_: Exception) {
            // expected
        }
        val row = checkNotNull(database!!.learningDao().findSavedExpression(PULL_IT_OFF))
        assertEquals(0, row.consecutiveCorrectCount)
        assertEquals(DAY1_NOON - DAY_MILLIS, row.lastReviewedAtEpochMillis)
        assertEquals(DAY1_NOON, row.nextReviewAtEpochMillis)
    }

    /** Exactly these (date, mode) sessions, each once, in order of completion. */
    private fun assertSessions(repository: LearningRepository, expected: List<Pair<String, String>>) {
        compose.waitUntil(SessionRouteFixtures.TIMEOUT_MILLIS) {
            runBlocking { repository.listAllSessions() }.size >= expected.size
        }
        val sessions = runBlocking { repository.listAllSessions() }
        assertEquals(expected, sessions.map { it.learningDate to it.mode })
        assertEquals(expected.size, sessions.map { it.learningDate to it.mode }.toSet().size)
        assertNull(sessions.groupBy { it.learningDate to it.completedAtEpochMillis }.values.firstOrNull { it.size > 1 })
    }

    private fun streakDays(repository: LearningRepository): Int =
        StreakPolicy.currentStreakDays(runBlocking { repository.listLearningDates() }, todayIso)

    private companion object {
        const val DATABASE_NAME = "test-record-consistency.db"
        const val HOUR_MILLIS = 60 * 60 * 1000L
    }
}
