package com.englishquiz.app.ui.quiz

import androidx.room.withTransaction
import com.englishquiz.app.data.local.SavedExpressionEntity
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.domain.game.GrowthStage
import com.englishquiz.app.domain.quiz.QuizMode
import com.englishquiz.app.domain.quiz.QuizQuestion

/**
 * The write behind one answer (백로그 056): every mode logs it, and only the daily quiz also moves
 * the expression's review schedule. Both land in one transaction, so a failed schedule write
 * that is retried cannot leave a second log row. Returns the updated row for the daily quiz's
 * growth line, null for the boss and the practice, whose rows do not change.
 */
internal suspend fun LearningRepository.recordQuizAnswerFor(
    question: QuizQuestion,
    wasCorrect: Boolean,
    mode: QuizMode,
    hintUsed: Boolean,
    nowEpochMillis: Long,
): SavedExpressionEntity? = database.withTransaction {
    val updated = if (mode == QuizMode.DAILY) recordAnswer(question.expression, wasCorrect, nowEpochMillis) else null
    recordQuizAnswer(question.expression, mode, wasCorrect, hintUsed, nowEpochMillis)
    updated
}

/** The stage move the answer just caused (백로그 036), from the row as it was written. */
internal fun growthChange(question: QuizQuestion, updated: SavedExpressionEntity): GrowthChange = GrowthChange(
    expression = question.expression,
    before = question.growthBefore,
    after = GrowthStage.of(updated.consecutiveCorrectCount, updated.isMastered),
)
