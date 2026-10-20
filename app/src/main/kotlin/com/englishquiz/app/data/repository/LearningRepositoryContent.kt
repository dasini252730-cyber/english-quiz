package com.englishquiz.app.data.repository

import androidx.room.withTransaction
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.toResponseJson
import com.englishquiz.app.data.local.DailyContentEntity
import com.englishquiz.app.data.local.LearningSessionEntity
import com.englishquiz.app.data.local.LibraryItem
import com.englishquiz.app.domain.session.LearningSessionSummary

/*
 * The day's passages (백로그 021/026) and the finished sessions, as extensions on
 * [LearningRepository]: the same object to callers, kept in a second file for the line limit.
 * Passages belong to one language (백로그 066); sessions are read whole for the streak and the
 * game, and by language for a level window.
 */

/**
 * The passage already generated for [learningDate] and [mode] in [language] (백로그 021), or null
 * when there is none. A stored row this build can no longer read also counts as none: a fresh
 * generation is worth more than a crash, and the next save replaces the row.
 */
suspend fun LearningRepository.findDailyContent(language: Language, learningDate: String, mode: ContentMode): LearningContent? =
    learningDao.findDailyContent(language.wireValue, learningDate, mode.wireValue)?.toContentOrNull(mode)

/**
 * Keeps [content] as the day's passage for its language and mode. Earlier days stay: they are the
 * library (백로그 026), and a passage read again weeks later is a review that costs nothing.
 */
suspend fun LearningRepository.saveDailyContent(
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
            language = content.language.wireValue,
        ),
    )
}

/** Every stored passage of [language], newest day first, for the library list (백로그 026). */
suspend fun LearningRepository.listLibrary(language: Language): List<LibraryItem> = learningDao.listDailyContent(language.wireValue)

suspend fun LearningRepository.listLearningDates(): List<String> = learningDao.listLearningDates()

/** Every finished session of every language, oldest first; the game layer derives points and badges from it (백로그 038). */
suspend fun LearningRepository.listAllSessions(): List<LearningSessionEntity> = learningDao.listAllSessions()

suspend fun LearningRepository.findLearningSessions(learningDate: String): List<LearningSessionEntity> =
    learningDao.findLearningSessions(learningDate)

/**
 * The most recently finished sessions of [mode] in [language], newest first, as the summaries the
 * domain policies read. Level suggestion (백로그 013/034) is the caller; it has no reason to know
 * the Room row shape. Sessions from before 백로그 034 carry no mode and are not part of any window.
 */
suspend fun LearningRepository.listRecentSessionSummaries(
    mode: ContentMode,
    limit: Int,
    language: Language = Language.DEFAULT,
): List<LearningSessionSummary> =
    learningDao.listRecentSessions(language.wireValue, mode.wireValue, limit).map { it.toSummary() }

/**
 * Records a finished learning session. Idempotent on (learningDate, completedAtEpochMillis):
 * if the result screen is recreated and retries a write that already committed, the day's
 * session is not counted twice.
 */
suspend fun LearningRepository.recordCompletedSession(session: LearningSessionEntity) {
    database.withTransaction {
        val alreadyRecorded = learningDao.countLearningSession(
            learningDate = session.learningDate,
            completedAtEpochMillis = session.completedAtEpochMillis,
        ) > 0
        if (!alreadyRecorded) learningDao.insertLearningSession(session)
    }
}
