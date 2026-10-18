package com.englishquiz.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * A story season (백로그 063): designed once, then followed for seven episodes. [bibleJson] is the
 * design and never changes; [canonJson] is what the episodes so far have settled, rewritten
 * after each one. [difficulty] is the level the bible was designed at.
 */
@Entity(tableName = "seasons", indices = [Index(value = ["language", "number"], unique = true)])
data class SeasonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val number: Int,
    val title: String,
    val bibleJson: String,
    val canonJson: String,
    val difficulty: Int,
    val createdAtEpochMillis: Long,
    /** Seasons are numbered per language (백로그 066): English and Chinese each have their own season 1. */
    @ColumnInfo(defaultValue = "'en'") val language: String = DEFAULT_LANGUAGE,
)

/**
 * One episode of a season. Episodes are identified by [number] inside their season, not by a
 * date: [readDate] is set the day the learner opens it, so an episode made ahead of time waits
 * (and moves the level change after it) until it is read. The unique index is what makes two
 * generations of the same episode impossible to keep.
 */
@Entity(
    tableName = "season_episodes",
    indices = [Index(value = ["seasonId", "number"], unique = true), Index(value = ["readDate"])],
)
data class SeasonEpisodeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val seasonId: Long,
    val number: Int,
    val contentJson: String,
    val synopsis: String,
    /** This episode's own canon delta, as the server returned it. */
    val canonJson: String,
    val difficulty: Int,
    val generatedAtEpochMillis: Long,
    val readDate: String?,
    /** The season's language, copied here so the unread and read-on-date queries need no join (백로그 066). */
    @ColumnInfo(defaultValue = "'en'") val language: String = DEFAULT_LANGUAGE,
)

@Dao
interface SeasonDao {
    @Query("SELECT * FROM seasons WHERE language = :language ORDER BY number DESC LIMIT 1")
    suspend fun latestSeason(language: String): SeasonEntity?

    @Query("SELECT * FROM seasons WHERE id = :id")
    suspend fun findSeason(id: Long): SeasonEntity?

    /** The most recent titles, newest first, so the next design avoids them; bounded for the request size. */
    @Query("SELECT title FROM seasons WHERE language = :language ORDER BY number DESC LIMIT 10")
    suspend fun listSeasonTitles(language: String): List<String>

    /** Fails on a duplicate number: two concurrent designs of the same season keep only the first. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSeason(season: SeasonEntity): Long

    @Query("UPDATE seasons SET canonJson = :canonJson WHERE id = :id")
    suspend fun updateSeasonCanon(id: Long, canonJson: String)

    @Query("SELECT COUNT(*) FROM season_episodes WHERE seasonId = :seasonId")
    suspend fun countEpisodes(seasonId: Long): Int

    @Query("SELECT * FROM season_episodes WHERE seasonId = :seasonId AND number = :number")
    suspend fun findEpisode(seasonId: Long, number: Int): SeasonEpisodeEntity?

    /** The episode the learner opens next in [language]: the oldest one not yet read, across its seasons. */
    @Query("SELECT * FROM season_episodes WHERE language = :language AND readDate IS NULL ORDER BY seasonId, number LIMIT 1")
    suspend fun oldestUnread(language: String): SeasonEpisodeEntity?

    @Query("SELECT COUNT(*) FROM season_episodes WHERE language = :language AND readDate IS NULL")
    suspend fun countUnread(language: String): Int

    @Query("SELECT * FROM season_episodes WHERE language = :language AND readDate = :readDate LIMIT 1")
    suspend fun episodeReadOn(language: String, readDate: String): SeasonEpisodeEntity?

    /** -1 when the (season, number) slot is already taken: the caller then reads the stored one. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEpisode(episode: SeasonEpisodeEntity): Long

    @Query("UPDATE season_episodes SET readDate = :readDate WHERE id = :id AND readDate IS NULL")
    suspend fun markRead(id: Long, readDate: String)
}
