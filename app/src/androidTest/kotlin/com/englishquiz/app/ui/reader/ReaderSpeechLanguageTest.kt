package com.englishquiz.app.ui.reader

import android.speech.tts.TextToSpeech
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.englishquiz.app.data.ai.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Locale

/**
 * Proves the language half of 백로그 071 with a [RecordingEngine]: a Chinese reader sets the engine
 * to zh-CN, an English one still to en-US, and a device without the language is told which one to
 * install. Whether the S26 actually has a zh-CN voice is device evidence, not something a test can
 * decide; see [AndroidSpeechEngineTest] for the real engine.
 */
@RunWith(AndroidJUnit4::class)
class ReaderSpeechLanguageTest {
    @Test
    fun aChineseReaderSetsTheEngineToSimplifiedChinese() {
        val engine = RecordingEngine()
        val speech = ready(engine, language = Language.CHINESE)

        assertEquals(Locale.SIMPLIFIED_CHINESE, engine.languageSet)
        assertTrue(speech.state.value.ready)
        assertNull(speech.state.value.error)
    }

    @Test
    fun anEnglishReaderStillSetsAmericanEnglishByDefault() {
        val engine = RecordingEngine()
        ready(engine)

        assertEquals(Locale.US, engine.languageSet)
    }

    @Test
    fun aMissingLanguageNamesItselfInTheMessage() {
        val engine = RecordingEngine().apply { languageResult = TextToSpeech.LANG_NOT_SUPPORTED }
        val speech = ready(engine, language = Language.CHINESE)

        assertFalse(speech.state.value.ready)
        assertEquals(
            "중국어 음성을 사용할 수 없어요. 설정 > 텍스트 음성 변환에서 중국어 음성을 설치해 주세요.",
            speech.state.value.error,
        )
    }
}
