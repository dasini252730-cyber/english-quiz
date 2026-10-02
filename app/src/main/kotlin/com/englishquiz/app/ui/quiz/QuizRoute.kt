package com.englishquiz.app.ui.quiz

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.englishquiz.app.domain.session.LearningSessionSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val UNKNOWN_TOTAL = -1

/**
 * Drives today's quiz (백로그 010). [todayContent] is the content the learner just read, or null
 * when entering the quiz without a fresh reading session. [baseSummary] carries counts the caller
 * already knows (newlySavedExpressionCount and learnedExpressionCount from the reading step);
 * this route fills in the quiz counts and hands the merged summary to [onFinished], including the
 * empty-quiz case.
 *
 * The question list is built once per entry and is not rebuilt when the app returns to the
 * foreground: recording an answer moves that expression's next review into the future, so a
 * reload would hand back a different, shorter quiz and restart the learner at question one. After
 * the activity is recreated the list is rebuilt from the same saved seed; answers already recorded
 * have dropped out of it, so the learner resumes instead of starting over.
 *
 * [now] and [seed] are providers rather than plain values so a test can inject a fixed instant and
 * shuffle seed; each is read once and then kept across recreation.
 *
 * With [enrolExpressions] the passage's annotated expressions are all quizzed, tapped or not
 * (백로그 031): they are saved before the due list is read, and [onExpressionsEnrolled] hears how
 * many were new. A library passage passes false (백로그 026: read again, nothing stored).
 *
 * [questionSource] names the expressions to ask; the default is what is due now. The weekend boss
 * (백로그 041) passes its own larger set, a higher [maxQuestions] and a [pointsMultiplier] above 1.
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
) {
    val scope = rememberCoroutineScope()
    var retry by rememberSaveable { mutableIntStateOf(0) }
    val nowEpochMillis = rememberSaveable { now() }
    val shuffleSeed = rememberSaveable { seed() }
    var correctCount by rememberSaveable { mutableIntStateOf(0) }
    var masteredCount by rememberSaveable { mutableIntStateOf(0) }
    var totalQuestions by rememberSaveable { mutableIntStateOf(UNKNOWN_TOTAL) }
    var finished by rememberSaveable { mutableStateOf(false) }
    var score by rememberSaveable(stateSaver = QuizScoreSaver) { mutableStateOf(QuizScore()) }

    var loadState by remember { mutableStateOf<QuizLoadState>(QuizLoadState.Loading) }
    var index by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableStateOf<QuizOption?>(null) }
    var recordFailed by remember { mutableStateOf(false) }
    var growth by remember { mutableStateOf<GrowthChange?>(null) }

    LaunchedEffect(repository, retry) {
        loadState = QuizLoadState.Loading
        try {
            // Re-runs (retry, recreation) find the rows already there and report zero.
            if (enrolExpressions && todayContent != null) {
                onExpressionsEnrolled(enrolContentExpressions(todayContent, repository, nowEpochMillis))
            }
            val targets = questionSource(repository, nowEpochMillis)
            val pool = repository.listSavedExpressions()
            val questions = QuizBuilder.build(todayContent, targets, pool, shuffleSeed, maxQuestions).questions
            if (totalQuestions == UNKNOWN_TOTAL) totalQuestions = questions.size
            index = 0
            selectedOption = null
            recordFailed = false
            loadState = QuizLoadState.Loaded(questions)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            loadState = QuizLoadState.Error
        }
    }

    val questions = (loadState as? QuizLoadState.Loaded)?.questions

    fun record(question: QuizQuestion, option: QuizOption) {
        scope.launch {
            // Writes are not ordered: a slow one may land after the learner has moved on. Its
            // growth line and its failure notice belong to the card that is still showing, so
            // both are only applied while that question is the current one.
            val current = { questions?.getOrNull(index) == question }
            try {
                val updated =
                    repository.recordAnswer(question.expression, option.isCorrect, nowEpochMillis)
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

    fun finish() {
        if (finished) return
        finished = true
        onFinished(
            baseSummary.copy(
                quizCorrectCount = correctCount,
                quizQuestionCount = totalQuestions.coerceAtLeast(0),
                masteredExpressionCount = masteredCount,
                score = score.points,
                maxCombo = score.maxCombo,
            ),
        )
    }

    BackHandler(onBack = onBack)

    QuizScreen(
        state = quizUiState(loadState, questions, index, totalQuestions, selectedOption, recordFailed, score, growth),
        onSelectOption = { option ->
            val question = questions?.getOrNull(index)
            if (question != null && selectedOption == null) {
                // The selection is committed before the write, so a fast second tap cannot record
                // a second answer for the same question: QuizScreen disables the options as soon
                // as selectedOption is set.
                selectedOption = option
                recordFailed = false
                growth = null
                if (option.isCorrect) correctCount += 1
                score = ScorePolicy.answer(score, option.isCorrect, pointsMultiplier)
                record(question, option)
            }
        },
        onNext = {
            if (selectedOption != null) {
                if (questions != null && index + 1 < questions.size) {
                    index += 1
                    selectedOption = null
                    recordFailed = false
                    growth = null
                } else {
                    finish()
                }
            }
        },
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
        onBack = onBack,
    )
}
