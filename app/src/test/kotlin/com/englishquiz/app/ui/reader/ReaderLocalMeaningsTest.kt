package com.englishquiz.app.ui.reader

import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.GlossaryEntry
import com.englishquiz.app.data.ai.LearningContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReaderLocalMeaningsTest {
    private val content = LearningContent(
        title = "A cafe",
        mode = ContentMode.CONVERSATION,
        segments = listOf(
            ContentSegment("Emma", "That sounds sketchy, doesn’t it?"),
            ContentSegment("Sam", "Sketchy or not, let's go."),
        ),
        expressions = listOf(ContentExpression("sketchy", "수상한", 0, 12, 19)),
        glossary = listOf(GlossaryEntry("sounds", "~처럼 들리다"), GlossaryEntry("Sketchy", "수상한 (어휘표)")),
    )

    private fun token(lookup: String, segment: Int = 0, start: Int = 0, end: Int = 0, expression: Boolean = false) =
        ReaderToken(display = lookup, lookup = lookup, segmentIndex = segment, startIndex = start, endIndex = end, isExpression = expression)

    @Test
    fun anAnnotatedExpressionIsFoundByItsPosition() {
        assertEquals("수상한", content.localMeaningFor(token("sketchy", segment = 0, start = 12, end = 19, expression = true)))
    }

    @Test
    fun theSamePhraseElsewhereInThePassageIsFoundByItsTextWhateverItsCaseOrPunctuation() {
        assertEquals("수상한", content.localMeaningFor(token("Sketchy", segment = 1, start = 0, end = 7)))
    }

    @Test
    fun aGlossaryWordIsFoundWithItsCaseAndEdgePunctuationIgnored() {
        // The Reader cuts plain text into single words with their punctuation attached, so a
        // glossary is matched word by word: "sounds," must find "sounds".
        assertEquals("~처럼 들리다", content.localMeaningFor(token("sounds")))
        assertEquals("~처럼 들리다", content.localMeaningFor(token("Sounds,")))
    }

    @Test
    fun anAnnotationOutranksAGlossaryEntryForTheSameWord() {
        // Both carry "sketchy"; the annotation is the one placed in the passage on purpose.
        assertEquals("수상한", content.localMeaningFor(token("sketchy.", segment = 1, start = 0, end = 8)))
    }

    @Test
    fun aWordNothingLocalKnowsGivesNullSoTheCallerMayAsk() {
        assertNull(content.localMeaningFor(token("doesn't")))
        assertNull(content.localMeaningFor(token("")))
    }
}
