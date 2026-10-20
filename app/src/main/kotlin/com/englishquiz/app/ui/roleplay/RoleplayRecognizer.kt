package com.englishquiz.app.ui.roleplay

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * How the role-play reaches the microphone. Split from the recogniser so a test can hand the
 * route one that denies, and prove the typed fallback appears, without a permission dialog.
 */
internal interface MicAccess {
    /** False when the device has no speech recogniser at all; the route then types from the start. */
    val recognitionAvailable: Boolean
    fun hasPermission(): Boolean
    /** Asks the system; the answer arrives on the main thread, once. */
    fun requestPermission(onResult: (Boolean) -> Unit)
}

/** The real [MicAccess]: `RECORD_AUDIO` through the activity result contract. */
@Composable
internal fun rememberMicAccess(): MicAccess {
    val context = LocalContext.current
    val pending = remember { mutableStateOf<((Boolean) -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pending.value?.invoke(granted)
        pending.value = null
    }
    return remember(context, launcher) {
        object : MicAccess {
            override val recognitionAvailable: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)
            override fun hasPermission(): Boolean =
                context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            override fun requestPermission(onResult: (Boolean) -> Unit) {
                pending.value = onResult
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
}

internal data class RecognizerState(val listening: Boolean = false, val error: String? = null)

/**
 * One utterance at a time through Android's [SpeechRecognizer] (백로그 076). A fresh recogniser is
 * created for every [listen] and the previous one destroyed: some engines refuse a second
 * `startListening` after an error, and a new instance costs nothing noticeable. Main thread only,
 * as the platform requires.
 */
internal class RoleplayRecognizer(private val context: Context) {
    private val mutableState = MutableStateFlow(RecognizerState())
    val state = mutableState.asStateFlow()
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    private var destroyed = false

    /** Hears one utterance in [locale] and hands the best transcript to [onTranscript]. [onPermissionDenied] fires when the system refuses the microphone after all. */
    fun listen(locale: Locale, onTranscript: (String) -> Unit, onPermissionDenied: () -> Unit) {
        // A permission answer that arrives after the screen is gone must not open the microphone.
        if (destroyed) return
        cancel()
        val id = ++generation
        val engine = SpeechRecognizer.createSpeechRecognizer(context.applicationContext)
        recognizer = engine
        engine.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: Bundle?) = Unit
            override fun onEvent(eventType: Int, params: Bundle?) = Unit

            override fun onResults(results: Bundle?) {
                if (id != generation) return
                val heard = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull { it.isNotBlank() }
                mutableState.value = RecognizerState()
                if (heard == null) mutableState.value = RecognizerState(error = NOT_HEARD) else onTranscript(heard)
            }

            override fun onError(error: Int) {
                if (id != generation) return
                Log.w(TAG, "recognition error $error")
                when (error) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        mutableState.value = RecognizerState()
                        onPermissionDenied()
                    }
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                        mutableState.value = RecognizerState(error = NOT_HEARD)
                    else -> mutableState.value = RecognizerState(error = FAILED)
                }
            }
        })
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, locale.toLanguageTag())
            .putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            .putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        mutableState.value = RecognizerState(listening = true)
        engine.startListening(intent)
    }

    /** Stops listening without a result and clears any message; a late callback from this attempt is ignored. */
    fun cancel() {
        generation++
        recognizer?.let { runCatching { it.cancel() }; runCatching { it.destroy() } }
        recognizer = null
        mutableState.value = RecognizerState()
    }

    fun destroy() {
        destroyed = true
        cancel()
    }

    internal companion object {
        const val TAG = "RoleplayRecognizer"
        const val NOT_HEARD = "잘 들리지 않았어요. 다시 말해 주세요."
        const val FAILED = "음성 인식에 실패했어요. 다시 시도하거나 직접 입력해 주세요."
    }
}
