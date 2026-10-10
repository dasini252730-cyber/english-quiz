package com.englishquiz.app.ui.badge

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.englishquiz.app.data.preferences.GameProgressRepository
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.domain.game.Badge
import com.englishquiz.app.domain.game.GameStatsPolicy
import com.englishquiz.app.ui.theme.MongleCard
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleTopBar
import com.englishquiz.app.ui.theme.mongleScreenInsets
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.englishquiz.app.data.repository.listAllSessions

/**
 * The badge list (백로그 039). Opening it is what marks the earned badges as seen, so the "새
 * 배지" flag on home goes away once the learner has looked.
 */
@Composable
fun BadgeRoute(
    repository: LearningRepository,
    gameRepository: GameProgressRepository,
    onBack: () -> Unit,
    todayIso: () -> String,
) {
    val earned by produceState<Set<Badge>?>(null, repository, gameRepository) {
        // Looking and marking as seen are one step under NonCancellable: a glance that leaves at
        // once would otherwise cancel the write (a separate effect may never even start) and the
        // badges would stay flagged as new on home until the next visit.
        value = withContext(NonCancellable) {
            try {
                val stats = GameStatsPolicy.compute(
                    sessions = repository.listAllSessions(),
                    expressions = repository.listSavedExpressions(),
                    progress = gameRepository.progress.first(),
                    todayIso = todayIso(),
                )
                // Seen-state is a convenience; a failed write still leaves the badge earned.
                runCatching { gameRepository.markBadgesSeen(stats.earnedBadges) }
                stats.earnedBadges
            } catch (_: Exception) {
                emptySet()
            }
        }
    }
    BackHandler(onBack = onBack)
    BadgeScreen(earned = earned.orEmpty(), onBack = onBack)
}

@Composable
fun BadgeScreen(earned: Set<Badge>, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MongleColor.Cream).mongleScreenInsets()) {
        MongleTopBar(title = "배지 ${earned.size} / ${Badge.entries.size}", onBack = onBack)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(Badge.entries, key = { it.name }) { badge -> BadgeRow(badge, badge in earned) }
        }
    }
}

@Composable
private fun BadgeRow(badge: Badge, isEarned: Boolean) {
    MongleCard(corner = 18.dp, contentPadding = 12.dp, face = if (isEarned) MongleColor.Surface else MongleColor.Cream) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (isEarned) badge.emoji else "🔒", style = MaterialTheme.typography.headlineMedium)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    badge.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isEarned) MongleColor.Ink else MongleColor.InkFaded,
                )
                Text(badge.description, style = MaterialTheme.typography.bodySmall, color = MongleColor.InkMuted)
            }
        }
    }
}
