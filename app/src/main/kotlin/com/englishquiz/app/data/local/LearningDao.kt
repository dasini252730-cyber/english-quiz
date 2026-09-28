package com.englishquiz.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LearningDao {
    @Query("SELECT * FROM saved_expressions ORDER BY firstSavedAtEpochMillis DESC")
    fun observeSavedExpressions(): Flow<List<SavedExpressionEntity>>

    @Query("SELECT * FROM saved_expressions WHERE normalizedExpression = :normalizedExpression")
    suspend fun findSavedExpression(normalizedExpression: String): SavedExpressionEntity?

    @Query(
        "SELECT * FROM saved_expressions " +
            "WHERE nextReviewAtEpochMillis IS NULL OR nextReviewAtEpochMillis <= :nowEpochMillis " +
            "ORDER BY COALESCE(nextReviewAtEpochMillis, firstSavedAtEpochMillis), id",
    )
    suspend fun findDueExpressions(nowEpochMillis: Long): List<SavedExpressionEntity>

    @Query("SELECT COUNT(*) FROM saved_expressions")
    suspend fun savedExpressionCount(): Int

    @Query("SELECT * FROM saved_expressions ORDER BY firstSavedAtEpochMillis DESC, id DESC")
    suspend fun listSavedExpressions(): List<SavedExpressionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExpressionIgnoringDuplicate(expression: SavedExpressionEntity): Long

    @Update
    suspend fun updateExpression(expression: SavedExpressionEntity)

    @Query(
        "SELECT * FROM learning_sessions WHERE learningDate = :learningDate " +
            "ORDER BY completedAtEpochMillis, id",
    )
    suspend fun findLearningSessions(learningDate: String): List<LearningSessionEntity>

    @Query("SELECT DISTINCT learningDate FROM learning_sessions ORDER BY learningDate DESC")
    suspend fun listLearningDates(): List<String>

    @Query(
        "SELECT * FROM learning_sessions ORDER BY completedAtEpochMillis DESC, id DESC LIMIT :limit",
    )
    suspend fun listRecentSessions(limit: Int): List<LearningSessionEntity>

    @Query(
        "SELECT COUNT(*) FROM learning_sessions WHERE learningDate = :learningDate " +
            "AND completedAtEpochMillis = :completedAtEpochMillis",
    )
    suspend fun countLearningSession(learningDate: String, completedAtEpochMillis: Long): Int

    @Insert
    suspend fun insertLearningSession(session: LearningSessionEntity)

    @Query("SELECT * FROM daily_content WHERE learningDate = :learningDate AND mode = :mode")
    suspend fun findDailyContent(learningDate: String, mode: String): DailyContentEntity?

    /** Replaces, because a retry after a failed save must not be refused as a duplicate. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyContent(content: DailyContentEntity)

    /** The library (백로그 026): every passage ever generated, newest day first. */
    @Query(
        "SELECT learningDate, mode, title, createdAtEpochMillis FROM daily_content " +
            "ORDER BY learningDate DESC, mode",
    )
    suspend fun listDailyContent(): List<LibraryItem>
}
