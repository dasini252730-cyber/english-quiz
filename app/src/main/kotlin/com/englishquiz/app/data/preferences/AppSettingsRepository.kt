package com.englishquiz.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

data class AppSettings(
    val isAssessmentComplete: Boolean = false,
    val currentDifficulty: Int? = null,
    /** How many sessions have finished since the difficulty last moved (백로그 013). */
    val sessionsSinceDifficultyChange: Int = 0,
)

class AppSettingsRepository(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.appSettingsDataStore)

    val settings: Flow<AppSettings> = dataStore.data.map(::toAppSettings)

    suspend fun saveAssessmentResult(difficulty: Int) {
        dataStore.edit { preferences ->
            preferences[ASSESSMENT_COMPLETE] = true
            preferences[CURRENT_DIFFICULTY] = difficulty
            // The diagnosis is itself a fresh level, so the anti-oscillation window restarts.
            preferences[SESSIONS_SINCE_CHANGE] = 0
        }
    }

    /**
     * Counts one finished session and lets [decide] move the difficulty (백로그 013). Every write
     * happens in one edit, so the stored level, the "sessions since it moved" counter and the
     * session this last ran for can never disagree — which matters because that counter is what
     * stops the level oscillating.
     *
     * [sessionCompletedAtEpochMillis] identifies the session. The result screen can re-run its
     * effect after a rotation or process death, so the same session may arrive more than once;
     * a repeat is ignored rather than counted again.
     *
     * Does nothing when no difficulty is stored yet: a session cannot finish before the diagnosis
     * has set one, and inventing a level here would override what the diagnosis is about to write.
     */
    suspend fun applyFinishedSession(
        sessionCompletedAtEpochMillis: Long,
        decide: (currentDifficulty: Int, sessionsSinceChange: Int) -> Int,
    ) {
        dataStore.edit { preferences ->
            val current = preferences[CURRENT_DIFFICULTY] ?: return@edit
            if (preferences[LAST_ADJUSTED_SESSION] == sessionCompletedAtEpochMillis) return@edit
            val sessionsSinceChange = (preferences[SESSIONS_SINCE_CHANGE] ?: 0) + 1
            val next = decide(current, sessionsSinceChange)
            preferences[CURRENT_DIFFICULTY] = next
            preferences[SESSIONS_SINCE_CHANGE] = if (next == current) sessionsSinceChange else 0
            preferences[LAST_ADJUSTED_SESSION] = sessionCompletedAtEpochMillis
        }
    }

    private fun toAppSettings(preferences: Preferences): AppSettings = AppSettings(
        isAssessmentComplete = preferences[ASSESSMENT_COMPLETE] ?: false,
        currentDifficulty = preferences[CURRENT_DIFFICULTY],
        sessionsSinceDifficultyChange = preferences[SESSIONS_SINCE_CHANGE] ?: 0,
    )

    private companion object {
        val ASSESSMENT_COMPLETE = booleanPreferencesKey("assessment_complete")
        val CURRENT_DIFFICULTY = intPreferencesKey("current_difficulty")
        val SESSIONS_SINCE_CHANGE = intPreferencesKey("sessions_since_difficulty_change")
        val LAST_ADJUSTED_SESSION = longPreferencesKey("last_adjusted_session_completed_at")
    }
}
