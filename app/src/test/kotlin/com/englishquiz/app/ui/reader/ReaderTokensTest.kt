package com.englishquiz.app.ui.reader

import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.Language
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

    // 백로그 071: Chinese has no spaces, so the plain text is cut by the learner's known words and then by character.

    @Test fun chineseKnownWordsAreMatchedLongestFirstAndTheRestIsSingleCharacters() {
        val tokens = tokenize(
            "我想去火车站买票。",
            emptyList(),
            segmentIndex = 0,
            language = Language.CHINESE,
            knownWords = listOf("火车", "火车站", "买票"),
        )

        assertEquals(listOf("我", "想", "去", "火车站", "买票", "。"), tokens.map { it.display })
        val station = tokens.first { it.display == "火车站" }
        assertEquals("火车站", station.lookup)
        assertEquals(3, station.startIndex)
        assertEquals(6, station.endIndex)
        assertFalse(station.isExpression)
        assertTrue(tokens.filter { it.display != "。" }.all { it.isTappable })
    }

    @Test fun chineseWithoutKnownWordsIsOneCharacterPerToken() {
        val tokens = tokenize("你好吗", emptyList(), segmentIndex = 1, language = Language.CHINESE)

        assertEquals(listOf("你", "好", "吗"), tokens.map { it.display })
        assertEquals(listOf(0, 1, 2), tokens.map { it.startIndex })
        assertTrue(tokens.all { it.segmentIndex == 1 && it.isTappable && !it.isExpression })
    }

    @Test fun cjkPunctuationIsItsOwnUntappableTokenAndNeverEntersALookup() {
        val tokens = tokenize(
            "“太好了！”她说，然后问：《西游记》呢？",
            emptyList(),
            segmentIndex = 0,
            language = Language.CHINESE,
            knownWords = listOf("太好了", "西游记"),
        )

        val punctuation = tokens.filter { !it.isTappable }.map { it.display }
        assertEquals(listOf("“", "！”", "，", "：《", "》", "？"), punctuation)
        assertTrue(tokens.filter { it.isTappable }.none { token -> token.lookup.any { it.isReaderPunctuation() } })
        assertEquals(listOf("太好了", "她", "说", "然", "后", "问", "西游记", "呢"), tokens.filter { it.isTappable }.map { it.lookup })
    }

    @Test fun chineseExpressionSpansStayWholeAndTheTextAroundThemIsStillCut() {
        val text = "这个太贵了，能便宜一点吗？"
        val start = text.indexOf("便宜一点")
        val expression = ContentExpression("便宜一点", "좀 싸게", 0, start, start + 4)

        val tokens = tokenize(text, listOf(expression), segmentIndex = 0, language = Language.CHINESE, knownWords = listOf("太贵了", "便宜"))

        val span = tokens.single { it.isExpression }
        assertEquals("便宜一点", span.display)
        assertEquals(start, span.startIndex)
        assertEquals(start + 4, span.endIndex)
        // The known word "便宜" inside the annotated span does not split it; "太贵了" outside it is still matched.
        assertEquals(listOf("这", "个", "太贵了", "，", "能", "便宜一点", "吗", "？"), tokens.map { it.display })
    }

    @Test fun latinLettersInsideAChineseLineStayTogether() {
        val tokens = tokenize("我家的WiFi密码是1234。", emptyList(), segmentIndex = 0, language = Language.CHINESE, knownWords = listOf("密码"))

        assertEquals(listOf("我", "家", "的", "WiFi", "密码", "是", "1234", "。"), tokens.map { it.display })
    }

    @Test fun chineseModeLeavesEnglishTokenisationAlone() {
        val english = tokenize("That sounds sketchy.", emptyList(), segmentIndex = 0)
        val explicit = tokenize("That sounds sketchy.", emptyList(), segmentIndex = 0, language = Language.ENGLISH)

        assertEquals(english, explicit)
    }
}
