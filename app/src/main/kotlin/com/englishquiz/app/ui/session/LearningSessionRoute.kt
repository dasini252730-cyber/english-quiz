package com.englishquiz.app.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.englishquiz.app.data.ai.ContentGenerationRequest
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.ai.ContextualMeaning
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.local.LIBRARY_SESSION_MODE
import com.englishquiz.app.data.preferences.GameProgressRepository
import com.englishquiz.app.data.ai.MeaningRequest
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.domain.session.LearningSessionSummary
import com.englishquiz.app.ui.quiz.QuizRoute
import com.englishquiz.app.ui.reader.ReaderRoute
import com.englishquiz.app.ui.roleplay.RoleplayRoute
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.englishquiz.app.data.repository.findDailyContent
import com.englishquiz.app.data.season.PipelineStatus
import com.englishquiz.app.data.season.SeasonCoordinator
import com.englishquiz.app.data.repository.findLearningSessions


/**
 * Saved across activity recreation by [SessionStepSaver]. A rotation must not throw away a
 * generated passage: regenerating costs a paid provider call and hands the learner a different
 * text than the one they were reading.
 */
internal sealed interface SessionStep {
    data object Preparing : SessionStep
    /** No episode is ready yet (백로그 075): the pipeline is making one; the screen offers the library meanwhile. */
    data object Waiting : SessionStep
    data class Failed(val message: String) : SessionStep
    data class Reading(val content: LearningContent) : SessionStep
    /** The conversation spoken by the learner as one of its people (백로그 076), between the Reader and the quiz. */
    data class Roleplay(val content: LearningContent) : SessionStep
    data class Quiz(val content: LearningContent, val base: LearningSessionSummary) : SessionStep
    data class Finished(val summary: LearningSessionSummary) : SessionStep
}

/**
 * One learning session end to end (요구사항 21절): content generation → Reader → quiz → result →
 * home. The steps live here rather than in the app's destination switch so each screen keeps its
 * own contract and this file owns only the handover between them.
 *
 * [explainMeaning] and [generateContent] are the two AI calls the flow needs. They are passed in
 * as functions so a test can drive the whole session without a network, and so the app can leave
 * them out entirely when no endpoint is configured.
 */
