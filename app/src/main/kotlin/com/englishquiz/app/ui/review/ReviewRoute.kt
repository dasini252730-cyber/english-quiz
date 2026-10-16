package com.englishquiz.app.ui.review

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.englishquiz.app.data.local.SavedExpressionEntity
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.domain.quiz.QuizBuilder
import com.englishquiz.app.domain.review.ReviewFilter
import com.englishquiz.app.domain.review.WeakSpotPolicy
import com.englishquiz.app.ui.reader.ReaderSpeech
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext

sealed interface ReviewUiState {
    data object Loading : ReviewUiState
    data object Error : ReviewUiState

    /** [expressions] is the stored list; the screen applies [filter] to it (백로그 044). */
    data class Ready(
        val expressions: List<SavedExpressionEntity>,
        val filter: ReviewFilter = ReviewFilter.ALL,
        /** Wrong answers logged by the boss and practice modes (백로그 056), by expression id. */
        val loggedWrong: Map<Long, Int> = emptyMap(),
        /** Ids no question can be built for from the box (백로그 057), shown as "출제 불가" rather than hidden. */
        val unaskable: Set<Long> = emptySet(),
    ) : ReviewUiState {
        val shown: List<SavedExpressionEntity> get() = WeakSpotPolicy.apply(filter, expressions, loggedWrong)
    }
}

@Composable
fun ReviewRoute(
    repository: LearningRepository,
    onBack: () -> Unit,
    /** Opens a practice quiz over the shown rows (백로그 052). */
    onQuiz: (List<Long>) -> Unit = {},
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var retry by remember { mutableIntStateOf(0) }
    var filterName by rememberSaveable { mutableStateOf(ReviewFilter.ALL.name) }
    val filter = ReviewFilter.valueOf(filterName)
    val loaded by produceState<ReviewUiState>(ReviewUiState.Loading, repository, owner, retry) {
        value = ReviewUiState.Loading
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                combine(repository.observeSavedExpressions(), repository.observeWrongAnswerCounts()) { rows, wrong -> rows to wrong }
                    .collect { (rows, wrong) ->
                        // Askability tries a question per row against the whole box: off the main thread.
                        val unaskable = withContext(Dispatchers.Default) {
                            rows.filterNot { QuizBuilder.isAskable(it, rows) }.mapTo(HashSet()) { it.id }
                        }
                        value = ReviewUiState.Ready(rows, loggedWrong = wrong, unaskable = unaskable)
                    }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                value = ReviewUiState.Error
            }
        }
    }
    val speech = remember(context, owner) { ReaderSpeech(context) }
    val speechState by speech.state.collectAsState()
    DisposableEffect(speech, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) speech.stop()
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            speech.close()
        }
    }
    val leave = { speech.stop(); onBack() }
    BackHandler(onBack = leave)
    val state = (loaded as? ReviewUiState.Ready)?.copy(filter = filter) ?: loaded
    ReviewScreen(
        state = state,
        speechState = speechState,
        onPlay = { speech.play(listOf(it)) },
        onStop = speech::stop,
        onRetry = { retry++ },
        onBack = leave,
        onFilter = { filterName = it.name },
        onQuiz = { ids -> speech.stop(); onQuiz(ids) },
    )
}
