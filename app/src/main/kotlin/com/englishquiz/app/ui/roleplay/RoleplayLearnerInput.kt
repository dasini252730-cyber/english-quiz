package com.englishquiz.app.ui.roleplay

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.domain.roleplay.RoleplayGrader
import com.englishquiz.app.domain.roleplay.RoleplayTurn
import com.englishquiz.app.domain.roleplay.TurnOutcome
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleButtonColors
import com.englishquiz.app.ui.theme.MongleCard
import com.englishquiz.app.ui.theme.MongleColor

internal const val TYPED_PLACEHOLDER = "대사를 직접 입력"
internal const val USE_TYPING_LABEL = "직접 입력하기"

/**
 * How the learner answers a line and what came of it: the microphone (or, when it is out of
 * reach, a text field), the transcript, the word-match grade and whether the line's highlighted
 * expression was in it. Once the line is settled only the result stays.
 */
@Composable
internal fun RoleplayLearnerInput(
    turn: RoleplayTurn,
    language: Language,
    progress: RoleplayProgress,
    recognizerState: RecognizerState,
    onListen: () -> Unit,
    onStopListening: () -> Unit,
    onTyped: (String) -> Unit,
    onUseTyping: () -> Unit,
) {
    progress.transcript?.let { heard -> AttemptResult(turn, language, heard, progress) }
    if (progress.settled) return
    if (progress.typedInput) {
        TypedLine(language, key = turn.index to progress.attempts, onSubmit = onTyped)
    } else {
        SpokenLine(progress, recognizerState, onListen, onStopListening, onUseTyping)
    }
}

@Composable
private fun AttemptResult(turn: RoleplayTurn, language: Language, heard: String, progress: RoleplayProgress) {
    val grade = remember(turn, heard, language) { RoleplayGrader.grade(turn.text, heard, language) }
    val verdict = when (progress.outcome) {
        TurnOutcome.PASSED -> "통과"
        TurnOutcome.FAILED -> "이번 대사는 넘어가요"
        else -> "다시 말하기 (남은 기회 ${progress.retriesLeft})"
    }
    val tone = if (progress.outcome == TurnOutcome.PASSED) MongleColor.GreenInk else MongleColor.Danger
    MongleCard {
        Text("내 말: $heard", style = MaterialTheme.typography.bodyMedium, color = MongleColor.Ink)
        Text(
            "일치 ${grade.percent}% (${grade.matched}/${grade.total}) · $verdict",
            style = MaterialTheme.typography.titleMedium,
            color = tone,
        )
        turn.expression?.let { expression ->
            val contained = remember(heard, expression) { RoleplayGrader.containsExpression(heard, expression.text, language) }
            Text(
                if (contained) "표현 '${expression.text}'을(를) 말했어요" else "표현 '${expression.text}'이(가) 들리지 않았어요",
                style = MaterialTheme.typography.bodyMedium,
                color = if (contained) MongleColor.GreenInk else MongleColor.AmberInk,
            )
        }
    }
}

/** The microphone: one tap starts an utterance, a second stops it; the learner can also switch to typing. */
@Composable
private fun SpokenLine(
    progress: RoleplayProgress,
    recognizerState: RecognizerState,
    onListen: () -> Unit,
    onStopListening: () -> Unit,
    onUseTyping: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        recognizerState.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MongleColor.Danger) }
        if (recognizerState.listening) {
            Text("듣고 있어요…", style = MaterialTheme.typography.bodyMedium, color = MongleColor.Purple)
            MongleButton("듣기 중단", onStopListening, colors = MongleButtonColors.Accent)
        } else {
            MongleButton(if (progress.attempts == 0) "말하기" else "다시 말하기", onListen)
        }
        Text(
            USE_TYPING_LABEL,
            style = MaterialTheme.typography.labelLarge,
            color = MongleColor.InkMuted,
            modifier = Modifier.clickable(onClick = onUseTyping).padding(vertical = 4.dp),
        )
    }
}

/** The fallback when the microphone is denied or absent: the line is typed instead. [key] clears the draft per attempt. */
@Composable
private fun TypedLine(language: Language, key: Pair<Int, Int>, onSubmit: (String) -> Unit) {
    var draft by rememberSaveable(key) { mutableStateOf("") }
    val submit = { if (draft.isNotBlank()) onSubmit(draft.trim()) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("마이크를 쓸 수 없어 직접 입력해요.", style = MaterialTheme.typography.bodySmall, color = MongleColor.InkMuted)
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("${language.korean} $TYPED_PLACEHOLDER") },
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MongleColor.Purple,
                unfocusedBorderColor = MongleColor.BorderStrong,
            ),
        )
        MongleButton("제출", submit, enabled = draft.isNotBlank())
    }
}
