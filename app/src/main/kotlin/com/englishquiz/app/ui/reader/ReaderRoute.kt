package com.englishquiz.app.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.englishquiz.app.data.ai.ContextualMeaning
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.MeaningRequest
import com.englishquiz.app.data.repository.LearningRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun ReaderRoute(
    content: LearningContent,
    repository: LearningRepository,
    onBack: () -> Unit,
    onQuiz: (() -> Unit)? = null,
    nowEpochMillis: () -> Long = System::currentTimeMillis,
    explainMeaning: suspend (MeaningRequest) -> ContextualMeaning,
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val speech = remember(content, context, owner) {
        ReaderSpeech(context).apply {
            cast = content.segments.map { it.speaker }
            genders = content.speakers
        }
    }
    val state by speech.state.collectAsState()
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

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var meaningState by remember(content) { mutableStateOf<MeaningUiState?>(null) }
    var pendingToken by remember(content) { mutableStateOf<ReaderToken?>(null) }
    var pendingContext by remember(content) { mutableStateOf("") }
    var job by remember(content) { mutableStateOf<Job?>(null) }

    fun lookup(token: ReaderToken) {
        pendingToken = token
        val contextSentence = content.segments.getOrNull(token.segmentIndex)?.text.orEmpty()
        pendingContext = contextSentence
        meaningState = MeaningUiState.Loading(token.display)
        job?.cancel()
        job = scope.launch {
            meaningState = lookupMeaning(token, contextSentence, content, repository, nowEpochMillis, explainMeaning)
        }
    }

    fun retrySave() {
        val current = meaningState as? MeaningUiState.Success ?: return
        job?.cancel()
        job = scope.launch {
            meaningState = retrySaveMeaning(current, pendingContext, repository, nowEpochMillis)
        }
    }

    ReaderScreen(
        content = content,
        speechState = state,
        onPlay = { lines -> speech.play(lines) },
        onStop = speech::stop,
        onBack = leave,
        onQuiz = onQuiz?.let { next -> { speech.stop(); next() } },
        meaningState = meaningState,
        onTokenTap = { token -> if (token.isTappable) lookup(token) },
        onDismissMeaning = { job?.cancel(); meaningState = null },
        onRetryMeaning = { pendingToken?.let { lookup(it) } },
        onRetrySave = ::retrySave,
        listState = listState,
    )
}
