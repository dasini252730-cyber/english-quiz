package com.englishquiz.app.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import com.englishquiz.app.domain.session.LearningSessionSummary
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleButtonColors
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleIcons
import com.englishquiz.app.ui.theme.DarkBackgroundSystemBars
import com.englishquiz.app.ui.theme.mongleScreenInsets

@Composable
fun ResultScreen(
    summary: LearningSessionSummary,
    streakState: StreakUiState,
    onRetry: () -> Unit,
    onDone: () -> Unit,
    /** Points, level, missions and badges (백로그 037~039); null hides the panel. */
    game: ResultGame? = null,
) {
    DarkBackgroundSystemBars()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MongleColor.Purple)
            .mongleScreenInsets()
            .verticalScroll(rememberScrollState())
            .padding(start = 24.dp, end = 24.dp, top = 44.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("오늘 학습 완료", style = MaterialTheme.typography.displayMedium, color = Color.White)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ResultStat("퀴즈 정답률", "${summary.correctPercent}%", MongleColor.GreenInk, Modifier.weight(1f))
            ResultStat("학습한 표현", "${summary.learnedExpressionCount}개", MongleColor.BlueInk, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ResultStat("새로 저장한 표현", "${summary.newlySavedExpressionCount}개", MongleColor.AmberLabel, Modifier.weight(1f))
            ResultStat("암기 완료 표현", "${summary.masteredExpressionCount}개", MongleColor.GreenInk, Modifier.weight(1f))
        }
        // 백로그 035: what the quiz paid and the longest run of correct answers.
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            ResultStat("오늘 점수", "${summary.score}점", MongleColor.PurpleDeep, Modifier.weight(1f))
            ResultStat("최고 콤보", "${summary.maxCombo}", MongleColor.AmberLabel, Modifier.weight(1f))
        }
        StreakPanel(streakState, onRetry)
        game?.let { ResultGamePanel(it) }
        Spacer(Modifier.padding(top = 8.dp))
        MongleButton("완료", onDone, colors = MongleButtonColors.Accent)
    }
}

@Composable
private fun StreakPanel(streakState: StreakUiState, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MongleColor.PurplePanel)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when (streakState) {
            StreakUiState.Loading -> Text(
                "스트릭을 계산하고 있어요.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White,
            )
            StreakUiState.Error -> {
                Text(
                    "스트릭을 불러오지 못했어요.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                )
                MongleButton("다시 시도", onRetry, colors = MongleButtonColors.Accent, height = 48.dp)
            }
            is StreakUiState.Ready -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    MongleIcons.Flame,
                    contentDescription = null,
                    tint = MongleColor.Yellow,
                    modifier = Modifier.size(34.dp),
                )
                Text(
                    "${streakState.streakDays}일 연속 학습 중",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ResultStat(label: String, value: String, labelInk: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MongleColor.Surface)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = labelInk)
        Text(value, style = MaterialTheme.typography.displaySmall, color = MongleColor.Ink)
    }
}
