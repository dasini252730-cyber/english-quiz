package com.englishquiz.app.ui.reader

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.englishquiz.app.BuildConfig
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.ai.SpeakerGender
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SpeechState(
    val ready: Boolean = false,
    val playing: Boolean = false,
    val error: String? = null,
)

/** One thing to say and, for a conversation, who says it. A `null` speaker gets the default voice. */
data class SpeechLine(val text: String, val speaker: String? = null)

/**
 * Owned by a visible reader; all calls and state changes run on the main thread. It reads one
 * [language] for its whole life (백로그 071): the engine is set to that language's locale, and
 * when the device cannot speak it the state carries a message naming it.
 */
class ReaderSpeech internal constructor(
    private val language: Language = Language.ENGLISH,
    createEngine: ((Int) -> Unit) -> SpeechEngine,
) {
    constructor(context: Context, language: Language = Language.ENGLISH) :
        this(language, { onInit -> AndroidSpeechEngine(context, language, onInit) })

    private val handler = Handler(Looper.getMainLooper())
    private val mutableState = MutableStateFlow(SpeechState())
    val state = mutableState.asStateFlow()

    /**
     * Everyone who speaks in the passage being read, in order of appearance. Voices are assigned
     * from this list, not from the lines of one request, so Anna sounds the same whether her
     * bubble is played alone or the whole conversation is. A story read by one narrator, or a
     * reader with no passage (the review box), leaves it empty and gets the default voice.
     */
    var cast: List<String> = emptyList()
    /** Who each speaker is, as the passage says (백로그 050); empty assigns by order of appearance. */
    var genders: Map<String, SpeakerGender> = emptyMap()
    private var closed = false
    private var generation = 0L
    private var lastId: String? = null
    private var engine: SpeechEngine? = null

    init {
        engine = createEngine { status ->
            handler.post {
                if (!closed) initialize(status)
            }
        }
    }

    private fun initialize(status: Int) {
        val tts = engine ?: return
        val supported = status == TextToSpeech.SUCCESS &&
            tts.setLanguage(language.locale) >= TextToSpeech.LANG_AVAILABLE
        mutableState.value = SpeechState(
            ready = supported,
            error = if (supported) null else unavailableMessage(language),
        )
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit
            override fun onDone(utteranceId: String?) {
                handler.post {
                    if (!closed && utteranceId == lastId) {
                        mutableState.value = mutableState.value.copy(playing = false)
                    }
                }
            }
            @Deprecated("Required by Android")
            override fun onError(utteranceId: String?) {
                handler.post {
                    if (!closed && utteranceId?.startsWith("$generation:") == true) fail()
                }
            }
        })
    }

    /**
     * Speaks [texts] in order, silencing whatever was playing first (요구사항 13절). The [stop] call
     * and the `QUEUE_FLUSH` on the first utterance are both deliberate: `stop` also retires the
     * previous generation's utterance ids, so a late callback from the old request cannot clear
     * the new one's playing state.
     */
    /** Plain lines in the default voice: a pronunciation, or a story read by one narrator. */
    @JvmName("playTexts")
    fun play(texts: List<String>) = play(texts.map { SpeechLine(it) })

    /**
     * Speaks [lines] in order, each in its speaker's voice (백로그 020). The voice and pitch are set
     * right before each `speak`, which is when the engine snapshots them for that utterance, so a
     * queued conversation alternates voices without waiting for one line to finish.
     */
    fun play(lines: List<SpeechLine>) {
        if (closed) return
        stop()
        if (!mutableState.value.ready) return
        val tts = engine ?: return
        val limit = tts.maxInputLength
        if (lines.isEmpty() || lines.any { it.text.isBlank() || it.text.length > limit }) {
            fail()
            return
        }
        val plan = SpeakerVoices.assign(cast + lines.mapNotNull { it.speaker }, tts.voices, genders)
        if (BuildConfig.DEBUG) Log.i(TAG, "voices: $plan")
        lastId = "$generation:${lines.lastIndex}"
        mutableState.value = mutableState.value.copy(playing = true, error = null)
        // Voice and pitch are set only when the speaker changes: each setVoice is a synchronous
        // binder call, and a forty-line conversation must not make eighty of them.
        var applied: VoiceChoice? = null
        for ((index, line) in lines.withIndex()) {
            val choice = line.speaker?.let(plan::get) ?: DEFAULT_VOICE
            if (choice != applied) applied = apply(tts, choice)
            val result = tts.speak(
                line.text,
                if (index == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD,
                "$generation:$index",
            )
            if (result != TextToSpeech.SUCCESS) {
                fail()
                break
            }
        }
    }

    fun stop() {
        generation++
        lastId = null
        engine?.stop()
        mutableState.value = mutableState.value.copy(playing = false)
    }

    /**
     * Puts [choice] in force for the utterances that follow. A voice the engine refuses must not
     * leave the previous speaker's voice in place, so the default is set instead, and what was
     * actually applied is returned for the change check.
     */
    private fun apply(tts: SpeechEngine, choice: VoiceChoice): VoiceChoice {
        val accepted = tts.setVoice(choice.voiceName) >= 0
        val effective = if (accepted || choice.voiceName == null) {
            // A refused default is already the default; asking again would change nothing.
            choice
        } else {
            // The default voice stands in at this speaker's own fallback pitch, so the roles
            // still differ by ear.
            Log.w(TAG, "voice ${choice.voiceName} refused; using the default")
            tts.setVoice(null)
            VoiceChoice(voiceName = null, pitch = choice.fallbackPitch, fallbackPitch = choice.fallbackPitch)
        }
        tts.setPitch(effective.pitch)
        return effective
    }

    private fun fail() {
        stop()
        mutableState.value = mutableState.value.copy(error = "음성을 재생하지 못했어요. 다시 시도해 주세요.")
    }

    fun close() {
        closed = true
        stop()
        engine?.shutdown()
        engine = null
    }

    private companion object {
        const val TAG = "ReaderSpeech"
        val DEFAULT_VOICE = VoiceChoice(voiceName = null, pitch = 1f)

        /** Shown when the engine has no voice for [language]; the fix is on the device, so it says where. */
        fun unavailableMessage(language: Language): String =
            "${language.korean} 음성을 사용할 수 없어요. 설정 > 텍스트 음성 변환에서 ${language.korean} 음성을 설치해 주세요."
    }
}
