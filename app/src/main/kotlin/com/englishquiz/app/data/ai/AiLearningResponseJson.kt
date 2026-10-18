package com.englishquiz.app.data.ai

import org.json.JSONArray
import org.json.JSONObject

/**
 * Writes [content] back out in exactly the shape `parseContentResponse` reads.
 *
 * A saved session is restored by handing this JSON straight back to the parser, so the round trip
 * goes through the same validation as a live reply instead of a second, separately-drifting
 * decoder. Expression offsets are recomputed by the parser from the segment text. The language and
 * the pinyin fields (백로그 066) ride along the same way; a passage from before 066 has neither and
 * reads back as English.
 */
internal fun LearningContent.toResponseJson(): JSONObject {
    val segmentArray = JSONArray()
    segments.forEach { segment ->
        segmentArray.put(JSONObject().put("speaker", segment.speaker).put("text", segment.text).putPinyin(segment.pinyin))
    }
    val expressionArray = JSONArray()
    expressions.forEach { expression ->
        expressionArray.put(
            JSONObject()
                .put("text", expression.text)
                .put("meaning", expression.meaning)
                .put("shortMeaning", expression.shortMeaning)
                .put("kind", expression.kind)
                .put("segmentIndex", expression.segmentIndex)
                .putPinyin(expression.pinyin),
        )
    }
    val glossaryArray = JSONArray()
    glossary.forEach { entry ->
        glossaryArray.put(JSONObject().put("word", entry.word).put("meaning", entry.meaning).putPinyin(entry.pinyin))
    }
    val comprehensionArray = JSONArray()
    comprehension.forEach { item ->
        comprehensionArray.put(
            JSONObject()
                .put("question", item.question)
                .put("options", JSONArray(item.options))
                .put("answerIndex", item.answerIndex)
                .put("explanation", item.explanation),
        )
    }
    val speakerArray = JSONArray()
    speakers.forEach { (name, gender) ->
        speakerArray.put(JSONObject().put("name", name).put("gender", gender.wireValue))
    }
    val data = JSONObject()
        .put("title", title)
        .put("mode", mode.wireValue)
        .put("language", language.wireValue)
        .put("segments", segmentArray)
        .put("expressions", expressionArray)
        .put("glossary", glossaryArray)
        .put("comprehension", comprehensionArray)
        .put("speakers", speakerArray)
        .put("synopsis", synopsis)
    canon?.let { data.put("canon", it.toJson()) }
    return JSONObject().put("data", data)
}

/** Pinyin is written only when there is some, so an English passage's JSON is unchanged by 066. */
private fun JSONObject.putPinyin(pinyin: String): JSONObject = if (pinyin.isEmpty()) this else put("pinyin", pinyin)
