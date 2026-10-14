package com.englishquiz.app.data.ai

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

    private fun bible(vararg characters: SeasonCharacter) = SeasonBible(
        title = "t", setting = "s", conflict = "c", characters = characters.toList(), episodes = emptyList(), openQuestions = emptyList(),
    )
}
