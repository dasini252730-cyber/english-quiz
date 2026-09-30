package com.englishquiz.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.englishquiz.app.domain.difficulty.DifficultyPolicy
import com.englishquiz.app.ui.theme.MongleColor

/** One mode's level as home shows it (백로그 034): the level itself and a pending suggestion. */
data class HomeLevel(val level: Int, val suggestedLevel: Int? = null)

/**
 * A mode's entry card with its level row. The ± buttons and the suggestion answers stop the tap
 * from reaching the card, so adjusting a level never starts a session by accident.
 */
@Composable
internal fun ModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    face: Color,
    shadow: Color,
    ink: Color,
    level: HomeLevel?,
    onClick: () -> Unit,
    onLevelChange: (Int) -> Unit,
    onSuggestionAnswer: (accepted: Boolean) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(shadow)
            .padding(bottom = 6.dp)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(face)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MongleColor.Surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = face, modifier = Modifier.size(30.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.headlineLarge, color = ink)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ink)
            }
            if (level != null) LevelRow(title, level, ink, onLevelChange, onSuggestionAnswer)
        }
    }
}

@Composable
private fun LevelRow(
    title: String,
    level: HomeLevel,
    ink: Color,
    onLevelChange: (Int) -> Unit,
    onSuggestionAnswer: (accepted: Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "레벨 ${level.level} / ${DifficultyPolicy.MAX_DIFFICULTY}",
                style = MaterialTheme.typography.titleSmall,
                color = ink,
            )
            LevelButton("−", "$title 레벨 낮추기", level.level > DifficultyPolicy.MIN_DIFFICULTY, ink) {
                onLevelChange(level.level - 1)
            }
            LevelButton("+", "$title 레벨 높이기", level.level < DifficultyPolicy.MAX_DIFFICULTY, ink) {
                onLevelChange(level.level + 1)
            }
        }
        val suggested = level.suggestedLevel
        if (suggested != null) {
            val direction = if (suggested > level.level) "올려" else "낮춰"
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "최근 결과를 보니 레벨 ${suggested}로 ${direction}볼까요?",
                    style = MaterialTheme.typography.bodySmall,
                    color = ink,
                    modifier = Modifier.weight(1f),
                )
                AnswerChip("레벨 $suggested", ink) { onSuggestionAnswer(true) }
                AnswerChip("유지", ink) { onSuggestionAnswer(false) }
            }
        }
    }
}

@Composable
private fun LevelButton(
    label: String,
    description: String,
    enabled: Boolean,
    ink: Color,
    onClick: () -> Unit,
) {
    // The clickable stays enabled at the end of the scale: a disabled clickable lets the tap
    // through to the card underneath, which would start a session. Only the semantics say so.
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(MongleColor.Surface.copy(alpha = if (enabled) 0.9f else 0.4f))
            .semantics {
                contentDescription = description
                if (!enabled) disabled()
            }
            .clickable(role = Role.Button) { if (enabled) onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, color = if (enabled) MongleColor.Ink else ink)
    }
}

@Composable
private fun AnswerChip(label: String, ink: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MongleColor.Surface)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MongleColor.Ink)
    }
}
