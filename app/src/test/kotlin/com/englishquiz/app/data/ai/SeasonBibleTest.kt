package com.englishquiz.app.data.ai

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 백로그 064: the episode prompt hears every person's want, secret and ties, and never loses the last people. */
class SeasonBibleTest {
    @Test
    fun aShortCastIsSentWhole() {
        val bible = bible(
            SeasonCharacter("Mina", SpeakerGender.FEMALE, "owner", "keep the hostel", "owes money", "cousin of Rui"),
            SeasonCharacter("Rui", SpeakerGender.MALE, "driver", "quiet life", "saw it", "cousin of Mina"),
        )

        assertEquals(
            "Mina (owner; wants: keep the hostel; hides: owes money; cousin of Rui); Rui (driver; wants: quiet life; hides: saw it; cousin of Mina)",
            bible.castLine(2_400),
        )
    }

    @Test
    fun aLongCastKeepsEveryPersonInsideTheLimit() {
        val long = "a reason that runs on and on and on for many words ".repeat(8).trim()
        val people = listOf("Ana", "Bo", "Cal", "Dee", "Eli", "Fay").map { SeasonCharacter(it, SpeakerGender.FEMALE, long, long, long, long) }
        val line = bible(*people.toTypedArray()).castLine(2_400)

        assertTrue(line.length <= 2_400)
        people.forEach { assertTrue("${it.name} is missing", line.contains("${it.name} (a reason")) }
        // Each entry is cut at a word and closed, so the model still sees six people, not one fragment.
        assertEquals(6, line.split("); ").size)
        assertTrue(line.endsWith(")"))
    }

    /** 백로그 065: the engine and the plan's two lists survive the round trip through Room's JSON, and an old bible reads back empty. */
    @Test
    fun theEngineAndThePlanListsRoundTripAndAnOldBibleReadsBackEmpty() {
        val designed = bible(SeasonCharacter("Mina", SpeakerGender.FEMALE, "owner", "keep the hostel", "owes money", "cousin of Rui")).copy(
            engine = "C. Social farce",
            whatIsTrue = listOf("Mina sent the guest away herself.", "Rui saw it."),
            readerBelieves = (1..7).map { "After episode $it the reader believes something." },
        )

        val restored = SeasonBible.fromJson(JSONObject(designed.toJson().toString()))

        assertEquals(designed, restored)
        val old = SeasonBible.fromJson(JSONObject("""{"title":"t","setting":"s","conflict":"c","characters":[],"episodes":[],"openQuestions":[]}"""))
        assertEquals("", old.engine)
        assertEquals(emptyList<String>(), old.whatIsTrue)
        assertEquals(emptyList<String>(), old.readerBelieves)
    }

    /** 백로그 075: the episode call's brief carries the cast as one line, every plan, and the lists at the server's reading size. */
    @Test
    fun anEpisodeBriefSendsTheCastLineEveryPlanAndTheListsAtTheServersSize() {
        val long = "x".repeat(EpisodeRequest.MAX_LIST_ITEM_CHARS + 50)
        val designed = bible(SeasonCharacter("Mina", SpeakerGender.FEMALE, "owner", "keep the hostel", "owes money", "cousin of Rui")).copy(
            engine = "A. Clever mystery",
            episodes = (1..7).map { EpisodePlan(it, "Episode $it", "Plan $it") },
            whatIsTrue = (1..EpisodeRequest.MAX_LIST_ITEMS + 3).map { "$it $long" },
            readerBelieves = (1..7).map { "Believed after $it" },
        )
        val request = EpisodeRequest(Language.ENGLISH, 2, 3, listOf("pull it off"), designed, listOf(3), listOf(PreviousEpisode(2, "E2", "Narrator: Rain.")), CanonState(events = listOf("It rained.")))

        val json = request.toJson()
        assertEquals("episodes", json.getString("action"))
        assertEquals(listOf(3), (0 until json.getJSONArray("episodes").length()).map { json.getJSONArray("episodes").getInt(it) })
        val season = json.getJSONObject("season")
        assertEquals("Mina (owner; wants: keep the hostel; hides: owes money; cousin of Rui)", season.getString("characters"))
        assertEquals(7, season.getJSONArray("plans").length())
        assertEquals("Plan 3", season.getJSONArray("plans").getJSONObject(2).getString("plan"))
        assertEquals(EpisodeRequest.MAX_LIST_ITEMS, season.getJSONArray("whatIsTrue").length())
        assertEquals(EpisodeRequest.MAX_LIST_ITEM_CHARS, season.getJSONArray("whatIsTrue").getString(0).length)
        assertEquals("Narrator: Rain.", json.getJSONArray("previousEpisodes").getJSONObject(0).getString("text"))
        assertEquals("It rained.", json.getJSONObject("canon").getJSONArray("events").getString(0))
        assertEquals(false, json.has("story"))
        // With a story given, only its title and lines travel: the server writes the conversation alone.
        val given = request.copy(story = GivenStory("E3", listOf(ContentSegment("Mina", "Hello.", "pinyin")))).toJson().getJSONObject("story")
        assertEquals("E3", given.getString("title"))
        assertEquals("Hello.", given.getJSONArray("segments").getJSONObject(0).getString("text"))
        assertEquals(false, given.getJSONArray("segments").getJSONObject(0).has("pinyin"))
    }

    private fun bible(vararg characters: SeasonCharacter) = SeasonBible(
        title = "t", setting = "s", conflict = "c", characters = characters.toList(), episodes = emptyList(), openQuestions = emptyList(),
    )
}
