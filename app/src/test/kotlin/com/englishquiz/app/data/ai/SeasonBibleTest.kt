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
        assertEquals("Mina (owner); Rui (driver)", bible.peopleLine())
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

    /** 백로그 065: a season context carries the bible's engine and lists to the server, and leaves them out when the bible has none. */
    @Test
    fun aSeasonContextSendsTheListsOnlyWhenItHasThem() {
        val base = SeasonContext("t", "s", "c", "cast", 2, "plan", "previous", CanonState())

        val bare = base.toJson()
        assertEquals(false, bare.has("engine"))
        assertEquals(false, bare.has("whatIsTrue"))
        assertEquals(false, bare.has("readerBelieves"))

        val full = base.copy(engine = "A. Clever mystery", whatIsTrue = listOf("fact"), readerBelieves = listOf("belief")).toJson()
        assertEquals("A. Clever mystery", full.getString("engine"))
        assertEquals("fact", full.getJSONArray("whatIsTrue").getString(0))
        assertEquals("belief", full.getJSONArray("readerBelieves").getString(0))

        // The request carries the lists at the server's reading size, so the body stays inside its limit.
        val long = "x".repeat(SeasonContext.MAX_LIST_ITEM_CHARS + 50)
        val capped = base.copy(whatIsTrue = (1..SeasonContext.MAX_LIST_ITEMS + 3).map { "$it $long" }).toJson().getJSONArray("whatIsTrue")
        assertEquals(SeasonContext.MAX_LIST_ITEMS, capped.length())
        assertEquals(SeasonContext.MAX_LIST_ITEM_CHARS, capped.getString(0).length)
    }

    private fun bible(vararg characters: SeasonCharacter) = SeasonBible(
        title = "t", setting = "s", conflict = "c", characters = characters.toList(), episodes = emptyList(), openQuestions = emptyList(),
    )
}
