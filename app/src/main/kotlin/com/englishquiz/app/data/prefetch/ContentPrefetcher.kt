package com.englishquiz.app.data.prefetch

import android.util.Log
import com.englishquiz.app.data.ai.ContentGenerationRequest
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.preferences.AppSettingsRepository
import com.englishquiz.app.data.repository.LearningRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Makes tomorrow's passages tonight (백로그 025), so the first tap of the day opens at once.
 *
 * It runs after a session is recorded and the difficulty has been adjusted (백로그 013), in a scope
 * that outlives the screen, so leaving the result screen does not cancel it. Each mode is made
 * only if tomorrow's row is missing, and a failure in one mode neither stops the other nor reaches
 * the learner: the worst case is the generation the app would have done on the tap anyway.
 */
class ContentPrefetcher(
    private val repository: LearningRepository,
    private val settings: AppSettingsRepository,
    private val generate: suspend (ContentGenerationRequest) -> LearningContent,
    private val scope: CoroutineScope,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
) {
    private val running = AtomicBoolean(false)

    /**
     * Starts the work in the background, at most once at a time. Nothing that goes wrong here may
     * reach the learner: the scope has no exception handler, so an unread settings store or any
     * other failure is logged and swallowed rather than allowed to end the process.
     */
    fun prefetchTomorrow() {
        if (!running.compareAndSet(false, true)) return
        val job = scope.launch {
            try {
                prefetchTomorrowNow()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "tomorrow's passages were not made: ${error.message}")
            }
        }
        // Released on completion of any kind, including a scope that never ran the job.
        job.invokeOnCompletion { running.set(false) }
    }

    /** The same work, awaited. Returns the modes that were actually generated. */
    suspend fun prefetchTomorrowNow(): List<ContentMode> {
        val now = nowEpochMillis()
        val tomorrowStart = Instant.ofEpochMilli(now).atZone(zoneId).toLocalDate().plusDays(1).atStartOfDay(zoneId)
        val tomorrowIso = tomorrowStart.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
        // Whatever comes due at any point tomorrow belongs in tomorrow's passage, whenever the
        // learner opens it, so "due" is measured against the last moment of that day.
        val dueBy = tomorrowStart.plusDays(1).toInstant().toEpochMilli() - 1
        val difficulty = settings.settings.first().currentDifficulty ?: DEFAULT_DIFFICULTY
        val made = mutableListOf<ContentMode>()
        for (mode in ContentMode.entries) {
            try {
                if (repository.findDailyContent(tomorrowIso, mode) != null) continue
                val due = repository.findDueExpressions(dueBy)
                val content = generate(
                    ContentGenerationRequest(
                        mode = mode,
                        difficulty = difficulty,
                        reviewExpressions = due.take(MAX_REVIEW_EXPRESSIONS).map { it.displayExpression },
                    ),
                )
                repository.saveDailyContent(tomorrowIso, content, now)
                made += mode
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Log.w(TAG, "tomorrow's $mode was not made: ${error.message}")
            }
        }
        return made
    }

    private companion object {
        const val TAG = "ContentPrefetcher"
        /** Same default as the app root uses before the first assessment. */
        const val DEFAULT_DIFFICULTY = 2
        /** Mirrors the Edge Function's MAX_REVIEW_EXPRESSIONS and the session route's cap. */
        const val MAX_REVIEW_EXPRESSIONS = 12
    }
}
