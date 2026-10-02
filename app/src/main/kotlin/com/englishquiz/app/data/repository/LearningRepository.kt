package com.englishquiz.app.data.repository

import androidx.room.withTransaction
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.parseContentResponse
import com.englishquiz.app.data.ai.toResponseJson
import com.englishquiz.app.data.local.DailyContentEntity
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.local.LearningSessionEntity
import com.englishquiz.app.data.local.LibraryItem
import com.englishquiz.app.data.local.SavedExpressionEntity
import com.englishquiz.app.domain.review.ReviewPolicy
import com.englishquiz.app.domain.review.ReviewProgress
import com.englishquiz.app.domain.session.LearningSessionSummary
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject

class LearningRepository(
    private val database: LearningDatabase,
) {
    private val learningDao = database.learningDao()

    fun observeSavedExpressions(): Flow<List<SavedExpressionEntity>> =
        learningDao.observeSavedExpressions()

    suspend fun findDueExpressions(nowEpochMillis: Long): List<SavedExpressionEntity> =
        learningDao.findDueExpressions(nowEpochMillis)

    suspend fun listSavedExpressions(): List<SavedExpressionEntity> =
        learningDao.listSavedExpressions()

    suspend fun listLearningDates(): List<String> = learningDao.listLearningDates()

    /** Every finished session, oldest first; the game layer derives points and badges from it (백로그 038). */
    suspend fun listAllSessions(): List<LearningSessionEntity> = learningDao.listAllSessions()

    /** The weekend boss's question set (백로그 041): the shakiest due, unmastered expressions, up to [limit]. */
    suspend fun listBossCandidates(nowEpochMillis: Long, limit: Int): List<SavedExpressionEntity> =
        learningDao.listBossCandidates(nowEpochMillis, limit)

    /**
     * The meaning stored for [displayExpression], but only when it was saved from this very
     * [contextSentence] (백로그 024). A phrase read again in the same sentence — a library passage,
     * the same day's second entry — needs no second call; the same phrase in another sentence may
     * mean something else (요구사항 11: the meaning in *this* sentence), so it is looked up afresh.
     */
    suspend fun findSavedMeaning(displayExpression: String, contextSentence: String): String? =
        learningDao.findSavedExpression(normalizeExpression(displayExpression))
            ?.takeIf { it.contextSentence.trim() == contextSentence.trim() }
            ?.contextMeaning

    /**
     * The passage already generated for [learningDate] and [mode] (백로그 021), or null when there
     * is none. A stored row this build can no longer read also counts as none: a fresh generation
     * is worth more than a crash, and the next save replaces the row.
     */
    suspend fun findDailyContent(learningDate: String, mode: ContentMode): LearningContent? {
        val row = learningDao.findDailyContent(learningDate, mode.wireValue) ?: return null
        return try {
            parseContentResponse(JSONObject(row.contentJson), mode)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Keeps [content] as the day's passage for its mode. Earlier days stay: they are the library
     * (백로그 026), and a passage read again weeks later is a review that costs nothing.
     */
    suspend fun saveDailyContent(
        learningDate: String,
        content: LearningContent,
        nowEpochMillis: Long,
    ) {
        learningDao.upsertDailyContent(
            DailyContentEntity(
                learningDate = learningDate,
                mode = content.mode.wireValue,
                title = content.title,
                contentJson = content.toResponseJson().toString(),
                createdAtEpochMillis = nowEpochMillis,
            ),
        )
    }

    /** Every stored passage, newest day first, for the library list (백로그 026). */
    suspend fun listLibrary(): List<LibraryItem> = learningDao.listDailyContent()

    suspend fun findLearningSessions(learningDate: String): List<LearningSessionEntity> =
        learningDao.findLearningSessions(learningDate)

    /**
     * The most recently finished sessions of [mode], newest first, as the summaries the domain
     * policies read. Level suggestion (백로그 013/034) is the caller; it has no reason to know the
     * Room row shape. Sessions from before 백로그 034 carry no mode and are not part of any window.
     */
    suspend fun listRecentSessionSummaries(mode: ContentMode, limit: Int): List<LearningSessionSummary> =
        learningDao.listRecentSessions(mode.wireValue, limit).map { session ->
            LearningSessionSummary(
                learnedExpressionCount = session.learnedExpressionCount,
                newlySavedExpressionCount = session.newlySavedExpressionCount,
                quizCorrectCount = session.quizCorrectCount,
                quizQuestionCount = session.quizQuestionCount,
                score = session.score,
                maxCombo = session.maxCombo,
            )
        }

    /**
     * Saves the expression the first time it is looked up. A repeat lookup keeps the stored row,
     * so the first-saved date and the review progress survive.
     */
    suspend fun saveExpression(
        displayExpression: String,
        contextMeaning: String,
        savedAtEpochMillis: Long,
        contextSentence: String = "",
    ): SavedExpressionEntity = database.withTransaction {
        val normalizedExpression = normalizeExpression(displayExpression)
        insertUnlessSaved(normalizedExpression, displayExpression, contextMeaning, savedAtEpochMillis, contextSentence)
        checkNotNull(learningDao.findSavedExpression(normalizedExpression))
    }

    /**
     * [saveExpression] for the day's annotated expressions (백로그 031): true when this call added
     * the row, false when the expression was already saved and therefore left as it was.
     */
    suspend fun saveExpressionIfNew(
        displayExpression: String,
        contextMeaning: String,
        savedAtEpochMillis: Long,
        contextSentence: String,
    ): Boolean = database.withTransaction {
        val normalizedExpression = normalizeExpression(displayExpression)
        insertUnlessSaved(normalizedExpression, displayExpression, contextMeaning, savedAtEpochMillis, contextSentence)
    }

    /** True when the row was inserted; false when the ignored duplicate kept the stored row. */
    private suspend fun insertUnlessSaved(
        normalizedExpression: String,
        displayExpression: String,
        contextMeaning: String,
        savedAtEpochMillis: Long,
        contextSentence: String,
    ): Boolean {
        require(normalizedExpression.isNotEmpty()) { "표현은 비어 있을 수 없습니다." }
        val rowId = learningDao.insertExpressionIgnoringDuplicate(
            SavedExpressionEntity(
                normalizedExpression = normalizedExpression,
                displayExpression = displayExpression.trim(),
                contextMeaning = contextMeaning.trim(),
                firstSavedAtEpochMillis = savedAtEpochMillis,
                contextSentence = contextSentence.trim(),
            ),
        )
        return rowId != IGNORED_ROW_ID
    }

    /**
     * Records a finished learning session. Idempotent on (learningDate, completedAtEpochMillis):
     * if the result screen is recreated and retries a write that already committed, the day's
     * session is not counted twice.
     */
    suspend fun recordCompletedSession(session: LearningSessionEntity) {
        database.withTransaction {
            val alreadyRecorded = learningDao.countLearningSession(
                learningDate = session.learningDate,
                completedAtEpochMillis = session.completedAtEpochMillis,
            ) > 0
            if (!alreadyRecorded) learningDao.insertLearningSession(session)
        }
    }

    suspend fun saveReviewProgress(
        displayExpression: String,
        lastReviewedAtEpochMillis: Long,
        nextReviewAtEpochMillis: Long,
        consecutiveCorrectCount: Int,
        incorrectCount: Int,
        isMastered: Boolean,
    ) {
        database.withTransaction {
            val normalizedExpression = normalizeExpression(displayExpression)
            val existing = checkNotNull(learningDao.findSavedExpression(normalizedExpression))
            learningDao.updateExpression(
                existing.copy(
                    lastReviewedAtEpochMillis = lastReviewedAtEpochMillis,
                    nextReviewAtEpochMillis = nextReviewAtEpochMillis,
                    consecutiveCorrectCount = consecutiveCorrectCount,
                    incorrectCount = incorrectCount,
                    isMastered = isMastered,
                ),
            )
        }
    }

    suspend fun recordAnswer(
        displayExpression: String,
        wasCorrect: Boolean,
        evaluatedAtEpochMillis: Long,
    ): SavedExpressionEntity = database.withTransaction {
        val normalizedExpression = normalizeExpression(displayExpression)
        val existing = checkNotNull(learningDao.findSavedExpression(normalizedExpression))
        val progress = ReviewPolicy.recordAnswer(
            current = ReviewProgress(
                lastReviewedAtEpochMillis = existing.lastReviewedAtEpochMillis,
                nextReviewAtEpochMillis = existing.nextReviewAtEpochMillis,
                consecutiveCorrectCount = existing.consecutiveCorrectCount,
                incorrectCount = existing.incorrectCount,
                isMastered = existing.isMastered,
            ),
            wasCorrect = wasCorrect,
            evaluatedAtEpochMillis = evaluatedAtEpochMillis,
        )
        val updated = existing.copy(
            lastReviewedAtEpochMillis = progress.lastReviewedAtEpochMillis,
            nextReviewAtEpochMillis = progress.nextReviewAtEpochMillis,
            consecutiveCorrectCount = progress.consecutiveCorrectCount,
            incorrectCount = progress.incorrectCount,
            isMastered = progress.isMastered,
        )
        learningDao.updateExpression(updated)
        updated
    }

    private fun normalizeExpression(expression: String): String =
        expression.trim().lowercase().split(Regex("\\s+")).joinToString(" ")

    private companion object {
        /** What Room's IGNORE insert returns for a row it did not write. */
        const val IGNORED_ROW_ID = -1L
    }
}
