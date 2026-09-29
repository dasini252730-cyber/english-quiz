package com.englishquiz.app.ui.reader

/** One voice the engine offers, reduced to what the assignment needs. */
internal data class VoiceOption(val name: String, val requiresNetwork: Boolean, val quality: Int)

/**
 * What to set on the engine before a speaker's line: a voice name (null = the language's default
 * voice) and a pitch. [fallbackPitch] is the pitch that still tells this speaker apart when the
 * engine refuses the voice and the default one has to stand in.
 */
internal data class VoiceChoice(
    val voiceName: String?,
    val pitch: Float,
    val fallbackPitch: Float = pitch,
)

/**
 * Gives each speaker of a conversation a voice of its own (백로그 020), so Anna and Tom are told
 * apart by ear and not only by the name over the bubble.
 *
 * The engine's voices carry no gender, so it is read from the name where a maker exposes it
 * (Samsung's `SMTf`/`SMTm`, Google's `#female`/`#male`) and one of each is put first, which is
 * where the first two speakers land. Voices whose names say nothing are still distinct voices.
 * When the device has fewer usable voices than speakers, the same voice is reused at a different
 * pitch rather than letting two roles sound identical.
 *
 * Pure Kotlin on purpose: the rule is proved on the JVM and the engine only carries it out.
 */
internal object SpeakerVoices {
    private const val DEFAULT_PITCH = 1f
    private val FALLBACK_PITCHES = floatArrayOf(1f, 0.8f, 1.2f, 0.9f)
    private val FEMALE_MARKERS = Regex("female|smtf|[#_-]f\\d")
    private val MALE_MARKERS = Regex("male|smtm|[#_-]m\\d")

    fun assign(speakers: List<String>, voices: List<VoiceOption>): Map<String, VoiceChoice> {
        val distinct = speakers.distinct()
        // A single narrator is the story mode of 요구사항 13절: it keeps the engine's own voice.
        if (distinct.size <= 1) return distinct.associateWith { VoiceChoice(null, DEFAULT_PITCH) }
        val ordered = orderForContrast(
            voices.filter { !it.requiresNetwork && !it.isGeneric }
                .sortedWith(compareByDescending<VoiceOption> { it.quality }.thenBy { it.name }),
        )
        return distinct.withIndex().associate { (index, speaker) ->
            val fallback = fallbackPitch(index)
            speaker to when {
                ordered.isEmpty() -> VoiceChoice(null, fallback, fallback)
                index < ordered.size -> VoiceChoice(ordered[index].name, DEFAULT_PITCH, fallback)
                else -> VoiceChoice(
                    ordered[index % ordered.size].name,
                    fallbackPitch(index / ordered.size),
                    fallback,
                )
            }
        }
    }

    private fun fallbackPitch(step: Int): Float = FALLBACK_PITCHES[step % FALLBACK_PITCHES.size]

    /**
     * Google lists an `en-US-language` entry that is only an alias for whichever voice is the
     * locale's default, not a voice of its own. Giving it to a speaker would hand that speaker the
     * same sound as another one, so it is never a candidate: with one real voice the second
     * speaker gets that voice at another pitch instead.
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
