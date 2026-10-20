package com.englishquiz.app.data.ai

import org.json.JSONObject

/**
 * Reads an episode call's reply (백로그 075). Each passage goes through [parseContentResponse], the
 * same validation a single passage gets, so a story or a conversation the Reader cannot show is
 * refused here rather than stored. A conversation the server dropped (`conversation: null`) is
 * null here too, with the server's check name kept for the log.
 */
internal fun parseEpisodeResponse(response: JSONObject, language: Language): EpisodeResult {
    val data = response.optJSONObject("data") ?: invalidEpisode()
    val episodes = data.optJSONArray("episodes") ?: invalidEpisode()
    if (episodes.length() != 1) invalidEpisode()
    val item = episodes.optJSONObject(0) ?: invalidEpisode()
    val number = item.optInt("number", -1).takeIf { it >= 1 } ?: invalidEpisode()
    val story = item.optJSONObject("story")?.let { parseContentResponse(JSONObject().put("data", it), ContentMode.STORY, language) }
    val conversation = item.optJSONObject("conversation")?.let { parseContentResponse(JSONObject().put("data", it), ContentMode.CONVERSATION, language) }
    if (story == null && conversation == null) invalidEpisode()
    return EpisodeResult(number, story, conversation, item.optString("conversationCheck"))
}

private fun invalidEpisode(): Nothing = throw AiLearningException("invalid_response")
