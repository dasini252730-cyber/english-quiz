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
