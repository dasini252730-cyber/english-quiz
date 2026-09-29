package com.englishquiz.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.englishquiz.app.assessment.AssessmentLevel
import com.englishquiz.app.data.ai.AiLearningClient
import com.englishquiz.app.data.preferences.AppSettings
import com.englishquiz.app.data.preferences.AppSettingsRepository
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.domain.difficulty.DifficultyPolicy
import com.englishquiz.app.ui.assessment.AssessmentScreen
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.mongleScreenInsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

/** Difficulty used until the first assessment has stored one. */
private const val DEFAULT_DIFFICULTY = 2

private sealed interface SettingsLoadState {
    data object Loading : SettingsLoadState
    data class Ready(val settings: AppSettings) : SettingsLoadState
    data object Failed : SettingsLoadState
}

/**
 * App root. Until the first assessment is stored the app opens the assessment; afterwards it
 * opens home. [learningRepository] and [aiLearningClient] are optional so a test can drive the
 * assessment gate on its own, and so a build without a configured AI endpoint still runs.
 */
@Composable
fun EnglishQuizApp(
    settingsRepository: AppSettingsRepository,
    learningRepository: LearningRepository? = null,
    aiLearningClient: AiLearningClient? = null,
    /** Called once a session is recorded and the difficulty adjusted: make tomorrow's passages (백로그 025). */
    prefetchTomorrow: () -> Unit = {},
) {
    var retryCount by remember { mutableIntStateOf(0) }
    val settingsState by produceState<SettingsLoadState>(
        initialValue = SettingsLoadState.Loading,
        key1 = settingsRepository,
        key2 = retryCount,
    ) {
        value = SettingsLoadState.Loading
        try {
            settingsRepository.settings.collect { value = SettingsLoadState.Ready(it) }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            value = SettingsLoadState.Failed
        }
    }
    when (val state = settingsState) {
        SettingsLoadState.Loading -> LoadingScreen()
        SettingsLoadState.Failed -> SettingsErrorScreen { retryCount++ }
        is SettingsLoadState.Ready -> {
            if (!state.settings.isAssessmentComplete) {
                AssessmentScreen(
                    onSaveResult = { level ->
                        settingsRepository.saveAssessmentResult(level.storedValue)
                    },
                )
            } else if (learningRepository == null) {
                HomePlaceholder(state.settings.currentDifficulty)
            } else {
                LearningHome(
                    repository = learningRepository,
                    aiClient = aiLearningClient,
                    difficulty = state.settings.currentDifficulty ?: DEFAULT_DIFFICULTY,
                    onSessionRecorded = { sessionCompletedAtEpochMillis ->
                        adjustDifficulty(
                            learningRepository,
                            settingsRepository,
                            sessionCompletedAtEpochMillis,
                        )
                        // After the adjustment, so tomorrow is written at tomorrow's level.
                        prefetchTomorrow()
                    },
                )
            }
        }
    }
}

/**
 * Moves the difficulty after a finished session (요구사항 8절, 백로그 013). The recent sessions come
 * from Room and the level itself lives in DataStore, so this is the one place that holds both.
 * A failure here must not break the result screen: the level simply stays where it was and the
 * next finished session tries again.
 */
private suspend fun adjustDifficulty(
    learningRepository: LearningRepository,
    settingsRepository: AppSettingsRepository,
    sessionCompletedAtEpochMillis: Long,
) {
    try {
        val recent = learningRepository.listRecentSessionSummaries(
            DifficultyPolicy.SESSIONS_CONSIDERED,
        )
        settingsRepository.applyFinishedSession(sessionCompletedAtEpochMillis) { current, sessionsSinceChange ->
            DifficultyPolicy.nextDifficulty(current, recent, sessionsSinceChange)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // Difficulty is a nicety; the session is already recorded and that is what must survive.
    }
}

@Composable
private fun SettingsErrorScreen(onRetry: () -> Unit) {
    CenteredMessage(
        title = "학습 정보를 불러오지 못했어요.",
        body = "저장된 진단 여부를 확인할 수 없어 진단을 시작하지 않았어요.",
    ) {
        MongleButton("다시 시도", onRetry)
    }
}

@Composable
internal fun LoadingScreen() {
    CenteredMessage(title = "StoryEnglish", body = "학습 정보를 불러오고 있어요.")
}

@Composable
private fun HomePlaceholder(difficulty: Int?) {
    val levelLabel = AssessmentLevel.entries
        .firstOrNull { it.storedValue == difficulty }
        ?.label ?: "미설정"
    CenteredMessage(title = "오늘의 영어 학습", body = "진단 완료 · 초기 난이도: $levelLabel")
}

@Composable
private fun CenteredMessage(
    title: String,
    body: String,
    action: @Composable (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MongleColor.Cream)
            .mongleScreenInsets()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MongleColor.InkMuted)
        action?.invoke()
    }
}
