package com.englishquiz.app.ui.quiz

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.englishquiz.app.domain.quiz.QuizOption
import com.englishquiz.app.domain.quiz.QuizQuestionType
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleButtonColors
import com.englishquiz.app.ui.theme.MongleCard
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
                is QuizUiState.InProgress ->
                    QuizQuestionContent(state, onSelectOption, onNext, onRetryRecord)
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
) {
    val answered = state.selectedOption != null
    Text(questionInstruction(state.question.type), style = MaterialTheme.typography.headlineSmall)
    QuizPrompt(state)
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
    if (answered) QuizFeedback(state, onNext, onRetryRecord)
}

/** Multiple-choice shows the bare expression with its pronunciation; a blank shows its sentence. */
@Composable
private fun QuizPrompt(state: QuizUiState.InProgress) {
    when (state.question.type) {
        QuizQuestionType.MULTIPLE_CHOICE -> Text(
            state.question.questionText,
            style = MaterialTheme.typography.displayLarge,
        )
        QuizQuestionType.FILL_IN_BLANK -> MongleCard(contentPadding = 20.dp) {
            Text(state.question.questionText, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun QuizFeedback(state: QuizUiState.InProgress, onNext: () -> Unit, onRetryRecord: () -> Unit) {
    val correct = state.isCorrect == true
    val face = if (correct) MongleColor.GreenSoft else MongleColor.Highlight
    val ink = if (correct) MongleColor.GreenInk else MongleColor.AmberLabel
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(face)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (correct) {
                Icon(
                    MongleIcons.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(MongleColor.Green)
                        .padding(7.dp),
                )
            }
            Text(
                if (correct) "정답이에요!" else "아쉬워요, 오답이에요.",
                style = MaterialTheme.typography.headlineMedium,
                color = ink,
            )
        }
        Text(state.question.explanation, style = MaterialTheme.typography.bodyLarge, color = ink)
        if (state.recordFailed) {
            Text(
                "답변을 학습 기록에 저장하지 못했어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MongleColor.Danger,
            )
            MongleButton("답변 다시 기록", onRetryRecord, height = 48.dp)
        }
        MongleButton(
            // The last question hands the session over to the result screen, so it says so.
            text = if (state.questionNumber == state.totalQuestions) "결과 보기" else "다음 문제",
            onClick = onNext,
            colors = if (correct) MongleButtonColors.Positive else MongleButtonColors.Primary,
        )
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
}
