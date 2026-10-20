package com.englishquiz.app.data.season

import android.content.Context
import com.englishquiz.app.data.ai.CanonState
import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.EpisodePlan
import com.englishquiz.app.data.ai.EpisodeRequest
import com.englishquiz.app.data.ai.EpisodeResult
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.SeasonBible
import com.englishquiz.app.data.ai.SeasonCharacter
import com.englishquiz.app.data.ai.SeasonRequest
import com.englishquiz.app.data.ai.SpeakerGender
import com.englishquiz.app.data.ai.toResponseJson
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.local.SeasonEntity
import com.englishquiz.app.data.local.SeasonEpisodeEntity
import com.englishquiz.app.data.repository.LearningRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.ZoneId

/**
 * Shared by the season tests (백로그 075): a database, fake generators that record what they were
 * asked, and a pipeline and coordinator wired the way `AppContainer` wires them.
 */
internal class SeasonFixtures(private val context: Context) {
    val episodeRequests = mutableListOf<EpisodeRequest>()
    val seasonRequests = mutableListOf<SeasonRequest>()
    val workRequests = mutableListOf<Language>()
    /** Episode numbers whose next generation fails once. */
    val failOnce = mutableSetOf<Int>()
    /** Episode numbers whose conversation the server "drops" on generation. */
    val dropConversation = mutableSetOf<Int>()
    var now = NOW

    val database: LearningDatabase = LearningDatabase.create(context, DATABASE_NAME)
    val repository = LearningRepository(database)
    val dao get() = database.seasonDao()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val pipeline = SeasonPipeline(
        repository,
        generateEpisode = ::generateEpisode,
        generateSeason = { request -> seasonRequests += request; bible(request.seasonNumber) },
        levelOf = { _, mode -> if (mode == ContentMode.STORY) 2 else 3 },
        nowEpochMillis = { now },
        zoneId = ZoneId.of("UTC"),
    )
    /** True lets a read run the pipeline in the background (the app hands this to WorkManager); off by default so a test's own run is not raced. */
    var autoEnsure = false
    val seasons = SeasonCoordinator(
        repository,
        requestWork = { language -> workRequests += language; if (autoEnsure) scope.launch { runCatching { pipeline.runUntilSettled(language) } } },
        pipelineStatus = pipeline.status,
    )

    fun close() {
        scope.cancel()
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    private fun generateEpisode(request: EpisodeRequest): EpisodeResult {
        episodeRequests += request
        val number = request.episodes.first()
        if (failOnce.remove(number)) error("provider hiccup")
        val seasonNumber = seasonRequests.size
        val story = if (request.story == null) passage("S${seasonNumber}E$number", ContentMode.STORY, request.language, number) else null
        val conversation = if (dropConversation.remove(number)) null else passage("S${seasonNumber}E$number talk", ContentMode.CONVERSATION, request.language, number)
        return EpisodeResult(number, story, conversation, if (conversation == null) "invalid_content_output" else "")
    }

    private fun passage(title: String, mode: ContentMode, language: Language, number: Int): LearningContent {
        val speaker = if (mode == ContentMode.STORY) "Narrator" else "Mina"
        return LearningContent(
            title = title,
            mode = mode,
            segments = listOf(ContentSegment(speaker, "We can pull it off together in $title.")),
            expressions = listOf(ContentExpression("pull it off", "해내다", 0, 7, 18)),
            synopsis = "Synopsis of $title.",
            language = language,
            canon = if (mode == ContentMode.STORY) CanonState(events = listOf("Event of episode $number"), openQuestions = listOf("Open after $number")) else null,
        )
    }

    /** An episode as a build before 075 stored it: a story and no conversation. */
    suspend fun storeLegacyEpisode(language: Language, seasonNumber: Int, number: Int, readDate: String? = null): Long {
        val season = dao.latestSeason(language.wireValue)?.takeIf { it.number == seasonNumber } ?: run {
            val bible = bible(seasonNumber)
            val id = dao.insertSeason(
                SeasonEntity(number = seasonNumber, title = bible.title, bibleJson = bible.toJson().toString(), canonJson = CanonState().toJson().toString(), difficulty = 2, createdAtEpochMillis = now, language = language.wireValue),
            )
            checkNotNull(dao.findSeason(id))
        }
        val story = passage("S${seasonNumber}E$number", ContentMode.STORY, language, number)
        return dao.insertEpisode(
            SeasonEpisodeEntity(
                seasonId = season.id, number = number, contentJson = story.toResponseJson().toString(), synopsis = story.synopsis,
                canonJson = CanonState().toJson().toString(), difficulty = 2, generatedAtEpochMillis = now, readDate = readDate, language = language.wireValue,
            ),
        )
    }

    companion object {
        const val DATABASE_NAME = "test-seasons.db"
        const val NOW = 1_791_201_600_000L
        const val DAY = 24 * 60 * 60 * 1000L

        fun date(day: Int) = "2026-10-%02d".format(day)

        fun bible(number: Int) = SeasonBible(
            title = "Season $number bible",
            setting = "Lisbon",
            conflict = "Who is sending the guests away?",
            characters = listOf(
                SeasonCharacter("Mina", SpeakerGender.FEMALE, "owner", "keep the hostel", "owes money", "cousin of Rui"),
                SeasonCharacter("Rui", SpeakerGender.MALE, "driver", "quiet life", "saw it", "cousin of Mina"),
            ),
            episodes = (1..7).map { EpisodePlan(it, "Episode $it", "Plan $it") },
            openQuestions = listOf("Who?"),
            engine = "A. Clever mystery",
            whatIsTrue = listOf("True: Mina sent the guest away."),
            readerBelieves = (1..7).map { "Believed after $it" },
        )
    }
}
