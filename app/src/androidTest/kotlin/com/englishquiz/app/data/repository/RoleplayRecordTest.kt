package com.englishquiz.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.domain.quiz.QuizMode
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** 백로그 076: a role-play turn is a `roleplay` answer row and nothing more. */
@RunWith(AndroidJUnit4::class)
class RoleplayRecordTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: LearningDatabase
    private lateinit var repository: LearningRepository

    @Before
    fun openDatabase() {
        context.deleteDatabase(DATABASE_NAME)
        database = LearningDatabase.create(context, DATABASE_NAME)
        repository = LearningRepository(database)
    }

    @After
    fun closeDatabase() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun aFailedTurnIsLoggedUnderTheSavedExpressionButIsNotAWeakSpot() = runBlocking {
        val row = repository.saveExpression(Language.ENGLISH, "pull it off", "해내다", 1_000L)
        repository.recordRoleplayTurn(Language.ENGLISH, "Pull it off", passed = false, revealed = true, answeredAtEpochMillis = 2_000L)
        repository.recordRoleplayTurn(Language.ENGLISH, "pull it off", passed = true, revealed = false, answeredAtEpochMillis = 3_000L)

        val answers = repository.learningDao.listQuizAnswers(row.id)
        assertEquals(listOf(false, true), answers.map { it.isCorrect })
        assertEquals(listOf(true, false), answers.map { it.hintUsed })
        assertEquals(setOf(QuizMode.ROLEPLAY.wireValue), answers.map { it.mode }.toSet())
        // Not a quiz miss: the schedule stands and the "자주 틀리는" count does not see it.
        val after = repository.listSavedExpressions(Language.ENGLISH).single()
        assertEquals(0, after.incorrectCount)
        assertEquals(0, after.consecutiveCorrectCount)
        assertFalse(repository.listWrongAnswerCounts().containsKey(row.id))
    }

    @Test
    fun aTurnWithoutASavedExpressionIsLoggedUnderIdZeroAndNeverCounted() = runBlocking {
        repository.recordRoleplayTurn(Language.ENGLISH, null, passed = false, revealed = false, answeredAtEpochMillis = 2_000L)
        repository.recordRoleplayTurn(Language.ENGLISH, "never saved", passed = false, revealed = false, answeredAtEpochMillis = 3_000L)

        assertEquals(2, repository.learningDao.listQuizAnswers(0L).size)
        assertEquals(emptyMap<Long, Int>(), repository.listWrongAnswerCounts())
    }

    private companion object {
        const val DATABASE_NAME = "test-roleplay-record.db"
    }
}
