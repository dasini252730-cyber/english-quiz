package com.englishquiz.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.englishquiz.app.domain.game.Badge
import com.englishquiz.app.domain.game.StreakShieldPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.gameProgressDataStore by preferencesDataStore(name = "game_progress")

/**
 * The little the game layer has to remember that the learning records do not already say
 * (백로그 039/040). Points earned and badges won are re-derived from Room every time; only what
 * was *spent* and what was *looked at* lives here.
 */
data class GameProgress(
    /** Shields in hand, bought with points and used up one per missed day. */
    val shields: Int = 0,
    /** `yyyy-MM-dd` days a shield has covered; `StreakPolicy` counts them as learning days. */
    val shieldedDates: Set<String> = emptySet(),
    /** Points spent on shields; the balance is the earned total minus this. */
    val pointsSpent: Int = 0,
    /** Badge names the learner has opened the badge screen on, so a newer one can be flagged. */
    val badgesSeen: Set<String> = emptySet(),
) {
    fun balance(totalPointsEarned: Int): Int = (totalPointsEarned - pointsSpent).coerceAtLeast(0)
}

class GameProgressRepository(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.gameProgressDataStore)

    val progress: Flow<GameProgress> = dataStore.data.map(::toGameProgress)

    /**
     * Buys one shield when the balance covers it. [totalPointsEarned] comes from the session
     * history, so the check and the debit happen in one edit against the stored spend.
     */
    suspend fun buyShield(totalPointsEarned: Int): Boolean {
        var bought = false
        dataStore.edit { preferences ->
            val spent = preferences[POINTS_SPENT] ?: 0
            if (totalPointsEarned - spent < StreakShieldPolicy.PRICE_POINTS) return@edit
            preferences[POINTS_SPENT] = spent + StreakShieldPolicy.PRICE_POINTS
            preferences[SHIELDS] = (preferences[SHIELDS] ?: 0) + 1
            bought = true
        }
        return bought
    }

    /** Covers [dateIso] with one shield; false when none is left. A day already covered costs nothing. */
    suspend fun useShield(dateIso: String): Boolean {
        var used = false
        dataStore.edit { preferences ->
            val covered = preferences[SHIELDED_DATES] ?: emptySet()
            if (dateIso in covered) {
                used = true
                return@edit
            }
            val shields = preferences[SHIELDS] ?: 0
            if (shields <= 0) return@edit
            preferences[SHIELDS] = shields - 1
            preferences[SHIELDED_DATES] = covered + dateIso
            used = true
        }
        return used
    }

    /**
     * Spends a shield on yesterday when yesterday is a one-day gap in the learning history and a
     * shield is in hand (백로그 040). Home and the result screen both call it, so the streak they
     * show agrees. True when yesterday ends up covered.
     */
    suspend fun shieldYesterdayIfNeeded(learningDates: Collection<String>, todayIso: String): Boolean {
        val current = progress.first()
        if (current.shields <= 0) return false
        val gap = StreakShieldPolicy.dayToShield(learningDates, current.shieldedDates, todayIso) ?: return false
        return useShield(gap)
    }

    suspend fun markBadgesSeen(badges: Collection<Badge>) {
        if (badges.isEmpty()) return
        dataStore.edit { preferences ->
            preferences[BADGES_SEEN] = (preferences[BADGES_SEEN] ?: emptySet()) + badges.map { it.name }
        }
    }

    private fun toGameProgress(preferences: Preferences) = GameProgress(
        shields = preferences[SHIELDS] ?: 0,
        shieldedDates = preferences[SHIELDED_DATES] ?: emptySet(),
        pointsSpent = preferences[POINTS_SPENT] ?: 0,
        badgesSeen = preferences[BADGES_SEEN] ?: emptySet(),
    )

    private companion object {
        val SHIELDS = intPreferencesKey("streak_shields")
        val SHIELDED_DATES = stringSetPreferencesKey("shielded_dates")
        val POINTS_SPENT = intPreferencesKey("points_spent")
        val BADGES_SEEN = stringSetPreferencesKey("badges_seen")
    }
}
