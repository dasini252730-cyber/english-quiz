package com.englishquiz.app.domain.roleplay

import com.englishquiz.app.data.ai.Language
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 백로그 076: a role-play turn is graded on the words said, with one normalisation on both sides. */
class RoleplayGraderTest {
    @Test
    fun englishScoreIsTheShareOfTheLinesWordsTheAttemptContained() {
        val grade = RoleplayGrader.grade("Can you help me find the station?", "can you help me find the bus", Language.ENGLISH)
        assertEquals(6, grade.matched)
        assertEquals(7, grade.total)
        assertEquals(85, grade.percent)
        assertTrue(grade.passed)

        val weak = RoleplayGrader.grade("Can you help me find the station?", "help me", Language.ENGLISH)
        assertEquals(2, weak.matched)
        assertFalse(weak.passed)
    }

    @Test
    fun wordsAreComparedAsSetsNotAsCounts() {
        // "I know, I know" has two distinct words; saying each once covers them all.
        val grade = RoleplayGrader.grade("I know, I know.", "i know", Language.ENGLISH)
        assertEquals(2, grade.total)
        assertEquals(2, grade.matched)
        assertTrue(grade.passed)
    }

    @Test
    fun seventyPercentIsTheLine() {
        // 7 of 10 words passes; 6 of 10 does not.
        val line = "one two three four five six seven eight nine ten"
        assertTrue(RoleplayGrader.grade(line, "one two three four five six seven", Language.ENGLISH).passed)
        assertFalse(RoleplayGrader.grade(line, "one two three four five six", Language.ENGLISH).passed)
    }

    @Test
    fun caseAndPunctuationDoNotMatter() {
        val grade = RoleplayGrader.grade("Sure, no problem!", "SURE NO PROBLEM", Language.ENGLISH)
        assertEquals(3, grade.matched)
        assertEquals(3, grade.total)
    }

    @Test
    fun contractionsAndCurlyApostrophesNormaliseTheSameOnBothSides() {
        assertEquals(listOf("i", "dont", "think", "so"), RoleplayGrader.englishWords("I don’t think so."))
        assertEquals(listOf("i", "dont", "think", "so"), RoleplayGrader.englishWords("i dont think so"))
        assertEquals(listOf("i", "dont", "think", "so"), RoleplayGrader.englishWords("I don't think so"))
        val grade = RoleplayGrader.grade("I don’t think so.", "I dont think so", Language.ENGLISH)
        assertEquals(grade.total, grade.matched)
    }

    @Test
    fun anEmptyAttemptFailsWithoutDividingByZero() {
        val grade = RoleplayGrader.grade("Hello there.", "", Language.ENGLISH)
        assertEquals(0, grade.matched)
        assertFalse(grade.passed)
        assertFalse(RoleplayGrader.grade("", "", Language.ENGLISH).passed)
    }

    @Test
    fun chineseScoreIsTheShareOfHanCharactersIgnoringPunctuationAndSpaces() {
        val grade = RoleplayGrader.grade("你能帮我找车站吗？", "你 能帮我找 车站", Language.CHINESE)
        assertEquals(8, grade.total)
        assertEquals(7, grade.matched)
        assertTrue(grade.passed)

        val weak = RoleplayGrader.grade("你能帮我找车站吗？", "车站", Language.CHINESE)
        assertEquals(2, weak.matched)
        assertFalse(weak.passed)
    }

    @Test
    fun aChineseLineWithoutHanCharactersFallsBackToWords() {
        val grade = RoleplayGrader.grade("OK, bye!", "ok bye", Language.CHINESE)
        assertEquals(2, grade.total)
        assertTrue(grade.passed)
    }

    @Test
    fun englishHintIsTheFirstLetterOfEveryWordWithoutPunctuation() {
        assertEquals("C y h m", RoleplayGrader.hint("Can you help me?", Language.ENGLISH))
        assertEquals("I f t", RoleplayGrader.hint("I’m fine, thanks!", Language.ENGLISH))
        assertEquals("W", RoleplayGrader.hint("  Wow...  ", Language.ENGLISH))
        assertEquals("", RoleplayGrader.hint("...", Language.ENGLISH))
        // A dash or a non-breaking space cuts words as the grade does; a repeated word is cued each time.
        assertEquals("W w", RoleplayGrader.hint("Wait—what?", Language.ENGLISH))
        assertEquals(2, RoleplayGrader.englishWords("Wait—what?").size)
        assertEquals("S i j", RoleplayGrader.hint("Sure,\u00A0it's just", Language.ENGLISH))
        assertEquals("I k I k", RoleplayGrader.hint("I know, I know.", Language.ENGLISH))
    }

    @Test
    fun aTurnPassesAtOnceStaysOpenForTwoRetriesAndThenFails() {
        assertEquals(TurnOutcome.PASSED, RoleplayGrader.settle(attempt = 1, passed = true))
        assertEquals(TurnOutcome.RETRY, RoleplayGrader.settle(attempt = 1, passed = false))
        assertEquals(TurnOutcome.RETRY, RoleplayGrader.settle(attempt = 2, passed = false))
        assertEquals(TurnOutcome.PASSED, RoleplayGrader.settle(attempt = 3, passed = true))
        assertEquals(TurnOutcome.FAILED, RoleplayGrader.settle(attempt = 3, passed = false))
    }

    @Test
    fun chineseHintIsTheFirstHanCharacterOnly() {
        assertEquals("你", RoleplayGrader.hint("“你好吗？”", Language.CHINESE))
        // No Han character to give: the word rule stands in.
        assertEquals("O b", RoleplayGrader.hint("OK bye", Language.CHINESE))
    }

    @Test
    fun englishExpressionContainmentNeedsTheWordsInOrderAndTogether() {
        assertTrue(RoleplayGrader.containsExpression("we can pull it off together", "pull it off", Language.ENGLISH))
        assertTrue(RoleplayGrader.containsExpression("Pull it off!", "pull it off", Language.ENGLISH))
        assertFalse(RoleplayGrader.containsExpression("we can pull off it", "pull it off", Language.ENGLISH))
        assertFalse(RoleplayGrader.containsExpression("we can pull this off", "pull it off", Language.ENGLISH))
        assertFalse(RoleplayGrader.containsExpression("anything", "", Language.ENGLISH))
    }

    @Test
    fun chineseExpressionContainmentIsASubstringOfTheCharacters() {
        assertTrue(RoleplayGrader.containsExpression("我想打 电话 给你。", "打电话", Language.CHINESE))
        assertFalse(RoleplayGrader.containsExpression("我想给你写信", "打电话", Language.CHINESE))
        assertFalse(RoleplayGrader.containsExpression("我想打电话", "", Language.CHINESE))
    }
}
