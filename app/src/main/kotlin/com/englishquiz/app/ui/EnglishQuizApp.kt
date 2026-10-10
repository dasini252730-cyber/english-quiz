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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.englishquiz.app.data.ai.AiLearningClient
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.preferences.AppSettings
import com.englishquiz.app.data.preferences.AppSettingsRepository
import com.englishquiz.app.data.preferences.GameProgressRepository
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.domain.difficulty.DifficultyPolicy
import com.englishquiz.app.ui.assessment.AssessmentScreen
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.mongleScreenInsets
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import com.englishquiz.app.data.repository.listRecentSessionSummaries

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
    /** Points spent, shields and badges seen (백로그 037~041); null runs home without the game cards. */
    gameProgressRepository: GameProgressRepository? = null,
) {
    var retryCount by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
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
                HomePlaceholder(state.settings)
            } else {
                LearningHome(
                    repository = learningRepository,
                    aiClient = aiLearningClient,
                    settings = state.settings,
                    gameRepository = gameProgressRepository,
                    onSessionRecorded = { mode, sessionCompletedAtEpochMillis ->
                        if (mode != null) suggestLevel(learningRepository, settingsRepository, mode, sessionCompletedAtEpochMillis)
                        prefetchTomorrow()
                    },
                    // The learner's own level moves at once (백로그 034); a write that fails leaves
                    // the level where it was, which the next tap can repeat.
                    onLevelChange = { mode, level ->
                        scope.launch { settingsWrite { settingsRepository.setLevel(mode, level) } }
                    },
                    onSuggestionAnswer = { mode, accepted ->
                        scope.launch {
                            settingsWrite {
                                if (accepted) settingsRepository.acceptSuggestion(mode) else settingsRepository.dismissSuggestion(mode)
                            }
                        }
                    },
                )
            }
        }
    }
}

/**
 * Leaves a level suggestion for [mode] after a finished session (요구사항 8절, 백로그 013/034). The
 * recent sessions come from Room and the level itself lives in DataStore, so this is the one
 * place that holds both. A failure here must not break the result screen: no suggestion is made
 * and the next finished session tries again.
 */
private suspend fun suggestLevel(
    learningRepository: LearningRepository,
    settingsRepository: AppSettingsRepository,
    mode: ContentMode,
    sessionCompletedAtEpochMillis: Long,
) {
    try {
        val recent = learningRepository.listRecentSessionSummaries(mode, DifficultyPolicy.SESSIONS_CONSIDERED)
        settingsRepository.applyFinishedSession(mode, sessionCompletedAtEpochMillis) { current, sessionsSinceChange ->
            DifficultyPolicy.nextDifficulty(current, recent, sessionsSinceChange)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // Difficulty is a nicety; the session is already recorded and that is what must survive.
    }
}

/** A settings write that fails leaves the stored value where it was; home shows what is stored. */
private suspend fun settingsWrite(write: suspend () -> Unit) {
    try {
        write()
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        // Deliberate: see above.
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
private fun HomePlaceholder(settings: AppSettings) {
    val level = settings.level(ContentMode.CONVERSATION)?.let { "$it / ${DifficultyPolicy.MAX_DIFFICULTY}" } ?: "미설정"
    CenteredMessage(title = "오늘의 영어 학습", body = "진단 완료 · 시작 레벨: $level")
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
