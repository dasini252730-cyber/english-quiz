package com.englishquiz.app.data.ai

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Proves 백로그 006's first acceptance criterion — both modes' replies become a parseable content
 * model — against payloads captured from the deployed Edge Function on 2026-09-23, not hand-written
 * fixtures. Hand-written JSON only proves the parser agrees with itself; these files are what
 * `claude-sonnet-5` actually returned through the function's own validation.
 */
class AiLearningResponsesTest {
    private val context = InstrumentationRegistry.getInstrumentation().context

    private fun payload(name: String): JSONObject =
        JSONObject(context.assets.open("ai/$name").bufferedReader().use { it.readText() })

    @Test
    fun theLiveConversationReplyBecomesAReadableContentModel() {
        val content = parseContentResponse(payload("conversation-live.json"), ContentMode.CONVERSATION)

        assertEquals(ContentMode.CONVERSATION, content.mode)
        assertTrue(content.title.isNotBlank())
        // A conversation carries more than one speaker; that is what the Reader lays out as a dialogue.
        assertTrue(content.segments.map { it.speaker }.toSet().size > 1)
        assertExpressionOffsetsLandOnTheirOwnWords(content)
    }

    @Test
    fun theLiveStoryReplyBecomesAReadableContentModel() {
        val content = parseContentResponse(payload("story-live.json"), ContentMode.STORY)

        assertEquals(ContentMode.STORY, content.mode)
        assertTrue(content.title.isNotBlank())
        assertExpressionOffsetsLandOnTheirOwnWords(content)
    }

    @Test
    fun theRequestedReviewExpressionsComeBackAnnotated() {
        // The captured conversation was generated for reviewExpressions = [pull it off, sketchy].
        val content = parseContentResponse(payload("conversation-live.json"), ContentMode.CONVERSATION)

        val annotated = content.expressions.map { it.text.lowercase() }.toSet()
        listOf("pull it off", "sketchy").forEach {
            assertTrue("복습 표현 '$it' 이 주석되지 않았다", it in annotated)
        }
        // 요구사항 10절: a session mixes review expressions with at least one new one.
        assertTrue(annotated.any { it !in setOf("pull it off", "sketchy") })
    }

    @Test
    fun aReplyForTheOtherModeIsRejectedInsteadOfShownAsTheRequestedOne() {
        assertThrows(AiLearningException::class.java) {
            parseContentResponse(payload("story-live.json"), ContentMode.CONVERSATION)
        }
    }

    @Test
    fun aPhraseWrittenWithTypographicPunctuationStillMatchesItsSegment() {
        // The Edge Function folds curly punctuation before it accepts a phrase, so this parser has
        // to fold it the same way — a stricter rule here would reject content the server passed.
        val body = """
            {"data":{"title":"Long Shot","mode":"story","segments":[
              {"speaker":"Narrator","text":"It’s a long shot – but she tried."}],
             "expressions":[
              {"text":"It's a long shot","meaning":"가능성이 낮다","segmentIndex":0}]}}
        """.trimIndent()

        val content = parseContentResponse(JSONObject(body), ContentMode.STORY)

        val expression = content.expressions.single()
        assertEquals(0, expression.startIndex)
        // The offsets index the original text, curly punctuation and all.
        assertEquals(
            "It’s a long shot",
            content.segments[0].text.substring(expression.startIndex, expression.endIndex),
        )
    }

    @Test
    fun anExpressionThatIsNotInItsSegmentIsRejected() {
        // Trusting a bad offset would highlight the wrong words in the Reader, so it must throw.
        val broken = payload("story-live.json")
        broken.getJSONObject("data").getJSONArray("expressions").getJSONObject(0)
            .put("text", "이 문장에 없는 표현")

        assertThrows(AiLearningException::class.java) {
            parseContentResponse(broken, ContentMode.STORY)
        }
    }

    @Test
    fun theGlossarySurvivesTheStoreAndRestoreRoundTripAndBadEntriesAreSkipped() {
        // 백로그 024: a stored passage that lost its glossary would send every word tap back to a
        // paid call, and one bad glossary entry must not cost the whole passage.
        val body = """
            {"data":{"title":"A cafe","mode":"conversation","segments":[
              {"speaker":"Emma","text":"That sounds sketchy."}],
             "expressions":[{"text":"sketchy","meaning":"수상한","segmentIndex":0}],
             "glossary":[{"word":"sounds","meaning":"~처럼 들리다"},{"word":"","meaning":"빈"},
                         {"word":"blank","meaning":"  "},"not an object",{"word":"That","meaning":"저것"}]}}
        """.trimIndent()

        val parsed = parseContentResponse(JSONObject(body), ContentMode.CONVERSATION)
        val restored = parseContentResponse(parsed.toResponseJson(), ContentMode.CONVERSATION)

        assertEquals(listOf(GlossaryEntry("sounds", "~처럼 들리다"), GlossaryEntry("That", "저것")), parsed.glossary)
        assertEquals(parsed, restored)
    }

