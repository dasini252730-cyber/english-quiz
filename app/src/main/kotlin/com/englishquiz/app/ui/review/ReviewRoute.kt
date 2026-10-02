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
import com.englishquiz.app.domain.review.ReviewFilter
import com.englishquiz.app.domain.review.WeakSpotPolicy
import com.englishquiz.app.ui.reader.ReaderSpeech
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect

sealed interface ReviewUiState {
    data object Loading : ReviewUiState
    data object Error : ReviewUiState

    /** [expressions] is the stored list; the screen applies [filter] to it (백로그 044). */
    data class Ready(
        val expressions: List<SavedExpressionEntity>,
        val filter: ReviewFilter = ReviewFilter.ALL,
    ) : ReviewUiState {
        val shown: List<SavedExpressionEntity> get() = WeakSpotPolicy.apply(filter, expressions)
    }
}

@Composable
fun ReviewRoute(repository: LearningRepository, onBack: () -> Unit) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var retry by remember { mutableIntStateOf(0) }
    var filterName by rememberSaveable { mutableStateOf(ReviewFilter.ALL.name) }
    val filter = ReviewFilter.valueOf(filterName)
    val loaded by produceState<ReviewUiState>(ReviewUiState.Loading, repository, owner, retry) {
        value = ReviewUiState.Loading
        owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                repository.observeSavedExpressions().collect { value = ReviewUiState.Ready(it) }
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
    )
}
