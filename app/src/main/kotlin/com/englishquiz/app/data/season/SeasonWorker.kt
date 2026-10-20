package com.englishquiz.app.data.season

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.englishquiz.app.EnglishQuizApplication
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.domain.season.SeasonWork
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

/**
 * One pipeline step as a WorkManager job (백로그 075 follow-up): the design, or one episode, or one
 * conversation. When the step was real work the worker appends the next step to the same chain, so
 * a season is made as a chain of short jobs that WorkManager carries on after the app is closed or
 * frozen; when there is nothing to make the chain ends. A step that fails is retried by WorkManager
 * with backoff, and since the step is re-planned from what is stored, only the failed piece is
 * done again. WorkManager's own stop (ten minutes) replaces the old watchdog.
 */
class SeasonWorker(context: Context, parameters: WorkerParameters) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val language = Language.fromWire(inputData.getString(KEY_LANGUAGE))
        val pipeline = pipelineOf(applicationContext) ?: return Result.failure()
        val work = try {
            pipeline.step(language)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Log.w(TAG, "${language.wireValue} step attempt $runAttemptCount failed: ${error.message}")
            return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure(workDataOf(KEY_ERROR to (error.message ?: "failed")))
        }
        if (work != SeasonWork.Nothing) SeasonScheduler.enqueueNext(applicationContext, language)
        return Result.success(workDataOf(KEY_STEP to work.toString()))
    }

    companion object {
        const val TAG = "SeasonWorker"
        const val KEY_LANGUAGE = "language"
        const val KEY_STEP = "step"
        const val KEY_ERROR = "error"
        /** Attempts per step before the chain gives up; the next ensure() from home starts a new chain. */
        const val MAX_ATTEMPTS = 5

        /** Where the worker finds the pipeline; a test replaces it with its own. */
        var pipelineOf: (Context) -> SeasonPipeline? = { context ->
            (context.applicationContext as? EnglishQuizApplication)?.appContainer?.seasonPipeline
        }
    }
}

/**
 * Starts and continues the chain of [SeasonWorker] jobs for a language. One unique chain per
 * language: [ensure] keeps a chain that is already running or waiting to retry, [retryNow] drops a
 * waiting one so the step starts at once, and the worker itself appends the next step.
 */
class SeasonScheduler(private val context: Context, private val scope: CoroutineScope) {
    fun ensure(language: Language) {
        WorkManager.getInstance(context).enqueueUniqueWork(chainName(language), ExistingWorkPolicy.KEEP, request(language))
    }

    /** The learner's "지금 다시 시도": a chain waiting out its backoff is replaced; a running step is left alone. */
    fun retryNow(language: Language) {
        val manager = WorkManager.getInstance(context)
        scope.launch {
            val running = manager.getWorkInfosForUniqueWorkFlow(chainName(language)).first().any { it.state == WorkInfo.State.RUNNING }
            if (running) return@launch
            manager.enqueueUniqueWork(chainName(language), ExistingWorkPolicy.REPLACE, request(language))
        }
    }

    companion object {
        /** The next step, appended to the chain by the step that just finished. */
        fun enqueueNext(context: Context, language: Language) {
            WorkManager.getInstance(context).enqueueUniqueWork(chainName(language), ExistingWorkPolicy.APPEND_OR_REPLACE, request(language))
        }

        fun chainName(language: Language) = "season-${language.wireValue}"

        /** Needs the network; runs as expedited work where the quota allows, as ordinary work otherwise. */
        fun request(language: Language) = OneTimeWorkRequestBuilder<SeasonWorker>()
            .setInputData(workDataOf(SeasonWorker.KEY_LANGUAGE to language.wireValue))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .addTag(TAG)
            .build()

        const val TAG = "season"
    }
}
