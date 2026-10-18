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

/** Wrong answers logged by the boss and the practice per expression id (백로그 056); absent means none. */
fun LearningRepository.observeWrongAnswerCounts(): Flow<Map<Long, Int>> =
    learningDao.observeWrongAnswerCounts().map { counts -> counts.associate { it.expressionId to it.wrong } }

suspend fun LearningRepository.listWrongAnswerCounts(): Map<Long, Int> = observeWrongAnswerCounts().first()

suspend fun LearningRepository.listQuizAnswers(language: Language, displayExpression: String): List<QuizAnswerEntity> {
    val row = findRow(language, displayExpression) ?: return emptyList()
    return learningDao.listQuizAnswers(row.id)
}
