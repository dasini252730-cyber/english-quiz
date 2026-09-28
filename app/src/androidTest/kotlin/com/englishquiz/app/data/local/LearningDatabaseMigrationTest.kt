package com.englishquiz.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LearningDatabaseMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LearningDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After
    fun removeDatabases() {
        context.deleteDatabase(MIGRATION_DATABASE_NAME)
        context.deleteDatabase(EXPRESSION_MIGRATION_DATABASE_NAME)
        context.deleteDatabase(CHAINED_MIGRATION_DATABASE_NAME)
        context.deleteDatabase(DAILY_CONTENT_MIGRATION_DATABASE_NAME)
    }

    @Test
    fun migrationAddsAnEmptyDailyContentTableAndKeepsSessions() {
        migrationHelper.createDatabase(DAILY_CONTENT_MIGRATION_DATABASE_NAME, 3).apply {
            execSQL(
                """INSERT INTO learning_sessions (
                    learningDate, completedAtEpochMillis, learnedExpressionCount,
                    newlySavedExpressionCount, quizCorrectCount, quizQuestionCount
                ) VALUES ('2026-09-17', 300, 5, 2, 8, 10)""",
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            DAILY_CONTENT_MIGRATION_DATABASE_NAME,
            4,
            true,
            MIGRATION_3_4,
        )
        val sessions = migrated.query("SELECT learningDate, quizQuestionCount FROM learning_sessions")
        val content = migrated.query("SELECT COUNT(*) FROM daily_content")
        try {
            assertTrue(sessions.moveToFirst())
            assertEquals("2026-09-17", sessions.getString(0))
            assertEquals(10, sessions.getInt(1))
            // The table exists (the query would throw otherwise) and starts empty.
            assertTrue(content.moveToFirst())
            assertEquals(0, content.getInt(0))
        } finally {
            sessions.close()
            content.close()
            migrated.close()
        }
    }

    @Test
    fun migrationPreservesExistingDailySession() {
        migrationHelper.createDatabase(MIGRATION_DATABASE_NAME, 1).apply {
            execSQL(
                """INSERT INTO learning_sessions VALUES (
                    '2026-09-17', 300, 5, 2, 8, 10
                )""",
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            MIGRATION_DATABASE_NAME,
            2,
            true,
            MIGRATION_1_2,
        )
        val rows = migrated.query(
            "SELECT learningDate, completedAtEpochMillis, quizQuestionCount " +
                "FROM learning_sessions",
        )
        try {
            assertTrue(rows.moveToFirst())
            assertEquals("2026-09-17", rows.getString(0))
            assertEquals(300L, rows.getLong(1))
            assertEquals(10, rows.getInt(2))
        } finally {
            rows.close()
            migrated.close()
        }
    }

    @Test
    fun migrationAddsContextSentenceAndKeepsSavedExpressions() {
        migrationHelper.createDatabase(EXPRESSION_MIGRATION_DATABASE_NAME, 2).apply {
            execSQL(
                """INSERT INTO saved_expressions (
                    normalizedExpression, displayExpression, contextMeaning,
                    firstSavedAtEpochMillis, lastReviewedAtEpochMillis, nextReviewAtEpochMillis,
                    consecutiveCorrectCount, incorrectCount, isMastered
                ) VALUES ('pull it off', 'pull it off', '해내다', 100, 150, 200, 3, 1, 1)""",
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            EXPRESSION_MIGRATION_DATABASE_NAME,
            3,
            true,
            MIGRATION_2_3,
        )
        val rows = migrated.query(
            "SELECT displayExpression, firstSavedAtEpochMillis, nextReviewAtEpochMillis, " +
                "isMastered, contextSentence FROM saved_expressions",
        )
        try {
            assertTrue(rows.moveToFirst())
            assertEquals("pull it off", rows.getString(0))
            assertEquals(100L, rows.getLong(1))
            assertEquals(200L, rows.getLong(2))
            assertEquals(1, rows.getInt(3))
            assertEquals("", rows.getString(4))
        } finally {
            rows.close()
            migrated.close()
        }
    }

    @Test
    fun chainedMigrationFromVersionOneKeepsExpressionsAndSessions() {
        migrationHelper.createDatabase(CHAINED_MIGRATION_DATABASE_NAME, 1).apply {
            execSQL(
                """INSERT INTO saved_expressions (
                    normalizedExpression, displayExpression, contextMeaning,
                    firstSavedAtEpochMillis, lastReviewedAtEpochMillis, nextReviewAtEpochMillis,
                    consecutiveCorrectCount, incorrectCount, isMastered
                ) VALUES ('pull it off', 'pull it off', '해내다', 100, 150, 200, 3, 1, 1)""",
            )
            execSQL(
                """INSERT INTO learning_sessions VALUES (
                    '2026-09-17', 300, 5, 2, 8, 10
                )""",
            )
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            CHAINED_MIGRATION_DATABASE_NAME,
            4,
            true,
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
        )
        val expressions = migrated.query(
            "SELECT displayExpression, firstSavedAtEpochMillis, consecutiveCorrectCount, " +
                "contextSentence FROM saved_expressions",
        )
        val sessions = migrated.query(
            "SELECT learningDate, completedAtEpochMillis, quizQuestionCount FROM learning_sessions",
        )
        try {
            assertTrue(expressions.moveToFirst())
            assertEquals("pull it off", expressions.getString(0))
            assertEquals(100L, expressions.getLong(1))
            assertEquals(3, expressions.getInt(2))
            assertEquals("", expressions.getString(3))
            assertTrue(sessions.moveToFirst())
            assertEquals("2026-09-17", sessions.getString(0))
            assertEquals(300L, sessions.getLong(1))
            assertEquals(10, sessions.getInt(2))
        } finally {
            expressions.close()
            sessions.close()
            migrated.close()
        }
    }

    private companion object {
        const val MIGRATION_DATABASE_NAME = "test-session-migration.db"
        const val EXPRESSION_MIGRATION_DATABASE_NAME = "test-expression-migration.db"
        const val CHAINED_MIGRATION_DATABASE_NAME = "test-chained-migration.db"
        const val DAILY_CONTENT_MIGRATION_DATABASE_NAME = "test-daily-content-migration.db"
    }
}
