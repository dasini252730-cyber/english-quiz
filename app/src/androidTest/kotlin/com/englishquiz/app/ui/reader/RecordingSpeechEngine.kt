package com.englishquiz.app.ui.reader

import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.test.platform.app.InstrumentationRegistry
import java.util.Locale

/**
 * An engine that only records what it was asked, shared by the ReaderSpeech tests. What they
 * prove — a new request silences the previous one, each speaker keeps its own voice — is
 * invisible through a real engine, which only reports that it was asked to speak.
 */
internal class RecordingEngine(
    override val voices: List<VoiceOption> = emptyList(),
) : SpeechEngine {
    val calls = mutableListOf<String>()
    val spoken = mutableListOf<Triple<String, Int, String>>()

    /** The voice name and pitch in force when each utterance was queued. */
    val voicedAs = mutableListOf<Pair<String?, Float>>()
    var listener: UtteranceProgressListener? = null
    var languageResult = TextToSpeech.LANG_AVAILABLE
    var speakResult = TextToSpeech.SUCCESS

    /** A voice name this engine pretends not to have. */
    var refuse: String? = null
    private var currentVoice: String? = null
    private var currentPitch = 1f

    override val maxInputLength = 4_000

    override fun setLanguage(locale: Locale): Int = languageResult

    override fun setOnUtteranceProgressListener(listener: UtteranceProgressListener) {
        this.listener = listener
    }

    override fun setVoice(name: String?): Int {
        if (name != null && name == refuse) return TextToSpeech.ERROR
        currentVoice = name
        return TextToSpeech.SUCCESS
    }

    override fun setPitch(pitch: Float): Int {
        currentPitch = pitch
        return TextToSpeech.SUCCESS
    }

    override fun speak(text: String, queueMode: Int, utteranceId: String): Int {
        calls += "speak:$text"
        spoken += Triple(text, queueMode, utteranceId)
        voicedAs += currentVoice to currentPitch
        return speakResult
    }

    override fun stop() {
        calls += "stop"
    }

    override fun shutdown() {
        calls += "shutdown"
    }
}

/** Runs [body] on the main thread and rethrows what it threw, so an assertion inside fails the test. */
internal fun onMain(body: () -> Unit) {
    var failure: Throwable? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
        try {
            body()
        } catch (error: Throwable) {
            failure = error
        }
    }
    failure?.let { throw it }
}

/**
 * Builds an initialised controller. The engine's init callback is delivered through a
 * `Handler.post`, so an extra empty turn on the main looper is needed before the controller has
 * actually read the result.
 */
internal fun ready(engine: RecordingEngine, status: Int = TextToSpeech.SUCCESS): ReaderSpeech {
    lateinit var speech: ReaderSpeech
    lateinit var init: (Int) -> Unit
    onMain { speech = ReaderSpeech { onInit -> init = onInit; engine } }
    onMain { init(status) }
    onMain { }
    return speech
}
