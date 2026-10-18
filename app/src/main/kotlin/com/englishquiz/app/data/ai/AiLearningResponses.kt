package com.englishquiz.app.data.ai

import com.englishquiz.app.domain.season.SeasonPolicy
import org.json.JSONArray
import org.json.JSONObject

/**
 * Turns an Edge Function reply into the content model the Reader uses (백로그 006).
 *
 * This is deliberately separate from [AiLearningClient]: parsing is a pure function of the JSON,
 * so it can be proved against payloads captured from the live function without opening a socket.
 * Anything the app cannot safely display is rejected as `invalid_response` rather than handed on
 * half-built — a wrong expression offset would highlight the wrong words in the Reader.
 */
internal fun parseContentResponse(
    response: JSONObject,
    expectedMode: ContentMode,
    /**
     * The language the passage was asked for (백로그 066). The server does not echo it, so a live
     * reply is labelled by its request and a stored row by its own `language` column; the JSON's
     * key is only the fallback for a row stored by this build before either was known.
     */
    expectedLanguage: Language? = null,
): LearningContent {
    val data = response.optJSONObject("data") ?: invalidResponse()
    val mode = when (data.optString("mode")) {
        ContentMode.CONVERSATION.wireValue -> ContentMode.CONVERSATION
        ContentMode.STORY.wireValue -> ContentMode.STORY
        else -> invalidResponse()
    }
    if (mode != expectedMode) invalidResponse()
    val segments = data.optJSONArray("segments")?.mapObjects { item ->
        ContentSegment(item.requiredString("speaker"), item.requiredString("text"), item.pinyin())
    } ?: invalidResponse()
    val expressions = data.optJSONArray("expressions")?.mapObjects { item ->
        val segmentIndex = item.requiredInt("segmentIndex")
        val text = item.requiredString("text")
        val segment = segments.getOrNull(segmentIndex) ?: invalidResponse()
        val foldedSegment = segment.text.foldTypography()
        val foldedText = text.foldTypography()
        // Lowercasing can change a string's length (U+0130 is the reachable case), and an offset
        // measured in folded space would then underline the wrong words. The Edge Function drops
        // such an expression for the same reason, so this rejects rather than guesses.
        if (foldedSegment.length != segment.text.length || foldedText.length != text.length) {
            invalidResponse()
        }
        val start = foldedSegment.indexOf(foldedText)
        if (start < 0) invalidResponse()
        ContentExpression(
            text = text,
            meaning = item.requiredString("meaning"),
            segmentIndex = segmentIndex,
            startIndex = start,
            endIndex = start + text.length,
            shortMeaning = item.optString("shortMeaning").trim().takeIf { it.length <= MAX_SHORT_MEANING_CHARS } ?: "",
            kind = ExpressionKinds.normalize(item.optString("kind")),
            pinyin = item.pinyin(),
        )
    } ?: invalidResponse()
    if (segments.isEmpty() || expressions.isEmpty()) invalidResponse()
    return LearningContent(
        title = data.requiredString("title"),
        mode = mode,
        // A reply or a stored row from before 백로그 066 names no language and is English.
        language = expectedLanguage ?: Language.fromWire(data.optString("language")),
        segments = segments,
        expressions = expressions,
        glossary = data.glossary(),
        comprehension = data.comprehension(),
        speakers = data.speakers(segments),
        synopsis = data.optString("synopsis").trim().take(MAX_SYNOPSIS_CHARS),
        canon = data.optJSONObject("canon")?.let(CanonState::fromJson),
    )
}

/** A season bible (백로그 063): the server has already checked it; here only the shape is read. */
internal fun parseSeasonResponse(response: JSONObject): SeasonBible {
    val data = response.optJSONObject("data") ?: invalidResponse()
    val bible = SeasonBible.fromJson(data)
    if (bible.title.isBlank() || bible.episodes.size != SeasonPolicy.EPISODES_PER_SEASON || bible.characters.isEmpty()) invalidResponse()
    // 백로그 065: a bible is stored once and never replaced, so one from a server deployed before
    // the engine and the reader-believes lines is refused rather than kept for seven episodes.
    if (bible.engine.isBlank() || bible.readerBelieves.size != SeasonPolicy.EPISODES_PER_SEASON) invalidResponse()
    return bible
}

private const val MAX_SYNOPSIS_CHARS = 600

/** Speaker genders (백로그 050): a courtesy like the glossary; an entry naming nobody is skipped. */
private fun JSONObject.speakers(segments: List<ContentSegment>): Map<String, SpeakerGender> {
    val array = optJSONArray("speakers") ?: return emptyMap()
    val known = segments.map { it.speaker.trim() }.toSet()
    return buildMap {
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val name = item.optString("name").trim()
            val gender = SpeakerGender.fromWire(item.optString("gender")) ?: continue
            if (name in known && name !in this) put(name, gender)
        }
    }
}

