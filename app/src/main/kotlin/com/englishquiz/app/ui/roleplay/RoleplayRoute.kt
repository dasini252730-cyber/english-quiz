package com.englishquiz.app.ui.roleplay

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.data.repository.recordRoleplayTurn
import com.englishquiz.app.domain.roleplay.RoleplayGrader
import com.englishquiz.app.domain.roleplay.RoleplayScript
import com.englishquiz.app.domain.roleplay.RoleplayTurn
import com.englishquiz.app.domain.roleplay.TurnOutcome
import com.englishquiz.app.ui.reader.ReaderSpeech
import com.englishquiz.app.ui.reader.SpeechLine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The role-play (백로그 076): the learner takes one speaker of a conversation, the device plays the
 * others with the Reader's voices, and each of the learner's lines is heard through the
 * recogniser and graded on its words. Only pass/fail per line is written, as a `roleplay` quiz
 * answer; the score, the streak and the review schedule are untouched.
 *
 * [micAccess] is injectable so a test can deny the microphone and see the typed fallback.
 */
@Composable
internal fun RoleplayRoute(
    content: LearningContent,
    repository: LearningRepository,
    onBack: () -> Unit,
    onQuiz: () -> Unit,
    nowEpochMillis: () -> Long = System::currentTimeMillis,
    allowNetworkVoices: Boolean = false,
    micAccess: MicAccess = rememberMicAccess(),
) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val speech = remember(content, context, owner, allowNetworkVoices) {
        ReaderSpeech(context, content.language, allowNetworkVoices).apply {
            cast = content.segments.map { it.speaker }
            genders = content.speakers
        }
    }
    // Same keys as the speech engine, so the two are always disposed and recreated together.
    val recognizer = remember(content, context, owner, allowNetworkVoices) { RoleplayRecognizer(context) }
    val speechState by speech.state.collectAsState()
    val recognizerState by recognizer.state.collectAsState()
    DisposableEffect(speech, recognizer, owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                speech.stop()
                recognizer.cancel()
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            speech.close()
            recognizer.destroy()
        }
    }
    val quiet = { speech.stop(); recognizer.cancel() }
    val leave = { quiet(); onBack() }
    BackHandler(onBack = leave)
    // Above the early returns: the write of the last line must outlive the switch to the result screen.
    val scope = rememberCoroutineScope()

    val progress = rememberSaveable(content, saver = RoleplayProgress.Saver) { RoleplayProgress() }
    val speakers = remember(content) { RoleplayScript.speakers(content) }
    val speaker = progress.speaker
    if (speaker == null) {
        SpeakerPickerScreen(speakers = speakers, onPick = progress::choose, onBack = leave)
        return
    }
    val turns = remember(content, speaker) { RoleplayScript.turns(content, speaker) }
    val turn = turns.getOrNull(progress.turnIndex)
    if (turn == null) {
        FinishedScreen(
            passed = progress.passedCount,
            total = turns.count { it.isLearner },
            onRestart = progress::restart,
            onQuiz = { quiet(); onQuiz() },
            onBack = leave,
        )
        return
    }

    var saveError by remember { mutableStateOf<String?>(null) }

    // The device's lines are read as soon as they come up and the engine can speak.
    LaunchedEffect(turn.index, speechState.ready) {
        if (!turn.isLearner && speechState.ready) speech.play(listOf(SpeechLine(turn.text, turn.speaker)))
    }
    // The microphone is asked for once per play, at the learner's first line, so the choice screen stays quiet.
    var asked by remember(speaker) { mutableStateOf(false) }
    LaunchedEffect(turn.index) {
        if (!turn.isLearner || progress.typedInput || asked) return@LaunchedEffect
        asked = true
        when {
            !micAccess.recognitionAvailable -> progress.typedInput = true
            !micAccess.hasPermission() -> micAccess.requestPermission { granted -> if (!granted) progress.typedInput = true }
        }
    }

    fun judge(heard: String) {
        val grade = RoleplayGrader.grade(turn.text, heard, content.language)
        val outcome = progress.attempted(heard, grade.passed)
        if (outcome == TurnOutcome.RETRY) return
        // Read now: "다음" may clear the line before the coroutine runs. The write itself is not
        // cancelled by leaving the screen, so a settled turn is never lost to a quick "퀴즈로".
        val revealed = progress.revealed
        val answeredAt = nowEpochMillis()
        scope.launch {
            try {
                withContext(NonCancellable) {
                    repository.recordRoleplayTurn(
                        language = content.language,
                        expressionText = turn.expression?.text,
                        passed = outcome == TurnOutcome.PASSED,
                        revealed = revealed,
                        answeredAtEpochMillis = answeredAt,
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                saveError = "이번 대사의 기록을 저장하지 못했어요. 역할극은 계속할 수 있어요."
            }
        }
    }

    fun listen() {
        speech.stop()
        val start = { recognizer.listen(content.language.locale, ::judge) { progress.typedInput = true } }
        if (micAccess.hasPermission()) start() else micAccess.requestPermission { granted -> if (granted) start() else progress.typedInput = true }
    }

    RoleplayTurnScreen(
        turn = turn,
        turnCount = turns.size,
        language = content.language,
        progress = progress,
        speechState = speechState,
        recognizerState = recognizerState,
        saveError = saveError,
        onPlay = { speech.play(listOf(SpeechLine(turn.text, turn.speaker))) },
        onStop = speech::stop,
        onListen = ::listen,
        onStopListening = recognizer::cancel,
        onTyped = ::judge,
        onUseTyping = { recognizer.cancel(); progress.typedInput = true },
        onNext = { quiet(); saveError = null; progress.next() },
        onBack = leave,
    )
}

/**
 * The turn's cue, for the screen and its test: the Korean meanings of the line's highlighted
 * expressions — never their original text, which is what the learner has to produce, and never a
 * translation the passage does not carry.
 */
internal fun RoleplayTurn.meanings(): List<String> = expressions.map { it.meaning }
