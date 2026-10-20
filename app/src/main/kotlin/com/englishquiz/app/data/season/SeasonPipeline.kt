package com.englishquiz.app.data.season

import android.util.Log
import com.englishquiz.app.data.ai.AiLearningException
import com.englishquiz.app.data.ai.CanonState
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.EpisodeRequest
import com.englishquiz.app.data.ai.EpisodeResult
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.ai.SeasonBible
import com.englishquiz.app.data.ai.SeasonRequest
import com.englishquiz.app.data.ai.toResponseJson
import com.englishquiz.app.data.local.SeasonEntity
import com.englishquiz.app.data.local.SeasonEpisodeEntity
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.domain.season.EpisodeStatus
import com.englishquiz.app.domain.season.SeasonPolicy
import com.englishquiz.app.domain.season.SeasonWork
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Makes seasons one step at a time (백로그 075), never on a tap: a conversation an episode still
 * lacks, the season's next episode (its story and conversation in one call), or the next season's
 * design once the current one is complete and nearly read. [step] does the next piece of work for
 * a language and stores it as soon as it arrives, so a failed call loses only itself and the next
 * step starts again at the same piece. Who runs the steps and when is [SeasonWorker]'s business:
 * a WorkManager chain that survives the app being closed or frozen (백로그 075 follow-up).
 */
