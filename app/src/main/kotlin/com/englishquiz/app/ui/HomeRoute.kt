package com.englishquiz.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.englishquiz.app.data.ai.AiLearningClient
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.preferences.AppSettings
import com.englishquiz.app.data.preferences.GameProgressRepository
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.ui.badge.BadgeRoute
import com.englishquiz.app.ui.boss.BossRoute
import com.englishquiz.app.ui.library.LibraryRoute
import com.englishquiz.app.ui.library.LibrarySessionRoute
import com.englishquiz.app.ui.review.ReviewQuizRoute
import com.englishquiz.app.ui.review.ReviewRoute
import com.englishquiz.app.ui.session.LearningSessionRoute
import com.englishquiz.app.ui.session.SessionStatusScreen

/** Level used for a mode that has none stored yet (before the diagnosis has written one). */
private const val DEFAULT_DIFFICULTY = 3

internal enum class HomeDestination(val title: String) {
    HOME("Home"),
    CONVERSATION("Conversation"),
    STORY("Story"),
    REVIEW("복습함"),
    REVIEW_QUIZ("복습 연습"),
    LIBRARY("지난 이야기"),
    LIBRARY_SESSION("다시 읽기"),
    BADGES("배지"),
    BOSS("주간 보스전"),
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
    settings: AppSettings,
    /** [mode] is null for a library re-read (백로그 026): recorded, but no level suggestion is drawn from it. */
    onSessionRecorded: suspend (mode: ContentMode?, sessionCompletedAtEpochMillis: Long) -> Unit = { _, _ -> },
    onLevelChange: (ContentMode, Int) -> Unit = { _, _ -> },
    onSuggestionAnswer: (ContentMode, accepted: Boolean) -> Unit = { _, _ -> },
    /** The game's own store (백로그 037~041); null shows home without the game cards. */
    gameRepository: GameProgressRepository? = null,
) {
    var destinationName by rememberSaveable { mutableStateOf(HomeDestination.HOME.name) }
    val destination = HomeDestination.valueOf(destinationName)
    val goHome = { destinationName = HomeDestination.HOME.name }
    // The library passage chosen for a re-read, as "<learningDate>|<mode>" so it survives recreation.
    var libraryPick by rememberSaveable { mutableStateOf<String?>(null) }
    // The rows the review box was showing when "퀴즈 풀기" was tapped (백로그 052), as "id,id,…".
    var reviewQuizIds by rememberSaveable { mutableStateOf("") }

    when (destination) {
        HomeDestination.HOME -> LearningHomeSummary(
            repository = repository,
            gameRepository = gameRepository,
            levels = settings.levels.mapValues { HomeLevel(it.value.level, it.value.suggestedLevel) },
            onLevelChange = onLevelChange,
            onSuggestionAnswer = onSuggestionAnswer,
        ) { destinationName = it.name }
        HomeDestination.REVIEW -> ReviewRoute(repository, goHome) { ids ->
            reviewQuizIds = ids.joinToString(",")
            destinationName = HomeDestination.REVIEW_QUIZ.name
        }
        HomeDestination.REVIEW_QUIZ -> ReviewQuizRoute(
            repository = repository,
            expressionIds = reviewQuizIds.split(",").mapNotNull { it.toLongOrNull() }.toSet(),
            onDone = { destinationName = HomeDestination.REVIEW.name },
        )
        HomeDestination.BADGES -> if (gameRepository != null) {
            BadgeRoute(repository, gameRepository, goHome, ::todayIso)
        } else {
            LaunchedEffect(Unit) { goHome() }
        }
        // A boss session is recorded like any other but drawn from no mode, so no level suggestion.
        HomeDestination.BOSS -> BossRoute(repository, gameRepository, goHome) { onSessionRecorded(null, it) }
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
                difficulty = settings.level(content.mode) ?: DEFAULT_DIFFICULTY,
                onExit = goHome,
                onSessionRecorded = { onSessionRecorded(null, it) },
                initialContent = content,
                gameRepository = gameRepository,
            )
        }
        HomeDestination.CONVERSATION, HomeDestination.STORY -> {
            val mode = if (destination == HomeDestination.CONVERSATION) ContentMode.CONVERSATION else ContentMode.STORY
            LearningSession(
                mode = mode,
                title = destination.title,
                repository = repository,
                aiClient = aiClient,
                difficulty = settings.level(mode) ?: DEFAULT_DIFFICULTY,
                onExit = goHome,
                onSessionRecorded = { onSessionRecorded(mode, it) },
                gameRepository = gameRepository,
            )
        }
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
    gameRepository: GameProgressRepository? = null,
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
        gameRepository = gameRepository,
    )
}
