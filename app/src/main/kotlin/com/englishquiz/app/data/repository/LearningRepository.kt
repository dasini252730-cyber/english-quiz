package com.englishquiz.app.data.repository

import androidx.room.withTransaction
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.local.QuizAnswerEntity
import com.englishquiz.app.data.local.SavedExpressionEntity
import com.englishquiz.app.domain.quiz.QuizMode
import com.englishquiz.app.domain.review.ReviewPolicy
import com.englishquiz.app.domain.review.ReviewProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * The learner's records: saved expressions and their review state here; the day's passages and
 * finished sessions in `LearningRepositoryContent.kt`, as extensions on this class.
 */
class LearningRepository(
    internal val database: LearningDatabase,
) {
    internal val learningDao = database.learningDao()

    fun observeSavedExpressions(): Flow<List<SavedExpressionEntity>> =
        learningDao.observeSavedExpressions()

    suspend fun findDueExpressions(nowEpochMillis: Long): List<SavedExpressionEntity> =
        learningDao.findDueExpressions(nowEpochMillis)

    suspend fun listSavedExpressions(): List<SavedExpressionEntity> =
        learningDao.listSavedExpressions()

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
     * Saves the expression the first time it is looked up. A repeat lookup keeps the stored row,
     * so the first-saved date and the review progress survive. Every call counts as one tap
     * (백로그 044): this is the Reader's path, and tapping a phrase again means it still is not known.
     */
    suspend fun saveExpression(
        displayExpression: String,
        contextMeaning: String,
        savedAtEpochMillis: Long,
        contextSentence: String = "",
        /** False for a retry of a save that failed: the learner tapped once. */
        countTap: Boolean = true,
        shortMeaning: String = "",
    ): SavedExpressionEntity = database.withTransaction {
        val normalizedExpression = normalizeExpression(displayExpression)
        insertUnlessSaved(normalizedExpression, displayExpression, contextMeaning, savedAtEpochMillis, contextSentence, shortMeaning)
        if (countTap) learningDao.incrementTapCount(normalizedExpression)
        if (shortMeaning.isNotBlank()) learningDao.fillShortMeaning(normalizedExpression, shortMeaning.trim())
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
        shortMeaning: String = "",
    ): Boolean = database.withTransaction {
        val normalizedExpression = normalizeExpression(displayExpression)
        val added = insertUnlessSaved(normalizedExpression, displayExpression, contextMeaning, savedAtEpochMillis, contextSentence, shortMeaning)
        if (!added && shortMeaning.isNotBlank()) learningDao.fillShortMeaning(normalizedExpression, shortMeaning.trim())
        added
    }

    /** True when the row was inserted; false when the ignored duplicate kept the stored row. */
    private suspend fun insertUnlessSaved(
        normalizedExpression: String,
        displayExpression: String,
        contextMeaning: String,
        savedAtEpochMillis: Long,
        contextSentence: String,
        shortMeaning: String,
    ): Boolean {
        require(normalizedExpression.isNotEmpty()) { "표현은 비어 있을 수 없습니다." }
        val rowId = learningDao.insertExpressionIgnoringDuplicate(
            SavedExpressionEntity(
                normalizedExpression = normalizedExpression,
                displayExpression = displayExpression.trim(),
                contextMeaning = contextMeaning.trim(),
                firstSavedAtEpochMillis = savedAtEpochMillis,
                contextSentence = contextSentence.trim(),
                shortMeaning = shortMeaning.trim(),
            ),
        )
        return rowId != IGNORED_ROW_ID
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
            val progress = ReviewProgress(
                lastReviewedAtEpochMillis, nextReviewAtEpochMillis, consecutiveCorrectCount, incorrectCount, isMastered,
            )
            learningDao.updateExpression(existing.withProgress(progress))
        }
    }

    /**
     * The learner saw the expression's card and said they do not know it yet (백로그 045): it
     * comes back tomorrow, but nothing is held against it — no wrong answer, no reset.
     */
    suspend fun markSeen(displayExpression: String, seenAtEpochMillis: Long): SavedExpressionEntity =
        database.withTransaction {
            val existing = checkNotNull(learningDao.findSavedExpression(normalizeExpression(displayExpression)))
            val updated = existing.copy(
                lastReviewedAtEpochMillis = seenAtEpochMillis,
                nextReviewAtEpochMillis = ReviewPolicy.seenAgainAt(seenAtEpochMillis),
            )
            learningDao.updateExpression(updated)
            updated
        }

    /**
     * Logs one answer (백로그 056) without touching the review schedule: every mode leaves this
     * record, and only the daily quiz also calls [recordAnswer]. Unknown expressions are ignored.
     */
    suspend fun recordQuizAnswer(
        displayExpression: String,
        mode: QuizMode,
        wasCorrect: Boolean,
        hintUsed: Boolean,
        answeredAtEpochMillis: Long,
    ) {
        val row = learningDao.findSavedExpression(normalizeExpression(displayExpression)) ?: return
        learningDao.insertQuizAnswer(
            QuizAnswerEntity(
                expressionId = row.id,
                mode = mode.wireValue,
                isCorrect = wasCorrect,
                hintUsed = hintUsed,
                answeredAtEpochMillis = answeredAtEpochMillis,
            ),
        )
    }

    /** Wrong answers logged by the boss and the practice per expression id (백로그 056); absent means none. */
    fun observeWrongAnswerCounts(): Flow<Map<Long, Int>> =
        learningDao.observeWrongAnswerCounts().map { counts -> counts.associate { it.expressionId to it.wrong } }

    suspend fun listWrongAnswerCounts(): Map<Long, Int> = observeWrongAnswerCounts().first()

    suspend fun listQuizAnswers(displayExpression: String): List<QuizAnswerEntity> {
        val row = learningDao.findSavedExpression(normalizeExpression(displayExpression)) ?: return emptyList()
        return learningDao.listQuizAnswers(row.id)
    }

    suspend fun recordAnswer(
        displayExpression: String,
        wasCorrect: Boolean,
        evaluatedAtEpochMillis: Long,
    ): SavedExpressionEntity = database.withTransaction {
        val normalizedExpression = normalizeExpression(displayExpression)
        val existing = checkNotNull(learningDao.findSavedExpression(normalizedExpression))
        val progress = ReviewPolicy.recordAnswer(existing.toReviewProgress(), wasCorrect, evaluatedAtEpochMillis)
        val updated = existing.withProgress(progress)
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
