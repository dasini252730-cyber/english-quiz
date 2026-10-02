package com.englishquiz.app.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.englishquiz.app.ui.theme.MongleChip
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleProgressBar

/** The points, level, missions and new badges block of the result screen (백로그 037~039). */
@Composable
internal fun ResultGamePanel(game: ResultGame) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(MongleColor.PurplePanel)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "+${game.pointsEarned} 경험치",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            if (game.leveledUp) MongleChip("레벨 업!", MongleColor.Yellow, MongleColor.Ink)
        }
        Text(
            "Lv.${game.level.level} ${game.level.title}",
            style = MaterialTheme.typography.titleMedium,
            color = Color.White,
        )
        MongleProgressBar(game.level.progress, Modifier.fillMaxWidth())
        game.level.xpForNextLevel?.let { next ->
            Text(
                "다음 레벨까지 ${next - game.level.xpIntoLevel}점",
                style = MaterialTheme.typography.labelMedium,
                color = MongleColor.PurpleSoft,
            )
        }
        val done = game.missions.count { it.done }
        Text(
            "오늘의 미션 $done / ${game.missions.size}" +
                if (done > 0) " · 보너스 +${game.missions.filter { it.done }.sumOf { it.bonusPoints }}점" else "",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
        )
        if (game.newBadges.isNotEmpty()) {
            Text(
                "새 배지: " + game.newBadges.joinToString(" · ") { "${it.emoji} ${it.title}" },
                style = MaterialTheme.typography.bodyMedium,
                color = MongleColor.Yellow,
            )
        }
    }
}
