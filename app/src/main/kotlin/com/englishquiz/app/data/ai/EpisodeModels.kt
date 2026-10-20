package com.englishquiz.app.data.ai

import org.json.JSONArray
import org.json.JSONObject

/*
 * The episode call's wire shapes (백로그 075): one request asks the server for an episode's story and
 * the conversation set inside it, or for the conversation of a story the app already holds; the
 * reply is `episodes[]`, one entry per episode made. The app keeps the numbers the server keeps
 * (`episodes.ts`): the brief lists at 12 x 300, the earlier episodes' text at 16,000 characters.
 */

/** An earlier episode of the season, in full, as the next episode hears it. */
data class PreviousEpisode(val number: Int, val title: String, val text: String)

/** A story already written, so the server writes only its conversation. */
data class GivenStory(val title: String, val segments: List<ContentSegment>)

data class EpisodeRequest(
    val language: Language,
    val storyDifficulty: Int,
    val conversationDifficulty: Int,
    val reviewExpressions: List<String>,
    val bible: SeasonBible,
    /** The episodes wanted, in order; the server makes the first of them and says which. */
    val episodes: List<Int>,
    val previousEpisodes: List<PreviousEpisode>,
    val canon: CanonState,
    val story: GivenStory? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("action", "episodes")
        .put("language", language.wireValue)
        .put("storyDifficulty", storyDifficulty)
        .put("conversationDifficulty", conversationDifficulty)
        .put("reviewExpressions", JSONArray(reviewExpressions))
        .put("season", briefJson())
        .put("episodes", JSONArray(episodes))
        .put("previousEpisodes", JSONArray(previousEpisodes.map { JSONObject().put("number", it.number).put("title", it.title).put("text", it.text) }))
        .put("canon", canon.toJson())
        .apply {
            story?.let { given ->
                put("story", JSONObject().put("title", given.title).put("segments", JSONArray(given.segments.map { JSONObject().put("speaker", it.speaker).put("text", it.text) })))
            }
        }

    /** The bible as the episode call reads it: the cast as one line, the lists at the server's size, every plan. */
    private fun briefJson(): JSONObject = JSONObject()
        .put("title", bible.title).put("engine", bible.engine).put("setting", bible.setting).put("conflict", bible.conflict)
        .put("characters", bible.castLine(MAX_CHARACTERS_CHARS))
        .put("whatIsTrue", JSONArray(bible.whatIsTrue.fitted()))
        .put("readerBelieves", JSONArray(bible.readerBelieves.fitted()))
        .put("plans", JSONArray(bible.episodes.map { JSONObject().put("number", it.number).put("title", it.title).put("plan", it.plan) }))

    private fun List<String>.fitted(): List<String> = take(MAX_LIST_ITEMS).map { it.take(MAX_LIST_ITEM_CHARS) }

    companion object {
        const val MAX_LIST_ITEMS = 12
        const val MAX_LIST_ITEM_CHARS = 300
        /** What the server keeps of the cast line (백로그 064). */
        const val MAX_CHARACTERS_CHARS = 2_400
        /** What the server keeps of the earlier episodes' text, newest first (백로그 075). */
        const val MAX_PREVIOUS_TEXT_CHARS = 16_000
    }
}

/**
 * One episode as the server made it: the story with its canon, and the conversation when it
 * passed its contract. [conversation] is null when the server kept the story and named in
 * [conversationCheck] what the conversation broke; the app then asks for it again with the story
 * given. With a story given, [story] is null and [conversation] is what was asked for.
 */
data class EpisodeResult(
    val number: Int,
    val story: LearningContent?,
    val conversation: LearningContent?,
    val conversationCheck: String = "",
)
