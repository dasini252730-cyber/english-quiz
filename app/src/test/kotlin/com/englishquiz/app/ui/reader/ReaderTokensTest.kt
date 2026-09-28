package com.englishquiz.app.ui.reader

import com.englishquiz.app.data.ai.ContentExpression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReaderTokensTest {
    @Test fun mergesMultiWordExpressionIntoOneToken() {
        val text = "I knew you could pull it off."
        val start = text.indexOf("pull it off")
        val expression = ContentExpression("pull it off", "성공적으로 해내다", 0, start, start + "pull it off".length)

        val tokens = tokenize(text, listOf(expression), segmentIndex = 0)

        val expressionToken = tokens.single { it.isExpression }
        assertEquals("pull it off", expressionToken.display)
        assertEquals("pull it off", expressionToken.lookup)
        assertEquals(start, expressionToken.startIndex)
        assertEquals(start + "pull it off".length, expressionToken.endIndex)
        // Surrounding words are still split individually, and the expression is one token.
        assertEquals(listOf("I", "knew", "you", "could", "pull it off", "."), tokens.map { it.display })
    }

    @Test fun stripsEdgePunctuationFromLookupButKeepsDisplay() {
        val tokens = tokenize("That sounds sketchy.", emptyList(), segmentIndex = 0)

        val last = tokens.last()
        assertEquals("sketchy.", last.display)
        assertEquals("sketchy", last.lookup)
        assertTrue(last.isTappable)
    }

    @Test fun splitsIntoWordsWhenNoExpressionsAreGiven() {
        val tokens = tokenize("Let's find another place.", emptyList(), segmentIndex = 2)

        assertEquals(listOf("Let's", "find", "another", "place."), tokens.map { it.display })
        assertTrue(tokens.all { it.segmentIndex == 2 })
        assertFalse(tokens.any { it.isExpression })
    }

    @Test fun punctuationOnlyTokenIsNotTappable() {
        val tokens = tokenize("Wait -- really?", emptyList(), segmentIndex = 0)

        val dashToken = tokens.first { it.display == "--" }
        assertEquals("", dashToken.lookup)
        assertFalse(dashToken.isTappable)
    }

    @Test fun ignoresExpressionRangeThatIsOutOfBounds() {
        val text = "Short text."
        val outOfBounds = ContentExpression("text", "meaning", 0, 6, 999)
        val reversed = ContentExpression("bad", "meaning", 0, 5, 2)

        val tokens = tokenize(text, listOf(outOfBounds, reversed), segmentIndex = 0)

        assertFalse(tokens.any { it.isExpression })
        assertEquals(listOf("Short", "text."), tokens.map { it.display })
    }

    @Test fun dropsOverlappingExpressionAndKeepsTheEarlierOne() {
        val text = "That sounds really sketchy today."
        val firstStart = text.indexOf("sounds really")
        val firstEnd = firstStart + "sounds really".length
        val overlappingStart = text.indexOf("really sketchy")
        val overlappingEnd = overlappingStart + "really sketchy".length
        val first = ContentExpression("sounds really", "meaning A", 0, firstStart, firstEnd)
        val overlapping = ContentExpression("really sketchy", "meaning B", 0, overlappingStart, overlappingEnd)

        val tokens = tokenize(text, listOf(first, overlapping), segmentIndex = 0)

        val expressionTokens = tokens.filter { it.isExpression }
        assertEquals(1, expressionTokens.size)
        assertEquals("sounds really", expressionTokens.single().display)
    }

    @Test fun ignoresExpressionsForOtherSegments() {
        val text = "Hello there."
        val other = ContentExpression("Hello", "meaning", 1, 0, 5)

        val tokens = tokenize(text, listOf(other), segmentIndex = 0)

        assertFalse(tokens.any { it.isExpression })
    }

    @Test fun emptySegmentTextProducesNoTokens() {
        assertTrue(tokenize("", emptyList(), segmentIndex = 0).isEmpty())
    }
}
