package com.englishquiz.app.ui.reader

import android.speech.tts.TextToSpeech
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers 백로그 007's playback contract with a [RecordingEngine], because the thing that must be
 * proved — a new request silences the previous one — is invisible through a real speech engine.
 * The per-speaker voices of 백로그 020 are in [ReaderSpeechVoicesTest].
 *
 * Runs on the device rather than the JVM because [ReaderSpeech] owns main-thread state and posts
 * its engine callbacks through a `Handler`.
 */
@RunWith(AndroidJUnit4::class)
class ReaderSpeechTest {
    @Test
    fun aNewRequestSilencesTheOneBeforeItSoTwoVoicesNeverOverlap() {
        val engine = RecordingEngine()
        val speech = ready(engine)

        onMain { speech.play(listOf("First sentence.", "Second sentence.")) }
        engine.calls.clear()
        onMain { speech.play(listOf("A single expression.")) }

        // The engine is stopped before anything new is queued, and the first new utterance
        // flushes whatever survived, so the previous playback cannot be heard under this one.
        assertEquals("stop", engine.calls.first())
        assertEquals("speak:A single expression.", engine.calls[1])
        assertEquals(TextToSpeech.QUEUE_FLUSH, engine.spoken.last().second)
    }

    @Test
    fun aSequentialPlaybackQueuesAfterItsOwnFirstUtterance() {
        val engine = RecordingEngine()
        val speech = ready(engine)

        onMain { speech.play(listOf("One.", "Two.", "Three.")) }

        val modes = engine.spoken.map { it.second }
        assertEquals(TextToSpeech.QUEUE_FLUSH, modes.first())
        assertEquals(listOf(TextToSpeech.QUEUE_ADD, TextToSpeech.QUEUE_ADD), modes.drop(1))
        assertTrue(speech.state.value.playing)
    }

    @Test
    fun aLateCallbackFromTheReplacedRequestCannotClearTheNewOnesPlayingState() {
        val engine = RecordingEngine()
        val speech = ready(engine)
        onMain { speech.play(listOf("First.", "Second.")) }
        val staleId = engine.spoken.last().third
        onMain { speech.play(listOf("Replacement.")) }

        onMain { engine.listener?.onDone(staleId) }
        onMain { }

        assertTrue("이전 요청의 완료 callback이 새 재생을 멈췄다", speech.state.value.playing)
    }

    @Test
    fun theOwnRequestsFinalCallbackDoesStopThePlayingState() {
        val engine = RecordingEngine()
        val speech = ready(engine)
        onMain { speech.play(listOf("Only one.")) }

        onMain { engine.listener?.onDone(engine.spoken.last().third) }
        onMain { }

        assertFalse(speech.state.value.playing)
    }

    @Test
    fun anEngineThatCannotSpeakEnglishReportsItAndNeverQueuesAnything() {
        val engine = RecordingEngine().apply { languageResult = TextToSpeech.LANG_MISSING_DATA }
        val speech = ready(engine)

        onMain { speech.play(listOf("Anything.")) }

        assertFalse(speech.state.value.ready)
        assertNotNull(speech.state.value.error)
        assertTrue(engine.spoken.isEmpty())
        assertFalse(speech.state.value.playing)
    }

    @Test
    fun aSentenceTheEngineRefusesLeavesTheScreenUsableWithAnError() {
        val engine = RecordingEngine().apply { speakResult = TextToSpeech.ERROR }
        val speech = ready(engine)

        onMain { speech.play(listOf("Anything.")) }

        assertFalse(speech.state.value.playing)
        assertNotNull(speech.state.value.error)
        // A refused sentence still stops the engine, so nothing is left running behind the error.
        assertTrue(engine.calls.contains("stop"))
    }

    @Test
    fun anOverlongOrBlankSentenceIsRejectedWithoutCallingTheEngine() {
        val engine = RecordingEngine()
        val speech = ready(engine)

        onMain { speech.play(listOf("   ")) }
        assertTrue(engine.spoken.isEmpty())
        assertNotNull(speech.state.value.error)

        onMain { speech.play(listOf("x".repeat(engine.maxInputLength + 1))) }
        assertTrue(engine.spoken.isEmpty())
    }

    @Test
    fun closingShutsTheEngineDownAndIgnoresLaterRequests() {
        val engine = RecordingEngine()
        val speech = ready(engine)

        onMain { speech.close() }
        onMain { speech.play(listOf("After close.")) }

        assertTrue(engine.calls.contains("shutdown"))
        assertTrue(engine.spoken.isEmpty())
    }
}
