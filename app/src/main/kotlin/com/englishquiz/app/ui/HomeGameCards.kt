package com.englishquiz.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.englishquiz.app.domain.game.BossPolicy
import com.englishquiz.app.domain.game.BossStatus
import com.englishquiz.app.domain.game.DailyMission
import com.englishquiz.app.domain.game.GameStats
import com.englishquiz.app.domain.game.StreakShieldPolicy
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleButtonColors
import com.englishquiz.app.ui.theme.MongleCard
import com.englishquiz.app.ui.theme.MongleChip
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleProgressBar

/** Level, title, experience bar and the shield shop (백로그 038/040). */
@Composable
internal fun HomeLevelCard(game: GameStats, onBuyShield: () -> Unit) {
    MongleCard(face = MongleColor.PurpleSoft, border = MongleColor.PurpleSoftBorder) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "Lv.${game.level.level} ${game.level.title}",
                style = MaterialTheme.typography.titleLarge,
                color = MongleColor.PurpleDeep,
                modifier = Modifier.weight(1f),
            )
            MongleChip("${game.totalPoints}점", MongleColor.Surface, MongleColor.PurpleDeep)
        }
        MongleProgressBar(game.level.progress, Modifier.fillMaxWidth())
        Text(
            game.level.xpForNextLevel?.let { "다음 레벨까지 ${it - game.level.xpIntoLevel}점" } ?: "최고 레벨이에요",
            style = MaterialTheme.typography.labelMedium,
            color = MongleColor.InkMuted,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "보호권 ${game.shields}개 · 쓸 수 있는 점수 ${game.balance}점",
                style = MaterialTheme.typography.bodySmall,
                color = MongleColor.InkMuted,
                modifier = Modifier.weight(1f),
            )
        }
        MongleButton(
            text = "보호권 사기 (${StreakShieldPolicy.PRICE_POINTS}점)",
            onClick = onBuyShield,
            enabled = game.balance >= StreakShieldPolicy.PRICE_POINTS,
            height = 44.dp,
            colors = MongleButtonColors.Accent,
        )
    }
}

/** Today's three goals (백로그 037) with a tick on each one met. */
@Composable
internal fun HomeMissionCard(missions: List<DailyMission>) {
    MongleCard {
        Text("오늘의 미션", style = MaterialTheme.typography.titleMedium)
        missions.forEach { mission ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (mission.done) "✅" else "⬜",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    mission.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (mission.done) MongleColor.InkMuted else MongleColor.Ink,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "+${mission.bonusPoints}점",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (mission.done) MongleColor.GreenInk else MongleColor.InkFaded,
                )
            }
        }
    }
}

/** The weekend boss (백로그 041): open to tap on a weekend, otherwise a countdown. */
@Composable
internal fun HomeBossCard(status: BossStatus, onClick: () -> Unit) {
    val open = status == BossStatus.Open
    MongleCard(
        modifier = if (open) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier,
        face = if (open) MongleColor.Yellow else MongleColor.Surface,
        border = if (open) MongleColor.YellowDeep else MongleColor.Border,
    ) {
        Text("⚔️ 주간 보스전", style = MaterialTheme.typography.titleMedium)
        Text(
            when (status) {
                BossStatus.Open ->
                    "흔들리는 표현 ${BossPolicy.MAX_QUESTIONS}문제, 점수 ${BossPolicy.POINTS_MULTIPLIER}배. 눌러서 도전하기"
                BossStatus.Cleared -> "이번 주 보스전은 끝냈어요. 다음 토요일에 다시 열려요"
                is BossStatus.Waiting -> "토요일에 열려요 (D-${status.daysUntil})"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MongleColor.InkMuted,
        )
    }
}
