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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap

/**
 * Makes seasons in the background (백로그 075), never on a tap. One run does the next piece of work
 * for a language until nothing is left: a conversation an episode still lacks, the season's next
 * episode (its story and conversation in one call), or the next season's design once the current
 * one is complete and nearly read. Each episode is stored as soon as it arrives, so a failed call
 * loses only itself: the next run starts again at the same piece. A run is started by [ensure]
 * from home, from the assessment's first question, after every read and from the waiting screen,
 * at most one per language at a time, and not within [RETRY_AFTER_MS] of a failure.
 */
class SeasonPipeline(
    private val repository: LearningRepository,
    private val generateEpisode: suspend (EpisodeRequest) -> EpisodeResult,
    private val generateSeason: suspend (SeasonRequest) -> SeasonBible,
    private val scope: CoroutineScope,
    /** The stored level of a mode in a language, or null before any is known. */
    private val levelOf: suspend (Language, ContentMode) -> Int?,
    private val nowEpochMillis: () -> Long = System::currentTimeMillis,
    private val zoneId: ZoneId = ZoneId.systemDefault(),
) {
    private val dao get() = repository.database.seasonDao()
    private val running = ConcurrentHashMap<Language, Job>()
    private val failedAt = ConcurrentHashMap<Language, Long>()
    private val statuses = MutableStateFlow<Map<Language, PipelineStatus>>(emptyMap())

    /** What each language's last run did or hit, for the waiting screen (a run that fails is otherwise invisible). */
    val status: StateFlow<Map<Language, PipelineStatus>> = statuses

    /** Starts a run now even inside the backoff: the learner asked. */
    fun retryNow(language: Language) {
        failedAt.remove(language)
        ensure(language)
    }

    /** Starts a run for [language] unless one is active or one just failed. Nothing here reaches the learner. */
    fun ensure(language: Language) {
        val now = nowEpochMillis()
        if (failedAt[language]?.let { now - it < RETRY_AFTER_MS } == true) return
        synchronized(running) {
            if (running[language]?.isActive == true) return
            running[language] = scope.launch {
                try {
                    runUntilSettled(language)
                } catch (error: TimeoutCancellationException) {
                    // A step that never came back (백로그 075 follow-up): the run is given up so the next
                    // ensure() can start a fresh one instead of waiting on this job for ever.
                    failedAt[language] = nowEpochMillis()
                    report(language, statuses.value[language]?.step ?: "", "step_timeout")
                    Log.w(TAG, "season work for ${language.wireValue} gave up on a step after ${STEP_TIMEOUT_MS / 1000}s")
                } catch (error: CancellationException) {
                    throw error
                } catch (error: Exception) {
                    failedAt[language] = nowEpochMillis()
                    report(language, statuses.value[language]?.step ?: "", error.message ?: error.javaClass.simpleName)
                    Log.w(TAG, "season work for ${language.wireValue} stopped: ${error.message}")
                }
            }
        }
    }

    /** Does each piece of work in turn and returns them; stops at the first failure, which it throws. */
    suspend fun runUntilSettled(language: Language): List<SeasonWork> {
        val done = mutableListOf<SeasonWork>()
        while (done.size < MAX_STEPS_PER_RUN) {
            val work = plan(language)
            if (work == SeasonWork.Nothing) break
            report(language, work.toString(), null)
            Log.i(TAG, "${language.wireValue}: $work")
            withTimeout(STEP_TIMEOUT_MS) { perform(language, work) }
            done += work
            Log.i(TAG, "${language.wireValue}: $work done")
        }
        failedAt.remove(language)
        report(language, if (done.isEmpty()) "nothing to make" else "done: ${done.last()}", null)
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
        const val RETRY_AFTER_MS = 60_000L
        /** A design, seven episodes, their conversations and the next design: more means a loop. */
        const val MAX_STEPS_PER_RUN = 24
        /** One step is one call of at most 148 s plus parsing and storing; longer means something is stuck. */
        const val STEP_TIMEOUT_MS = 200_000L
    }
}

/** One language's last pipeline step and, when it failed, the error code it hit (백로그 075). */
data class PipelineStatus(val step: String, val error: String?, val atEpochMillis: Long)
