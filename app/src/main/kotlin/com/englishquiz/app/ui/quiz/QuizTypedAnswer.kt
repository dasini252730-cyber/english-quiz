package com.englishquiz.app.ui.quiz

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleButtonColors
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleOptionButton
import com.englishquiz.app.ui.theme.OptionTone

/**
 * The typed blank (백로그 043): a text field with submit, a first-letter hint and a way to give
 * up. Once answered, the correct expression is shown as a marked option and, after a wrong
 * attempt, what the learner typed beside it. The draft is keyed on the question so a new question
 * starts with an empty field.
 */
@Composable
internal fun QuizTypedAnswer(
    state: QuizUiState.InProgress,
    onSubmit: (String) -> Unit,
    onHint: () -> Unit,
) {
    val selected = state.selectedOption
    if (selected != null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            MongleOptionButton(state.question.expression, onClick = {}, enabled = false, tone = OptionTone.Correct)
            if (!selected.isCorrect) {
                Text(
                    if (selected.text.isEmpty()) "이번에는 넘겼어요." else "내 답: ${selected.text}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MongleColor.Danger,
                )
            }
        }
        return
    }
    var draft by rememberSaveable(state.question.expression) { mutableStateOf("") }
    val submit = { if (draft.isNotBlank()) onSubmit(draft) }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("영어 표현 입력") },
            supportingText = state.hint?.let { hint -> { Text("힌트: $hint") } },
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
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MongleButton(
                text = "힌트",
                onClick = onHint,
                modifier = Modifier.weight(1f),
                enabled = state.hint == null,
                colors = MongleButtonColors.Accent,
                height = 44.dp,
            )
            MongleButton(
                text = "모르겠어요",
                onClick = { onSubmit("") },
                modifier = Modifier.weight(1f),
                colors = MongleButtonColors.Accent,
                height = 44.dp,
            )
        }
    }
}