    @Test
    fun comprehensionQuestionsSurviveTheRoundTripAndBadOnesAreSkipped() {
        // 백로그 042: the questions ride with the passage; one bad entry never costs the passage.
        val body = """
            {"data":{"title":"A cafe","mode":"conversation","segments":[
              {"speaker":"Emma","text":"That sounds sketchy."}],
             "expressions":[{"text":"sketchy","meaning":"수상한","segmentIndex":0}],
             "comprehension":[
               {"question":"Emma는 왜 그렇게 말했나요?","options":["의심스러워서","배가 고파서","늦어서","기뻐서"],"answerIndex":0,"explanation":"수상하다고 했다."},
               {"question":"답이 범위 밖","options":["a","b"],"answerIndex":5,"explanation":""},
               {"question":"","options":["a","b"],"answerIndex":0,"explanation":""},
               {"question":"선택지 하나","options":["a"],"answerIndex":0,"explanation":""},
               {"question":"빈 선택지가 있으면 통째로 버림","options":["","맞음","틀림"],"answerIndex":1,"explanation":""},
               "not an object"]}}
        """.trimIndent()

        val parsed = parseContentResponse(JSONObject(body), ContentMode.CONVERSATION)
        val restored = parseContentResponse(parsed.toResponseJson(), ContentMode.CONVERSATION)

        assertEquals(
            listOf(ComprehensionQuestion("Emma는 왜 그렇게 말했나요?", listOf("의심스러워서", "배가 고파서", "늦어서", "기뻐서"), 0, "수상하다고 했다.")),
            parsed.comprehension,
        )
        assertEquals(parsed, restored)
    }

    @Test
    fun theSynopsisSurvivesTheRoundTripAndBecomesTheNextPassagesSummary() {
        // 백로그 054: what tomorrow's story and today's conversation hear about this passage.
        val body = """
            {"data":{"title":"The Night Train","mode":"story","synopsis":"  Mina shared a cabin with a stranger. He lied about his stop.  ",
             "segments":[{"speaker":"Narrator","text":"That sounds sketchy."}],
             "expressions":[{"text":"sketchy","meaning":"수상한","segmentIndex":0}]}}
        """.trimIndent()

        val parsed = parseContentResponse(JSONObject(body), ContentMode.STORY)
        val restored = parseContentResponse(parsed.toResponseJson(), ContentMode.STORY)

        assertEquals("Mina shared a cabin with a stranger. He lied about his stop.", parsed.synopsis)
        assertEquals(parsed, restored)
        assertEquals(PassageSummary(ContentMode.STORY, "The Night Train", parsed.synopsis), parsed.summary())
        assertEquals(null, parsed.copy(synopsis = "").summary())
    }

    @Test
    fun speakerGendersRideAlongAndNameOnlyWhoActuallySpeaks() {
        // 백로그 050: "Nobody" is not in the passage and a made-up gender is skipped.
        val body = """
            {"data":{"title":"A cafe","mode":"conversation","segments":[
              {"speaker":"Emma","text":"That sounds sketchy."},{"speaker":"Tom","text":"Does it?"}],
             "expressions":[{"text":"sketchy","meaning":"수상한","segmentIndex":0}],
             "speakers":[{"name":"Emma","gender":"female"},{"name":"Tom","gender":"male"},
                         {"name":"Nobody","gender":"male"},{"name":"Emma","gender":"robot"}]}}
        """.trimIndent()

        val parsed = parseContentResponse(JSONObject(body), ContentMode.CONVERSATION)
        val restored = parseContentResponse(parsed.toResponseJson(), ContentMode.CONVERSATION)

        assertEquals(mapOf("Emma" to SpeakerGender.FEMALE, "Tom" to SpeakerGender.MALE), parsed.speakers)
        assertEquals(parsed, restored)
    }

