package com.englishquiz.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleIconButton
import com.englishquiz.app.ui.theme.MongleIcons
import com.englishquiz.app.ui.theme.mongleBottomSheetInsets

/** The dimmed area around the sheet; tapping it closes the overlay. */
internal const val SCRIM_DESCRIPTION = "표현 뜻 닫기"

/** Whether a fetched meaning was persisted to the review store. */
enum class SaveStatus { SAVED, FAILED }

/** UI state for the expression-meaning overlay shown over the reader. */
sealed interface MeaningUiState {
    data class Loading(val expression: String) : MeaningUiState
    data class Success(val expression: String, val meaning: String, val saveStatus: SaveStatus) : MeaningUiState
    data class Error(val expression: String, val message: String) : MeaningUiState
}

/**
 * A modal sheet (not a screen transition) that shows the contextual meaning of a tapped
 * expression: a loading state, a success state (meaning + pronunciation + save feedback, with a
 * save-only retry when persisting the lookup failed), and a failure state (error + full retry).
 * It sits above the reader, so dismissing it leaves the reader's scroll position untouched.
 */
@Composable
fun MeaningOverlay(
    state: MeaningUiState,
    canPlay: Boolean,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
    onRetrySave: () -> Unit,
    onPlay: (String) -> Unit,
) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        // The dialog content fills the window, so the platform's own outside-tap dismiss never
        // fires: the scrim is the dismiss target instead, and the sheet swallows taps that land
        // on it so a stray touch inside the sheet does not close it.
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier
                    .matchParentSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    )
                    .semantics { contentDescription = SCRIM_DESCRIPTION },
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) { detectTapGestures { } }
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MongleColor.Surface)
                    .mongleBottomSheetInsets()
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .width(40.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(MongleColor.Handle),
                )
                when (state) {
                    is MeaningUiState.Loading -> LoadingBody(state)
                    is MeaningUiState.Success -> SuccessBody(state, canPlay, onPlay, onRetrySave)
                    is MeaningUiState.Error -> ErrorBody(state, onRetry)
                }
                MongleButton("닫기", onDismiss)
            }
        }
    }
}

@Composable
private fun ColumnScope.LoadingBody(state: MeaningUiState.Loading) {
    Text(state.expression, style = MaterialTheme.typography.displayMedium)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        Text("뜻을 찾는 중이에요...", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ColumnScope.SuccessBody(
    state: MeaningUiState.Success,
    canPlay: Boolean,
    onPlay: (String) -> Unit,
    onRetrySave: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = state.expression,
            style = MaterialTheme.typography.displayMedium,
            modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp),
        )
        MongleIconButton(
            icon = MongleIcons.Speaker,
            contentDescription = "발음 듣기",
            onClick = { onPlay(state.expression) },
            enabled = canPlay,
            tint = MongleColor.BlueIcon,
            background = MongleColor.BlueSoft,
            size = 52.dp,
            iconSize = 26.dp,
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MongleColor.MeaningPanel)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("이 문장에서는", style = MaterialTheme.typography.labelMedium, color = MongleColor.AmberLabel)
        Text(state.meaning, style = MaterialTheme.typography.titleLarge)
    }
    when (state.saveStatus) {
        SaveStatus.SAVED -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                MongleIcons.Check,
                contentDescription = null,
                tint = MongleColor.Surface,
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MongleColor.Green)
                    .padding(5.dp),
            )
            Text(
                "복습함에 저장했어요",
                style = MaterialTheme.typography.labelLarge,
                color = MongleColor.GreenInk,
            )
        }
        SaveStatus.FAILED -> {
            Text(
                "복습함에 저장하지 못했어요",
                style = MaterialTheme.typography.bodyLarge,
                color = MongleColor.Danger,
            )
            MongleButton("다시 저장", onRetrySave, height = 48.dp)
        }
    }
}

@Composable
private fun ColumnScope.ErrorBody(state: MeaningUiState.Error, onRetry: () -> Unit) {
    Text(state.expression, style = MaterialTheme.typography.displayMedium)
    Text(state.message, style = MaterialTheme.typography.bodyLarge, color = MongleColor.Danger)
    MongleButton("다시 시도", onRetry, height = 48.dp)
}
