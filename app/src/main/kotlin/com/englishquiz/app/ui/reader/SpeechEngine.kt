package com.englishquiz.app.ui.reader

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.Locale

/**
 * The slice of [TextToSpeech] the reader actually uses.
 *
 * It exists so the playback rules that 요구사항 13절 cares about — a new request always silences the
 * previous one — can be proved without a speech engine. A test that needs a real engine can only
 * assert that no exception was thrown; it cannot observe whether two voices overlapped.
 */
internal interface SpeechEngine {
    val maxInputLength: Int

    /** The English voices installed on the device, for [SpeakerVoices]. Empty when the engine has none. */
    val voices: List<VoiceOption>

    fun setLanguage(locale: Locale): Int
    fun setOnUtteranceProgressListener(listener: UtteranceProgressListener)

    /**
     * Selects the voice named by [SpeakerVoices], or for `null` the default voice of the language
     * given to [setLanguage]. Negative results are failures, as for [TextToSpeech.setLanguage].
     */
    fun setVoice(name: String?): Int
    fun setPitch(pitch: Float): Int

    /** Mirrors [TextToSpeech.speak]; [queueMode] is [TextToSpeech.QUEUE_FLUSH] or `QUEUE_ADD`. */
    fun speak(text: String, queueMode: Int, utteranceId: String): Int

    fun stop()
    fun shutdown()
}

/** The real engine. Created eagerly, like [TextToSpeech] itself, and reports readiness to [onInit]. */
internal class AndroidSpeechEngine(context: Context, onInit: (Int) -> Unit) : SpeechEngine {
    private val engine = TextToSpeech(context.applicationContext) { onInit(it) }

    /** The language last asked for. "Default voice" means this language's own default, never the device's. */
    private var language: Locale = Locale.US
    private var cachedVoices: Map<String, Voice> = emptyMap()

    /**
     * Read on demand. An engine can throw while listing, or list nothing until it has finished
     * starting, so an empty result is not kept: the next call asks again.
     */
    private fun englishVoices(): Map<String, Voice> {
        if (cachedVoices.isNotEmpty()) return cachedVoices
        val all = runCatching { engine.voices.orEmpty() }.getOrDefault(emptySet())
        val english = all.filter { it.locale.language.equals("en", ignoreCase = true) }
        val american = english.filter { it.locale.country.equals("US", ignoreCase = true) }
        cachedVoices = american.ifEmpty { english }.associateBy { it.name }
        return cachedVoices
    }

    override val maxInputLength: Int get() = TextToSpeech.getMaxSpeechInputLength()

    override val voices: List<VoiceOption>
        get() = englishVoices().values.map { VoiceOption(it.name, it.isNetworkConnectionRequired, it.quality) }

    override fun setLanguage(locale: Locale): Int {
        language = locale
        return engine.setLanguage(locale)
    }

    /**
     * `null` goes back through [TextToSpeech.setLanguage], which selects that language's default
     * voice — not through [TextToSpeech.getDefaultVoice], which follows the device's TTS language
     * (Korean on a Korean phone) and would drag the language along with the voice, so a story or
     * a pronunciation would be read in Korean.
     */
    override fun setVoice(name: String?): Int {
        val voice = name?.let { englishVoices()[it] } ?: return engine.setLanguage(language)
        return engine.setVoice(voice)
    }

    override fun setPitch(pitch: Float): Int = engine.setPitch(pitch)

    /** The voice the engine will use next; read by the device test that proves [setVoice]'s contract. */
    internal val currentVoiceName: String? get() = runCatching { engine.voice?.name }.getOrNull()
    internal val currentVoiceLocale: Locale? get() = runCatching { engine.voice?.locale }.getOrNull()

    override fun setOnUtteranceProgressListener(listener: UtteranceProgressListener) {
        engine.setOnUtteranceProgressListener(listener)
    }

    override fun speak(text: String, queueMode: Int, utteranceId: String): Int =
        engine.speak(text, queueMode, null, utteranceId)

    override fun stop() {
        engine.stop()
    }

    override fun shutdown() {
        engine.shutdown()
    }
}
