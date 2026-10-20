package com.englishquiz.app.ui.reader

import com.englishquiz.app.data.ai.SpeakerGender

/** One installed voice as [SpeakerVoices] sees it; [locale] is the engine's own tag, kept for the log (백로그 073). */
internal data class VoiceOption(val name: String, val requiresNetwork: Boolean, val quality: Int, val locale: String = "")

/**
 * What to set on the engine before a speaker's line: a voice name (null = the language's default
 * voice) and a pitch. Since 백로그 073 the pitch is always 1: bending a voice's pitch to tell two
 * speakers apart made Mandarin tones, which are pitch, unintelligible, and sounded synthetic in
 * English too. [fallbackPitch] is kept for the engine-refused path and is 1 as well.
 */
internal data class VoiceChoice(
    val voiceName: String?,
    val pitch: Float = 1f,
    val fallbackPitch: Float = pitch,
)

/**
 * Gives each speaker of a passage a voice (백로그 020/050). A speaker whose gender the passage
 * names takes the first voice whose name says that gender; the rest take the remaining voices in
 * order of appearance, a female- and a male-sounding voice first so the first two speakers differ
 * most. Voices whose names say nothing are still distinct voices. When the device has fewer usable
 * voices than speakers, the same voice is reused as it is (백로그 073): a repeated voice beats a
 * bent one. The markers name no language, so zh-CN voices go through the same rule.
 *
 * Pure Kotlin on purpose: the rule is proved on the JVM and the engine only carries it out.
 */
internal object SpeakerVoices {
    private val FEMALE_MARKERS = Regex("female|smtf|[#_-]f\\d")
    private val MALE_MARKERS = Regex("male|smtm|[#_-]m\\d")

    /**
     * [genders] is what the passage says about its speakers (백로그 050). [allowNetwork] admits
     * voices that need a connection (백로그 073, the home toggle); off, only installed voices are
     * candidates, as before.
     */
    fun assign(
        speakers: List<String>,
        voices: List<VoiceOption>,
        genders: Map<String, SpeakerGender> = emptyMap(),
        allowNetwork: Boolean = false,
    ): Map<String, VoiceChoice> {
        val distinct = speakers.distinct()
        // A single narrator is the story mode of 요구사항 13절: it keeps the engine's own voice.
        if (distinct.size <= 1) return distinct.associateWith { VoiceChoice(null) }
        val usable = voices.filter { (allowNetwork || !it.requiresNetwork) && !it.isGeneric }
            .sortedWith(compareByDescending<VoiceOption> { it.quality }.thenBy { it.name })
        val ordered = orderForContrast(usable)
        // Known genders first: a woman takes the best female voice, a man the best male one. The
        // narrator and anyone unnamed take the remaining voices in order of appearance.
        val taken = mutableSetOf<String>()
        val named = distinct.mapNotNull { speaker ->
            val wanted = when (genders[speaker]) {
                SpeakerGender.FEMALE -> Gender.FEMALE
                SpeakerGender.MALE -> Gender.MALE
                else -> return@mapNotNull null
            }
            val voice = usable.firstOrNull { it.gender() == wanted && it.name !in taken } ?: return@mapNotNull null
            taken += voice.name
            speaker to voice
        }.toMap()
        val remaining = ordered.filter { it.name !in taken }
        var next = 0
        return distinct.associateWith { speaker ->
            val chosen = named[speaker]
            when {
                chosen != null -> VoiceChoice(chosen.name)
                remaining.isEmpty() -> VoiceChoice(null)
                // Past the last voice the list wraps: the same voice again, untouched.
                else -> VoiceChoice(remaining[next++ % remaining.size].name)
            }
        }
    }

    /**
     * Google lists an `en-US-language` entry that is only an alias for whichever voice is the
     * locale's default, not a voice of its own. Giving it to a speaker would hand that speaker the
     * same sound as another one, so it is never a candidate.
     */
    private val VoiceOption.isGeneric: Boolean
        get() = name.endsWith("-language", ignoreCase = true)

    /** One apparently-female and one apparently-male voice first, so the first two speakers differ most. */
    private fun orderForContrast(voices: List<VoiceOption>): List<VoiceOption> {
        val female = voices.firstOrNull { it.gender() == Gender.FEMALE }
        val male = voices.firstOrNull { it.gender() == Gender.MALE }
        val lead = listOfNotNull(female, male)
        return lead + voices.filter { it !in lead }
    }

    private enum class Gender { FEMALE, MALE, UNKNOWN }

    /** "female" contains "male", so it is tested first. */
    private fun VoiceOption.gender(): Gender {
        val lower = name.lowercase()
        return when {
            FEMALE_MARKERS.containsMatchIn(lower) -> Gender.FEMALE
            MALE_MARKERS.containsMatchIn(lower) -> Gender.MALE
            else -> Gender.UNKNOWN
        }
    }
}
