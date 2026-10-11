package com.englishquiz.app.domain.season

/**
 * Where the next story episode goes (백로그 063). Episodes are numbered, not dated: a season holds
 * [EPISODES_PER_SEASON] of them in order, and once the last one exists the next story opens a new
 * season at episode 1. Pure Kotlin; the coordinator supplies what is stored.
 */
object SeasonPolicy {
    const val EPISODES_PER_SEASON = 7

    /** The next episode to generate, given the latest season and how many episodes it already has. */
    fun nextEpisode(latestSeasonNumber: Int?, episodesInLatest: Int): NextEpisode {
        if (latestSeasonNumber == null) return NextEpisode(seasonNumber = 1, episodeNumber = 1, startsNewSeason = true)
        if (episodesInLatest >= EPISODES_PER_SEASON) {
            return NextEpisode(seasonNumber = latestSeasonNumber + 1, episodeNumber = 1, startsNewSeason = true)
        }
        return NextEpisode(seasonNumber = latestSeasonNumber, episodeNumber = episodesInLatest + 1, startsNewSeason = false)
    }

    /** "시즌 2 · 3/7": the line the reader shows over an episode. */
    fun label(seasonNumber: Int, episodeNumber: Int): String = "시즌 $seasonNumber · $episodeNumber/$EPISODES_PER_SEASON"
}

data class NextEpisode(val seasonNumber: Int, val episodeNumber: Int, val startsNewSeason: Boolean)
