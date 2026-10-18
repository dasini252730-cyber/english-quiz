package com.englishquiz.app.ui.session

import com.englishquiz.app.data.ai.ContentGenerationRequest
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.data.repository.findDailyContent
import com.englishquiz.app.data.repository.saveDailyContent
import com.englishquiz.app.data.season.SeasonCoordinator
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Mirrors the Edge Function's MAX_REVIEW_EXPRESSIONS and the pipeline's cap. */
internal const val MAX_REVIEW_EXPRESSIONS = 12

/**
 * The passage for [mode] in [language] on [learningDate]: the stored one on a second entry
 * (백로그 021); inside the seasons (백로그 063/075) the day's episode's story or conversation, or null
 * when no episode is ready yet; without seasons a fresh generation stored for the day. Everything
 * it reads and writes is that language's (백로그 066).
 */
internal suspend fun todaysContent(
    repository: LearningRepository,
    seasons: SeasonCoordinator?,
    language: Language,
    mode: ContentMode,
    difficulty: Int,
    learningDate: String,
    nowEpochMillis: Long,
    generateContent: suspend (ContentGenerationRequest) -> LearningContent,
): LearningContent? {
    repository.findDailyContent(language, learningDate, mode)?.let { return it }
    if (seasons != null) return seasons.passageFor(language, mode, learningDate, nowEpochMillis)
    val review = repository.findDueExpressions(language, nowEpochMillis).take(MAX_REVIEW_EXPRESSIONS).map { it.displayExpression }
    val content = generateContent(ContentGenerationRequest(mode = mode, difficulty = difficulty, reviewExpressions = review, language = language))
    keepForToday(repository, learningDate, content, nowEpochMillis)
    return content
}

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
