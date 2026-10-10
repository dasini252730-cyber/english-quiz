package com.englishquiz.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "saved_expressions",
    indices = [Index(value = ["normalizedExpression"], unique = true)],
)
data class SavedExpressionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val normalizedExpression: String,
    val displayExpression: String,
    val contextMeaning: String,
    val firstSavedAtEpochMillis: Long,
    val lastReviewedAtEpochMillis: Long? = null,
    val nextReviewAtEpochMillis: Long? = null,
    val consecutiveCorrectCount: Int = 0,
    val incorrectCount: Int = 0,
    val isMastered: Boolean = false,
    @ColumnInfo(defaultValue = "''") val contextSentence: String = "",
    /** How often the learner looked this expression up while reading (백로그 044); 0 for an enrolled, untapped phrase. */
    @ColumnInfo(defaultValue = "0") val tapCount: Int = 0,
    /** The gloss used as a quiz option (백로그 046); "" when the passage carried none. */
    @ColumnInfo(defaultValue = "''") val shortMeaning: String = "",
)

/**
 * The passage generated for one day and one mode (요구사항 9.1: 상황은 매일 다양하게 — daily, not per
 * tap). Re-entering the same mode on the same day reads this row instead of paying for and waiting
 * on another generation. [contentJson] is the Edge Function's own reply shape, so it is restored
 * through the same parser and validation as a live reply.
 */
@Entity(tableName = "daily_content", primaryKeys = ["learningDate", "mode"])
data class DailyContentEntity(
    val learningDate: String,
    val mode: String,
    val title: String,
    val contentJson: String,
    val createdAtEpochMillis: Long,
)

/** One row of the library list (백로그 026): enough to show a passage without parsing its JSON. */
data class LibraryItem(
    val learningDate: String,
    val mode: String,
    val title: String,
    val createdAtEpochMillis: Long,
)

/**
 * One answer given in any quiz (백로그 056): the daily quiz, the weekend boss or the review box's
 * practice. Only the daily quiz moves an expression's review schedule; every mode leaves this
 * row, so a weak spot shows up wherever it was missed and hint use (백로그 058) can be studied
 * later. Retries at the end of a quiz (백로그 047) and passage questions are not answers here.
 */
@Entity(tableName = "quiz_answers", indices = [Index(value = ["expressionId"])])
data class QuizAnswerEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expressionId: Long,
    /** `daily`, `boss` or `practice`: `QuizMode.wireValue`. */
    val mode: String,
    val isCorrect: Boolean,
    val hintUsed: Boolean,
    val answeredAtEpochMillis: Long,
)

/** How many logged answers an expression got wrong, across every quiz mode. */
data class WrongAnswerCount(val expressionId: Long, val wrong: Int)

@Entity(tableName = "learning_sessions", indices = [Index(value = ["learningDate"])])
data class LearningSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val learningDate: String,
    val completedAtEpochMillis: Long,
    val learnedExpressionCount: Int,
    val newlySavedExpressionCount: Int,
    val quizCorrectCount: Int,
    val quizQuestionCount: Int,
    /**
     * `ContentMode.wireValue` for a day's session, [LIBRARY_SESSION_MODE] for a passage read again
     * from the library, empty for sessions recorded before 백로그 034. Only a day's session counts
     * toward its mode's level suggestion: an old passage was not written at today's level.
     */
    @ColumnInfo(defaultValue = "''") val mode: String = "",
    /** The quiz's points and longest correct run (백로그 035); sessions before it carry 0. */
    @ColumnInfo(defaultValue = "0") val score: Int = 0,
    @ColumnInfo(defaultValue = "0") val maxCombo: Int = 0,
)

/** The `mode` a library re-read (백로그 026) records; see [LearningSessionEntity.mode]. */
const val LIBRARY_SESSION_MODE = "library"
