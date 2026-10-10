package com.englishquiz.app.ui.session

import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.repository.LearningRepository
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.englishquiz.app.data.repository.saveDailyContent

/**
 * The day a moment belongs to, in the learner's zone. The same rule keys the stored passage
 * (백로그 021), the recorded session and the streak, so "today" means one thing everywhere.
 */
internal fun isoDate(epochMillis: Long, zoneId: ZoneId): String =
    Instant.ofEpochMilli(epochMillis).atZone(zoneId).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)

/**
 * Stores the passage just generated as today's. A store that fails must not cost the learner the
 * passage they already paid for and are about to read, so the failure is swallowed: the worst
 * case is one more generation on the next entry, which is where the app was before 021.
 */
internal suspend fun keepForToday(
    repository: LearningRepository,
    learningDate: String,
    content: LearningContent,
    nowEpochMillis: Long,
) {
    try {
        repository.saveDailyContent(learningDate, content, nowEpochMillis)
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // Deliberate: see the doc comment.
    }
}
