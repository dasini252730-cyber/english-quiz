package com.englishquiz.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.local.LearningDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewProgressPersistenceTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: LearningDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun correctAnswersPersistMasteryAndDueQueryIncludesMasteredAtBoundary() = runBlocking {
        val firstDatabase = LearningDatabase.create(context, DATABASE_NAME)
        database = firstDatabase
        val repository = LearningRepository(firstDatabase)
        repository.saveExpression("pull it off", "해내다", EVALUATED_AT)
        repository.saveExpression("on the fence", "결정을 못 한", EVALUATED_AT + 1)

        val first = repository.recordAnswer("pull it off", true, EVALUATED_AT)
        val second = repository.recordAnswer(
            "pull it off",
            true,
            requireNotNull(first.nextReviewAtEpochMillis),
        )
        val third = repository.recordAnswer(
            "pull it off",
            true,
            requireNotNull(second.nextReviewAtEpochMillis),
        )
        assertFalse(third.isMastered)
        val fourth = repository.recordAnswer(
            "pull it off",
            true,
            requireNotNull(third.nextReviewAtEpochMillis),
        )
        val masteredDueAt = requireNotNull(fourth.nextReviewAtEpochMillis)

        assertTrue(fourth.isMastered)
        assertFalse(
            repository.findDueExpressions(masteredDueAt - 1)
                .any { it.normalizedExpression == "pull it off" },
        )
        assertTrue(
            repository.findDueExpressions(masteredDueAt + 1)
                .any { it.normalizedExpression == "pull it off" },
        )
        assertEquals(
            setOf("on the fence", "pull it off"),
            repository.findDueExpressions(masteredDueAt)
                .map { it.normalizedExpression }
                .toSet(),
        )

        firstDatabase.close()
        val reopenedDatabase = LearningDatabase.create(context, DATABASE_NAME)
        database = reopenedDatabase
        val stored = requireNotNull(reopenedDatabase.learningDao().findSavedExpression("pull it off"))
        assertEquals(4, stored.consecutiveCorrectCount)
        assertTrue(stored.isMastered)
        assertEquals(masteredDueAt, stored.nextReviewAtEpochMillis)
    }

    private companion object {
        const val DATABASE_NAME = "test-review-progress.db"
        const val EVALUATED_AT = 1_000_000L
    }
}