    @Test
    fun aShortMeaningRidesWithItsPhraseAndAnOverlongOneIsLeftOut() {
        // 백로그 046: a gloss short enough to be an option is kept; a longer one is not an option.
        val body = """
            {"data":{"title":"A cafe","mode":"conversation","segments":[
              {"speaker":"Emma","text":"That sounds sketchy, so hang out later."}],
             "expressions":[{"text":"sketchy","meaning":"수상하다는 뜻이에요.","shortMeaning":"수상한","segmentIndex":0},
                            {"text":"hang out","meaning":"어울려 놀다","shortMeaning":"이건 선택지로 쓰기에는 너무 긴 뜻풀이예요 정말로","segmentIndex":0}]}}
        """.trimIndent()

        val parsed = parseContentResponse(JSONObject(body), ContentMode.CONVERSATION)
        val restored = parseContentResponse(parsed.toResponseJson(), ContentMode.CONVERSATION)

        assertEquals("수상한", parsed.expressions[0].shortMeaning)
        assertEquals("", parsed.expressions[1].shortMeaning)
        assertEquals(parsed, restored)
    }

    @Test
    fun aPassageStoredBeforeTheGlossaryExistedStillReads() {
        // Rows written by 백로그 021 have no "glossary" key at all.
        val body = """
            {"data":{"title":"A cafe","mode":"story","segments":[
              {"speaker":"Narrator","text":"That sounds sketchy."}],
             "expressions":[{"text":"sketchy","meaning":"수상한","segmentIndex":0}]}}
        """.trimIndent()

        val parsed = parseContentResponse(JSONObject(body), ContentMode.STORY)

        assertEquals(emptyList<GlossaryEntry>(), parsed.glossary)
        assertEquals(emptyList<ComprehensionQuestion>(), parsed.comprehension)
    }

    @Test
    fun aReplyWithoutADataObjectIsRejected() {
        listOf("""{"error":{"code":"provider_busy"}}""", "{}").forEach { body ->
            assertThrows(AiLearningException::class.java) {
                parseContentResponse(JSONObject(body), ContentMode.STORY)
            }
            assertThrows(AiLearningException::class.java) {
                parseMeaningResponse(JSONObject(body))
            }
        }
    }

    @Test
    fun theMeaningReplyBecomesAContextualMeaning() {
        val body = """{"data":{"expression":"pull it off","meaning":"어려운 일을 해내다"}}"""

        val meaning = parseMeaningResponse(JSONObject(body))

        assertEquals("pull it off", meaning.expression)
        assertEquals("어려운 일을 해내다", meaning.meaning)
    }

    @Test
    fun everyFoldedCodePointMatchesTheEdgeFunctionsOwnTable() {
        // Kept in step with `foldTypography` in supabase/functions/ai-learning/index.ts. The
        // mappings are written as escapes on both sides precisely so this table can check them:
        // a literal that an editor normalises would otherwise diverge with no test failing.
        val mappings = mapOf(
            '\u2018' to '\'', '\u2019' to '\'', '\u02BC' to '\'', '\u00B4' to '\'',
            '\u201C' to '"', '\u201D' to '"',
            '\u00A0' to ' ', '\u2007' to ' ', '\u2009' to ' ', '\u202F' to ' ',
            '\u2010' to '-', '\u2011' to '-', '\u2013' to '-', '\u2014' to '-',
        )

        mappings.forEach { (original, folded) ->
            assertEquals(
                "U+%04X 이 접히지 않았다".format(original.code),
                folded.toString(),
                original.toString().foldTypography(),
            )
        }
        // Folding must never change a string's length, or the offsets stop being the Reader's
        // highlight coordinates.
        val all = mappings.keys.joinToString("")
        assertEquals(all.length, all.foldTypography().length)
    }

    @Test
    fun aSegmentWhoseFoldingWouldShiftTheOffsetsIsRejected() {
        // U+0130 lowercases to two code points, so an offset measured in folded space would
        // underline the wrong words. The Edge Function drops such an expression; this rejects.
        val body = """
            {"data":{"title":"Istanbul","mode":"story","segments":[
              {"speaker":"Narrator","text":"\u0130stanbul was quiet."}],
             "expressions":[
              {"text":"was quiet","meaning":"조용했다","segmentIndex":0}]}}
        """.trimIndent()

        assertThrows(AiLearningException::class.java) {
            parseContentResponse(JSONObject(body), ContentMode.STORY)
        }
    }

    /** Every phrase must sit at the offsets the parser computed, or the Reader underlines noise. */
    private fun assertExpressionOffsetsLandOnTheirOwnWords(content: LearningContent) {
        assertTrue(content.expressions.isNotEmpty())
        content.expressions.forEach { expression ->
            val segment = content.segments[expression.segmentIndex]
            assertEquals(
                expression.text.lowercase(),
                segment.text.substring(expression.startIndex, expression.endIndex).lowercase(),
            )
            assertTrue(expression.meaning.isNotBlank())
        }
    }
}
