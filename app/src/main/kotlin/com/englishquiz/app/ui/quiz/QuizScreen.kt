package com.englishquiz.app.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.englishquiz.app.domain.game.QuizScore
import com.englishquiz.app.domain.quiz.QuizOption
import com.englishquiz.app.domain.quiz.QuizQuestionType
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleCard
import com.englishquiz.app.ui.theme.MongleChip
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleIconButton
import com.englishquiz.app.ui.theme.MongleIcons
import com.englishquiz.app.ui.theme.MongleOptionButton
import com.englishquiz.app.ui.theme.MongleProgressBar
import com.englishquiz.app.ui.theme.OptionTone
import com.englishquiz.app.ui.theme.mongleScreenInsets

@Composable
fun QuizScreen(
    state: QuizUiState,
    onSelectOption: (QuizOption) -> Unit,
    onNext: () -> Unit,
    onRetryRecord: () -> Unit,
    onEmptyContinue: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
    /** The typed blank's submit and hint (백로그 043); unused by the other question types. */
    onSubmitTyped: (String) -> Unit = {},
    onHint: () -> Unit = {},
    /** The learn card's answers and pronunciation (백로그 045). */
    onKnown: () -> Unit = {},
    onUnknown: () -> Unit = {},
    speechReady: Boolean = false,
    onPlay: (String) -> Unit = {},
) {
    Column(Modifier.fillMaxSize().background(MongleColor.Cream).mongleScreenInsets()) {
        QuizHeader(state, onBack)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            when (state) {
                QuizUiState.Loading -> Text(
                    "문제를 준비하고 있어요.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                QuizUiState.Error -> QuizErrorContent(onRetry)
                QuizUiState.Empty -> QuizEmptyContent(onEmptyContinue)
                is QuizUiState.InProgress -> if (state.question.type == QuizQuestionType.LEARN_CARD) {
                    Text("처음 보는 표현이에요. 읽어 보고 아는지 골라 주세요.", style = MaterialTheme.typography.headlineSmall)
                    QuizLearnCard(state.question, speechReady, onPlay, onKnown, onUnknown)
                } else {
                    QuizQuestionContent(state, onSelectOption, onNext, onRetryRecord, onSubmitTyped, onHint)
                }
            }
        }
    }
}

/** The close button, the progress rail and the question counter of the canvas's quiz header. */
@Composable
private fun QuizHeader(state: QuizUiState, onBack: () -> Unit) {
    val progress = state as? QuizUiState.InProgress
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MongleIconButton(MongleIcons.Close, "퀴즈 종료", onBack)
        if (progress == null) {
            Text("오늘의 퀴즈", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        } else {
            MongleProgressBar(
                progress = progress.questionNumber.toFloat() / progress.totalQuestions,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${progress.questionNumber} / ${progress.totalQuestions}",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
    if (progress != null && progress.scoring) QuizScoreRow(progress.score)
}

/** Points so far and the current combo (백로그 035); the combo chip only shows during a run. */
@Composable
private fun QuizScoreRow(score: QuizScore) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MongleChip("${score.points}점", MongleColor.PurpleSoft, MongleColor.PurpleDeep)
        if (score.combo >= 2) {
            MongleChip("${score.combo} 콤보", MongleColor.AmberSoft, MongleColor.AmberInk)
        }
    }
}

@Composable
private fun QuizErrorContent(onRetry: () -> Unit) {
    Text("문제를 불러오지 못했어요.", style = MaterialTheme.typography.bodyLarge)
    MongleButton("다시 시도", onRetry)
}

@Composable
private fun QuizEmptyContent(onContinue: () -> Unit) {
    Text("오늘 복습할 표현이 없어요.", style = MaterialTheme.typography.headlineSmall)
    Text(
        "콘텐츠를 학습하면 오늘의 표현이 쌓여서 다음 퀴즈에 나와요.",
        style = MaterialTheme.typography.bodyLarge,
        color = MongleColor.InkMuted,
    )
    MongleButton("완료", onContinue)
}

@Composable
private fun QuizQuestionContent(
    state: QuizUiState.InProgress,
    onSelectOption: (QuizOption) -> Unit,
    onNext: () -> Unit,
    onRetryRecord: () -> Unit,
    onSubmitTyped: (String) -> Unit,
    onHint: () -> Unit,
) {
    val answered = state.selectedOption != null
    val instruction = questionInstruction(state.question.type)
    Text(if (state.question.isRetry) "다시 풀기 · $instruction" else instruction, style = MaterialTheme.typography.headlineSmall)
    QuizPrompt(state)
    if (state.question.type == QuizQuestionType.TYPED_BLANK) {
        QuizTypedAnswer(state, onSubmitTyped, onHint)
    } else {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            state.question.options.forEach { option ->
                MongleOptionButton(
                    text = option.text,
                    onClick = { onSelectOption(option) },
                    enabled = !answered,
                    tone = optionTone(option, state),
                )
            }
        }
    }
    if (answered) QuizFeedback(state, onNext, onRetryRecord)
}

/** Multiple-choice shows the bare expression with its pronunciation; a blank shows its sentence. */
@Composable
private fun QuizPrompt(state: QuizUiState.InProgress) {
    when (state.question.type) {
        QuizQuestionType.MULTIPLE_CHOICE, QuizQuestionType.LEARN_CARD -> Text(
            state.question.questionText,
            style = MaterialTheme.typography.displayLarge,
        )
        QuizQuestionType.FILL_IN_BLANK, QuizQuestionType.TYPED_BLANK, QuizQuestionType.COMPREHENSION ->
            MongleCard(contentPadding = 20.dp) {
                Text(state.question.questionText, style = MaterialTheme.typography.titleMedium)
            }
    }
}

private fun optionTone(option: QuizOption, state: QuizUiState.InProgress): OptionTone {
    val selected = state.selectedOption ?: return OptionTone.Idle
    return when {
        option.isCorrect -> OptionTone.Correct
        option == selected -> OptionTone.Wrong
        else -> OptionTone.Muted
    }
}

private fun questionInstruction(type: QuizQuestionType): String = when (type) {
    QuizQuestionType.MULTIPLE_CHOICE -> "다음 표현의 뜻으로 알맞은 것을 고르세요."
    QuizQuestionType.FILL_IN_BLANK -> "빈칸에 들어갈 표현을 고르세요."
    QuizQuestionType.TYPED_BLANK -> "빈칸에 들어갈 표현을 직접 입력하세요."
    QuizQuestionType.COMPREHENSION -> "오늘 읽은 내용을 떠올려 답하세요."
    QuizQuestionType.LEARN_CARD -> "처음 보는 표현이에요."
}
