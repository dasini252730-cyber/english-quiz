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
    fun contractionsInnerPunctuationHyphensAndALeadingToAreForgiven() {
        // 백로그 058: she recalled the words; the spelling of the form is not the test.
        assertTrue(typedAnswerMatches("I am in", "I'm in"))
        assertTrue(typedAnswerMatches("don't", "do not"))
        assertTrue(typedAnswerMatches("cannot", "can't"))
        assertTrue(typedAnswerMatches("let us go", "let's go"))
        assertTrue(typedAnswerMatches("we are going to", "we're gonna"))
        assertTrue(typedAnswerMatches("well known", "well-known"))
        assertTrue(typedAnswerMatches("call it a day", "to call it a day"))
        assertTrue(typedAnswerMatches("to call it a day", "call it a day"))
        assertTrue(typedAnswerMatches("so, so", "so so"))
        assertEquals(setOf("i am in"), typedAnswerKeys("I’m in!"))
        // 's and 'd each hide two verbs: both long forms are accepted.
        assertTrue(typedAnswerMatches("she has been there", "she's been there"))
        assertTrue(typedAnswerMatches("I had better", "I'd better"))
        assertTrue(typedAnswerMatches("I am not done", "I ain't done"))
    }

    @Test
    fun aRegularInflectionOfTheHeadWordIsForgiven() {
        // 백로그 058: the predefined verb forms — 3rd person, -ing, -ed — on the first word only.
        assertTrue(typedAnswerMatches("hanging out", "hang out"))
        assertTrue(typedAnswerMatches("hangs out", "hang out"))
        assertTrue(typedAnswerMatches("called it a day", "call it a day"))
        assertTrue(typedAnswerMatches("making do", "make do"))
        assertTrue(typedAnswerMatches("stopped by", "stop by"))
        assertEquals("study", stem("studies"))
        // Only the head word may change: a later word is held to the letter.
        assertFalse(typedAnswerMatches("hang outs", "hang out"))
    }

    @Test
    fun aChangedWordIsStillWrong() {
        assertFalse(typedAnswerMatches("hang in", "hang out"))
        assertFalse(typedAnswerMatches("I am out", "I'm in"))
        assertFalse(typedAnswerMatches("do", "do not"))
        assertFalse(typedAnswerMatches("its here", "it's here"))
        assertFalse(typedAnswerMatches("were done", "we're done"))
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
