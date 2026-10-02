package com.englishquiz.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.englishquiz.app.ui.EnglishQuizApp
import com.englishquiz.app.ui.theme.EnglishQuizTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Android 15 draws a targetSdk 35+ app edge to edge whether or not it asks, so say so
        // explicitly and let the screens inset their own content (see `mongleScreenInsets`).
        // Transparent scrims keep the screen's own background running under both bars, and
        // `light` keeps the dark bar icons the cream canvas needs — what the theme's now-ignored
        // windowLightStatusBar used to give.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        val appContainer = (application as EnglishQuizApplication).appContainer
        setContent {
            EnglishQuizTheme {
                EnglishQuizApp(
                    settingsRepository = appContainer.appSettingsRepository,
                    learningRepository = appContainer.learningRepository,
                    aiLearningClient = appContainer.aiLearningClient,
                    prefetchTomorrow = { appContainer.contentPrefetcher?.prefetchTomorrow() },
                    gameProgressRepository = appContainer.gameProgressRepository,
                )
            }
        }
    }
}
