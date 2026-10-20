package com.englishquiz.app.ui.reader

import com.englishquiz.app.data.ai.SpeakerGender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpeakerVoicesTest {
    private fun voice(name: String, network: Boolean = false, quality: Int = 300) =
        VoiceOption(name, network, quality)

    @Test
    fun twoSpeakersGetTwoDifferentVoices() {
        val plan = SpeakerVoices.assign(
            listOf("Anna", "Tom", "Anna"),
            listOf(voice("en-us-x-tpf-local"), voice("en-us-x-tpd-local")),
        )

        assertEquals(setOf("Anna", "Tom"), plan.keys)
        assertNotEquals(plan.getValue("Anna").voiceName, plan.getValue("Tom").voiceName)
        assertEquals(1f, plan.getValue("Anna").pitch)
        assertEquals(1f, plan.getValue("Tom").pitch)
    }

    @Test
    fun thePassagesOwnGendersBeatTheOrderOfAppearance() {
        // 백로그 050: Tom speaks first but is a man; the narrator takes what is left.
        val plan = SpeakerVoices.assign(
            listOf("Tom", "Narrator", "Anna"),
            listOf(voice("en-US-SMTx01"), voice("en-US-SMTm00"), voice("en-US-SMTf00")),
            mapOf("Tom" to SpeakerGender.MALE, "Anna" to SpeakerGender.FEMALE, "Narrator" to SpeakerGender.NARRATOR),
        )

        assertEquals("en-US-SMTm00", plan.getValue("Tom").voiceName)
        assertEquals("en-US-SMTf00", plan.getValue("Anna").voiceName)
        assertEquals("en-US-SMTx01", plan.getValue("Narrator").voiceName)
    }

    @Test
    fun aFemaleAndAMaleVoiceLeadWhenTheNamesSaySo() {
        // Samsung names its voices SMTf00 / SMTm00; Google's older ones carry #female_1 / #male_1.
        val plan = SpeakerVoices.assign(
            listOf("Anna", "Tom"),
            listOf(voice("en-US-SMTx01"), voice("en-US-SMTm00"), voice("en-US-SMTf00")),
        )

        assertEquals("en-US-SMTf00", plan.getValue("Anna").voiceName)
        assertEquals("en-US-SMTm00", plan.getValue("Tom").voiceName)
    }

    @Test
    fun theWordFemaleIsNotMistakenForMale() {
        val plan = SpeakerVoices.assign(
            listOf("Anna", "Tom"),
            listOf(voice("en-us-x-sfg#female_1-local"), voice("en-us-x-sfg#male_1-local")),
        )

        assertEquals("en-us-x-sfg#female_1-local", plan.getValue("Anna").voiceName)
        assertEquals("en-us-x-sfg#male_1-local", plan.getValue("Tom").voiceName)
    }

    @Test
    fun oneVoiceIsReusedUnbentWhenThereAreMoreSpeakersThanVoices() {
        // 백로그 073: no pitch tricks in any language — a repeated voice beats a bent one.
        val plan = SpeakerVoices.assign(listOf("Anna", "Tom", "Mia"), listOf(voice("only")))

        assertEquals(listOf("only", "only", "only"), listOf("Anna", "Tom", "Mia").map { plan.getValue(it).voiceName })
        assertEquals(setOf(1f), plan.values.map { it.pitch }.toSet())
        assertEquals(setOf(1f), plan.values.map { it.fallbackPitch }.toSet())
    }

    @Test
    fun noVoicesAtAllLeavesEveryoneOnTheDefaultAtTheNaturalPitch() {
        val plan = SpeakerVoices.assign(listOf("Anna", "Tom"), emptyList())

        assertNull(plan.getValue("Anna").voiceName)
        assertNull(plan.getValue("Tom").voiceName)
        assertEquals(1f, plan.getValue("Anna").pitch)
        assertEquals(1f, plan.getValue("Tom").pitch)
    }

    @Test
    fun aSingleNarratorKeepsTheEnginesOwnVoice() {
        val plan = SpeakerVoices.assign(listOf("Narrator", "Narrator"), listOf(voice("a"), voice("b")))

        assertEquals(mapOf("Narrator" to VoiceChoice(null, 1f)), plan)
    }

    @Test
    fun voicesThatNeedTheNetworkAreChosenOnlyWhenAllowed() {
        val voices = listOf(voice("cloud-a", network = true), voice("cloud-b", network = true), voice("local"))

        val offline = SpeakerVoices.assign(listOf("Anna", "Tom"), voices)
        assertEquals("local", offline.getValue("Anna").voiceName)
        assertEquals("local", offline.getValue("Tom").voiceName)

        // 백로그 073: the home toggle admits them, and two speakers then get two voices.
        val online = SpeakerVoices.assign(listOf("Anna", "Tom"), voices, allowNetwork = true)
        assertEquals("cloud-a", online.getValue("Anna").voiceName)
        assertEquals("cloud-b", online.getValue("Tom").voiceName)
    }

    @Test
    fun theLocalesGenericEntryIsNeverAVoiceOfItsOwn() {
        // Seen on the emulator's Google engine: "en-US-language" sorted first and took a speaker,
        // though it is only an alias for whichever concrete voice is the default.
        val withTwoRealVoices = SpeakerVoices.assign(
            listOf("Sarah", "James"),
            listOf(voice("en-US-language"), voice("en-us-x-iob-local"), voice("en-us-x-tpf-local")),
        )
        assertEquals("en-us-x-iob-local", withTwoRealVoices.getValue("Sarah").voiceName)
        assertEquals("en-us-x-tpf-local", withTwoRealVoices.getValue("James").voiceName)

        // With one real voice the alias must not be handed out as a second voice: it is the
        // same sound under another name. The real voice is reused instead.
        val withOneRealVoice = SpeakerVoices.assign(
            listOf("Sarah", "James"),
            listOf(voice("en-US-language"), voice("en-us-x-iob-local")),
        )
        assertEquals("en-us-x-iob-local", withOneRealVoice.getValue("Sarah").voiceName)
        assertEquals("en-us-x-iob-local", withOneRealVoice.getValue("James").voiceName)
    }

    @Test
    fun theSameInputAlwaysGivesTheSamePlan() {
        val voices = listOf(voice("b", quality = 200), voice("a", quality = 400), voice("c", quality = 400))

        val first = SpeakerVoices.assign(listOf("Anna", "Tom", "Mia"), voices)
        val second = SpeakerVoices.assign(listOf("Anna", "Tom", "Mia"), voices)

        assertEquals(first, second)
        // Higher quality first, then by name: a, c, b.
        assertEquals(listOf("a", "c", "b"), listOf("Anna", "Tom", "Mia").map { first.getValue(it).voiceName })
    }
}
