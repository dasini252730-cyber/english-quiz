package com.englishquiz.app.data.ai

import org.json.JSONArray
import org.json.JSONObject

/*
 * The season wire shapes (백로그 063): the bible the server designs once and the canon each episode
 * adds to. JSON codecs live here too, because the same shapes are stored in Room and sent to the
 * Edge Function; the episode call's own shapes are in `EpisodeModels.kt` (백로그 075).
 */

/**
 * One season's design, made once and never replaced. [engine], [whatIsTrue] and [readerBelieves]
 * (백로그 065: the plan rules' engine and two lists, one reader-believes line per episode) are
 * empty in a bible designed before them.
 */
data class SeasonBible(
    val title: String,
    val setting: String,
    val conflict: String,
    val characters: List<SeasonCharacter>,
    val episodes: List<EpisodePlan>,
    val openQuestions: List<String>,
    val engine: String = "",
    val whatIsTrue: List<String> = emptyList(),
    val readerBelieves: List<String> = emptyList(),
) {
    /**
     * The cast as an episode hears of it (백로그 064): the story rules want each person's want,
     * secret and ties, not only a name and a role. The line is kept inside [maxChars] by giving
     * every person an equal share, so a long cast loses words at the end of each entry rather
     * than its last people.
     */
    fun castLine(maxChars: Int): String {
        if (characters.isEmpty()) return ""
        val share = (maxChars - SEPARATOR.length * (characters.size - 1)) / characters.size
        return characters.joinToString(SEPARATOR) { it.castEntry().fitted(share) }
    }

    private fun String.fitted(max: Int): String {
        if (length <= max) return this
        val cut = take(max - 1).trimEnd().substringBeforeLast(' ', missingDelimiterValue = take(max - 1))
        return "$cut)"
    }

    fun toJson(): JSONObject = JSONObject()
        .put("title", title).put("engine", engine).put("setting", setting).put("conflict", conflict)
        .put("characters", JSONArray(characters.map { it.toJson() }))
        .put("whatIsTrue", JSONArray(whatIsTrue)).put("readerBelieves", JSONArray(readerBelieves))
        .put("episodes", JSONArray(episodes.map { JSONObject().put("number", it.number).put("title", it.title).put("plan", it.plan) }))
        .put("openQuestions", JSONArray(openQuestions))

    companion object {
        private const val SEPARATOR = "; "

        fun fromJson(json: JSONObject): SeasonBible = SeasonBible(
            title = json.optString("title"),
            setting = json.optString("setting"),
            conflict = json.optString("conflict"),
            characters = json.optJSONArray("characters").objects().map(SeasonCharacter::fromJson),
            episodes = json.optJSONArray("episodes").objects().map {
                EpisodePlan(it.optInt("number"), it.optString("title"), it.optString("plan"))
            },
            openQuestions = json.optJSONArray("openQuestions").strings(),
            engine = json.optString("engine"),
            whatIsTrue = json.optJSONArray("whatIsTrue").strings(),
            readerBelieves = json.optJSONArray("readerBelieves").strings(),
        )
    }
}

data class SeasonCharacter(
    val name: String,
    val gender: SpeakerGender,
    val role: String,
    val motive: String,
    val secret: String,
    val relationships: String,
) {
    /** "Name (role; wants: ...; hides: ...; ties)": one person as the story prompt hears of them. */
    fun castEntry(): String = "$name ($role; wants: $motive; hides: $secret; $relationships)"

    fun toJson(): JSONObject = JSONObject()
        .put("name", name).put("gender", gender.wireValue).put("role", role)
        .put("motive", motive).put("secret", secret).put("relationships", relationships)

    companion object {
        fun fromJson(json: JSONObject) = SeasonCharacter(
            name = json.optString("name"),
            gender = SpeakerGender.fromWire(json.optString("gender")) ?: SpeakerGender.FEMALE,
            role = json.optString("role"),
            motive = json.optString("motive"),
            secret = json.optString("secret"),
            relationships = json.optString("relationships"),
        )
    }
}

data class EpisodePlan(val number: Int, val title: String, val plan: String)

/**
 * What the season has settled so far. An episode returns its delta in the same shape; [merged]
 * folds it in, keeping the lists short enough to ride in every request.
 */
data class CanonState(
    val events: List<String> = emptyList(),
    val facts: List<String> = emptyList(),
    val relationships: List<String> = emptyList(),
    val openQuestions: List<String> = emptyList(),
    val carryForward: List<String> = emptyList(),
) {
    fun merged(delta: CanonState): CanonState = CanonState(
        events = (events + delta.events).squeezed(),
        facts = (facts + delta.facts).squeezed(),
        relationships = (relationships + delta.relationships).squeezed(),
        // The latest episode's view of what is still open replaces the old one.
        openQuestions = delta.openQuestions.ifEmpty { openQuestions }.squeezed(),
        carryForward = (carryForward + delta.carryForward).squeezed(),
    )

    /** Short lines, the newest kept: five such lists must ride in every request under the server's body limit (32 KB since 백로그 065). */
    private fun List<String>.squeezed(): List<String> =
        map { it.trim().take(MAX_ITEM_CHARS) }.filter { it.isNotEmpty() }.distinct().takeLast(MAX_ITEMS)

    fun toJson(): JSONObject = JSONObject()
        .put("events", JSONArray(events)).put("facts", JSONArray(facts))
        .put("relationships", JSONArray(relationships)).put("openQuestions", JSONArray(openQuestions))
        .put("carryForward", JSONArray(carryForward))

    companion object {
        const val MAX_ITEMS = 8
        const val MAX_ITEM_CHARS = 160

        fun fromJson(json: JSONObject?): CanonState = if (json == null) CanonState() else CanonState(
            events = json.optJSONArray("events").strings(),
            facts = json.optJSONArray("facts").strings(),
            relationships = json.optJSONArray("relationships").strings(),
            openQuestions = json.optJSONArray("openQuestions").strings(),
            carryForward = json.optJSONArray("carryForward").strings(),
        )
    }
}

/** Asks the server to design a new season in [language] (백로그 066). */
data class SeasonRequest(val difficulty: Int, val seasonNumber: Int, val previousTitles: List<String>, val language: Language = Language.DEFAULT)

internal fun JSONArray?.strings(): List<String> =
    this?.let { array -> (0 until array.length()).map { array.optString(it) }.filter { it.isNotBlank() } } ?: emptyList()

internal fun JSONArray?.objects(): List<JSONObject> =
    this?.let { array -> (0 until array.length()).mapNotNull { array.optJSONObject(it) } } ?: emptyList()
