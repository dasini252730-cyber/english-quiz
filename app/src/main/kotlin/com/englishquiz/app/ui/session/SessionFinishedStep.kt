package com.englishquiz.app.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.preferences.GameProgressRepository
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.domain.session.LearningSessionSummary
import com.englishquiz.app.ui.result.ResultRoute
import kotlinx.coroutines.CancellationException
import java.time.ZoneId

/**
 * Fills in the two counts only the session as a whole can know — how many expressions were saved
 * while reading, and how many were reviewed in the quiz — before handing over to the result
 * screen, which records the session.
 */
@Composable
internal fun FinishedStep(
    repository: LearningRepository,
    summary: LearningSessionSummary,
    sessionMode: String,
    savedCountAtStart: Int,
    onDone: () -> Unit,
    nowEpochMillis: () -> Long,
    zoneId: ZoneId,
    onSessionRecorded: suspend (sessionCompletedAtEpochMillis: Long) -> Unit,
    gameRepository: GameProgressRepository?,
    language: Language,
) {
    var resolved by remember { mutableStateOf<LearningSessionSummary?>(null) }
    LaunchedEffect(summary) {
        val newlySaved = try {
            (repository.listSavedExpressions(language).size - savedCountAtStart).coerceAtLeast(0)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            0
        }
        // The quiz reports what was met, cards included (백로그 045); only the saved count is filled here.
        resolved = summary.copy(newlySavedExpressionCount = newlySaved)
    }

    val ready = resolved
    if (ready == null) {
        SessionStatusScreen(title = "학습 결과", message = "결과를 정리하고 있어요.", onBack = onDone)
    } else {
        // The same zone rule keys the stored passage and dates the recorded session. (They are
        // taken at different moments, so a session that crosses midnight still records on the
        // day it finished, which is the day the streak should count.)
        ResultRoute(
            repository = repository,
            summary = ready,
            sessionMode = sessionMode,
            onDone = onDone,
            nowEpochMillis = nowEpochMillis,
            zoneId = zoneId,
            onSessionRecorded = onSessionRecorded,
            gameRepository = gameRepository,
            language = language,
        )
    }
}

internal fun contentTitle(mode: ContentMode): String = when (mode) {
    ContentMode.CONVERSATION -> "Conversation"
    ContentMode.STORY -> "Story"
}
