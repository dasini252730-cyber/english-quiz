package com.englishquiz.app.data.ai

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
internal fun parseContentResponse(response: JSONObject, expectedMode: ContentMode): LearningContent {
    val data = response.optJSONObject("data") ?: invalidResponse()
    val mode = when (data.optString("mode")) {
        ContentMode.CONVERSATION.wireValue -> ContentMode.CONVERSATION
        ContentMode.STORY.wireValue -> ContentMode.STORY
        else -> invalidResponse()
    }
    if (mode != expectedMode) invalidResponse()
    val segments = data.optJSONArray("segments")?.mapObjects { item ->
        ContentSegment(item.requiredString("speaker"), item.requiredString("text"))
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
        ContentExpression(text, item.requiredString("meaning"), segmentIndex, start, start + text.length)
    } ?: invalidResponse()
    if (segments.isEmpty() || expressions.isEmpty()) invalidResponse()
    return LearningContent(data.requiredString("title"), mode, segments, expressions, data.glossary())
}

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
            add(GlossaryEntry(word, meaning))
        }
    }
}

private const val MAX_GLOSSARY_ENTRIES = 40

internal fun parseMeaningResponse(response: JSONObject): ContextualMeaning {
    val data = response.optJSONObject("data") ?: invalidResponse()
    return ContextualMeaning(data.requiredString("expression"), data.requiredString("meaning"))
}

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

/**
 * Writes [content] back out in exactly the shape [parseContentResponse] reads.
 *
 * A saved session is restored by handing this JSON straight back to the parser, so the round trip
 * goes through the same validation as a live reply instead of a second, separately-drifting
 * decoder. Expression offsets are recomputed by the parser from the segment text.
 */
internal fun LearningContent.toResponseJson(): JSONObject {
    val segmentArray = JSONArray()
    segments.forEach { segment ->
        segmentArray.put(JSONObject().put("speaker", segment.speaker).put("text", segment.text))
    }
    val expressionArray = JSONArray()
    expressions.forEach { expression ->
        expressionArray.put(
            JSONObject()
                .put("text", expression.text)
                .put("meaning", expression.meaning)
                .put("segmentIndex", expression.segmentIndex),
        )
    }
    val glossaryArray = JSONArray()
    glossary.forEach { entry ->
        glossaryArray.put(JSONObject().put("word", entry.word).put("meaning", entry.meaning))
    }
    val data = JSONObject()
        .put("title", title)
        .put("mode", mode.wireValue)
        .put("segments", segmentArray)
        .put("expressions", expressionArray)
        .put("glossary", glossaryArray)
    return JSONObject().put("data", data)
}
