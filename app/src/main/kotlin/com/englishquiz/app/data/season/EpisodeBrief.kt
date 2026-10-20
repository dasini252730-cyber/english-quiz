package com.englishquiz.app.data.season

import com.englishquiz.app.data.ai.CanonState
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.EpisodeRequest
import com.englishquiz.app.data.ai.GivenStory
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.PreviousEpisode
import com.englishquiz.app.data.ai.SeasonBible
import com.englishquiz.app.data.ai.parseContentResponse
import com.englishquiz.app.data.local.SeasonEntity
import com.englishquiz.app.data.local.SeasonEpisodeEntity
import org.json.JSONObject

/**
 * Builds an episode call's request from what is stored (백로그 075): the season's bible and canon,
 * the earlier episodes in full, and either the number to write or the story whose conversation is
 * wanted. Kept apart from the pipeline so the request shape is one place and the pipeline is only
 * the order of work.
 */
internal object EpisodeBrief {
    /** The story of a stored episode, as the Reader reads it. */
    fun story(episode: SeasonEpisodeEntity): LearningContent =
        parseContentResponse(JSONObject(episode.contentJson), ContentMode.STORY, Language.fromWire(episode.language))

    /** The request for episode [number]'s story and conversation. */
    fun forEpisode(
        language: Language,
        season: SeasonEntity,
        episodes: List<SeasonEpisodeEntity>,
        number: Int,
        storyDifficulty: Int,
        conversationDifficulty: Int,
        reviewExpressions: List<String>,
    ): EpisodeRequest = EpisodeRequest(
        language = language,
        storyDifficulty = storyDifficulty,
        conversationDifficulty = conversationDifficulty,
        reviewExpressions = reviewExpressions,
        bible = SeasonBible.fromJson(JSONObject(season.bibleJson)),
        episodes = listOf(number),
        previousEpisodes = previous(episodes, number),
        canon = CanonState.fromJson(JSONObject(season.canonJson)),
    )

    /** The request for the conversation of [episode], whose story is already stored. */
    fun forConversation(
        language: Language,
        season: SeasonEntity,
        episodes: List<SeasonEpisodeEntity>,
        episode: SeasonEpisodeEntity,
        storyDifficulty: Int,
        conversationDifficulty: Int,
        reviewExpressions: List<String>,
    ): EpisodeRequest {
        val story = story(episode)
        // The server writes the conversation from the given story alone and never reads the earlier episodes.
        return forEpisode(language, season, episodes, episode.number, storyDifficulty, conversationDifficulty, reviewExpressions)
            .copy(story = GivenStory(story.title, story.segments), previousEpisodes = emptyList())
    }

    /**
     * The episodes before [number], newest kept whole first inside the text budget the server reads
     * (its `MAX_PREVIOUS_TEXT_CHARS`), so the request stays inside the body limit. An episode whose
     * stored story this build cannot read is left out rather than failing the next one.
     */
    fun previous(episodes: List<SeasonEpisodeEntity>, number: Int): List<PreviousEpisode> {
        val earlier = episodes.filter { it.number < number }.sortedBy { it.number }.mapNotNull { episode ->
            val story = runCatching { story(episode) }.getOrNull() ?: return@mapNotNull null
            PreviousEpisode(episode.number, story.title, story.segments.joinToString("\n") { "${it.speaker}: ${it.text}" })
        }
        val kept = ArrayDeque<PreviousEpisode>()
        var used = 0
        for (item in earlier.asReversed()) {
            if (used + item.text.length > EpisodeRequest.MAX_PREVIOUS_TEXT_CHARS) break
            used += item.text.length
            kept.addFirst(item)
        }
        return kept.toList()
    }
}
