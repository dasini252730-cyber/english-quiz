package com.englishquiz.app.data.local

import android.database.sqlite.SQLiteConstraintException
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 백로그 066: version 12 gives every learner-owned row a language. Everything stored before it is
 * English and must read back unchanged; only the identity of an expression (and of a season
 * number) widens to include the language.
 */
@RunWith(AndroidJUnit4::class)
class LearningDatabaseLanguageMigrationTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        LearningDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @After
    fun removeDatabase() {
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun existingRowsStayReadableAsEnglishAndTheExpressionKeyBecomesLanguageAndText() {
        migrationHelper.createDatabase(DATABASE_NAME, 11).apply {
            execSQL(
                """INSERT INTO saved_expressions (
                    normalizedExpression, displayExpression, contextMeaning, firstSavedAtEpochMillis,
                    consecutiveCorrectCount, incorrectCount, isMastered, contextSentence, tapCount, shortMeaning, kind
                ) VALUES ('pull it off', 'pull it off', '해내다', 100, 3, 1, 0, 'I knew you could pull it off.', 2, '해내다', 'verb')""",
            )
            execSQL(
                """INSERT INTO daily_content (learningDate, mode, title, contentJson, createdAtEpochMillis)
                VALUES ('2026-10-17', 'story', 'Ep. 1', '{"title":"Ep. 1"}', 200)""",
            )
            execSQL(
                """INSERT INTO learning_sessions (
                    learningDate, completedAtEpochMillis, learnedExpressionCount, newlySavedExpressionCount,
                    quizCorrectCount, quizQuestionCount, mode, score, maxCombo
                ) VALUES ('2026-10-17', 300, 5, 2, 8, 10, 'story', 80, 4)""",
            )
            execSQL(
                """INSERT INTO seasons (id, number, title, bibleJson, canonJson, difficulty, createdAtEpochMillis)
                VALUES (1, 1, 'Season 1', '{}', '{}', 2, 400)""",
            )
            execSQL(
                """INSERT INTO season_episodes (
                    seasonId, number, contentJson, synopsis, canonJson, difficulty, generatedAtEpochMillis, readDate
                ) VALUES (1, 1, '{"title":"Ep. 1"}', 'Synopsis.', '{}', 2, 500, '2026-10-17')""",
            )
            close()
        }

        // Validation against the exported schema also proves the old (text-only) unique indexes are gone.
        val migrated = migrationHelper.runMigrationsAndValidate(DATABASE_NAME, 12, true, MIGRATION_11_12)

        migrated.query("SELECT language, pinyin, displayExpression, tapCount, kind, contextSentence FROM saved_expressions").use { rows ->
            assertEquals(1, rows.count)
            assertTrue(rows.moveToFirst())
            assertEquals(listOf("en", "", "pull it off"), (0..2).map { rows.getString(it) })
            assertEquals(2, rows.getInt(3))
            assertEquals("verb", rows.getString(4))
            assertEquals("I knew you could pull it off.", rows.getString(5))
        }
        // The rebuilt passage table carries every old row over, as English.
        migrated.query("SELECT language, learningDate, mode, title, contentJson, createdAtEpochMillis FROM daily_content").use { rows ->
            assertEquals(1, rows.count)
            assertTrue(rows.moveToFirst())
            assertEquals(listOf("en", "2026-10-17", "story", "Ep. 1", """{"title":"Ep. 1"}"""), (0..4).map { rows.getString(it) })
            assertEquals(200L, rows.getLong(5))
        }
        migrated.query("SELECT language, learningDate, mode, score, quizQuestionCount FROM learning_sessions").use { rows ->
            assertTrue(rows.moveToFirst())
            assertEquals(listOf("en", "2026-10-17", "story"), (0..2).map { rows.getString(it) })
            assertEquals(80, rows.getInt(3))
            assertEquals(10, rows.getInt(4))
        }
        migrated.query("SELECT language, number, title FROM seasons").use { rows ->
            assertTrue(rows.moveToFirst())
            assertEquals("en", rows.getString(0))
            assertEquals(1, rows.getInt(1))
            assertEquals("Season 1", rows.getString(2))
        }
        migrated.query("SELECT language, seasonId, number, readDate FROM season_episodes").use { rows ->
            assertTrue(rows.moveToFirst())
            assertEquals("en", rows.getString(0))
            assertEquals(1L, rows.getLong(1))
            assertEquals(1, rows.getInt(2))
            assertEquals("2026-10-17", rows.getString(3))
        }

        // The identity of an expression is now (language, text), and of a season (language, number).
        assertEquals(
            listOf("language", "normalizedExpression"),
            uniqueIndexColumns(migrated, "saved_expressions", "index_saved_expressions_language_normalizedExpression"),
        )
        assertEquals(listOf("language", "number"), uniqueIndexColumns(migrated, "seasons", "index_seasons_language_number"))
        // So the same text in Chinese is a new row, while in English it is still the duplicate it was.
        migrated.execSQL(insertExpression("zh"))
        val duplicate = runCatching { migrated.execSQL(insertExpression("en")) }.exceptionOrNull()
        assertTrue(
            "the English duplicate must be refused by the unique index, got $duplicate",
            duplicate is SQLiteConstraintException && duplicate.message.orEmpty().contains("UNIQUE"),
        )
        migrated.query("SELECT language FROM saved_expressions ORDER BY language").use { rows ->
            val languages = generateSequence { if (rows.moveToNext()) rows.getString(0) else null }.toList()
            assertEquals(listOf("en", "zh"), languages)
        }
        migrated.close()
    }

    private fun insertExpression(language: String) =
        """INSERT INTO saved_expressions (
            language, normalizedExpression, displayExpression, contextMeaning, firstSavedAtEpochMillis,
            consecutiveCorrectCount, incorrectCount, isMastered
        ) VALUES ('$language', 'pull it off', 'pull it off', '해내다', 600, 0, 0, 0)"""

    /** The columns of [index] on [table], in index order; fails when the index is missing or not unique. */
    private fun uniqueIndexColumns(database: SupportSQLiteDatabase, table: String, index: String): List<String> {
        database.query("PRAGMA index_list(`$table`)").use { list ->
            var found = false
            while (list.moveToNext()) {
                if (list.getString(list.getColumnIndexOrThrow("name")) != index) continue
                found = true
                assertEquals("$index must be unique", 1, list.getInt(list.getColumnIndexOrThrow("unique")))
            }
            assertTrue("$index should exist on $table", found)
        }
        database.query("PRAGMA index_info(`$index`)").use { info ->
            val columns = mutableListOf<Pair<Int, String>>()
            while (info.moveToNext()) {
                columns += info.getInt(info.getColumnIndexOrThrow("seqno")) to info.getString(info.getColumnIndexOrThrow("name"))
            }
            return columns.sortedBy { it.first }.map { it.second }
        }
    }

    private companion object {
        const val DATABASE_NAME = "test-language-migration.db"
    }
}
