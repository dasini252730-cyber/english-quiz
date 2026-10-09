package com.englishquiz.app.ui.quiz

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleButtonColors
import com.englishquiz.app.ui.theme.MongleChip
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleIcons

/**
 * The card under the options once an answer is marked (백로그 035/036). A correct answer pulses
 * the card and buzzes once; a wrong one shakes it. Both run only when the card first appears for
 * that answer, keyed on the question, so a recomposition (the record landing, a rotation) does not
 * replay them.
 */
@Composable
internal fun QuizFeedback(state: QuizUiState.InProgress, onNext: () -> Unit, onRetryRecord: () -> Unit) {
    val correct = state.isCorrect == true
    val face = if (correct) MongleColor.GreenSoft else MongleColor.Highlight
    val ink = if (correct) MongleColor.GreenInk else MongleColor.AmberLabel
    val haptic = LocalHapticFeedback.current
    val scale = remember { Animatable(1f) }
    val shake = remember { Animatable(0f) }
    LaunchedEffect(state.question, correct) {
        if (correct) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            scale.snapTo(0.92f)
            scale.animateTo(1f, spring(dampingRatio = 0.4f, stiffness = 500f))
        } else {
            haptic.performHapticFeedback(HapticFeedbackType.Reject)
            shake.snapTo(0f)
            shake.animateTo(
                0f,
                keyframes {
                    durationMillis = 360
                    -10f at 60
                    8f at 120
                    -6f at 180
                    4f at 240
                    0f at 360
                },
            )
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Lambda overloads read the animations at layout/draw time, so each frame does not recompose.
            .offset { IntOffset(shake.value.dp.roundToPx(), 0) }
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(RoundedCornerShape(24.dp))
            .background(face)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FeedbackHeadline(state, correct, ink)
        Text(state.question.explanation, style = MaterialTheme.typography.bodyLarge, color = ink)
        state.growth?.let { GrowthLine(it, ink) }
        if (state.recordFailed) {
            Text(
                "답변을 학습 기록에 저장하지 못했어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MongleColor.Danger,
            )
            MongleButton("답변 다시 기록", onRetryRecord, height = 48.dp)
        }
        MongleButton(
            // The last question hands over to the result screen, or to the missed ones first.
            text = when {
                state.questionNumber != state.totalQuestions -> "다음 문제"
                state.retryPending -> "틀린 문제 다시 풀기"
                else -> "결과 보기"
            },
            onClick = onNext,
            colors = if (correct) MongleButtonColors.Positive else MongleButtonColors.Primary,
        )
    }
}

@Composable
private fun FeedbackHeadline(state: QuizUiState.InProgress, correct: Boolean, ink: Color) {
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
            modifier = Modifier.weight(1f),
        )
        if (correct && state.score.lastEarned > 0) {
            MongleChip("+${state.score.lastEarned}점", MongleColor.Green, Color.White)
        }
    }
}

/** "sketchy: 🌱 새싹 → 🌿 잎으로 자랐어요" or the fall back to the seed after a wrong answer. */
@Composable
private fun GrowthLine(growth: GrowthChange, ink: Color) {
    val after = growth.after.title + directionParticle(growth.after.label)
    val text = when {
        growth.grew -> "${growth.expression}: ${growth.before.title} → ${after} 자랐어요"
        growth.after == growth.before -> "${growth.expression}: ${growth.after.title} 그대로예요"
        else -> "${growth.expression}: ${after} 돌아갔어요"
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, color = ink)
}

/** "(으)로" after [word]: "로" after a vowel or ㄹ, "으로" after any other final consonant. */
internal fun directionParticle(word: String): String {
    val last = word.lastOrNull() ?: return "로"
    if (last !in '가'..'힣') return "로"
    val finalConsonant = (last - '가') % 28
    return if (finalConsonant == 0 || finalConsonant == 8) "로" else "으로"
}
