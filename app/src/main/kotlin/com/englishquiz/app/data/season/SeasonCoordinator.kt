package com.englishquiz.app.data.season

import androidx.room.withTransaction
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.parseContentResponse
import com.englishquiz.app.data.local.SeasonEpisodeEntity
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.data.repository.findDailyContent
import com.englishquiz.app.data.repository.saveDailyContent
import com.englishquiz.app.domain.season.SeasonPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject

/**
 * The reading side of the seasons (백로그 063/075). An episode holds a story and the conversation set
 * inside it, made together by [SeasonPipeline] ahead of time; nothing is generated here. Opening
 * either passage on a date consumes the day's episode: today's if one was already opened, else the
 * oldest unread. Both passages of that episode are then the day's, whichever was opened first, and
 * each is also stored as that date's passage so the library, the result screen and the same-day
 * re-entry work as before. When no episode is ready the caller gets null and shows "준비 중"; the
 * pipeline is asked to look at what to make through [requestWork], as it is after every read.
 *
 * Each language runs its own seasons (백로그 066): numbering, bibles, episodes and the unread queue
 * never cross.
 */
class SeasonCoordinator(
    private val repository: LearningRepository,
    /** Asked to look at the language's seasons: after an episode is consumed, and when none was ready. */
    private val requestWork: (Language) -> Unit = {},
    /** The pipeline's last step per language, for the waiting screen; empty when there is no pipeline. */
    val pipelineStatus: Flow<Map<Language, PipelineStatus>> = flowOf(emptyMap()),
    /** The learner's own "try again" on the waiting screen: starts a run even inside the pipeline's backoff. */
    private val retryWork: (Language) -> Unit = requestWork,
) {
    private val dao get() = repository.database.seasonDao()

    /**
     * The passage of [mode] to read on [learningDate]: the one stored for the day, else the day's
     * episode's; null when no episode is ready, or when the episode's conversation is still to come.
     */
    suspend fun passageFor(language: Language, mode: ContentMode, learningDate: String, nowEpochMillis: Long): LearningContent? {
        repository.findDailyContent(language, learningDate, mode)?.let { return it }
        val episode = dao.episodeReadOn(language.wireValue, learningDate) ?: dao.oldestUnread(language.wireValue)
        val passages = episode?.let { ContentMode.entries.mapNotNull { mode -> passageOf(it, mode) } }.orEmpty()
        val content = passages.firstOrNull { it.mode == mode }
        if (episode == null || content == null) {
            requestWork(language)
            return null
        }
        // One transaction: an episode is never consumed without both its passages becoming the day's,
        // so the story of a day on which only the conversation was opened is still in the library.
        repository.database.withTransaction {
            dao.markRead(episode.id, learningDate)
            passages.forEach { repository.saveDailyContent(learningDate, it, nowEpochMillis) }
        }
        requestWork(language)
        return content
    }

    /** Asks the pipeline to look at [language] again; the waiting screen does so while it waits (백로그 075). */
    fun prepare(language: Language) = requestWork(language)

    /** The learner tapped "다시 시도": a run starts now, backoff or not. */
    fun retryNow(language: Language) = retryWork(language)

    suspend fun hasUnread(language: Language): Boolean = dao.countUnread(language.wireValue) > 0

    /** How many episodes wait unread; home shows "준비 중" at zero (백로그 075). */
    fun observeUnreadCount(language: Language): Flow<Int> = dao.observeUnreadCount(language.wireValue)

    /** Changes when an episode arrives or gets its conversation, so a waiting screen can try again. */
    fun observeEpisodeState(language: Language): Flow<Int> = dao.observeEpisodeState(language.wireValue)

    /** "시즌 2 · 3/7" for the episode read on [learningDate], or null when that day's passages are not an episode's. */
    suspend fun labelFor(language: Language, learningDate: String): String? {
        val episode = dao.episodeReadOn(language.wireValue, learningDate) ?: return null
        val season = dao.findSeason(episode.seasonId) ?: return null
        return SeasonPolicy.label(season.number, episode.number)
    }

    /** The story is always there; the conversation is null until the pipeline has made it. */
    private fun passageOf(episode: SeasonEpisodeEntity, mode: ContentMode): LearningContent? {
        val json = when (mode) {
            ContentMode.STORY -> episode.contentJson
            ContentMode.CONVERSATION -> episode.conversationJson ?: return null
        }
        return parseContentResponse(JSONObject(json), mode, Language.fromWire(episode.language))
    }
}
