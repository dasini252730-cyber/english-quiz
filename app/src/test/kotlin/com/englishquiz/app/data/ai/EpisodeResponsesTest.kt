package com.englishquiz.app.data.ai

import com.englishquiz.app.data.local.SeasonEpisodeEntity
import com.englishquiz.app.data.season.EpisodeBrief
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

/** 백로그 075: an episode call's reply is read passage by passage, and the earlier episodes are sent whole inside the budget. */
class EpisodeResponsesTest {
    private fun passage(title: String, mode: String, speaker: String = "Mina") = """
        {"title":"$title","mode":"$mode","synopsis":"Two sentences.",
         "segments":[{"speaker":"$speaker","text":"We can pull it off together."}],
         "expressions":[{"text":"pull it off","meaning":"해내다","segmentIndex":0}]}
    """.trimIndent()

    @Test
    fun aStoryAndItsConversationAreReadAndADroppedConversationIsNullWithItsCheck() {
        val both = JSONObject("""{"data":{"episodes":[{"number":3,"story":${passage("E3", "story", "Narrator")},"conversation":${passage("E3 talk", "conversation")}}]}}""")
        val parsed = parseEpisodeResponse(both, Language.ENGLISH)
        assertEquals(3, parsed.number)
        assertEquals("E3", parsed.story?.title)
        assertEquals(ContentMode.STORY, parsed.story?.mode)
        assertEquals("E3 talk", parsed.conversation?.title)
        assertEquals(ContentMode.CONVERSATION, parsed.conversation?.mode)
        assertEquals(Language.ENGLISH, parsed.conversation?.language)
        assertEquals("", parsed.conversationCheck)

        val dropped = JSONObject("""{"data":{"episodes":[{"number":3,"story":${passage("E3", "story")},"conversation":null,"conversationCheck":"conversation_speakers_not_in_story"}]}}""")
        val kept = parseEpisodeResponse(dropped, Language.ENGLISH)
        assertEquals("E3", kept.story?.title)
        assertNull(kept.conversation)
        assertEquals("conversation_speakers_not_in_story", kept.conversationCheck)

        // A conversation-only reply has no story; a reply with neither, or with no episode, is refused.
        val only = parseEpisodeResponse(JSONObject("""{"data":{"episodes":[{"number":2,"conversation":${passage("talk", "conversation")}}]}}"""), Language.CHINESE)
        assertNull(only.story)
        assertEquals(Language.CHINESE, only.conversation?.language)
        assertThrows(AiLearningException::class.java) { parseEpisodeResponse(JSONObject("""{"data":{"episodes":[]}}"""), Language.ENGLISH) }
        assertThrows(AiLearningException::class.java) { parseEpisodeResponse(JSONObject("""{"data":{"episodes":[{"number":1}]}}"""), Language.ENGLISH) }
        // A passage the Reader could not show fails the reply, as it fails a single passage.
        assertThrows(AiLearningException::class.java) {
            parseEpisodeResponse(JSONObject("""{"data":{"episodes":[{"number":1,"story":{"title":"x","mode":"story","segments":[],"expressions":[]}}]}}"""), Language.ENGLISH)
        }
    }

    @Test
    fun theEarlierEpisodesTravelWholeNewestFirstInsideTheTextBudget() {
        fun episode(number: Int, lines: Int) = SeasonEpisodeEntity(
            id = number.toLong(), seasonId = 1, number = number, synopsis = "", canonJson = "{}", difficulty = 2, generatedAtEpochMillis = 0, readDate = null,
            contentJson = JSONObject().put(
                "data",
                JSONObject(passage("E$number", "story")).put(
                    "segments",
                    org.json.JSONArray((1..lines).map { JSONObject().put("speaker", "Narrator").put("text", "We can pull it off together. Line $it of episode $number.") }),
                ),
            ).toString(),
        )
        // Short episodes all fit, oldest first, each as "Speaker: text" lines.
        val short = EpisodeBrief.previous(listOf(episode(2, 2), episode(1, 1), episode(3, 1)), number = 3)
        assertEquals(listOf(1, 2), short.map { it.number })
        assertEquals("E1", short[0].title)
        assertEquals("Narrator: We can pull it off together. Line 1 of episode 1.", short[0].text)
        assertEquals(2, short[1].text.lines().size)
        // Long episodes: the newest are kept whole first, and an older one that would overflow is left out.
        val long = EpisodeBrief.previous((1..6).map { episode(it, 120) }, number = 7)
        val used = long.sumOf { it.text.length }
        assertEquals(true, used <= EpisodeRequest.MAX_PREVIOUS_TEXT_CHARS)
        assertEquals(true, long.isNotEmpty() && long.size < 6)
        assertEquals(6, long.last().number)
        assertEquals(long.map { it.number }.sorted(), long.map { it.number })
    }
}
