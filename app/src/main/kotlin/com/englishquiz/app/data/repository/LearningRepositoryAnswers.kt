package com.englishquiz.app.data.repository

import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.local.QuizAnswerEntity
import com.englishquiz.app.domain.quiz.QuizMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/*
 * The quiz answer log (백로그 056), as extensions on [LearningRepository]; kept in its own file for
 * the line limit. An answer belongs to an expression, and the expression to a language (백로그 066).
 */

/**
 * Logs one answer (백로그 056) without touching the review schedule: every mode leaves this
 * record, and only the daily quiz also calls [LearningRepository.recordAnswer]. Unknown
 * expressions are ignored.
 */
suspend fun LearningRepository.recordQuizAnswer(
    language: Language,
    displayExpression: String,
    mode: QuizMode,
    wasCorrect: Boolean,
    hintUsed: Boolean,
    answeredAtEpochMillis: Long,
) {
    val row = findRow(language, displayExpression) ?: return
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

/**
 * Logs one role-play turn (백로그 076): whether the learner's spoken line passed. Nothing else
 * moves — not the review schedule, not the score, not the weak-spot counts, which leave this mode
 * out. [expressionText] is the line's highlighted expression; its saved row's id is kept when the
 * box holds it, else 0, as for a line with no expression. [revealed] is whether the learner
 * looked at the original before speaking, kept in `hintUsed`.
 */
suspend fun LearningRepository.recordRoleplayTurn(
    language: Language,
    expressionText: String?,
    passed: Boolean,
    revealed: Boolean,
    answeredAtEpochMillis: Long,
) {
    val expressionId = expressionText?.takeIf { it.isNotBlank() }?.let { findRow(language, it)?.id } ?: 0L
    learningDao.insertQuizAnswer(
        QuizAnswerEntity(
            expressionId = expressionId,
            mode = QuizMode.ROLEPLAY.wireValue,
            isCorrect = passed,
            hintUsed = revealed,
            answeredAtEpochMillis = answeredAtEpochMillis,
        ),
    )
}

/** Wrong answers logged by the boss and the practice per expression id (백로그 056); absent means none. */
fun LearningRepository.observeWrongAnswerCounts(): Flow<Map<Long, Int>> =
    learningDao.observeWrongAnswerCounts().map { counts -> counts.associate { it.expressionId to it.wrong } }

suspend fun LearningRepository.listWrongAnswerCounts(): Map<Long, Int> = observeWrongAnswerCounts().first()

suspend fun LearningRepository.listQuizAnswers(language: Language, displayExpression: String): List<QuizAnswerEntity> {
    val row = findRow(language, displayExpression) ?: return emptyList()
    return learningDao.listQuizAnswers(row.id)
}
