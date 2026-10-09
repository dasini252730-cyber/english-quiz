package com.englishquiz.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.GlossaryEntry
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.PassageSummary
import com.englishquiz.app.data.local.LIBRARY_SESSION_MODE
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.local.LearningSessionEntity
import com.englishquiz.app.data.preferences.AppSettingsRepository
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LearningRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: LearningDatabase? = null

    @After
    fun removeDatabase() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun aTapCountsEveryLookUpButPassageEnrolmentCountsNone() = runBlocking {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)

        // 백로그 044: the Reader's save is a tap; the quiz's passage-wide enrolment is not.
        repository.saveExpression("sketchy", "수상한", 100, "That sounds sketchy.")
        repository.saveExpression("sketchy", "수상한", 200, "That sounds sketchy.")
        repository.saveExpressionIfNew("hang out", "놀다", 100, "Let's hang out.")
        repository.saveExpressionIfNew("hang out", "놀다", 200, "Let's hang out.")

        assertEquals(2, db.learningDao().findSavedExpression("sketchy")?.tapCount)
        assertEquals(0, db.learningDao().findSavedExpression("hang out")?.tapCount)
    }

    @Test
    fun aCardSeenIsScheduledForTomorrowWithNothingHeldAgainstIt() = runBlocking {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)
        repository.saveExpressionIfNew("sketchy", "수상한", 100, "That sounds sketchy.", shortMeaning = "수상한")

        val seen = repository.markSeen("sketchy", seenAtEpochMillis = 1_000L)

        // 백로그 045/046: scheduled, not judged; and the gloss the passage gave is kept.
        assertEquals(1_000L, seen.lastReviewedAtEpochMillis)
        assertEquals(1_000L + 24 * 60 * 60 * 1000L, seen.nextReviewAtEpochMillis)
        assertEquals(0, seen.incorrectCount)
        assertEquals(0, seen.consecutiveCorrectCount)
        assertEquals("수상한", seen.shortMeaning)

        // A row saved before the gloss existed takes the gloss a later passage brings (백로그 046).
        repository.saveExpressionIfNew("hang out", "놀다", 100, "Let's hang out.")
        repository.saveExpressionIfNew("hang out", "놀다", 200, "Let's hang out.", shortMeaning = "놀다")
        repository.saveExpression("hang out", "놀다", 300, "Let's hang out.", shortMeaning = "다른 뜻")
        assertEquals("놀다", db.learningDao().findSavedExpression("hang out")?.shortMeaning)
    }

    @Test
    fun duplicateExpressionKeepsExistingReviewStateAfterDatabaseReopen() = runBlocking {
        val firstDatabase = LearningDatabase.create(context, DATABASE_NAME)
        database = firstDatabase
        val firstRepository = LearningRepository(firstDatabase)

        firstRepository.saveExpression(
            displayExpression = "Pull   It Off",
            contextMeaning = "성공적으로 해내다",
            savedAtEpochMillis = 100,
            contextSentence = "I knew you could pull it off.",
        )
        firstRepository.saveReviewProgress(
            displayExpression = "pull it off",
            lastReviewedAtEpochMillis = 150,
            nextReviewAtEpochMillis = 200,
            consecutiveCorrectCount = 3,
            incorrectCount = 1,
            isMastered = true,
        )
        firstRepository.recordCompletedSession(
            LearningSessionEntity(
                learningDate = "2026-09-18",
                completedAtEpochMillis = 300,
                learnedExpressionCount = 5,
                newlySavedExpressionCount = 2,
                quizCorrectCount = 8,
                quizQuestionCount = 10,
            ),
        )
        firstRepository.recordCompletedSession(
            LearningSessionEntity(
                learningDate = "2026-09-18",
                completedAtEpochMillis = 400,
                learnedExpressionCount = 2,
                newlySavedExpressionCount = 1,
                quizCorrectCount = 1,
                quizQuestionCount = 3,
            ),
        )
        firstDatabase.close()
        database = null

        val reopenedDatabase = LearningDatabase.create(context, DATABASE_NAME)
        database = reopenedDatabase
        val reopenedRepository = LearningRepository(reopenedDatabase)
        reopenedRepository.saveExpression(
            displayExpression = "pull it off",
            contextMeaning = "덮어쓰면 안 되는 새 뜻",
            savedAtEpochMillis = 200,
            contextSentence = "덮어쓰면 안 되는 새 문맥",
        )

        val stored = reopenedDatabase.learningDao().findSavedExpression("pull it off")
        assertNotNull(stored)
        requireNotNull(stored)
        assertEquals("Pull   It Off", stored.displayExpression)
        assertEquals("성공적으로 해내다", stored.contextMeaning)
        assertEquals(100L, stored.firstSavedAtEpochMillis)
        assertEquals(150L, stored.lastReviewedAtEpochMillis)
        assertEquals(200L, stored.nextReviewAtEpochMillis)
        assertEquals(3, stored.consecutiveCorrectCount)
        assertEquals(1, stored.incorrectCount)
        assertTrue(stored.isMastered)
        assertEquals("I knew you could pull it off.", stored.contextSentence)
        assertEquals(1, reopenedDatabase.learningDao().savedExpressionCount())
        val sessions = reopenedDatabase.learningDao().findLearningSessions("2026-09-18")
        assertEquals(2, sessions.size)
        assertEquals(listOf(300L, 400L), sessions.map { it.completedAtEpochMillis })
        assertEquals(listOf(10, 3), sessions.map { it.quizQuestionCount })
        reopenedDatabase.close()
        database = null

        val settingsFile = context.filesDir.resolve("test-app-settings.preferences_pb")
        val firstSettingsJob = SupervisorJob()
        val reopenedSettingsJob = SupervisorJob()
        try {
            val firstSettingsRepository = AppSettingsRepository(
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(firstSettingsJob + Dispatchers.IO),
                    produceFile = { settingsFile },
                ),
            )
            firstSettingsRepository.saveAssessmentResult(assessmentLevel = 3)
            firstSettingsJob.cancelAndJoin()

            val reopenedSettings = AppSettingsRepository(
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(reopenedSettingsJob + Dispatchers.IO),
                    produceFile = { settingsFile },
                ),
            ).settings.first()
            assertTrue(reopenedSettings.isAssessmentComplete)
            assertEquals(4, reopenedSettings.level(ContentMode.CONVERSATION))
        } finally {
            firstSettingsJob.cancelAndJoin()
            reopenedSettingsJob.cancelAndJoin()
            settingsFile.delete()
        }
    }

    @Test
    fun aModesRecentSessionsExcludeOtherModesLibraryRereadsAndSessionsWithoutAMode() = runBlocking {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)
        fun session(completedAt: Long, mode: String, questions: Int) = LearningSessionEntity(
            learningDate = "2026-09-30",
            completedAtEpochMillis = completedAt,
            learnedExpressionCount = questions,
            newlySavedExpressionCount = 0,
            quizCorrectCount = questions,
            quizQuestionCount = questions,
            mode = mode,
        )
        repository.recordCompletedSession(session(100, "", 1))
        repository.recordCompletedSession(session(200, ContentMode.STORY.wireValue, 2))
        repository.recordCompletedSession(session(300, LIBRARY_SESSION_MODE, 3))
        repository.recordCompletedSession(session(400, ContentMode.CONVERSATION.wireValue, 4))
        repository.recordCompletedSession(session(500, ContentMode.CONVERSATION.wireValue, 5))

        // Newest first, this mode only (백로그 034): the level suggestion must not read a story
        // session, a re-read of an old passage, or a session from before modes were recorded.
        val recent = repository.listRecentSessionSummaries(ContentMode.CONVERSATION, 3)
        assertEquals(listOf(5, 4), recent.map { it.quizQuestionCount })
        assertEquals(listOf(2), repository.listRecentSessionSummaries(ContentMode.STORY, 3).map { it.quizQuestionCount })
    }

    @Test
    fun thePreviousEpisodeIsTheNewestStoryBeforeTheDayWithASynopsis() = runBlocking {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val first = LearningRepository(db)
        val episode = LearningContent(
            title = "Ep. 1",
            mode = ContentMode.STORY,
            segments = listOf(ContentSegment("Narrator", "Alex planned a Saturday.")),
            expressions = listOf(ContentExpression("planned", "계획했다", 0, 5, 12)),
            synopsis = "Alex planned a Saturday.",
        )
        // 백로그 054: the previous episode is the newest story strictly before the day, with a synopsis.
        assertNull(first.findPreviousSummary(ContentMode.STORY, "2026-09-27"))
        first.saveDailyContent("2026-09-24", content = episode, nowEpochMillis = 90)
        first.saveDailyContent("2026-09-25", content = episode.copy(title = "Ep. 2", synopsis = ""), nowEpochMillis = 95)
        assertEquals(null, first.findPreviousSummary(ContentMode.STORY, "2026-09-26")?.takeIf { it.title == "Ep. 1" })
        assertEquals(PassageSummary(ContentMode.STORY, "Ep. 1", "Alex planned a Saturday."), first.findPreviousSummary(ContentMode.STORY, "2026-09-25"))
        assertNull(first.findPreviousSummary(ContentMode.STORY, "2026-09-24"))
    }

    @Test
    fun dailyContentIsKeyedByDateAndModeAndSurvivesReopen() = runBlocking {
        val firstDatabase = LearningDatabase.create(context, DATABASE_NAME)
        database = firstDatabase
        val first = LearningRepository(firstDatabase)
        val conversation = LearningContent(
            title = "A Saturday Plan",
            mode = ContentMode.CONVERSATION,
            segments = listOf(ContentSegment("Alex", "Let's pull it off together.")),
            expressions = listOf(ContentExpression("pull it off", "해내다", 0, 6, 17)),
            // Part of the round trip on purpose: a stored passage that lost its glossary would
            // send every word tap back to a paid call (백로그 024).
            glossary = listOf(GlossaryEntry("together", "함께")),
        )

        first.saveDailyContent("2026-09-26", content = conversation, nowEpochMillis = 100)

        // Same day and mode reads back the exact passage; another mode or day has nothing.
        assertEquals(conversation, first.findDailyContent("2026-09-26", ContentMode.CONVERSATION))
        assertNull(first.findDailyContent("2026-09-26", ContentMode.STORY))
        assertNull(first.findDailyContent("2026-09-27", ContentMode.CONVERSATION))
        // A passage stored in Korean (백로그 051) reads as none, so the session generates again.
        val korean = conversation.copy(
            segments = listOf(ContentSegment("지은", "어? 민지? 정말 오랜만이다! 요즘 어떻게 지내?")),
            expressions = listOf(ContentExpression("오랜만이다", "오래간만", 0, 10, 15)),
            glossary = emptyList(),
        )
        first.saveDailyContent("2026-09-26", content = korean, nowEpochMillis = 150)
        assertNull(first.findDailyContent("2026-09-26", ContentMode.CONVERSATION))
        first.saveDailyContent("2026-09-26", content = conversation, nowEpochMillis = 200)
        firstDatabase.close()

        // The whole point: a fresh process the same day must not generate again.
        val reopenedDatabase = LearningDatabase.create(context, DATABASE_NAME)
        database = reopenedDatabase
        val reopened = LearningRepository(reopenedDatabase)
        assertEquals(conversation, reopened.findDailyContent("2026-09-26", ContentMode.CONVERSATION))

        // A new day's save keeps yesterday's row: earlier passages are the library (백로그 026).
        val story = conversation.copy(title = "Sunday", mode = ContentMode.STORY)
        reopened.saveDailyContent("2026-09-27", content = story, nowEpochMillis = 200)
        assertEquals(conversation, reopened.findDailyContent("2026-09-26", ContentMode.CONVERSATION))
        assertEquals(story, reopened.findDailyContent("2026-09-27", ContentMode.STORY))
        assertEquals(
            listOf("2026-09-27" to "Sunday", "2026-09-26" to "A Saturday Plan"),
            reopened.listLibrary().map { it.learningDate to it.title },
        )
    }

    private companion object {
        const val DATABASE_NAME = "test-learning-repository.db"
    }
}
