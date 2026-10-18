package com.englishquiz.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `learning_sessions_new` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `learningDate` TEXT NOT NULL,
                `completedAtEpochMillis` INTEGER NOT NULL,
                `learnedExpressionCount` INTEGER NOT NULL,
                `newlySavedExpressionCount` INTEGER NOT NULL,
                `quizCorrectCount` INTEGER NOT NULL,
                `quizQuestionCount` INTEGER NOT NULL
            )""",
        )
        database.execSQL(
            """INSERT INTO `learning_sessions_new` (
                `learningDate`, `completedAtEpochMillis`, `learnedExpressionCount`,
                `newlySavedExpressionCount`, `quizCorrectCount`, `quizQuestionCount`
            ) SELECT `learningDate`, `completedAtEpochMillis`, `learnedExpressionCount`,
                `newlySavedExpressionCount`, `quizCorrectCount`, `quizQuestionCount`
            FROM `learning_sessions`""",
        )
        database.execSQL("DROP TABLE `learning_sessions`")
        database.execSQL("ALTER TABLE `learning_sessions_new` RENAME TO `learning_sessions`")
        database.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_learning_sessions_learningDate` " +
                "ON `learning_sessions` (`learningDate`)",
        )
    }
}

internal val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE `saved_expressions` " +
                "ADD COLUMN `contextSentence` TEXT NOT NULL DEFAULT ''",
        )
    }
}

/** 백로그 034: each mode has its own level, so a session records which mode it was. */
internal val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            "ALTER TABLE `learning_sessions` ADD COLUMN `mode` TEXT NOT NULL DEFAULT ''",
        )
    }
}

/** 백로그 035: a session keeps its quiz score and best combo; earlier sessions count as 0. */
internal val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `learning_sessions` ADD COLUMN `score` INTEGER NOT NULL DEFAULT 0")
        database.execSQL("ALTER TABLE `learning_sessions` ADD COLUMN `maxCombo` INTEGER NOT NULL DEFAULT 0")
    }
}

/** 백로그 044: how often an expression was tapped while reading; older rows count as never tapped. */
internal val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `saved_expressions` ADD COLUMN `tapCount` INTEGER NOT NULL DEFAULT 0")
    }
}

/** 백로그 046: the short gloss a quiz option is made of; older rows have none and use the full meaning. */
internal val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `saved_expressions` ADD COLUMN `shortMeaning` TEXT NOT NULL DEFAULT ''")
    }
}

/** 백로그 056: every quiz answer in its own table, so boss and practice can be studied without moving a schedule. */
internal val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `quiz_answers` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `expressionId` INTEGER NOT NULL,
                `mode` TEXT NOT NULL,
                `isCorrect` INTEGER NOT NULL,
                `hintUsed` INTEGER NOT NULL,
                `answeredAtEpochMillis` INTEGER NOT NULL
            )""",
        )
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_quiz_answers_expressionId` ON `quiz_answers` (`expressionId`)")
    }
}

/** 백로그 063: story seasons and their numbered episodes; nothing existing is touched. */
internal val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `seasons` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `number` INTEGER NOT NULL,
                `title` TEXT NOT NULL,
                `bibleJson` TEXT NOT NULL,
                `canonJson` TEXT NOT NULL,
                `difficulty` INTEGER NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL
            )""",
        )
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_seasons_number` ON `seasons` (`number`)")
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `season_episodes` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `seasonId` INTEGER NOT NULL,
                `number` INTEGER NOT NULL,
                `contentJson` TEXT NOT NULL,
                `synopsis` TEXT NOT NULL,
                `canonJson` TEXT NOT NULL,
                `difficulty` INTEGER NOT NULL,
                `generatedAtEpochMillis` INTEGER NOT NULL,
                `readDate` TEXT
            )""",
        )
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_season_episodes_seasonId_number` ON `season_episodes` (`seasonId`, `number`)")
        database.execSQL("CREATE INDEX IF NOT EXISTS `index_season_episodes_readDate` ON `season_episodes` (`readDate`)")
    }
}

/** 백로그 059: the kind of phrase, for wrong options of the same kind; older rows match by nothing. */
internal val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `saved_expressions` ADD COLUMN `kind` TEXT NOT NULL DEFAULT ''")
    }
}

/**
 * 백로그 066: a second language. Every learner-owned row gets `language`, defaulting to 'en' so
 * nothing stored so far changes meaning. The two identity keys that would collide across languages
 * are widened: `daily_content` is rebuilt with `language` in its primary key (a key cannot be
 * altered in place), and the unique index on `saved_expressions` becomes (`language`,
 * `normalizedExpression`). Seasons are numbered per language; episodes carry the language too so
 * the unread/read-on-date queries need no join. Chinese expressions also get `pinyin`.
 */
internal val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL("ALTER TABLE `saved_expressions` ADD COLUMN `language` TEXT NOT NULL DEFAULT 'en'")
        database.execSQL("ALTER TABLE `saved_expressions` ADD COLUMN `pinyin` TEXT NOT NULL DEFAULT ''")
        database.execSQL("DROP INDEX IF EXISTS `index_saved_expressions_normalizedExpression`")
        database.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_saved_expressions_language_normalizedExpression` " +
                "ON `saved_expressions` (`language`, `normalizedExpression`)",
        )
        database.execSQL("ALTER TABLE `learning_sessions` ADD COLUMN `language` TEXT NOT NULL DEFAULT 'en'")
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `daily_content_new` (
                `learningDate` TEXT NOT NULL,
                `mode` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `contentJson` TEXT NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                `language` TEXT NOT NULL DEFAULT 'en',
                PRIMARY KEY(`language`, `learningDate`, `mode`)
            )""",
        )
        database.execSQL(
            """INSERT INTO `daily_content_new` (`learningDate`, `mode`, `title`, `contentJson`, `createdAtEpochMillis`, `language`)
            SELECT `learningDate`, `mode`, `title`, `contentJson`, `createdAtEpochMillis`, 'en' FROM `daily_content`""",
        )
        database.execSQL("DROP TABLE `daily_content`")
        database.execSQL("ALTER TABLE `daily_content_new` RENAME TO `daily_content`")
        database.execSQL("ALTER TABLE `seasons` ADD COLUMN `language` TEXT NOT NULL DEFAULT 'en'")
        database.execSQL("DROP INDEX IF EXISTS `index_seasons_number`")
        database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_seasons_language_number` ON `seasons` (`language`, `number`)")
        database.execSQL("ALTER TABLE `season_episodes` ADD COLUMN `language` TEXT NOT NULL DEFAULT 'en'")
    }
}

/** 백로그 021: one generated passage per day and mode, so re-entry stops paying for a new one. */
internal val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(database: SupportSQLiteDatabase) {
        database.execSQL(
            """CREATE TABLE IF NOT EXISTS `daily_content` (
                `learningDate` TEXT NOT NULL,
                `mode` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `contentJson` TEXT NOT NULL,
                `createdAtEpochMillis` INTEGER NOT NULL,
                PRIMARY KEY(`learningDate`, `mode`)
            )""",
        )
    }
}