@Composable
fun LearningSessionRoute(
    mode: ContentMode,
    repository: LearningRepository,
    difficulty: Int,
    generateContent: suspend (ContentGenerationRequest) -> LearningContent,
    explainMeaning: suspend (MeaningRequest) -> ContextualMeaning,
    onExit: () -> Unit,
    nowEpochMillis: () -> Long = System::currentTimeMillis,
    zoneId: ZoneId = ZoneId.systemDefault(),
    /**
     * A passage from the library (백로그 026): read as it is, with nothing generated or stored —
     * its annotated expressions are not enrolled for the quiz either (백로그 031), only what came
     * due and what the learner taps.
     */
    initialContent: LearningContent? = null,
    onSessionRecorded: suspend (sessionCompletedAtEpochMillis: Long) -> Unit = {},
    /** Points, level and badges on the result screen (백로그 038/039); null leaves them out. */
    gameRepository: GameProgressRepository? = null,
    /** The story as a season of episodes (백로그 063); null keeps the loose daily chain. */
    seasons: SeasonCoordinator? = null,
    /** The language of this session (백로그 066): its passages, expressions and voices. */
    language: Language = Language.DEFAULT,
    /** Online voices for speech (백로그 073). */
    allowNetworkVoices: Boolean = false,
    /** Opens the library from the waiting screen (백로그 075): a past episode to read again while the next is made. */
    onLibrary: (() -> Unit)? = null,
) {
    var episodeLabel by rememberSaveable { mutableStateOf<String?>(null) }
    var step by rememberSaveable(stateSaver = SessionStepSaver) {
        mutableStateOf<SessionStep>(SessionStep.Preparing)
    }
    var retry by rememberSaveable { mutableIntStateOf(0) }
    var savedCountAtStart by rememberSaveable { mutableIntStateOf(-1) }
    // The episode state the look-up that found nothing saw (백로그 075): the waiting screen tries again on any other value.
    var waitingSeen by rememberSaveable { mutableIntStateOf(-1) }

    LaunchedEffect(retry) {
        // A restored session already holds its passage. Without this the effect would re-run on
        // every recreation, spend another provider call and replace the text mid-read.
        if (step !is SessionStep.Preparing && step !is SessionStep.Failed && step !is SessionStep.Waiting) return@LaunchedEffect
        step = SessionStep.Preparing
        try {
            val now = nowEpochMillis()
            val learningDate = isoDate(now, zoneId)
            if (savedCountAtStart < 0) savedCountAtStart = repository.listSavedExpressions(language).size
            if (initialContent != null) {
                step = SessionStep.Reading(initialContent)
                return@LaunchedEffect
            }
            // Today's passage for this mode: stored on a second entry (백로그 021), the day's episode
            // inside the seasons (백로그 063/075), or a fresh generation stored for the day. Null means
            // no episode is ready: the pipeline has been asked, and the screen waits for it. The state
            // is read before the look-up, so an episode stored between the two is not missed.
            if (seasons != null) waitingSeen = seasons.observeEpisodeState(language).first()
            val content = todaysContent(repository, seasons, language, mode, difficulty, learningDate, now, generateContent)
            if (content == null) {
                step = SessionStep.Waiting
                return@LaunchedEffect
            }
            if (seasons != null) episodeLabel = seasons.labelFor(language, learningDate)
            step = SessionStep.Reading(content)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            step = SessionStep.Failed(generationErrorMessage(error))
        }
    }

    // The waiting screen tries again as soon as an episode arrives or gets its conversation, and
    // every little while regardless (백로그 075): a pipeline run that failed is asked for again that
    // way, since its backoff would otherwise leave the screen with nothing to wake it.
    if (step is SessionStep.Waiting && seasons != null) {
        LaunchedEffect(language, waitingSeen) {
            seasons.observeEpisodeState(language).filter { it != waitingSeen }.collect { retry++ }
        }
        LaunchedEffect(language) {
            while (true) {
                delay(WAITING_POLL_MS)
                seasons.prepare(language)
                retry++
            }
        }
    }

    when (val current = step) {
        SessionStep.Preparing -> SessionStatusScreen(
            title = contentTitle(mode),
            message = "오늘의 학습 내용을 만들고 있어요.",
            onBack = onExit,
        )

        SessionStep.Waiting -> {
            val status = seasons?.let { produceState<PipelineStatus?>(null, it, language) { it.pipelineStatus.collect { all -> value = all[language] } } }?.value
            SessionWaitingScreen(
                title = contentTitle(mode),
                onBack = onExit,
                onLibrary = onLibrary,
                statusLine = status?.let { waitingStatusLine(it, zoneId) },
                onRetry = seasons?.let { { it.retryNow(language); retry++ } },
            )
        }

        is SessionStep.Failed -> SessionStatusScreen(
            title = contentTitle(mode),
            message = current.message,
            onBack = onExit,
            onRetry = { retry++ },
        )

        is SessionStep.Reading -> ReaderRoute(
            content = current.content,
            repository = repository,
            onBack = onExit,
            subtitle = episodeLabel,
            onQuiz = { step = quizStep(current.content) },
            onRoleplay = { step = SessionStep.Roleplay(current.content) },
            nowEpochMillis = nowEpochMillis,
            explainMeaning = explainMeaning,
            allowNetworkVoices = allowNetworkVoices,
        )

        is SessionStep.Roleplay -> RoleplayRoute(
            content = current.content,
            repository = repository,
            onBack = { step = SessionStep.Reading(current.content) },
            onQuiz = { step = quizStep(current.content) },
            nowEpochMillis = nowEpochMillis,
            allowNetworkVoices = allowNetworkVoices,
        )

        is SessionStep.Quiz -> QuizRoute(
            repository = repository,
            todayContent = current.content,
            language = language,
            allowNetworkVoices = allowNetworkVoices,
            onFinished = { summary ->
                step = SessionStep.Finished(summary)
            },
            onBack = onExit,
            baseSummary = current.base,
            now = nowEpochMillis,
            enrolExpressions = initialContent == null,
            // The passage's own expressions are quizzed without a tap (백로그 031). Counting them
            // as saved at the start keeps "새로 저장한 표현" — and the difficulty signal it feeds
            // (백로그 013) — as the count of what the learner actually tapped.
            onExpressionsEnrolled = { savedCountAtStart += it },
            // The passage questions (백로그 042) are asked once per passage: not on a library
            // re-read, and not on a second entry into a day whose session for this mode is recorded.
            skipComprehension = {
                initialContent != null ||
                    repository.findLearningSessions(isoDate(nowEpochMillis(), zoneId))
                        .any { it.mode == mode.wireValue && it.language == language.wireValue }
            },
        )

        is SessionStep.Finished -> FinishedStep(
            repository = repository,
            summary = current.summary,
            sessionMode = if (initialContent == null) mode.wireValue else LIBRARY_SESSION_MODE,
            savedCountAtStart = savedCountAtStart,
            onDone = onExit,
            nowEpochMillis = nowEpochMillis,
            zoneId = zoneId,
            onSessionRecorded = onSessionRecorded,
            gameRepository = gameRepository,
            language = language,
        )
    }
}

/** How often the waiting screen looks again on its own (백로그 075); the pipeline's backoff is a minute. */
private const val WAITING_POLL_MS = 20_000L

/** "14:02:10 MakeEpisode(1, 3) · 오류: network_error": the pipeline's last step, so a failure is visible on the screen. */
private fun waitingStatusLine(status: PipelineStatus, zoneId: ZoneId): String {
    val time = Instant.ofEpochMilli(status.atEpochMillis).atZone(zoneId).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
    return "$time ${status.step}" + (status.error?.let { " · 오류: $it" } ?: "")
}

/** The quiz over [content], entered from the Reader or from the role-play (백로그 076) alike. */
private fun quizStep(content: LearningContent): SessionStep =
    SessionStep.Quiz(content, LearningSessionSummary(newlySavedExpressionCount = 0, learnedExpressionCount = 0))
