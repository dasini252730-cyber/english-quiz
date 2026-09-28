package com.englishquiz.app.ui.reader

import android.speech.tts.TextToSpeech
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Runs against the device's real speech engine, because the one thing the recording engine cannot
 * prove is how [android.speech.tts.TextToSpeech] itself answers: going back to the default voice
 * must keep English, not follow the device's TTS language (백로그 020 review).
 *
 * A device without a working English engine is not a failure of the app, so such a device skips.
 */
@RunWith(AndroidJUnit4::class)
class AndroidSpeechEngineTest {
    @Test
    fun goingBackToTheDefaultVoiceKeepsEnglishNotTheDevicesLanguage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val started = CountDownLatch(1)
        var status = TextToSpeech.ERROR
        lateinit var engine: AndroidSpeechEngine
        onMain {
            engine = AndroidSpeechEngine(context) {
                status = it
                started.countDown()
            }
        }
        assumeTrue("no speech engine on this device", started.await(15, TimeUnit.SECONDS))
        assumeTrue("the speech engine did not start", status == TextToSpeech.SUCCESS)
        try {
            assumeTrue("English is not installed", engine.setLanguage(Locale.US) >= 0)
            val named = engine.voices.firstOrNull()
            assumeTrue("no English voice is installed", named != null)

            assertTrue(engine.setVoice(named!!.name) >= 0)
            assertEquals(named.name, engine.currentVoiceName)

            // The step under test: after a character's voice, "default" is English's default.
            assertTrue(engine.setVoice(null) >= 0)
            assertEquals("en", engine.currentVoiceLocale?.language?.lowercase())
        } finally {
            onMain { engine.shutdown() }
        }
    }
}
