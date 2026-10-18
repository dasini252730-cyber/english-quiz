package com.englishquiz.app.data.repository

import androidx.room.withTransaction
import com.englishquiz.app.data.ai.ExpressionKinds
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.local.SavedExpressionEntity
import com.englishquiz.app.domain.review.ReviewPolicy
import com.englishquiz.app.domain.review.ReviewProgress
import kotlinx.coroutines.flow.Flow

/**
 * The learner's records: saved expressions and their review state here; quiz answers in
 * `LearningRepositoryAnswers.kt` and the day's passages and finished sessions in
 * `LearningRepositoryContent.kt`, as extensions on this class. Every expression belongs to one
 * [Language] (백로그 066): the same string saved in English and in Chinese is two rows.
 */
class LearningRepository(
    internal val database: LearningDatabase,
) {
    internal val learningDao = database.learningDao()

    fun observeSavedExpressions(language: Language): Flow<List<SavedExpressionEntity>> =
        learningDao.observeSavedExpressions(language.wireValue)

    suspend fun findDueExpressions(language: Language, nowEpochMillis: Long): List<SavedExpressionEntity> =
        learningDao.findDueExpressions(language.wireValue, nowEpochMillis)

    suspend fun listSavedExpressions(language: Language): List<SavedExpressionEntity> =
        learningDao.listSavedExpressions(language.wireValue)

    /** Every language's expressions, for the game's counts (백로그 038): the learner is one person. */
    suspend fun listAllSavedExpressions(): List<SavedExpressionEntity> = learningDao.listAllSavedExpressions()

    /** The weekend boss's question set (백로그 041): the shakiest due, unmastered expressions, up to [limit]. */
    suspend fun listBossCandidates(language: Language, nowEpochMillis: Long, limit: Int): List<SavedExpressionEntity> =
        learningDao.listBossCandidates(language.wireValue, nowEpochMillis, limit)

    /**
     * The meaning stored for [displayExpression], but only when it was saved from this very
     * [contextSentence] (백로그 024). A phrase read again in the same sentence — a library passage,
     * the same day's second entry — needs no second call; the same phrase in another sentence may
     * mean something else (요구사항 11: the meaning in *this* sentence), so it is looked up afresh.
     */
    suspend fun findSavedMeaning(language: Language, displayExpression: String, contextSentence: String): String? =
        findRow(language, displayExpression)
            ?.takeIf { it.contextSentence.trim() == contextSentence.trim() }
            ?.contextMeaning

    /**
     * Saves the expression the first time it is looked up. A repeat lookup keeps the stored row,
     * so the first-saved date and the review progress survive. Every call counts as one tap
     * (백로그 044): this is the Reader's path, and tapping a phrase again means it still is not known.
     */
    suspend fun saveExpression(
        language: Language,
        displayExpression: String,
        contextMeaning: String,
        savedAtEpochMillis: Long,
        contextSentence: String = "",
        /** False for a retry of a save that failed: the learner tapped once. */
        countTap: Boolean = true,
        shortMeaning: String = "",
        kind: String = "",
        pinyin: String = "",
    ): SavedExpressionEntity = database.withTransaction {
        val key = normalizeExpression(language, displayExpression)
        insertUnlessSaved(language, key, displayExpression, contextMeaning, savedAtEpochMillis, contextSentence, shortMeaning, kind, pinyin)
        if (countTap) learningDao.incrementTapCount(language.wireValue, key)
        fillWhatIsMissing(language, key, shortMeaning, kind, pinyin)
        checkNotNull(learningDao.findSavedExpression(language.wireValue, key))
    }

    /**
     * [saveExpression] for the day's annotated expressions (백로그 031): true when this call added
     * the row, false when the expression was already saved and therefore left as it was.
     */
    suspend fun saveExpressionIfNew(
        language: Language,
        displayExpression: String,
        contextMeaning: String,
        savedAtEpochMillis: Long,
        contextSentence: String,
        shortMeaning: String = "",
        kind: String = "",
        pinyin: String = "",
    ): Boolean = database.withTransaction {
        val key = normalizeExpression(language, displayExpression)
        val added = insertUnlessSaved(language, key, displayExpression, contextMeaning, savedAtEpochMillis, contextSentence, shortMeaning, kind, pinyin)
        if (!added) fillWhatIsMissing(language, key, shortMeaning, kind, pinyin)
        added
    }

    /** A row from before 백로그 046/059/066 takes the gloss, kind and pinyin a later passage carries; a filled one keeps its own. */
    private suspend fun fillWhatIsMissing(language: Language, key: String, shortMeaning: String, kind: String, pinyin: String) {
        if (shortMeaning.isNotBlank()) learningDao.fillShortMeaning(language.wireValue, key, shortMeaning.trim())
        ExpressionKinds.normalize(kind).takeIf { it.isNotEmpty() }?.let { learningDao.fillKind(language.wireValue, key, it) }
        if (pinyin.isNotBlank()) learningDao.fillPinyin(language.wireValue, key, pinyin.trim())
    }

    /** True when the row was inserted; false when the ignored duplicate kept the stored row. */
    private suspend fun insertUnlessSaved(
        language: Language,
        key: String,
        displayExpression: String,
        contextMeaning: String,
        savedAtEpochMillis: Long,
        contextSentence: String,
        shortMeaning: String,
        kind: String,
        pinyin: String,
    ): Boolean {
        require(key.isNotEmpty()) { "표현은 비어 있을 수 없습니다." }
        val rowId = learningDao.insertExpressionIgnoringDuplicate(
            SavedExpressionEntity(
                normalizedExpression = key,
                displayExpression = displayExpression.trim(),
                contextMeaning = contextMeaning.trim(),
                firstSavedAtEpochMillis = savedAtEpochMillis,
                contextSentence = contextSentence.trim(),
                shortMeaning = shortMeaning.trim(),
                kind = ExpressionKinds.normalize(kind),
                language = language.wireValue,
                pinyin = pinyin.trim(),
            ),
        )
        return rowId != IGNORED_ROW_ID
    }

    suspend fun saveReviewProgress(
        language: Language,
        displayExpression: String,
        lastReviewedAtEpochMillis: Long,
        nextReviewAtEpochMillis: Long,
        consecutiveCorrectCount: Int,
        incorrectCount: Int,
        isMastered: Boolean,
    ) {
        database.withTransaction {
            val existing = checkNotNull(findRow(language, displayExpression))
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
    suspend fun markSeen(language: Language, displayExpression: String, seenAtEpochMillis: Long): SavedExpressionEntity =
        database.withTransaction {
            val existing = checkNotNull(findRow(language, displayExpression))
            val updated = existing.copy(
                lastReviewedAtEpochMillis = seenAtEpochMillis,
                nextReviewAtEpochMillis = ReviewPolicy.seenAgainAt(seenAtEpochMillis),
            )
            learningDao.updateExpression(updated)
            updated
        }

    suspend fun recordAnswer(
        language: Language,
        displayExpression: String,
        wasCorrect: Boolean,
        evaluatedAtEpochMillis: Long,
    ): SavedExpressionEntity = database.withTransaction {
        val existing = checkNotNull(findRow(language, displayExpression))
        val progress = ReviewPolicy.recordAnswer(existing.toReviewProgress(), wasCorrect, evaluatedAtEpochMillis)
        val updated = existing.withProgress(progress)
        learningDao.updateExpression(updated)
        updated
    }

    /** The stored row for [displayExpression] in [language], or null. */
    internal suspend fun findRow(language: Language, displayExpression: String): SavedExpressionEntity? =
        learningDao.findSavedExpression(language.wireValue, normalizeExpression(language, displayExpression))

    private companion object {
        /** What Room's IGNORE insert returns for a row it did not write. */
        const val IGNORED_ROW_ID = -1L
    }
}

/**
 * The identity of an expression inside its language (백로그 012: duplicates are one row). English
 * ignores case and runs of whitespace. Chinese has neither; there the only noise is whitespace and
 * punctuation, so those go and the characters are the key.
 */
internal fun normalizeExpression(language: Language, expression: String): String = when (language) {
    Language.ENGLISH -> expression.trim().lowercase().split(Regex("\\s+")).joinToString(" ")
    Language.CHINESE -> expression.filterNot { it.isWhitespace() || it.isPunctuationMark() }
}

private fun Char.isPunctuationMark(): Boolean = when (Character.getType(this).toByte()) {
    Character.CONNECTOR_PUNCTUATION, Character.DASH_PUNCTUATION, Character.START_PUNCTUATION, Character.END_PUNCTUATION,
    Character.INITIAL_QUOTE_PUNCTUATION, Character.FINAL_QUOTE_PUNCTUATION, Character.OTHER_PUNCTUATION,
    -> true
    else -> false
}