class SeasonPipeline(
    private val repository: LearningRepository,
    private val generateEpisode: suspend (EpisodeRequest) -> EpisodeResult,
    private val generateSeason: suspend (SeasonRequest) -> SeasonBible,
    /** The stored level of a mode in a language, or null before any is known. */
    private val levelOf: suspend (Language, ContentMode) -> Int?,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val dao get() = repository.database.seasonDao()
    private val statuses = MutableStateFlow<Map<Language, PipelineStatus>>(emptyMap())

    /** What each language's last step did or hit, for the waiting screen while the app is open. */
    val status: StateFlow<Map<Language, PipelineStatus>> = statuses

    /**
     * The next piece of work for [language], done and stored; [SeasonWork.Nothing] when there is
     * none. A failure is reported and rethrown: the worker decides whether to try again.
     */
    suspend fun step(language: Language): SeasonWork {
        val work = plan(language)
        if (work == SeasonWork.Nothing) {
            report(language, "nothing to make", null)
            return work
        }
        report(language, work.toString(), null)
        Log.i(TAG, "${language.wireValue}: $work")
        try {
            perform(language, work)
        } catch (error: Exception) {
            report(language, work.toString(), error.message ?: error.javaClass.simpleName)
            Log.w(TAG, "${language.wireValue}: $work failed: ${error.message}")
            throw error
        }
        report(language, "done: $work", null)
        Log.i(TAG, "${language.wireValue}: $work done")
        return work
    }

    /** Every step in turn until nothing is left; the tests' way of running a whole season. */
    suspend fun runUntilSettled(language: Language): List<SeasonWork> {
        val done = mutableListOf<SeasonWork>()
        while (done.size < MAX_STEPS_PER_RUN) {
            val work = step(language)
            if (work == SeasonWork.Nothing) break
            done += work
        }
        return done
    }

    /** What the language needs next, from what is stored. */
    suspend fun plan(language: Language): SeasonWork {
        val latest = dao.latestSeason(language.wireValue) ?: return SeasonPolicy.nextWork(null, emptyList(), today())
        val states = dao.listEpisodeStates(latest.id).map { EpisodeStatus(it.number, it.readDate, it.hasConversation) }
        return SeasonPolicy.nextWork(latest.number, states, today())
    }

    private suspend fun perform(language: Language, work: SeasonWork) {
        when (work) {
            is SeasonWork.DesignSeason -> createSeason(language, work.seasonNumber)
            is SeasonWork.MakeEpisode -> makeEpisode(language, work.episodeNumber)
            is SeasonWork.MakeConversation -> makeConversation(language, work.episodeNumber)
            SeasonWork.Nothing -> Unit
        }
    }

    private suspend fun createSeason(language: Language, number: Int) {
        val bible = generateSeason(SeasonRequest(storyLevel(language), number, dao.listSeasonTitles(language.wireValue), language))
        val season = SeasonEntity(
            number = number, title = bible.title, bibleJson = bible.toJson().toString(),
            canonJson = CanonState(openQuestions = bible.openQuestions).toJson().toString(),
            difficulty = storyLevel(language), createdAtEpochMillis = nowEpochMillis(), language = language.wireValue,
        )
        try {
            dao.insertSeason(season)
        } catch (error: Exception) {
            // The same number landed from another run: that one is the season.
            if (dao.latestSeason(language.wireValue)?.number != number) throw error
        }
    }

    private suspend fun makeEpisode(language: Language, number: Int) {
        val season = checkNotNull(dao.latestSeason(language.wireValue))
        val episodes = dao.listEpisodes(season.id)
        val storyLevel = storyLevel(language)
        val request = EpisodeBrief.forEpisode(language, season, episodes, number, storyLevel, conversationLevel(language), reviewExpressions(language))
        val result = generateEpisode(request)
        val story = result.story?.takeIf { result.number == number } ?: throw AiLearningException("invalid_response")
        val delta = story.canon ?: CanonState()
        val episode = SeasonEpisodeEntity(
            seasonId = season.id, number = number, contentJson = story.toResponseJson().toString(),
            synopsis = story.synopsis, canonJson = delta.toJson().toString(), difficulty = storyLevel,
            generatedAtEpochMillis = nowEpochMillis(), readDate = null, language = language.wireValue,
            conversationJson = result.conversation?.toResponseJson()?.toString(),
        )
        // -1 when another run stored this number first: that one is the episode, this one is let go.
        if (dao.insertEpisode(episode) == -1L) return
        if (result.conversation == null) Log.w(TAG, "episode $number arrived without its conversation (${result.conversationCheck}); asking again")
        val canon = CanonState.fromJson(JSONObject(season.canonJson))
        dao.updateSeasonCanon(season.id, canon.merged(delta).toJson().toString())
    }

    private suspend fun makeConversation(language: Language, number: Int) {
        val season = checkNotNull(dao.latestSeason(language.wireValue))
        val episodes = dao.listEpisodes(season.id)
        val episode = episodes.firstOrNull { it.number == number } ?: return
        if (episode.conversationJson != null) return
        val request = EpisodeBrief.forConversation(language, season, episodes, episode, storyLevel(language), conversationLevel(language), reviewExpressions(language))
        val result = generateEpisode(request)
        val conversation = result.conversation?.takeIf { result.number == number } ?: throw AiLearningException("invalid_response")
        dao.fillConversation(episode.id, conversation.toResponseJson().toString())
    }

    private suspend fun storyLevel(language: Language): Int = levelOf(language, ContentMode.STORY) ?: DEFAULT_DIFFICULTY

    private suspend fun conversationLevel(language: Language): Int = levelOf(language, ContentMode.CONVERSATION) ?: DEFAULT_DIFFICULTY

    /** What is due now or within the week the episode may be read in; the server weaves the first five. */
    private suspend fun reviewExpressions(language: Language): List<String> =
        repository.findDueExpressions(language, nowEpochMillis() + REVIEW_HORIZON_MS).take(MAX_REVIEW_EXPRESSIONS).map { it.displayExpression }

    private fun report(language: Language, step: String, error: String?) {
        statuses.value = statuses.value + (language to PipelineStatus(step, error, nowEpochMillis()))
    }

    private fun today(): String = Instant.ofEpochMilli(nowEpochMillis()).atZone(zoneId).toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)

    private companion object {
        const val TAG = "SeasonPipeline"
        /** Same default as the app root uses before the first assessment. */
        const val DEFAULT_DIFFICULTY = 3
        /** Mirrors the Edge Function's MAX_REVIEW_EXPRESSIONS. */
        const val MAX_REVIEW_EXPRESSIONS = 12
        const val REVIEW_HORIZON_MS = 7 * 24 * 60 * 60 * 1000L
        /** A design, seven episodes, their conversations and the next design: more means a loop. */
        const val MAX_STEPS_PER_RUN = 24
    }
}

/** One language's last pipeline step and, when it failed, the error code it hit (백로그 075). */
data class PipelineStatus(val step: String, val error: String?, val atEpochMillis: Long)
