package com.englishquiz.app.ui.roleplay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.domain.roleplay.RoleplayGrader
import com.englishquiz.app.domain.roleplay.RoleplayTurn
import com.englishquiz.app.ui.reader.SpeechState
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleCard
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleIconButton
import com.englishquiz.app.ui.theme.MongleIcons

internal const val REVEAL_LABEL = "원문 보기"
internal const val SPEAK_PROMPT = "이 대사를 말해 보세요"

/**
 * One turn of the play. The device's line is shown and read aloud; the learner's line is hidden
 * behind its meanings and a hint until "원문 보기", and answered through [RoleplayLearnerInput].
 */
@Composable
internal fun RoleplayTurnScreen(
    turn: RoleplayTurn,
    turnCount: Int,
    language: Language,
    progress: RoleplayProgress,
    speechState: SpeechState,
    recognizerState: RecognizerState,
    saveError: String?,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onListen: () -> Unit,
    onStopListening: () -> Unit,
    onTyped: (String) -> Unit,
    onUseTyping: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val settled = !turn.isLearner || progress.settled
    RoleplayScaffold(
        onBack = onBack,
        trailing = {
            Text("${turn.index + 1}/$turnCount", style = MaterialTheme.typography.labelLarge, color = MongleColor.InkMuted)
        },
        actions = {
            saveError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MongleColor.Danger) }
            MongleButton(if (turn.index + 1 == turnCount) "결과 보기" else "다음", onNext, enabled = settled)
        },
    ) {
        Text("내 역할: ${progress.speaker}", style = MaterialTheme.typography.labelLarge, color = MongleColor.InkMuted)
        speechState.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MongleColor.Danger) }
        if (turn.isLearner) {
            LearnerLineCard(turn, language, progress, canPlay = speechState.ready, onPlay = onPlay, onReveal = { progress.revealed = true })
            RoleplayLearnerInput(turn, language, progress, recognizerState, onListen, onStopListening, onTyped, onUseTyping)
        } else {
            DeviceLineCard(turn, speechState, onPlay, onStop)
        }
    }
}

/** Someone else's line: name, text and a play/stop control. */
@Composable
private fun DeviceLineCard(turn: RoleplayTurn, speechState: SpeechState, onPlay: () -> Unit, onStop: () -> Unit) {
    MongleCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Text(turn.speaker, style = MaterialTheme.typography.labelLarge, color = MongleColor.InkMuted)
            if (speechState.playing) {
                MongleIconButton(MongleIcons.Close, "재생 중단", onStop, tint = MongleColor.Surface, background = MongleColor.Purple, circleSize = 32.dp, iconSize = 14.dp)
            } else {
                MongleIconButton(
                    MongleIcons.Play, "대사 다시 듣기", onPlay, enabled = speechState.ready,
                    tint = MongleColor.BlueIcon, background = MongleColor.BlueSoft, circleSize = 32.dp, iconSize = 14.dp,
                )
            }
        }
        Text(turn.text, style = MaterialTheme.typography.bodyLarge, color = MongleColor.Ink)
    }
}

/**
 * The learner's own line, as a cue rather than a text to read: the passage's meanings for the
 * highlighted expressions in it (there is no per-line translation, so none is invented), the
 * first-letter hint, and the original behind "원문 보기".
 */
@Composable
private fun LearnerLineCard(
    turn: RoleplayTurn,
    language: Language,
    progress: RoleplayProgress,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onReveal: () -> Unit,
) {
    val meanings = remember(turn) { turn.meanings() }
    val hint = remember(turn, language) { RoleplayGrader.hint(turn.text, language) }
    MongleCard(face = MongleColor.PurpleSoft, border = MongleColor.PurpleSoftBorder) {
        Text("${turn.speaker} (나)", style = MaterialTheme.typography.labelLarge, color = MongleColor.Purple)
        Text(SPEAK_PROMPT, style = MaterialTheme.typography.titleMedium, color = MongleColor.Ink)
        if (meanings.isEmpty()) {
            Text("이 대사에는 표시된 표현이 없어요. 힌트를 보고 말해 보세요.", style = MaterialTheme.typography.bodySmall, color = MongleColor.InkMuted)
        } else {
            meanings.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium, color = MongleColor.Ink) }
        }
        if (hint.isNotEmpty()) Text("힌트: $hint", style = MaterialTheme.typography.bodyMedium, color = MongleColor.InkMuted)
        if (progress.revealed) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(turn.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MongleColor.Ink, modifier = Modifier.weight(1f))
                MongleIconButton(
                    MongleIcons.Play, "원문 듣기", onPlay, enabled = canPlay,
                    tint = MongleColor.BlueIcon, background = MongleColor.BlueSoft, circleSize = 32.dp, iconSize = 14.dp,
                )
            }
        } else {
            Text(
                REVEAL_LABEL,
                style = MaterialTheme.typography.labelLarge,
                color = MongleColor.Purple,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(MongleColor.Surface)
                    .clickable(onClick = onReveal)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
