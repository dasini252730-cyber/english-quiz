package com.englishquiz.app.ui.reader

import android.speech.tts.TextToSpeech
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.englishquiz.app.data.ai.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 백로그 020: each speaker of a conversation is queued in a voice of its own, a pronunciation goes
 * back to the default voice, and the playback contract of [ReaderSpeechTest] stays as it was.
 */
@RunWith(AndroidJUnit4::class)
class ReaderSpeechVoicesTest {
    private val twoVoices = listOf(
        VoiceOption("en-US-SMTf00", requiresNetwork = false, quality = 300),
        VoiceOption("en-US-SMTm00", requiresNetwork = false, quality = 300),
    )

    @Test
    fun eachSpeakerIsQueuedInItsOwnVoiceAndAPronunciationInTheDefaultOne() {
        val engine = RecordingEngine(voices = twoVoices)
        val speech = ready(engine)

        onMain {
            speech.play(
                listOf(
                    SpeechLine("Hi Tom!", "Anna"),
                    SpeechLine("Hi Anna!", "Tom"),
                    SpeechLine("How are you?", "Anna"),
                ),
            )
        }

        val (anna1, tom, anna2) = engine.voicedAs
        assertEquals("en-US-SMTf00", anna1.first)
        assertEquals("en-US-SMTm00", tom.first)
        assertEquals(anna1, anna2)
        // The queue contract is untouched: flush first, add the rest.
        assertEquals(TextToSpeech.QUEUE_FLUSH, engine.spoken.first().second)

        // The expression sheet's pronunciation has no speaker and must come back in the default
        // voice, not stuck on whichever character spoke last.
        onMain { speech.play(listOf("sketchy")) }
        assertEquals(null to 1f, engine.voicedAs.last())
    }

    @Test
    fun oneBubblePlayedAloneStillGetsItsSpeakersVoice() {
        // The sentence button plays a single line. Anna must sound as she does in the whole
        // conversation, which the cast of the passage — not the lines of this request — decides.
        val engine = RecordingEngine(voices = twoVoices)
        val speech = ready(engine)
        onMain { speech.cast = listOf("Anna", "Tom", "Anna") }

        onMain { speech.play(listOf(SpeechLine("Hi Tom!", "Anna"))) }
        onMain { speech.play(listOf(SpeechLine("Hi Anna!", "Tom"))) }

        assertEquals(listOf("en-US-SMTf00", "en-US-SMTm00"), engine.voicedAs.map { it.first })
    }

    @Test
    fun aRefusedVoiceFallsBackToTheDefaultInsteadOfTheLastSpeakers() {
        // Equal quality, so the voices are taken in name order: Anna gets "alpha", Tom "zulu".
        val engine = RecordingEngine(
            voices = listOf(VoiceOption("zulu", false, 300), VoiceOption("alpha", false, 300)),
        ).apply { refuse = "zulu" }
        val speech = ready(engine)
        onMain { speech.cast = listOf("Anna", "Tom") }

        onMain { speech.play(listOf(SpeechLine("Hi!", "Anna"), SpeechLine("Hey.", "Tom"))) }

        // Tom's "zulu" was refused, so his line is spoken in the default voice — not in Anna's —
        // and, since 백로그 073, at the natural pitch: a bent voice is worse than a shared one.
        assertEquals(listOf("alpha" to 1f, null to 1f), engine.voicedAs)
    }

    @Test
    fun withOnlyOneVoiceBothSpeakersUseItUnbent() {
        val engine = RecordingEngine(voices = listOf(VoiceOption("only", false, 300)))
        val speech = ready(engine)

        onMain { speech.play(listOf(SpeechLine("Hi!", "Anna"), SpeechLine("Hey.", "Tom"))) }

        // 백로그 073: pitch shifting made Mandarin tones unintelligible, so the voice is reused as it is.
        assertEquals(listOf("only" to 1f, "only" to 1f), engine.voicedAs)
    }

    @Test
    fun aChineseReaderSlowsTheEngineAndAnEnglishOneDoesNot() {
        val chinese = RecordingEngine()
        ready(chinese, language = Language.CHINESE)
        assertEquals(0.9f, chinese.rateSet)

        val english = RecordingEngine()
        ready(english)
        assertEquals(1f, english.rateSet)
        assertTrue(engineIsQuiet(english))
    }

    private fun engineIsQuiet(engine: RecordingEngine) = engine.spoken.isEmpty()

    private val cloudAndLocal = listOf(
        VoiceOption("cloud", requiresNetwork = true, quality = 500),
        VoiceOption("local", requiresNetwork = false, quality = 300),
    )

    @Test
    fun theOnlineVoiceSwitchReachesTheVoicePlan() {
        // 백로그 073: off, the better online voice is skipped; on, it is used.
        val off = RecordingEngine(voices = cloudAndLocal)
        val offSpeech = ready(off)
        onMain { offSpeech.play(listOf(SpeechLine("Hi!", "Anna"), SpeechLine("Hey.", "Tom"))) }
        assertEquals(listOf("local", "local"), off.voicedAs.map { it.first })

        val on = RecordingEngine(voices = cloudAndLocal)
        val onSpeech = ready(on, allowNetworkVoices = true)
        onMain { onSpeech.play(listOf(SpeechLine("Hi!", "Anna"), SpeechLine("Hey.", "Tom"))) }
        assertEquals(listOf("cloud", "local"), on.voicedAs.map { it.first })
    }

    @Test
    fun anOnlineVoiceThatCannotConnectIsReplacedByInstalledOnesAndTheReaderIsTold() {
        val engine = RecordingEngine(voices = cloudAndLocal)
        val speech = ready(engine, allowNetworkVoices = true)
        onMain { speech.play(listOf(SpeechLine("Hi!", "Anna"), SpeechLine("Hey.", "Tom"))) }
        assertEquals("cloud", engine.voicedAs.first().first)
        val firstId = engine.spoken.first().third

        // The engine reports the network failure for the utterance it was given (API 21 onError).
        onMain { engine.listener!!.onError(firstId, TextToSpeech.ERROR_NETWORK) }
        onMain { }

        // The same two lines were queued again, installed voices only, and the state says why.
        assertEquals(listOf("cloud", "local", "local", "local"), engine.voicedAs.map { it.first })
        assertEquals(listOf("Hi!", "Hey.", "Hi!", "Hey."), engine.spoken.map { it.first })
        assertEquals(ReaderSpeech.OFFLINE_FALLBACK_MESSAGE, speech.state.value.error)
        assertTrue(speech.state.value.playing)

        // Any other error is still the plain failure.
        onMain { engine.listener!!.onError(engine.spoken.last().third, TextToSpeech.ERROR_SYNTHESIS) }
        onMain { }
        assertEquals("음성을 재생하지 못했어요. 다시 시도해 주세요.", speech.state.value.error)
    }
}
