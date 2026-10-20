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
import java.util.Locale

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
 * when the device cannot speak it the state carries a message naming it. [allowNetworkVoices]
 * (백로그 073, the home toggle) admits voices that need a connection; when such a voice fails for
 * lack of one, the same lines are read again with installed voices only.
 */
class ReaderSpeech internal constructor(
    private val language: Language = Language.ENGLISH,
    private val allowNetworkVoices: Boolean = false,
    createEngine: ((Int) -> Unit) -> SpeechEngine,
) {
    constructor(context: Context, language: Language = Language.ENGLISH, allowNetworkVoices: Boolean = false) :
        this(language, allowNetworkVoices, { onInit -> AndroidSpeechEngine(context, language, onInit) })

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
    private var lastLines: List<SpeechLine> = emptyList()
    /** Set once an online voice failed for lack of a connection: this reader stays on installed voices. */
    private var localOnly = false
    private var engine: SpeechEngine? = null

    init {
        engine = createEngine { status ->
            handler.post {
                if (!closed) initialize(status)
            }
        }
    }

    /**
     * Sets the language, trying each locale the engine might file it under (백로그 073: Mandarin is
     * `zh_CN` to Android but `cmn` to some engines). What was asked and answered is logged; the
     * engine's whole voice inventory once, in a debug build. When no locale works the engine is
     * put back on the language's own locale, so nothing later runs against a stray `cmn`.
     */
    private fun initialize(status: Int) {
        val tts = engine ?: return
        val results = linkedMapOf<Locale, Int>()
        var chosen: Locale? = null
        if (status == TextToSpeech.SUCCESS) {
            for (locale in localesFor(language)) {
                val result = tts.setLanguage(locale)
                results[locale] = result
                if (result >= TextToSpeech.LANG_AVAILABLE) {
                    chosen = locale
                    break
                }
            }
        }
        if (chosen != null) {
            Log.i(TAG, "$language ready via $chosen (${results.describe()}); ${tts.voices.size} voices for it")
            tts.setSpeechRate(rateFor(language))
            if (BuildConfig.DEBUG) logChunked("engine voices", tts.allVoices.map { "${it.name}@${it.locale}${if (it.requiresNetwork) "/net" else ""}" })
        } else {
            Log.w(TAG, "$language unavailable: status=$status, ${results.describe().ifEmpty { "no locale tried" }}")
            if (results.isNotEmpty()) tts.setLanguage(language.locale)
        }
        mutableState.value = SpeechState(
            ready = chosen != null,
            error = if (chosen != null) null else unavailableMessage(language),
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
            override fun onError(utteranceId: String?) = onError(utteranceId, TextToSpeech.ERROR)
            override fun onError(utteranceId: String?, errorCode: Int) {
                handler.post {
                    if (!closed && utteranceId?.startsWith("$generation:") == true) failed(errorCode)
                }
            }
        })
    }

    /** Plain lines in the default voice: a pronunciation, or a story read by one narrator. */
    @JvmName("playTexts")
    fun play(texts: List<String>) = play(texts.map { SpeechLine(it) })

    /**
     * Speaks [lines] in order, silencing whatever was playing first (요구사항 13절), each in its
     * speaker's voice (백로그 020). The [stop] call and the `QUEUE_FLUSH` on the first utterance are
     * both deliberate: `stop` also retires the previous generation's utterance ids, so a late
     * callback from the old request cannot clear the new one's playing state. The voice is set
     * right before each `speak`, which is when the engine snapshots it for that utterance.
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
        lastLines = lines
        val plan = SpeakerVoices.assign(cast + lines.mapNotNull { it.speaker }, tts.voices, genders, allowNetworkVoices && !localOnly)
        if (BuildConfig.DEBUG) Log.d(TAG, "plan for $language: ${plan.entries.joinToString { "${it.key}=${it.value.voiceName ?: "default"}" }}")
        lastId = "$generation:${lines.lastIndex}"
        mutableState.value = mutableState.value.copy(playing = true)
        // The voice is set only when the speaker changes: each setVoice is a synchronous binder
        // call, and a forty-line conversation must not make eighty of them.
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
     * actually applied is returned for the change check. The pitch is always 1 (백로그 073). What
     * the engine really holds afterwards is logged, because `setVoice` can succeed and still leave
     * another voice in force.
     */
    private fun apply(tts: SpeechEngine, choice: VoiceChoice): VoiceChoice {
        val accepted = tts.setVoice(choice.voiceName) >= 0
        val effective = if (accepted || choice.voiceName == null) {
            choice
        } else {
            Log.w(TAG, "voice ${choice.voiceName} refused; using the default")
            tts.setVoice(null)
            DEFAULT_VOICE
        }
        tts.setPitch(effective.pitch)
        val actual = tts.appliedVoice
        if (effective.voiceName != null && actual != null && !actual.startsWith(effective.voiceName)) {
            Log.w(TAG, "asked for ${effective.voiceName}, engine holds $actual")
        } else if (BuildConfig.DEBUG) {
            Log.d(TAG, "voice ${effective.voiceName ?: "default"} -> engine holds ${actual ?: "?"}")
        }
        return effective
    }

    /**
     * An utterance failed. A network voice that could not reach its server (백로그 073) is retried
     * once with installed voices only, and the reader is told why it sounds different; anything
     * else is the plain failure.
     */
    private fun failed(errorCode: Int) {
        val network = errorCode == TextToSpeech.ERROR_NETWORK || errorCode == TextToSpeech.ERROR_NETWORK_TIMEOUT
        if (network && allowNetworkVoices && !localOnly && lastLines.isNotEmpty()) {
            Log.w(TAG, "online voice failed ($errorCode); falling back to installed voices")
            localOnly = true
            val lines = lastLines
            play(lines)
            mutableState.value = mutableState.value.copy(error = OFFLINE_FALLBACK_MESSAGE)
            return
        }
        fail()
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

    private fun Map<Locale, Int>.describe(): String = entries.joinToString { "${it.key}=${it.value}" }

    /** Logcat truncates a long line, so the inventory goes out in pieces that each fit. */
    private fun logChunked(label: String, items: List<String>) {
        if (items.isEmpty()) {
            Log.d(TAG, "$label: (none)")
            return
        }
        val chunks = items.chunked(LOG_CHUNK_ITEMS)
        chunks.forEachIndexed { index, chunk -> Log.d(TAG, "$label ${index + 1}/${chunks.size}: ${chunk.joinToString()}") }
    }

    internal companion object {
        const val TAG = "ReaderSpeech"
        const val LOG_CHUNK_ITEMS = 25
        const val OFFLINE_FALLBACK_MESSAGE = "온라인 음성에 연결하지 못해 기기에 설치된 음성으로 읽어요."
        val DEFAULT_VOICE = VoiceChoice(voiceName = null)
        /** Mandarin for an intermediate learner reads a little slower than the engine's default (백로그 073). */
        const val CHINESE_SPEECH_RATE = 0.9f

        fun rateFor(language: Language): Float = if (language == Language.CHINESE) CHINESE_SPEECH_RATE else 1f

        /** The locales to try, most specific first; English has one, Mandarin has the `cmn` spellings too. */
        fun localesFor(language: Language): List<Locale> = when (language) {
            Language.CHINESE -> listOf(language.locale, Locale("cmn", "CN"), Locale("zh"), Locale("cmn"))
            else -> listOf(language.locale)
        }

        /** Shown when the engine has no voice for [language]; the fix is on the device, so it says where. */
        fun unavailableMessage(language: Language): String =
            "${language.korean} 음성을 사용할 수 없어요. 설정 > 텍스트 음성 변환에서 ${language.korean} 음성을 설치해 주세요."
    }
}
