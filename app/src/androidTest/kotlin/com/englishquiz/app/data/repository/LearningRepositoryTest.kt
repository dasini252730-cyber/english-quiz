package com.englishquiz.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.GlossaryEntry
import com.englishquiz.app.data.ai.LearningContent
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
            firstSettingsRepository.saveAssessmentResult(difficulty = 3)
            firstSettingsJob.cancelAndJoin()

            val reopenedSettings = AppSettingsRepository(
                PreferenceDataStoreFactory.create(
                    scope = CoroutineScope(reopenedSettingsJob + Dispatchers.IO),
                    produceFile = { settingsFile },
                ),
            ).settings.first()
            assertTrue(reopenedSettings.isAssessmentComplete)
            assertEquals(3, reopenedSettings.currentDifficulty)
        } finally {
            firstSettingsJob.cancelAndJoin()
            reopenedSettingsJob.cancelAndJoin()
            settingsFile.delete()
        }
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
