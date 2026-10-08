package com.englishquiz.app.domain.quiz

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 백로그 043: a typed answer is judged on the same key the builder uses for options. */
class QuizTextTest {
    @Test
    fun caseEdgePunctuationSpacingAndTypographyDoNotMatter() {
        assertTrue(typedAnswerMatches("Hang out.", "hang out"))
        assertTrue(typedAnswerMatches("  hang   OUT ", "hang out"))
        assertTrue(typedAnswerMatches("I’m in", "I'm in"))
    }

    @Test
    fun aDifferentOrEmptyAnswerIsWrong() {
        assertFalse(typedAnswerMatches("hang in", "hang out"))
        assertFalse(typedAnswerMatches("hangout", "hang out"))
        assertFalse(typedAnswerMatches("", "hang out"))
        assertFalse(typedAnswerMatches("...", "hang out"))
    }

    @Test
    fun theHintKeepsEveryFirstLetter() {
        assertEquals("h___ o__", typedAnswerHint("hang out"))
        assertEquals("s______", typedAnswerHint(" sketchy "))
        assertEquals("I__ i_", typedAnswerHint("I'm in"))
    }
}
