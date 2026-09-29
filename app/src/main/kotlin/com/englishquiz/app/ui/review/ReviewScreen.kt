package com.englishquiz.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.englishquiz.app.data.local.SavedExpressionEntity
import com.englishquiz.app.ui.reader.SpeechState
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleCard
import com.englishquiz.app.ui.theme.MongleChip
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleIconButton
import com.englishquiz.app.ui.theme.MongleIcons
import com.englishquiz.app.ui.theme.mongleScreenInsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ReviewScreen(
    state: ReviewUiState,
    speechState: SpeechState,
    onPlay: (String) -> Unit,
    onStop: () -> Unit,
    onRetry: () -> Unit,
    onBack: () -> Unit,
) {
    val count = (state as? ReviewUiState.Ready)?.expressions?.size
    Column(Modifier.fillMaxSize().background(MongleColor.Cream).mongleScreenInsets()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MongleIconButton(MongleIcons.ChevronLeft, "홈으로", onBack)
            Text(
                "복습함",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            count?.let {
                Text(
                    "${it}개 · 최근 저장순",
                    style = MaterialTheme.typography.labelLarge,
                    color = MongleColor.InkMuted,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
        }
        speechState.error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MongleColor.Danger,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        if (speechState.playing) {
            MongleIconButton(
                icon = MongleIcons.Close,
                contentDescription = "재생 중단",
                onClick = onStop,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 8.dp,
                bottom = 20.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (state) {
                ReviewUiState.Loading -> item {
                    Text("저장한 표현을 불러오고 있어요.", style = MaterialTheme.typography.bodyLarge)
                }
                ReviewUiState.Error -> item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("저장한 표현을 불러오지 못했어요.", style = MaterialTheme.typography.bodyLarge)
                        MongleButton("다시 시도", onRetry)
                    }
                }
                is ReviewUiState.Ready -> {
                    if (state.expressions.isEmpty()) {
                        item {
                            Text(
                                "아직 저장한 표현이 없어요. Conversation이나 Story에서 모르는 표현의 뜻을 확인하면 자동으로 저장돼요.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MongleColor.InkMuted,
                            )
                        }
                    }
                    items(state.expressions, key = { it.id }) { expression ->
                        ReviewExpressionCard(expression, speechState.ready, onPlay)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewExpressionCard(
    expression: SavedExpressionEntity,
    speechReady: Boolean,
    onPlay: (String) -> Unit,
) {
    MongleCard(corner = 18.dp, contentPadding = 10.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f).padding(start = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(expression.displayExpression, style = MaterialTheme.typography.titleMedium)
                    val (chip, face, ink) = reviewChip(expression)
                    MongleChip(chip, face, ink)
                }
                Text(
                    expression.contextMeaning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MongleColor.InkMuted,
                )
                nextReviewLabel(expression).takeIf { it.isNotEmpty() }?.let { next ->
                    Text(
                        next,
                        style = MaterialTheme.typography.labelSmall,
                        color = MongleColor.InkMuted,
                    )
                }
            }
            MongleIconButton(
                icon = MongleIcons.Play,
                contentDescription = "${expression.displayExpression} 발음 듣기",
                onClick = { onPlay(expression.displayExpression) },
                enabled = speechReady,
                tint = MongleColor.BlueIcon,
                background = MongleColor.BlueSoft,
                circleSize = 32.dp,
                iconSize = 14.dp,
            )
        }
    }
}

/** The short state pill of the canvas: its label plus the fill and ink it is drawn in. */
internal fun reviewChip(expression: SavedExpressionEntity): Triple<String, Color, Color> = when {
    expression.isMastered -> Triple("암기 완료", MongleColor.GreenSoft, MongleColor.GreenInk)
    expression.lastReviewedAtEpochMillis == null ->
        Triple("새로 저장", MongleColor.PurpleSoft, MongleColor.PurpleDeep)
    else -> Triple("학습 중", MongleColor.BlueSoft, MongleColor.BlueInk)
}

/**
 * The scheduled review date, or "" when there is none to show. The chip beside the expression
 * already names the state, so repeating it here would say the same thing twice.
 */
internal fun nextReviewLabel(expression: SavedExpressionEntity): String {
    if (expression.isMastered) return ""
    val next = expression.nextReviewAtEpochMillis ?: return ""
    val date = Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))
    return "다음 복습: $date"
}
