package com.englishquiz.app.domain.quiz

import com.englishquiz.app.data.ai.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 백로그 070: a Chinese typed answer is the expression's characters, exactly, minus the keyboard's noise. */
class ChineseQuizTextTest {
    @Test
    fun spacesAndPunctuationAsciiOrCjkDoNotMatter() {
        assertTrue(ChineseText.matches("打电话。", "打电话"))
        assertTrue(ChineseText.matches("打 电 话", "打电话"))
        assertTrue(ChineseText.matches("　打电话　", "打电话"))
        assertTrue(ChineseText.matches("“打电话”！？", "打电话"))
        assertTrue(ChineseText.matches("打电话...", "打电话，"))
        assertTrue(ChineseText.matches("（打电话）、《》〈〉【】…—·~～", "打电话"))
        assertTrue(ChineseText.matches("打电话;:\"'()[]-", "打电话"))
        assertEquals("打电话", ChineseText.key(" 打，电。话！ "))
    }

    @Test
    fun fullWidthDigitsAndLettersMatchTheirAsciiTwins() {
        assertTrue(ChineseText.matches("３点", "３点"))
        assertTrue(ChineseText.matches("3点", "３点"))
        // Width is folded, case is not: ｋ is k, and k is not K.
        assertFalse(ChineseText.matches("ｋ歌", "K歌"))
        assertEquals("K歌", ChineseText.key("Ｋ歌"))
    }

    @Test
    fun noPartialCreditAndNoCaseFolding() {
        assertFalse(ChineseText.matches("打电", "打电话"))
        assertFalse(ChineseText.matches("打电话了", "打电话"))
        assertFalse(ChineseText.matches("电话打", "打电话"))
        assertFalse(ChineseText.matches("打電話", "打电话"))
        assertFalse(ChineseText.matches("", "打电话"))
        assertFalse(ChineseText.matches("。。。", "打电话"))
        assertFalse(ChineseText.matches("ok", "OK"))
    }

    @Test
    fun theHintShowsTheFirstCharacterAndACircleForEachOther() {
        assertEquals("打○○", ChineseText.hint("打电话"))
        assertEquals("打○○", ChineseText.hint(" 打电话。"))
        assertEquals("好", ChineseText.hint("好"))
        assertEquals("", ChineseText.hint("。"))
    }

    @Test
    fun theLanguageParameterDispatchesAndEnglishIsUnchanged() {
        assertTrue(typedAnswerMatches("打电话。", "打电话", Language.CHINESE))
        assertFalse(typedAnswerMatches("打电话", "打电", Language.CHINESE))
        assertEquals("打○○", typedAnswerHint("打电话", Language.CHINESE))
        // The default is English and keeps every English leniency; Chinese grants none of them.
        assertTrue(typedAnswerMatches("Hanging out.", "hang out"))
        assertTrue(typedAnswerMatches("Hanging out.", "hang out", Language.ENGLISH))
        assertFalse(typedAnswerMatches("Hanging out.", "hang out", Language.CHINESE))
        assertEquals("h___ o__", typedAnswerHint("hang out"))
        assertEquals("h___ o__", typedAnswerHint("hang out", Language.ENGLISH))
        assertEquals("h○○○○○○", typedAnswerHint("hang out", Language.CHINESE))
    }
}
