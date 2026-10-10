package com.englishquiz.app.ui.session

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.englishquiz.app.data.ai.ContentGenerationRequest
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContextualMeaning
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.local.LIBRARY_SESSION_MODE
import com.englishquiz.app.data.preferences.GameProgressRepository
import com.englishquiz.app.data.ai.MeaningRequest
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.domain.session.LearningSessionSummary
import com.englishquiz.app.ui.quiz.QuizRoute
import com.englishquiz.app.ui.reader.ReaderRoute
import com.englishquiz.app.ui.result.ResultRoute
import kotlinx.coroutines.CancellationException
import java.time.ZoneId
import com.englishquiz.app.data.repository.findDailyContent
import com.englishquiz.app.data.repository.findLearningSessions
import com.englishquiz.app.data.repository.findPreviousSummary

private const val MAX_REVIEW_EXPRESSIONS = 12

/**
 * Saved across activity recreation by [SessionStepSaver]. A rotation must not throw away a
 * generated passage: regenerating costs a paid provider call and hands the learner a different
 * text than the one they were reading.
 */
internal sealed interface SessionStep {
    data object Preparing : SessionStep
    data class Failed(val message: String) : SessionStep
    data class Reading(val content: LearningContent) : SessionStep
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
) {
    var step by rememberSaveable(stateSaver = SessionStepSaver) {
        mutableStateOf<SessionStep>(SessionStep.Preparing)
    }
    var retry by rememberSaveable { mutableIntStateOf(0) }
    var savedCountAtStart by rememberSaveable { mutableIntStateOf(-1) }

    LaunchedEffect(retry) {
        // A restored session already holds its passage. Without this the effect would re-run on
        // every recreation, spend another provider call and replace the text mid-read.
        if (step !is SessionStep.Preparing && step !is SessionStep.Failed) return@LaunchedEffect
        step = SessionStep.Preparing
        try {
            val now = nowEpochMillis()
            val learningDate = isoDate(now, zoneId)
            if (savedCountAtStart < 0) savedCountAtStart = repository.listSavedExpressions().size
            if (initialContent != null) {
                step = SessionStep.Reading(initialContent)
                return@LaunchedEffect
            }
            // Today's passage for this mode already exists: read it back rather than pay for, and
            // wait on, another one. 요구사항 9.1 asks for a new situation each day, not each tap
            // (백로그 021). The same date rule as the result screen keys it, so "today" here is
            // the "today" the streak counts.
            val stored = repository.findDailyContent(learningDate, mode)
            if (stored != null) {
                step = SessionStep.Reading(stored)
                return@LaunchedEffect
            }
            val due = repository.findDueExpressions(now)
            // 백로그 054: a story goes on from the last one; a conversation practises today's story.
            val other = if (mode == ContentMode.STORY) ContentMode.CONVERSATION else ContentMode.STORY
            val content = generateContent(
                ContentGenerationRequest(
                    mode = mode,
                    difficulty = difficulty,
                    reviewExpressions = due.take(MAX_REVIEW_EXPRESSIONS).map { it.displayExpression },
                    previousStory = repository.findPreviousSummary(ContentMode.STORY, learningDate),
                    companion = repository.findDailyContent(learningDate, other)?.summary(),
                ),
            )
            keepForToday(repository, learningDate, content, now)
            step = SessionStep.Reading(content)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            step = SessionStep.Failed(generationErrorMessage(error))
        }
    }

    when (val current = step) {
        SessionStep.Preparing -> SessionStatusScreen(
            title = contentTitle(mode),
            message = "오늘의 학습 내용을 만들고 있어요.",
            onBack = onExit,
        )

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
            onQuiz = {
                step = SessionStep.Quiz(
                    content = current.content,
                    base = LearningSessionSummary(
                        newlySavedExpressionCount = 0,
                        learnedExpressionCount = 0,
                    ),
                )
            },
            nowEpochMillis = nowEpochMillis,
            explainMeaning = explainMeaning,
        )

        is SessionStep.Quiz -> QuizRoute(
            repository = repository,
            todayContent = current.content,
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
                    repository.findLearningSessions(isoDate(nowEpochMillis(), zoneId)).any { it.mode == mode.wireValue }
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
        )
    }
}

/**
 * Fills in the two counts only the session as a whole can know — how many expressions were saved
 * while reading, and how many were reviewed in the quiz — before handing over to the result
 * screen, which records the session.
 */
@Composable
private fun FinishedStep(
    repository: LearningRepository,
    summary: LearningSessionSummary,
    sessionMode: String,
    savedCountAtStart: Int,
    onDone: () -> Unit,
    nowEpochMillis: () -> Long,
    zoneId: ZoneId,
    onSessionRecorded: suspend (sessionCompletedAtEpochMillis: Long) -> Unit,
    gameRepository: GameProgressRepository?,
) {
    var resolved by remember { mutableStateOf<LearningSessionSummary?>(null) }
    LaunchedEffect(summary) {
        val newlySaved = try {
            (repository.listSavedExpressions().size - savedCountAtStart).coerceAtLeast(0)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            0
        }
        // The quiz reports what was met, cards included (백로그 045); only the saved count is filled here.
        resolved = summary.copy(newlySavedExpressionCount = newlySaved)
    }

    val ready = resolved
    if (ready == null) {
        SessionStatusScreen(title = "학습 결과", message = "결과를 정리하고 있어요.", onBack = onDone)
    } else {
        // The same zone rule keys the stored passage and dates the recorded session. (They are
        // taken at different moments, so a session that crosses midnight still records on the
        // day it finished, which is the day the streak should count.)
        ResultRoute(
            repository = repository,
            summary = ready,
            sessionMode = sessionMode,
            onDone = onDone,
            nowEpochMillis = nowEpochMillis,
            zoneId = zoneId,
            onSessionRecorded = onSessionRecorded,
            gameRepository = gameRepository,
        )
    }
}

private fun contentTitle(mode: ContentMode): String = when (mode) {
    ContentMode.CONVERSATION -> "Conversation"
    ContentMode.STORY -> "Story"
}
