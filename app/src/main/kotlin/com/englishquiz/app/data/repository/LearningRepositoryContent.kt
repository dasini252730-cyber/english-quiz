package com.englishquiz.app.data.repository

import androidx.room.withTransaction
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.PassageSummary
import com.englishquiz.app.data.ai.toResponseJson
import com.englishquiz.app.data.local.DailyContentEntity
import com.englishquiz.app.data.local.LearningSessionEntity
import com.englishquiz.app.data.local.LibraryItem
import com.englishquiz.app.domain.session.LearningSessionSummary

/*
 * The day's passages (백로그 021/026/054) and the finished sessions, as extensions on
 * [LearningRepository]: the same object to callers, kept in a second file for the line limit.
 */

/**
 * The passage already generated for [learningDate] and [mode] (백로그 021), or null when there
 * is none. A stored row this build can no longer read also counts as none: a fresh generation
 * is worth more than a crash, and the next save replaces the row.
 */
suspend fun LearningRepository.findDailyContent(learningDate: String, mode: ContentMode): LearningContent? =
    learningDao.findDailyContent(learningDate, mode.wireValue)?.toContentOrNull(mode)

/** The latest passage of [mode] before [learningDate] as the next one hears of it (백로그 054), or null. */
suspend fun LearningRepository.findPreviousSummary(mode: ContentMode, learningDate: String): PassageSummary? =
    learningDao.findLatestDailyContentBefore(mode.wireValue, learningDate)?.toContentOrNull(mode)?.summary()

/**
 * Keeps [content] as the day's passage for its mode. Earlier days stay: they are the library
 * (백로그 026), and a passage read again weeks later is a review that costs nothing.
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
        ),
    )
}

/** Every stored passage, newest day first, for the library list (백로그 026). */
suspend fun LearningRepository.listLibrary(): List<LibraryItem> = learningDao.listDailyContent()

suspend fun LearningRepository.listLearningDates(): List<String> = learningDao.listLearningDates()

/** Every finished session, oldest first; the game layer derives points and badges from it (백로그 038). */
suspend fun LearningRepository.listAllSessions(): List<LearningSessionEntity> = learningDao.listAllSessions()

suspend fun LearningRepository.findLearningSessions(learningDate: String): List<LearningSessionEntity> =
    learningDao.findLearningSessions(learningDate)

/**
 * The most recently finished sessions of [mode], newest first, as the summaries the domain
 * policies read. Level suggestion (백로그 013/034) is the caller; it has no reason to know the
 * Room row shape. Sessions from before 백로그 034 carry no mode and are not part of any window.
 */
suspend fun LearningRepository.listRecentSessionSummaries(mode: ContentMode, limit: Int): List<LearningSessionSummary> =
    learningDao.listRecentSessions(mode.wireValue, limit).map { it.toSummary() }

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
