package com.englishquiz.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.preferences.GameProgress
import com.englishquiz.app.data.preferences.GameProgressRepository
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.data.season.SeasonCoordinator
import com.englishquiz.app.domain.game.GameStatsPolicy
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.mongleScreenInsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.englishquiz.app.domain.review.ReviewQueue
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.englishquiz.app.data.repository.listAllSessions

/**
 * Loads what home shows: the saved expressions, the streak and — with a [gameRepository] — the
 * game picture (백로그 037~041). Both stores are observed, so buying a shield or finishing a
 * session refreshes the cards without leaving home.
 *
 * A missed yesterday is shielded here, on the way in (백로그 040): when a shield is in hand it is
 * spent on that day and the store's next emission shows the streak kept.
 */
@Composable
internal fun LearningHomeSummary(
    repository: LearningRepository,
    gameRepository: GameProgressRepository?,
    levels: Map<ContentMode, HomeLevel>,
    onLevelChange: (ContentMode, Int) -> Unit,
    onSuggestionAnswer: (ContentMode, accepted: Boolean) -> Unit,
    /** One line under a mode's level after a change (백로그 063/075); absent shows none. */
    levelNotes: Map<ContentMode, String> = emptyMap(),
    /** The language home works in (백로그 066): its expressions are counted and queued; the streak is shared. */
    language: Language = Language.DEFAULT,
    onLanguageChange: (Language) -> Unit = {},
    /** Online voices for speech (백로그 073). */
    networkVoices: Boolean = false,
    onNetworkVoicesChange: (Boolean) -> Unit = {},
    /** The seasons (백로그 075): home says "준비 중" while no episode waits, and asks the pipeline on every open. */
    seasons: SeasonCoordinator? = null,
    onPrepare: (Language) -> Unit = {},
    onNavigate: (HomeDestination) -> Unit,
) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(language) { onPrepare(language) }
    val unread by produceState<Int?>(null, seasons, language) {
        if (seasons == null) return@produceState
        try {
            seasons.observeUnreadCount(language).collect { value = it }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            value = null
        }
    }
    var retry by remember { mutableIntStateOf(0) }
    val result by produceState<Result<HomeSummary>?>(null, repository, gameRepository, retry, language) {
        value = null
        try {
            val progress = gameRepository?.progress ?: flowOf(GameProgress())
            combine(repository.observeSavedExpressions(language), progress) { expressions, game -> expressions to game }
                .collect { (expressions, game) ->
                    val todayIso = todayIso()
                    val sessions = repository.listAllSessions()
                    // The store emits again once a shield is spent, and the next pass finds
                    // yesterday covered, so this settles after one shield at most.
                    gameRepository?.shieldYesterdayIfNeeded(sessions.map { it.learningDate }, todayIso)
                    // The game counts every language (백로그 066: one learner, one streak); the tiles below show this language's.
                    val stats = GameStatsPolicy.compute(sessions, repository.listAllSavedExpressions(), game, todayIso)
                    value = Result.success(
                        HomeSummary(
                            streakDays = stats.streakDays,
                            savedExpressionCount = expressions.size,
                            masteredExpressionCount = expressions.count { it.isMastered },
                            queue = ReviewQueue.of(expressions, System.currentTimeMillis()),
                            game = stats.takeIf { gameRepository != null },
                        ),
                    )
                }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            value = Result.failure(error)
        }
    }

    val summary = result?.getOrNull()
    when {
        result == null -> LoadingScreen()
        summary == null -> Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MongleColor.Cream)
                .mongleScreenInsets()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("저장한 학습 정보를 불러오지 못했어요.", style = MaterialTheme.typography.bodyLarge)
            MongleButton("다시 시도", { retry++ })
        }
        else -> HomeScreen(
            summary = summary.copy(levels = levels, levelNotes = levelNotes, episodeNote = if (unread == 0) EPISODE_PREPARING_NOTE else null),
            onConversationClick = { onNavigate(HomeDestination.CONVERSATION) },
            onStoryClick = { onNavigate(HomeDestination.STORY) },
            onReviewClick = { onNavigate(HomeDestination.REVIEW) },
            onLibraryClick = { onNavigate(HomeDestination.LIBRARY) },
            onLevelChange = onLevelChange,
            onSuggestionAnswer = onSuggestionAnswer,
            onBuyShield = {
                val total = summary.game?.totalPoints ?: return@HomeScreen
                // A purchase that fails leaves the balance as it was; the cards show what is stored.
                scope.launch { runCatching { gameRepository?.buyShield(total) } }
            },
            onBadgesClick = { onNavigate(HomeDestination.BADGES) },
            onBossClick = { onNavigate(HomeDestination.BOSS) },
            language = language,
            onLanguageChange = onLanguageChange,
            networkVoices = networkVoices,
            onNetworkVoicesChange = onNetworkVoicesChange,
        )
    }
}

/** Shown under both cards while no episode waits (백로그 075); the pipeline is making the next one. */
internal const val EPISODE_PREPARING_NOTE = "다음 회차를 준비하고 있어요"

internal fun todayIso(): String = Instant.ofEpochMilli(System.currentTimeMillis())
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .format(DateTimeFormatter.ISO_LOCAL_DATE)
