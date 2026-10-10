package com.englishquiz.app.data

import android.content.Context
import com.englishquiz.app.BuildConfig
import com.englishquiz.app.data.ai.AiLearningClient
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.prefetch.ContentPrefetcher
import com.englishquiz.app.data.preferences.AppSettingsRepository
import com.englishquiz.app.data.preferences.GameProgressRepository
import com.englishquiz.app.data.repository.LearningRepository
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

    /** Work that must outlive a screen, such as making tomorrow's passages (백로그 025). */
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val contentPrefetcher: ContentPrefetcher? = aiLearningClient?.let { client ->
        ContentPrefetcher(learningRepository, appSettingsRepository, client::generateContent, applicationScope)
    }
}
