package com.englishquiz.app.ui.result

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.englishquiz.app.data.local.LearningSessionEntity
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.domain.session.LearningSessionSummary
import com.englishquiz.app.domain.streak.StreakPolicy
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

sealed interface StreakUiState {
    data object Loading : StreakUiState
    data object Error : StreakUiState
    data class Ready(val streakDays: Int) : StreakUiState
}

/**
 * Shows the just-finished session's numbers and records it exactly once. [summary] is produced by
 * the caller (quiz flow); this route's own job is persistence and the streak readout. Guarded by
 * [rememberSaveable] so a rotation or recomposition never inserts the session twice.
 *
 * [onSessionRecorded] runs inside that same guard, right after the session row lands, for work
 * that belongs to a recorded session — difficulty adjustment (백로그 013) is the one caller. It is
 * passed in rather than done here so this route keeps its single job and stays testable without
 * app settings. It receives the session's `completedAtEpochMillis`: the guard flag is set after
 * the write, so an interrupted effect can replay the hook, and the identifier is what lets the
 * callee ignore the repeat.
 */
@Composable
fun ResultRoute(
    repository: LearningRepository,
    summary: LearningSessionSummary,
    onDone: () -> Unit,
    nowEpochMillis: () -> Long = System::currentTimeMillis,
    zoneId: ZoneId = ZoneId.systemDefault(),
    onSessionRecorded: suspend (sessionCompletedAtEpochMillis: Long) -> Unit = {},
) {
    // Captured once and kept across recreation, so a retried write targets the same session row.
    val completedAtEpochMillis = rememberSaveable { nowEpochMillis() }
    var hasRecorded by rememberSaveable { mutableStateOf(false) }
    var retry by remember { mutableIntStateOf(0) }
    var streakState by remember { mutableStateOf<StreakUiState>(StreakUiState.Loading) }

    LaunchedEffect(retry) {
        streakState = StreakUiState.Loading
        try {
            val todayIso = Instant.ofEpochMilli(completedAtEpochMillis).atZone(zoneId).toLocalDate()
                .format(DateTimeFormatter.ISO_LOCAL_DATE)
            if (!hasRecorded) {
                // The learner may leave or rotate the moment this screen appears. NonCancellable
                // keeps the write from being torn down half-way, and the repository call is
                // idempotent, so a retry after an interrupted write cannot double-count the day.
                withContext(NonCancellable) {
                    repository.recordCompletedSession(
                        LearningSessionEntity(
                            learningDate = todayIso,
                            completedAtEpochMillis = completedAtEpochMillis,
                            learnedExpressionCount = summary.learnedExpressionCount,
                            newlySavedExpressionCount = summary.newlySavedExpressionCount,
                            quizCorrectCount = summary.quizCorrectCount,
                            quizQuestionCount = summary.quizQuestionCount,
                        ),
                    )
                    onSessionRecorded(completedAtEpochMillis)
                }
                hasRecorded = true
            }
            val learningDates = repository.listLearningDates()
            streakState = StreakUiState.Ready(StreakPolicy.currentStreakDays(learningDates, todayIso))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            streakState = StreakUiState.Error
        }
    }

    BackHandler(onBack = onDone)
    ResultScreen(
        summary = summary,
        streakState = streakState,
        onRetry = { retry++ },
        onDone = onDone,
    )
}
