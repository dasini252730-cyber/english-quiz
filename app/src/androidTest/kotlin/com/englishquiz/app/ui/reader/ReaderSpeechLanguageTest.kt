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
    fun aMissingLanguageNamesItselfInTheMessageAndLeavesTheEngineOnTheRequestedLocale() {
        val engine = RecordingEngine().apply { languageResult = TextToSpeech.LANG_NOT_SUPPORTED }
        val speech = ready(engine, language = Language.CHINESE)

        assertFalse(speech.state.value.ready)
        assertEquals(
            "중국어 음성을 사용할 수 없어요. 설정 > 텍스트 음성 변환에서 중국어 음성을 설치해 주세요.",
            speech.state.value.error,
        )
        // 백로그 073: every spelling was tried, then the engine was put back on zh_CN rather than
        // left on a stray "cmn"; no rate is set for a language that cannot be spoken.
        assertEquals(
            listOf(Locale.SIMPLIFIED_CHINESE, Locale("cmn", "CN"), Locale("zh"), Locale("cmn"), Locale.SIMPLIFIED_CHINESE),
            engine.languagesSet,
        )
        assertNull(engine.rateSet)
    }

    @Test
    fun anEngineThatFilesMandarinUnderCmnIsStillReady() {
        // 백로그 073: zh_CN is refused, cmn_CN accepted — the reader is ready, slower, and stops looking.
        val engine = RecordingEngine().apply {
            languageResults[Locale.SIMPLIFIED_CHINESE] = TextToSpeech.LANG_NOT_SUPPORTED
            languageResults[Locale("cmn", "CN")] = TextToSpeech.LANG_COUNTRY_AVAILABLE
        }
        val speech = ready(engine, language = Language.CHINESE)

        assertTrue(speech.state.value.ready)
        assertNull(speech.state.value.error)
        assertEquals(listOf(Locale.SIMPLIFIED_CHINESE, Locale("cmn", "CN")), engine.languagesSet)
        assertEquals(0.9f, engine.rateSet)
    }
}
