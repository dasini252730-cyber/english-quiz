package com.englishquiz.app.ui.boss

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.englishquiz.app.data.preferences.GameProgressRepository
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.domain.game.BossPolicy
import com.englishquiz.app.domain.session.LearningSessionSummary
import com.englishquiz.app.domain.quiz.QuizMode
import com.englishquiz.app.ui.quiz.QuizRoute
import com.englishquiz.app.ui.result.ResultRoute
import com.englishquiz.app.ui.session.SessionStatusScreen
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleButtonColors
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleTopBar
import com.englishquiz.app.ui.theme.mongleScreenInsets

/**
 * The weekend boss (백로그 041): an intro, a bigger quiz over the shakiest expressions at double
 * points, and the ordinary result screen recording a [BossPolicy.SESSION_MODE] session. Nothing
 * is generated and nothing is enrolled, so it costs no AI call.
 */
@Composable
fun BossRoute(
    repository: LearningRepository,
    gameRepository: GameProgressRepository?,
    onExit: () -> Unit,
    onSessionRecorded: suspend (sessionCompletedAtEpochMillis: Long) -> Unit = {},
) {
    var started by rememberSaveable { mutableStateOf(false) }
    var finished by rememberSaveable(stateSaver = SummarySaver) { mutableStateOf<LearningSessionSummary?>(null) }
    val summary = finished
    when {
        // Nothing was asked, so nothing is recorded: an empty boss must not count as this week's
        // clear, a learning day or a finished-session mission.
        summary != null && summary.quizQuestionCount == 0 -> SessionStatusScreen(
            title = "주간 보스전",
            message = "지금은 도전할 표현이 없어요. 복습 예정인 표현이 쌓이면 다시 열려요.",
            onBack = onExit,
            onRetry = null,
            showWaiting = false,
        )
        summary != null -> ResultRoute(
            repository = repository,
            summary = summary,
            sessionMode = BossPolicy.SESSION_MODE,
            onDone = onExit,
            onSessionRecorded = onSessionRecorded,
            gameRepository = gameRepository,
        )
        started -> QuizRoute(
            repository = repository,
            todayContent = null,
            onFinished = { finished = it.copy(learnedExpressionCount = it.quizQuestionCount) },
            onBack = onExit,
            enrolExpressions = false,
            questionSource = { repo, at -> repo.listBossCandidates(at, BossPolicy.MAX_QUESTIONS) },
            maxQuestions = BossPolicy.MAX_QUESTIONS,
            pointsMultiplier = BossPolicy.POINTS_MULTIPLIER,
            // A boss is a test, not a first meeting: every candidate is asked (백로그 045).
            askCards = false,
            // Points and the challenge record only (백로그 056): the schedule stays with the daily quiz.
            mode = QuizMode.BOSS,
        )
        else -> BossIntro(onStart = { started = true }, onBack = onExit)
    }
}

@Composable
private fun BossIntro(onStart: () -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MongleColor.Cream).mongleScreenInsets()) {
        MongleTopBar(title = "주간 보스전", onBack = onBack)
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Text("⚔️", style = MaterialTheme.typography.displayLarge)
            Text(
                "이번 주 가장 흔들린 표현 ${BossPolicy.MAX_QUESTIONS}개가 한꺼번에 나와요.",
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Text(
                "문제당 점수는 ${BossPolicy.POINTS_MULTIPLIER}배. 주말에 한 번만 열려요.",
                style = MaterialTheme.typography.bodyLarge,
                color = MongleColor.InkMuted,
                textAlign = TextAlign.Center,
            )
            MongleButton("도전하기", onStart, colors = MongleButtonColors.Accent)
        }
    }
}

/** Keeps the finished summary across recreation so the result screen is not lost to a rotation. */
private val SummarySaver = listSaver<LearningSessionSummary?, Int>(
    save = { summary ->
        summary?.let {
            listOf(
                it.learnedExpressionCount, it.newlySavedExpressionCount, it.quizCorrectCount,
                it.quizQuestionCount, it.masteredExpressionCount, it.score, it.maxCombo,
            )
        } ?: emptyList()
    },
    restore = { values ->
        values.takeIf { it.size == 7 }?.let {
            LearningSessionSummary(
                learnedExpressionCount = it[0],
                newlySavedExpressionCount = it[1],
                quizCorrectCount = it[2],
                quizQuestionCount = it[3],
                masteredExpressionCount = it[4],
                score = it[5],
                maxCombo = it[6],
            )
        }
    },
)
