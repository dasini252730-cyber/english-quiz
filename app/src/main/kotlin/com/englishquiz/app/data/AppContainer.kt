package com.englishquiz.app.data

import android.content.Context
import com.englishquiz.app.BuildConfig
import com.englishquiz.app.data.ai.AiLearningClient
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.preferences.AppSettingsRepository
import com.englishquiz.app.data.preferences.GameProgressRepository
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.data.season.SeasonCoordinator
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.season.SeasonPipeline
import com.englishquiz.app.data.season.SeasonScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(
    context: Context,
    aiEndpoint: String = BuildConfig.AI_ENDPOINT,
    aiAppToken: String = BuildConfig.AI_APP_TOKEN,
) {
    private val database = LearningDatabase.create(context)

    val learningRepository = LearningRepository(database)
    val appSettingsRepository = AppSettingsRepository(context)

    /** Shields, points spent and badges seen (백로그 039/040); everything else the game shows is derived from Room. */
    val gameProgressRepository = GameProgressRepository(context)

    /** Null until a build supplies the Edge Function URL, so AI screens can say so instead of failing. */
    val aiLearningClient: AiLearningClient? =
        aiEndpoint.takeIf { it.isNotBlank() }?.let { AiLearningClient(it, appToken = aiAppToken) }

    /** Work that must outlive a screen, such as asking WorkManager about the season chain (백로그 075). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Makes seasons and their episodes one step at a time (백로그 075); never on a tap. */
    val seasonPipeline: SeasonPipeline? = aiLearningClient?.let { client ->
        SeasonPipeline(learningRepository, client::generateEpisode, client::generateSeason, appSettingsRepository::levelFor)
    }

    /** Runs the pipeline's steps as a WorkManager chain, so a season is finished with the app closed. */
    private val seasonScheduler = SeasonScheduler(context.applicationContext ?: context, applicationScope)

    /** The story and the conversation as episodes of a season (백로그 063/075). */
    val seasonCoordinator: SeasonCoordinator? = seasonPipeline?.let { pipeline ->
        SeasonCoordinator(learningRepository, requestWork = seasonScheduler::ensure, pipelineStatus = pipeline.status, retryWork = seasonScheduler::retryNow)
    }

    /** Asks the chain to look at a language's seasons (home, the first assessment question). */
    fun prepareSeason(language: Language) {
        if (seasonPipeline != null) seasonScheduler.ensure(language)
    }
}
