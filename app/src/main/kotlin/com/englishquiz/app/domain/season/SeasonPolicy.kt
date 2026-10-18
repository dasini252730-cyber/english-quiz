package com.englishquiz.app.domain.season

/**
 * What the pipeline makes next (백로그 063/075). Episodes are numbered, not dated: a season holds
 * [EPISODES_PER_SEASON] of them in order, and once the last one exists the next season starts at
 * episode 1. Pure Kotlin; the pipeline supplies what is stored.
 */
object SeasonPolicy {
    const val EPISODES_PER_SEASON = 7

    /** The next season is designed once this few episodes of the current one wait unread (백로그 075). */
    const val PREFETCH_WHEN_UNREAD_AT_MOST = 2

    /**
     * What the background pipeline should make next for one language (백로그 075), from the latest
     * season's episodes: first a conversation an unread (or today's) episode still lacks, so a story
     * already made can be practised; then the season's next episode; and only when the season is
     * complete and at most [PREFETCH_WHEN_UNREAD_AT_MOST] of its episodes wait unread, the next
     * season's design. Nothing when the season is complete and the reader is not near its end.
     */
    fun nextWork(latestSeasonNumber: Int?, episodes: List<EpisodeStatus>, today: String): SeasonWork {
        if (latestSeasonNumber == null) return SeasonWork.DesignSeason(1)
        val missingConversation = episodes.firstOrNull { !it.hasConversation && (it.readDate == null || it.readDate == today) }
        if (missingConversation != null) return SeasonWork.MakeConversation(latestSeasonNumber, missingConversation.number)
        if (episodes.size < EPISODES_PER_SEASON) return SeasonWork.MakeEpisode(latestSeasonNumber, episodes.size + 1)
        val unread = episodes.count { it.readDate == null }
        return if (unread <= PREFETCH_WHEN_UNREAD_AT_MOST) SeasonWork.DesignSeason(latestSeasonNumber + 1) else SeasonWork.Nothing
    }

    /** "시즌 2 · 3/7": the line the reader shows over an episode. */
    fun label(seasonNumber: Int, episodeNumber: Int): String = "시즌 $seasonNumber · $episodeNumber/$EPISODES_PER_SEASON"
}

/** One stored episode as the plan sees it (백로그 075). */
data class EpisodeStatus(val number: Int, val readDate: String?, val hasConversation: Boolean)

/** One step of the background pipeline (백로그 075). */
sealed interface SeasonWork {
    data class DesignSeason(val seasonNumber: Int) : SeasonWork
    data class MakeEpisode(val seasonNumber: Int, val episodeNumber: Int) : SeasonWork
    data class MakeConversation(val seasonNumber: Int, val episodeNumber: Int) : SeasonWork
    data object Nothing : SeasonWork
}
