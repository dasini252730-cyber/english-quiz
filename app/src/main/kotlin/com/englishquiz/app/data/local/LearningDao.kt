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

    /** Every finished session, oldest first: the game layer (백로그 038/039) sums points and badges from it. */
    @Query("SELECT * FROM learning_sessions ORDER BY learningDate, completedAtEpochMillis, id")
    suspend fun listAllSessions(): List<LearningSessionEntity>

    /**
     * The weekend boss's candidates (백로그 041): what is due and not yet mastered, the shakiest
     * first (fewest correct in a row, then most wrong). Due-only matters beyond scope: answering
     * moves an expression's review into the future, so a quiz rebuilt after recreation finds the
     * answered ones gone and resumes instead of asking them again (see `QuizRoute`).
     */
    @Query(
        "SELECT * FROM saved_expressions WHERE isMastered = 0 " +
            "AND (nextReviewAtEpochMillis IS NULL OR nextReviewAtEpochMillis <= :nowEpochMillis) " +
            "ORDER BY consecutiveCorrectCount, incorrectCount DESC, " +
            "COALESCE(nextReviewAtEpochMillis, firstSavedAtEpochMillis), id LIMIT :limit",
    )
    suspend fun listBossCandidates(nowEpochMillis: Long, limit: Int): List<SavedExpressionEntity>

    @Query(
        "SELECT * FROM learning_sessions WHERE mode = :mode " +
            "ORDER BY completedAtEpochMillis DESC, id DESC LIMIT :limit",
    )
    suspend fun listRecentSessions(mode: String, limit: Int): List<LearningSessionEntity>

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
