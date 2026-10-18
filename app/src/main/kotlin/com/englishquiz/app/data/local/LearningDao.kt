package com.englishquiz.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/*
 * Every expression and passage query takes the language (백로그 066): the two languages' records
 * never mix. Sessions are read whole where the streak and the game need every language, and by
 * language where a level window does.
 */
@Dao
interface LearningDao {
    @Query("SELECT * FROM saved_expressions WHERE language = :language ORDER BY firstSavedAtEpochMillis DESC")
    fun observeSavedExpressions(language: String): Flow<List<SavedExpressionEntity>>

    @Query("SELECT * FROM saved_expressions WHERE language = :language AND normalizedExpression = :normalizedExpression")
    suspend fun findSavedExpression(language: String, normalizedExpression: String): SavedExpressionEntity?

    @Query(
        "SELECT * FROM saved_expressions WHERE language = :language " +
            "AND (nextReviewAtEpochMillis IS NULL OR nextReviewAtEpochMillis <= :nowEpochMillis) " +
            "ORDER BY COALESCE(nextReviewAtEpochMillis, firstSavedAtEpochMillis), id",
    )
    suspend fun findDueExpressions(language: String, nowEpochMillis: Long): List<SavedExpressionEntity>

    @Query("SELECT COUNT(*) FROM saved_expressions")
    suspend fun savedExpressionCount(): Int

    @Query("SELECT * FROM saved_expressions WHERE language = :language ORDER BY firstSavedAtEpochMillis DESC, id DESC")
    suspend fun listSavedExpressions(language: String): List<SavedExpressionEntity>

    /** Every language's expressions: the game layer (백로그 038/039) counts them all. */
    @Query("SELECT * FROM saved_expressions ORDER BY firstSavedAtEpochMillis DESC, id DESC")
    suspend fun listAllSavedExpressions(): List<SavedExpressionEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertExpressionIgnoringDuplicate(expression: SavedExpressionEntity): Long

    @Update
    suspend fun updateExpression(expression: SavedExpressionEntity)

    /** Gives a row saved before 백로그 046 the gloss a later passage carries for it; never replaces one. */
    @Query("UPDATE saved_expressions SET shortMeaning = :shortMeaning WHERE language = :language AND normalizedExpression = :normalizedExpression AND shortMeaning = ''")
    suspend fun fillShortMeaning(language: String, normalizedExpression: String, shortMeaning: String)

    /** Gives a row saved before 백로그 059 the kind a later passage names for it; never replaces one. */
    @Query("UPDATE saved_expressions SET kind = :kind WHERE language = :language AND normalizedExpression = :normalizedExpression AND kind = ''")
    suspend fun fillKind(language: String, normalizedExpression: String, kind: String)

    /** Gives a Chinese row its pinyin when a later passage carries one (백로그 066); never replaces one. */
    @Query("UPDATE saved_expressions SET pinyin = :pinyin WHERE language = :language AND normalizedExpression = :normalizedExpression AND pinyin = ''")
    suspend fun fillPinyin(language: String, normalizedExpression: String, pinyin: String)

    /** One more look-up while reading (백로그 044). */
    @Query("UPDATE saved_expressions SET tapCount = tapCount + 1 WHERE language = :language AND normalizedExpression = :normalizedExpression")
    suspend fun incrementTapCount(language: String, normalizedExpression: String)

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
        "SELECT * FROM saved_expressions WHERE language = :language AND isMastered = 0 " +
            "AND (nextReviewAtEpochMillis IS NULL OR nextReviewAtEpochMillis <= :nowEpochMillis) " +
            "ORDER BY consecutiveCorrectCount, incorrectCount DESC, " +
            "COALESCE(nextReviewAtEpochMillis, firstSavedAtEpochMillis), id LIMIT :limit",
    )
    suspend fun listBossCandidates(language: String, nowEpochMillis: Long, limit: Int): List<SavedExpressionEntity>

    @Query(
        "SELECT * FROM learning_sessions WHERE language = :language AND mode = :mode " +
            "ORDER BY completedAtEpochMillis DESC, id DESC LIMIT :limit",
    )
    suspend fun listRecentSessions(language: String, mode: String, limit: Int): List<LearningSessionEntity>

    @Query(
        "SELECT COUNT(*) FROM learning_sessions WHERE learningDate = :learningDate " +
            "AND completedAtEpochMillis = :completedAtEpochMillis",
    )
    suspend fun countLearningSession(learningDate: String, completedAtEpochMillis: Long): Int

    @Insert
    suspend fun insertLearningSession(session: LearningSessionEntity)

    @Insert
    suspend fun insertQuizAnswer(answer: QuizAnswerEntity)

    @Query("SELECT * FROM quiz_answers WHERE expressionId = :expressionId ORDER BY answeredAtEpochMillis, id")
    suspend fun listQuizAnswers(expressionId: Long): List<QuizAnswerEntity>

    /**
     * Wrong answers per expression in the boss and the practice (백로그 056), for the "자주 틀리는"
     * view. A daily miss is already in the row's `incorrectCount`, so it is not counted twice.
     * Keyed by expression id, so the caller's own language filter applies.
     */
    @Query("SELECT expressionId, COUNT(*) AS wrong FROM quiz_answers WHERE isCorrect = 0 AND mode <> 'daily' GROUP BY expressionId")
    fun observeWrongAnswerCounts(): Flow<List<WrongAnswerCount>>

    @Query("SELECT * FROM daily_content WHERE language = :language AND learningDate = :learningDate AND mode = :mode")
    suspend fun findDailyContent(language: String, learningDate: String, mode: String): DailyContentEntity?

    /** Replaces, because a retry after a failed save must not be refused as a duplicate. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyContent(content: DailyContentEntity)

    /** The newest passage of [mode] from before [learningDate] (백로그 054): the previous episode. */
    @Query(
        "SELECT * FROM daily_content WHERE language = :language AND mode = :mode AND learningDate < :learningDate " +
            "ORDER BY learningDate DESC LIMIT 1",
    )
    suspend fun findLatestDailyContentBefore(language: String, mode: String, learningDate: String): DailyContentEntity?

    /** The library (백로그 026): every passage of one language, newest day first. */
    @Query(
        "SELECT learningDate, mode, title, createdAtEpochMillis, language FROM daily_content " +
            "WHERE language = :language ORDER BY learningDate DESC, mode",
    )
    suspend fun listDailyContent(language: String): List<LibraryItem>
}
