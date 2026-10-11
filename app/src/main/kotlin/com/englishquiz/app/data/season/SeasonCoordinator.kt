package com.englishquiz.app.data.season

import androidx.room.withTransaction
import com.englishquiz.app.data.ai.CanonState
import com.englishquiz.app.data.ai.ContentGenerationRequest
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.PassageSummary
import com.englishquiz.app.data.ai.SeasonBible
import com.englishquiz.app.data.ai.SeasonContext
import com.englishquiz.app.data.ai.SeasonRequest
import com.englishquiz.app.data.ai.parseContentResponse
import com.englishquiz.app.data.ai.toResponseJson
import com.englishquiz.app.data.local.SeasonEntity
import com.englishquiz.app.data.local.SeasonEpisodeEntity
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.data.repository.findDailyContent
import com.englishquiz.app.data.repository.saveDailyContent
import com.englishquiz.app.domain.season.SeasonPolicy
import org.json.JSONObject

/**
 * The story as a season of seven episodes (백로그 063). Episodes are made in order and identified
 * by number; the day the learner opens one is recorded on it. Opening the story on a date reads
 * the oldest episode not yet read, or makes the next one; a passage made ahead of time therefore
 * waits for the learner instead of being skipped. A read episode is also stored as that date's
 * passage, so the library, the result screen and the same-day re-entry work as before.
 */
class SeasonCoordinator(
    private val repository: LearningRepository,
    private val generateContent: suspend (ContentGenerationRequest) -> LearningContent,
    private val generateSeason: suspend (SeasonRequest) -> SeasonBible,
) {
    private val dao get() = repository.database.seasonDao()

    /** The story to read on [learningDate]: today's if already opened, else the oldest unread, else a new episode. */
    suspend fun storyFor(learningDate: String, difficulty: Int, reviewExpressions: List<String>, nowEpochMillis: Long): LearningContent {
        repository.findDailyContent(learningDate, ContentMode.STORY)?.let { return it }
        val episode = dao.oldestUnread() ?: generateNext(difficulty, reviewExpressions, nowEpochMillis)
        val content = parse(episode)
        // One transaction: an episode is never consumed without becoming the day's passage.
        repository.database.withTransaction {
            dao.markRead(episode.id, learningDate)
            repository.saveDailyContent(learningDate, content, nowEpochMillis)
        }
        return content
    }

    /** Makes the next episode ahead of time. False when one already waits unread: nothing is made. */
    suspend fun prefetchNext(difficulty: Int, reviewExpressions: List<String>, nowEpochMillis: Long): Boolean {
        if (dao.countUnread() > 0) return false
        generateNext(difficulty, reviewExpressions, nowEpochMillis)
        return true
    }

    suspend fun hasUnread(): Boolean = dao.countUnread() > 0

    /** What a conversation on [learningDate] hears of the story: the episode read that day, with its people and place. */
    suspend fun companionFor(learningDate: String): PassageSummary? = dao.episodeReadOn(learningDate)?.let { summaryOf(it) }

    /** What tomorrow's conversation hears: the episode the learner will open next. */
    suspend fun companionForNextRead(): PassageSummary? = dao.oldestUnread()?.let { summaryOf(it) }

    /** "시즌 2 · 3/7" for the episode read on [learningDate], or null when that day's story is not an episode. */
    suspend fun labelFor(learningDate: String): String? {
        val episode = dao.episodeReadOn(learningDate) ?: return null
        val season = dao.findSeason(episode.seasonId) ?: return null
        return SeasonPolicy.label(season.number, episode.number)
    }

    private suspend fun generateNext(difficulty: Int, reviewExpressions: List<String>, nowEpochMillis: Long, attempt: Int = 1): SeasonEpisodeEntity {
        val latest = dao.latestSeason()
        val next = SeasonPolicy.nextEpisode(latest?.number, latest?.let { dao.countEpisodes(it.id) } ?: 0)
        val season = if (next.startsNewSeason || latest == null) createSeason(next.seasonNumber, difficulty, nowEpochMillis) else latest
        val bible = SeasonBible.fromJson(JSONObject(season.bibleJson))
        val canon = CanonState.fromJson(JSONObject(season.canonJson))
        val plan = bible.episodes.firstOrNull { it.number == next.episodeNumber }
        val previous = if (next.episodeNumber > 1) dao.findEpisode(season.id, next.episodeNumber - 1)?.synopsis.orEmpty() else ""
        val content = generateContent(
            ContentGenerationRequest(
                mode = ContentMode.STORY,
                difficulty = difficulty,
                reviewExpressions = reviewExpressions,
                season = SeasonContext(
                    title = bible.title, setting = bible.setting, conflict = bible.conflict, characters = bible.peopleLine().take(MAX_CHARACTERS_CHARS),
                    episodeNumber = next.episodeNumber, episodePlan = plan?.plan ?: "Move the season forward.",
                    previousSummary = previous, canon = canon,
                ),
            ),
        )
        val delta = content.canon ?: CanonState()
        val episode = SeasonEpisodeEntity(
            seasonId = season.id, number = next.episodeNumber, contentJson = content.toResponseJson().toString(),
            synopsis = content.synopsis, canonJson = delta.toJson().toString(), difficulty = difficulty,
            generatedAtEpochMillis = nowEpochMillis, readDate = null,
        )
        val id = dao.insertEpisode(episode)
        if (id == -1L) {
            // Another generation of the same episode landed first (prefetch and a tap at once): the
            // stored one is the episode and this one is let go. If it was already read in the
            // meantime, the next number is the one to make.
            val stored = checkNotNull(dao.findEpisode(season.id, next.episodeNumber))
            if (stored.readDate == null || attempt >= MAX_RESERVATION_ATTEMPTS) return stored
            return generateNext(difficulty, reviewExpressions, nowEpochMillis, attempt + 1)
        }
        dao.updateSeasonCanon(season.id, canon.merged(delta).toJson().toString())
        return episode.copy(id = id)
    }

    private suspend fun createSeason(number: Int, difficulty: Int, nowEpochMillis: Long): SeasonEntity {
        val bible = generateSeason(SeasonRequest(difficulty, number, dao.listSeasonTitles()))
        val season = SeasonEntity(
            number = number, title = bible.title, bibleJson = bible.toJson().toString(),
            canonJson = CanonState(openQuestions = bible.openQuestions).toJson().toString(),
            difficulty = difficulty, createdAtEpochMillis = nowEpochMillis,
        )
        return try {
            season.copy(id = dao.insertSeason(season))
        } catch (error: Exception) {
            // The same season number was designed concurrently: keep the one that landed.
            dao.latestSeason()?.takeIf { it.number == number } ?: throw error
        }
    }

    private companion object {
        const val MAX_RESERVATION_ATTEMPTS = 3
        /** The request must stay well inside the Edge Function's 16 KB body limit. */
        const val MAX_CHARACTERS_CHARS = 600
    }

    private fun parse(episode: SeasonEpisodeEntity): LearningContent =
        parseContentResponse(JSONObject(episode.contentJson), ContentMode.STORY)

    private suspend fun summaryOf(episode: SeasonEpisodeEntity): PassageSummary? {
        val base = runCatching { parse(episode) }.getOrNull()?.summary() ?: return null
        val bible = dao.findSeason(episode.seasonId)?.let { runCatching { SeasonBible.fromJson(JSONObject(it.bibleJson)) }.getOrNull() }
        return base.copy(people = bible?.peopleLine().orEmpty(), place = bible?.setting.orEmpty())
    }
}
