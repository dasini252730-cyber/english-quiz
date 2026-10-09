package com.englishquiz.app.ui.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.local.SavedExpressionEntity
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.domain.game.GrowthStage
import com.englishquiz.app.domain.game.QuizScore
import com.englishquiz.app.domain.game.ScorePolicy
import com.englishquiz.app.domain.quiz.QuizBuilder
import com.englishquiz.app.domain.quiz.QuizOption
import com.englishquiz.app.domain.quiz.QuizQuestion
import com.englishquiz.app.domain.quiz.QuizQuestionType
import com.englishquiz.app.domain.quiz.typedAnswerHint
import com.englishquiz.app.domain.quiz.typedAnswerMatches
import com.englishquiz.app.domain.session.LearningSessionSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val UNKNOWN_TOTAL = -1

/**
 * Drives today's quiz (백로그 010). [todayContent] is what was just read, or null outside a
 * reading session; the merged summary goes to [onFinished], the empty quiz included. The list is
 * built once per entry; after recreation it is rebuilt from the same saved seed, and answers
 * already recorded have dropped out, so the learner resumes. Passage questions (백로그 042) are
 * counted and skipped instead; a recreation in the retry round (백로그 047) ends the quiz.
 * [questionSource], [maxQuestions], [pointsMultiplier] and [askCards] are the weekend boss's
 * knobs (백로그 041/045); [scoring] false is the review box's practice quiz (백로그 052).
 */
