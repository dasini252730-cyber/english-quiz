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

@Entity(tableName = "learning_sessions", indices = [Index(value = ["learningDate"])])
data class LearningSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val learningDate: String,
    val completedAtEpochMillis: Long,
    val learnedExpressionCount: Int,
    val newlySavedExpressionCount: Int,
    val quizCorrectCount: Int,
    val quizQuestionCount: Int,
)
