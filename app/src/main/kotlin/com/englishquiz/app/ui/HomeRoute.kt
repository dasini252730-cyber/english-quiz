package com.englishquiz.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.englishquiz.app.data.ai.AiLearningClient
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.domain.streak.StreakPolicy
import com.englishquiz.app.ui.library.LibraryRoute
import com.englishquiz.app.ui.library.LibrarySessionRoute
import com.englishquiz.app.ui.review.ReviewRoute
import com.englishquiz.app.ui.session.LearningSessionRoute
import com.englishquiz.app.ui.session.SessionStatusScreen
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.mongleScreenInsets
import kotlinx.coroutines.CancellationException
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal enum class HomeDestination(val title: String) {
    HOME("Home"),
    CONVERSATION("Conversation"),
    STORY("Story"),
    REVIEW("복습함"),
    LIBRARY("지난 이야기"),
    LIBRARY_SESSION("다시 읽기"),
}

/**
 * The home destination switch (요구사항 21절). Conversation and Story both start a full learning
 * session; 복습함 is a plain list. The selected destination survives recreation so a rotation does
 * not drop the learner back at home mid-session.
 */
@Composable
internal fun LearningHome(
    repository: LearningRepository,
    aiClient: AiLearningClient?,
    difficulty: Int,
    onSessionRecorded: suspend (sessionCompletedAtEpochMillis: Long) -> Unit = {},
) {
    var destinationName by rememberSaveable { mutableStateOf(HomeDestination.HOME.name) }
    val destination = HomeDestination.valueOf(destinationName)
    val goHome = { destinationName = HomeDestination.HOME.name }
    // The library passage chosen for a re-read, as "<learningDate>|<mode>" so it survives recreation.
    var libraryPick by rememberSaveable { mutableStateOf<String?>(null) }

    when (destination) {
        HomeDestination.HOME -> LearningHomeSummary(repository) { destinationName = it.name }
        HomeDestination.REVIEW -> ReviewRoute(repository, goHome)
        HomeDestination.LIBRARY -> LibraryRoute(
            repository = repository,
            onOpen = { item ->
                libraryPick = "${item.learningDate}|${item.mode}"
                destinationName = HomeDestination.LIBRARY_SESSION.name
            },
            onBack = goHome,
        )
        HomeDestination.LIBRARY_SESSION -> LibrarySessionRoute(
            repository = repository,
            pick = libraryPick,
            onBack = goHome,
        ) { content ->
            LearningSession(
                mode = content.mode,
                title = HomeDestination.LIBRARY_SESSION.title,
                repository = repository,
                aiClient = aiClient,
                difficulty = difficulty,
                onExit = goHome,
                onSessionRecorded = onSessionRecorded,
                initialContent = content,
            )
        }
        HomeDestination.CONVERSATION, HomeDestination.STORY -> LearningSession(
            mode = if (destination == HomeDestination.CONVERSATION) {
                ContentMode.CONVERSATION
            } else {
                ContentMode.STORY
            },
            title = destination.title,
            repository = repository,
            aiClient = aiClient,
            difficulty = difficulty,
            onExit = goHome,
            onSessionRecorded = onSessionRecorded,
        )
    }
}

@Composable
private fun LearningSession(
    mode: ContentMode,
    title: String,
    repository: LearningRepository,
    aiClient: AiLearningClient?,
    difficulty: Int,
    onExit: () -> Unit,
    onSessionRecorded: suspend (sessionCompletedAtEpochMillis: Long) -> Unit,
    initialContent: LearningContent? = null,
) {
    if (aiClient == null) {
        // No endpoint was configured for this build, so say so instead of failing a call.
        SessionStatusScreen(
            title = title,
            message = "AI 연결이 아직 설정되지 않았어요. 학습 내용을 만들려면 앱을 빌드할 때 " +
                "Edge Function 주소를 넣어야 해요.",
            onBack = onExit,
        )
        return
    }
    LearningSessionRoute(
        mode = mode,
        repository = repository,
        difficulty = difficulty,
        generateContent = aiClient::generateContent,
        explainMeaning = aiClient::explainMeaning,
        onExit = onExit,
        initialContent = initialContent,
        onSessionRecorded = onSessionRecorded,
    )
}

@Composable
private fun LearningHomeSummary(
    repository: LearningRepository,
    onNavigate: (HomeDestination) -> Unit,
) {
    var retry by remember { mutableIntStateOf(0) }
    val result by produceState<Result<HomeSummary>?>(null, repository, retry) {
        value = null
        try {
            repository.observeSavedExpressions().collect { expressions ->
                val streakDays = StreakPolicy.currentStreakDays(
                    learningDates = repository.listLearningDates(),
                    todayIso = todayIso(),
                )
                value = Result.success(
                    HomeSummary(
                        streakDays = streakDays,
                        savedExpressionCount = expressions.size,
                        masteredExpressionCount = expressions.count { it.isMastered },
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
            summary = summary,
            onConversationClick = { onNavigate(HomeDestination.CONVERSATION) },
            onStoryClick = { onNavigate(HomeDestination.STORY) },
            onReviewClick = { onNavigate(HomeDestination.REVIEW) },
            onLibraryClick = { onNavigate(HomeDestination.LIBRARY) },
        )
    }
}

private fun todayIso(): String = Instant.ofEpochMilli(System.currentTimeMillis())
    .atZone(ZoneId.systemDefault())
    .toLocalDate()
    .format(DateTimeFormatter.ISO_LOCAL_DATE)
