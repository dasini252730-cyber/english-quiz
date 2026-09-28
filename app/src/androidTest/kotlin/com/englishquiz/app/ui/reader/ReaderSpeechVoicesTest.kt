package com.englishquiz.app.ui.reader

import android.speech.tts.TextToSpeech
import androidx.test.ext.junit.runners.AndroidJUnit4
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

        // Tom's "zulu" was refused, so his line is spoken in the default voice — not in Anna's,
        // and at his own fallback pitch, so the two still sound different.
        assertEquals(listOf("alpha" to 1f, null to 0.8f), engine.voicedAs)
    }

    @Test
    fun withOnlyOneVoiceTheSpeakersStillDifferByPitch() {
        val engine = RecordingEngine(voices = listOf(VoiceOption("only", false, 300)))
        val speech = ready(engine)

        onMain { speech.play(listOf(SpeechLine("Hi!", "Anna"), SpeechLine("Hey.", "Tom"))) }

        val (anna, tom) = engine.voicedAs
        assertEquals("only", anna.first)
        assertEquals("only", tom.first)
        assertTrue("두 화자의 피치가 같다", anna.second != tom.second)
    }
}