@Composable
fun QuizRoute(
    repository: LearningRepository,
    todayContent: LearningContent?,
    onFinished: (LearningSessionSummary) -> Unit,
    onBack: () -> Unit = {},
    baseSummary: LearningSessionSummary = LearningSessionSummary(),
    now: () -> Long = { System.currentTimeMillis() },
    seed: () -> Long = { System.nanoTime() },
    enrolExpressions: Boolean = true,
    onExpressionsEnrolled: (Int) -> Unit = {},
    questionSource: suspend (LearningRepository, Long) -> List<SavedExpressionEntity> =
        { repo, at -> repo.findDueExpressions(at) },
    maxQuestions: Int = QuizBuilder.DEFAULT_MAX_QUESTIONS,
    pointsMultiplier: Int = 1,
    skipComprehension: suspend () -> Boolean = { false },
    /** False for the boss (백로그 041): first meetings are asked, not shown as cards. */
    askCards: Boolean = true,
    /**
     * The review box's practice (백로그 052): no points, only a wrong answer is recorded (a right
     * one must not advance a schedule that is not due), and a recreation ends it rather than
     * asking the same questions again, since nothing dropped out of the list. A never-asked
     * expression still gets its card instead of a question it cannot know, but the card is only
     * read: nothing is written, so the daily quiz still meets it first (백로그 048 budget intact).
     */
    practice: Boolean = false,
) {
    val scope = rememberCoroutineScope()
    var retry by rememberSaveable { mutableIntStateOf(0) }
    val nowEpochMillis = rememberSaveable { now() }
    val shuffleSeed = rememberSaveable { seed() }
    var correctCount by rememberSaveable { mutableIntStateOf(0) }
    var masteredCount by rememberSaveable { mutableIntStateOf(0) }
    var totalQuestions by rememberSaveable { mutableIntStateOf(UNKNOWN_TOTAL) }
    // Cards (백로그 045) and the retry round (백로그 047) are shown but not counted as questions.
    var uncounted by rememberSaveable { mutableIntStateOf(0) }
    var retries by rememberSaveable { mutableIntStateOf(0) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var retryRound by rememberSaveable { mutableStateOf(false) }
    var score by rememberSaveable(stateSaver = QuizScoreSaver) { mutableStateOf(QuizScore()) }
    var comprehensionDone by rememberSaveable { mutableIntStateOf(0) }

    var loadState by remember { mutableStateOf<QuizLoadState>(QuizLoadState.Loading) }
    var index by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableStateOf<QuizOption?>(null) }
    var recordFailed by remember { mutableStateOf(false) }
    var growth by remember { mutableStateOf<GrowthChange?>(null) }
    var hint by remember { mutableStateOf<String?>(null) }
    val wrong = remember { mutableListOf<QuizQuestion>() }
    val speech = rememberQuizSpeech()
    val speechState by speech.state.collectAsState()

    fun finish() {
        if (finished) return
        finished = true
        onFinished(baseSummary.withQuizResult(totalQuestions, retries, uncounted, correctCount, masteredCount, score))
    }

    LaunchedEffect(repository, retry) {
        loadState = QuizLoadState.Loading
        val resumed = totalQuestions != UNKNOWN_TOTAL
        try {
            val questions = loadQuizQuestions(
                repository, todayContent, nowEpochMillis, shuffleSeed, enrolExpressions, onExpressionsEnrolled,
                questionSource, maxQuestions, skipComprehension, comprehensionDone, askCards,
            )
            if (totalQuestions == UNKNOWN_TOTAL) {
                totalQuestions = questions.size
                uncounted = questions.count { it.type == QuizQuestionType.LEARN_CARD }
            }
            index = 0
            selectedOption = null
            recordFailed = false
            loadState = QuizLoadState.Loaded(questions)
            if (retryRound || (practice && resumed)) finish()
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            loadState = QuizLoadState.Error
        }
    }

    val questions = (loadState as? QuizLoadState.Loaded)?.questions

    fun record(question: QuizQuestion, option: QuizOption) {
        // A passage question belongs to no expression (백로그 042) and a retry was recorded the
        // first time (백로그 047): nothing to write for either.
        if (question.type == QuizQuestionType.COMPREHENSION || question.isRetry || (practice && option.isCorrect)) return
        scope.launch {
            // A slow write may land after the learner moved on: its growth line and failure
            // notice apply only while that question is still the current one.
            val current = { questions?.getOrNull(index) == question }
            try {
                // The last answer's write must outlive the screen: "결과 보기" leaves at once.
                val updated = withContext(NonCancellable) {
                    repository.recordAnswer(question.expression, option.isCorrect, nowEpochMillis)
                }
                if (option.isCorrect && updated.isMastered) masteredCount += 1
                if (current()) {
                    growth = GrowthChange(
                        expression = question.expression,
                        before = question.growthBefore,
                        after = GrowthStage.of(updated.consecutiveCorrectCount, updated.isMastered),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (current()) {
                    growth = null
                    recordFailed = true
                }
            }
        }
    }

    fun next() {
        val list = questions ?: return
        val atLast = index + 1 >= list.size
        if (atLast && (retryRound || wrong.isEmpty())) {
            finish()
            return
        }
        if (atLast) {
            // Once through, the missed ones come back for one more go (백로그 047): no points,
            // nothing recorded, just the chance to get it right while it is fresh.
            val again = wrong.map { it.copy(isRetry = true) }
            wrong.clear()
            retryRound = true
            uncounted += again.size
            retries += again.size
            totalQuestions += again.size
            loadState = QuizLoadState.Loaded(list + again)
        }
        index += 1
        selectedOption = null
        recordFailed = false
        growth = null
        hint = null
    }

    fun answer(option: QuizOption) {
        val question = questions?.getOrNull(index)
        if (question == null || selectedOption != null) return
        // Committed before the write, so a fast second tap cannot answer the same question twice.
        selectedOption = option
        recordFailed = false
        growth = null
        if (question.isRetry) {
            score = score.copy(lastEarned = 0)
            return
        }
        if (option.isCorrect) correctCount += 1 else wrong += question
        if (!practice) score = ScorePolicy.answer(score, option.isCorrect, pointsMultiplier)
        // Counted at answer time, so a recreation before "next" cannot re-ask it for points.
        if (question.type == QuizQuestionType.COMPREHENSION) comprehensionDone += 1
        record(question, option)
    }

    /**
     * The card's two answers (백로그 045) move straight on. Neither scores and neither is judged:
     * both bring the expression back tomorrow, where the ladder starts at 씨앗. ("알아요" is the
     * learner's word, not the quiz's — the two-option question tomorrow is the quick check.)
     */
    fun card() {
        val question = questions?.getOrNull(index) ?: return
        if (!practice) {
            scope.launch {
                // The tap that answers the last card also leaves the screen; the write must land.
                withContext(NonCancellable) { runCatching { repository.markSeen(question.expression, nowEpochMillis) } }
            }
        }
        next()
    }

    BackHandler { speech.stop(); onBack() }

    QuizScreen(
        state = quizUiState(
            loadState, questions, index, totalQuestions, selectedOption, recordFailed, score, growth, hint, !practice,
            retryPending = wrong.isNotEmpty() && !retryRound,
        ),
        onSelectOption = ::answer,
        onSubmitTyped = { typed ->
            val question = questions?.getOrNull(index)
            if (question != null) answer(QuizOption(typed.trim(), typedAnswerMatches(typed, question.expression)))
        },
        onHint = { questions?.getOrNull(index)?.let { hint = typedAnswerHint(it.expression) } },
        onNext = { if (selectedOption != null) next() },
        onRetryRecord = {
            val question = questions?.getOrNull(index)
            val option = selectedOption
            if (question != null && option != null && recordFailed) {
                recordFailed = false
                record(question, option)
            }
        },
        onEmptyContinue = ::finish,
        onRetry = { retry++ },
        onBack = { speech.stop(); onBack() },
        onKnown = ::card,
        onUnknown = ::card,
        speechReady = speechState.ready,
        onPlay = { speech.play(listOf(it)) },
    )
}