/**
 * Comprehension questions (백로그 042) follow the glossary's rule: an entry the app cannot ask
 * honestly — a blank question, fewer than two options, an answer outside them — is skipped and
 * never fails the passage. The key is absent in rows stored before 042.
 */
private fun JSONObject.comprehension(): List<ComprehensionQuestion> {
    val array = optJSONArray("comprehension") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            if (size >= MAX_COMPREHENSION_QUESTIONS) break
            val item = array.optJSONObject(index) ?: continue
            val question = item.optString("question").trim()
            val options = item.optJSONArray("options")?.let { raw -> List(raw.length()) { raw.optString(it).trim() } } ?: continue
            val answerIndex = item.optInt("answerIndex", -1)
            // A blank option is not dropped but disqualifies the entry: dropping it would move
            // answerIndex onto a wrong option.
            if (question.isEmpty() || options.size < 2 || options.any { it.isEmpty() } || answerIndex !in options.indices) continue
            add(ComprehensionQuestion(question, options, answerIndex, item.optString("explanation").trim()))
        }
    }
}

private const val MAX_COMPREHENSION_QUESTIONS = 3

/** Mirrors the Edge Function's bound; a longer gloss is kept out of the options, not rejected. */
private const val MAX_SHORT_MEANING_CHARS = 20

/**
 * The glossary (백로그 024) is a courtesy, never a reason to reject a passage: an entry that is not
 * an object, is blank, or is implausibly long is skipped, exactly as the Edge Function skips it.
 * The key is absent both in rows stored before 024 and in a reply the model sent without one.
 */
private fun JSONObject.glossary(): List<GlossaryEntry> {
    val array = optJSONArray("glossary") ?: return emptyList()
    return buildList {
        for (index in 0 until array.length()) {
            if (size >= MAX_GLOSSARY_ENTRIES) break
            val item = array.optJSONObject(index) ?: continue
            val word = item.optString("word").trim()
            val meaning = item.optString("meaning").trim()
            if (word.isEmpty() || meaning.isEmpty() || word.length > 80 || meaning.length > 300) continue
            add(GlossaryEntry(word, meaning, item.pinyin()))
        }
    }
}

private const val MAX_GLOSSARY_ENTRIES = 40

internal fun parseMeaningResponse(response: JSONObject): ContextualMeaning {
    val data = response.optJSONObject("data") ?: invalidResponse()
    return ContextualMeaning(data.requiredString("expression"), data.requiredString("meaning"), data.pinyin())
}

/** Pinyin (백로그 066) is a courtesy like the glossary: absent, blank or overlong reads as none. */
private fun JSONObject.pinyin(): String = optString("pinyin").trim().takeIf { it.length <= MAX_PINYIN_CHARS } ?: ""

private const val MAX_PINYIN_CHARS = 400

/**
 * Folds the typographic variants a model mixes into prose onto their ASCII equivalents, so an
 * annotation written with a straight apostrophe still matches a segment that used a curly one.
 *
 * This must stay identical to `foldTypography` in `supabase/functions/ai-learning/index.ts`: the
 * function has already accepted the phrase against its own folding, so a stricter rule here would
 * reject content the server considers valid. Every mapping is one character to one character, so
 * the offsets stay the Reader's true highlight coordinates.
 */
internal fun String.foldTypography(): String = buildString(length) {
    this@foldTypography.forEach { character ->
        append(
            when (character) {
                '\u2018', '\u2019', '\u02BC', '\u00B4' -> '\''
                '\u201C', '\u201D' -> '"'
                '\u00A0', '\u2007', '\u2009', '\u202F' -> ' '
                '\u2010', '\u2011', '\u2013', '\u2014' -> '-'
                else -> character
            },
        )
    }
}.lowercase()

private fun invalidResponse(): Nothing = throw AiLearningException("invalid_response")

private fun JSONObject.requiredString(name: String): String {
    if (!has(name) || isNull(name) || get(name) !is String) invalidResponse()
    return getString(name).takeIf { it.isNotBlank() } ?: invalidResponse()
}

private fun JSONObject.requiredInt(name: String): Int {
    if (!has(name) || isNull(name) || get(name) !is Number) invalidResponse()
    val value = get(name) as Number
    if (value.toDouble() % 1.0 != 0.0 || value.toLong() !in Int.MIN_VALUE..Int.MAX_VALUE) invalidResponse()
    return value.toInt()
}

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> = buildList {
    for (index in 0 until length()) add(transform(getJSONObject(index)))
}

