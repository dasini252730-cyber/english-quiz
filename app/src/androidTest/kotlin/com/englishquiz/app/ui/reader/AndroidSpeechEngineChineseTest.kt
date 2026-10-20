package com.englishquiz.app.ui.reader

import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.ai.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Device evidence for 백로그 073: which Mandarin voices this device's real engine has, under which
 * locale codes, and that a Chinese line can be queued in one of them at the slower rate. The
 * voice inventory is logged whatever the outcome, so a device without Chinese still tells us what
 * it does have. Naturalness of the audio is something only a listener can judge.
 */
@RunWith(AndroidJUnit4::class)
class AndroidSpeechEngineChineseTest {
    @Test
    fun aMandarinVoiceIsFoundUnderZhOrCmnAndALineCanBeQueued() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val started = CountDownLatch(1)
        var status = TextToSpeech.ERROR
        lateinit var engine: AndroidSpeechEngine
        onMain {
            engine = AndroidSpeechEngine(context, Language.CHINESE) {
                status = it
                started.countDown()
            }
        }
        assumeTrue("no speech engine on this device", started.await(15, TimeUnit.SECONDS))
        assumeTrue("the speech engine did not start", status == TextToSpeech.SUCCESS)
        try {
            val all = engine.allVoices
            Log.i("ReaderSpeechProbe", "device voices (${all.size}): ${all.joinToString { "${it.name}@${it.locale}" }}")
            val results = ReaderSpeech.localesFor(Language.CHINESE).associateWith { engine.setLanguage(it) }
            Log.i("ReaderSpeechProbe", "setLanguage results: $results")
            val spoken = results.entries.firstOrNull { it.value >= TextToSpeech.LANG_AVAILABLE }
            assumeTrue("no Mandarin on this device: $results", spoken != null)
            engine.setLanguage(spoken!!.key)
            val voices = engine.voices
            Log.i("ReaderSpeechProbe", "mandarin voices via ${spoken.key}: ${voices.joinToString { "${it.name}@${it.locale}" }}")
            assertTrue("a supported language lists no voice", voices.isNotEmpty())
            assertTrue(voices.all { it.locale.startsWith("zh") || it.locale.startsWith("cmn") })

            assertTrue(engine.setSpeechRate(ReaderSpeech.CHINESE_SPEECH_RATE) >= 0)
            assertTrue(engine.setVoice(voices.first().name) >= 0)
            assertEquals(voices.first().name, engine.currentVoiceName)
            assertEquals(TextToSpeech.SUCCESS, engine.speak("她把雨伞还给了老板。", TextToSpeech.QUEUE_FLUSH, "probe"))
        } finally {
            onMain { engine.shutdown() }
        }
    }
}
