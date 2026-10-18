package com.englishquiz.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.domain.difficulty.DifficultyPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appSettingsDataStore by preferencesDataStore(name = "app_settings")

/** One mode's level (1..5, 백로그 034) with the state the automatic suggestion needs. */
data class ModeLevel(
    val level: Int,
    /** How many sessions of this mode have finished since its level last moved (백로그 013). */
    val sessionsSinceChange: Int = 0,
    /** A level the policy recommends and the learner has not answered yet, or null. */
    val suggestedLevel: Int? = null,
)

/**
 * What the app knows about the learner. [levels] are the levels of [language], the language
 * selected on home (백로그 066): every screen works in one language at a time, so the settings
 * carry that language's levels and nothing else needs to know there are two sets.
 */
data class AppSettings(
    val isAssessmentComplete: Boolean = false,
    val language: Language = Language.DEFAULT,
    val levels: Map<ContentMode, ModeLevel> = emptyMap(),
) {
    fun level(mode: ContentMode): Int? = levels[mode]?.level
}

/**
 * Conversation and Story each carry their own level (백로그 034). The learner moves a level
 * directly from home; the policy (백로그 013) only leaves a suggestion, which the learner accepts
 * or dismisses. A build before 034 stored one `current_difficulty` (1..3) — it is read as the
 * initial level of both English modes until the first per-mode write.
 *
 * Levels are kept per language (백로그 066): English under the keys from before 066, Chinese under
 * `…_zh_<mode>` keys with no diagnosis and a starting level of 3 (요구사항 26절).
 */
class AppSettingsRepository(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.appSettingsDataStore)

    val settings: Flow<AppSettings> = dataStore.data.map(::toAppSettings)

    /** The diagnosis sets both English modes to the same starting level and restarts their windows. */
    suspend fun saveAssessmentResult(assessmentLevel: Int) {
        dataStore.edit { preferences ->
            preferences[ASSESSMENT_COMPLETE] = true
            preferences[CURRENT_DIFFICULTY] = assessmentLevel
            for (mode in ContentMode.entries) {
                preferences.writeLevel(Language.ENGLISH, mode, DifficultyPolicy.initialLevel(assessmentLevel))
            }
        }
    }

    /** The language home works in from now on (백로그 066); the next emission carries its levels. */
    suspend fun setLanguage(language: Language) {
        dataStore.edit { preferences -> preferences[LANGUAGE] = language.wireValue }
    }

    /** The learner's own choice: applied at once, the suggestion (if any) withdrawn. */
    suspend fun setLevel(mode: ContentMode, level: Int, language: Language = Language.ENGLISH) {
        dataStore.edit { preferences -> preferences.writeLevel(language, mode, level) }
    }

    suspend fun acceptSuggestion(mode: ContentMode, language: Language = Language.ENGLISH) {
        dataStore.edit { preferences ->
            val suggested = preferences[suggestedKey(language, mode)] ?: return@edit
            preferences.writeLevel(language, mode, suggested)
        }
    }

    /** "Keep it": the suggestion goes, and the next one needs a whole fresh window of sessions. */
    suspend fun dismissSuggestion(mode: ContentMode, language: Language = Language.ENGLISH) {
        dataStore.edit { preferences ->
            preferences.remove(suggestedKey(language, mode))
            preferences[sinceChangeKey(language, mode)] = 0
        }
    }

    /**
     * Counts one finished session of [mode] in [language] and lets [decide] name the level it
     * would move to. The level itself does not move: a recommendation different from the current
     * level is stored as the mode's suggestion for the learner to answer on home. Every write
     * happens in one edit, so the level, the counter and the session this last ran for never
     * disagree.
     *
     * [sessionCompletedAtEpochMillis] identifies the session. The result screen can re-run its
     * effect after a rotation or process death, so the same session may arrive more than once;
     * a repeat is ignored rather than counted again.
     *
     * Does nothing when the mode has no level yet: a session cannot finish before the diagnosis
     * has set one, and inventing a level here would override what the diagnosis is about to write.
     */
    suspend fun applyFinishedSession(
        mode: ContentMode,
        sessionCompletedAtEpochMillis: Long,
        language: Language = Language.ENGLISH,
        decide: (currentLevel: Int, sessionsSinceChange: Int) -> Int,
    ) {
        dataStore.edit { preferences ->
            val current = preferences.readLevel(language, mode)?.level ?: return@edit
            if (preferences[lastSessionKey(language, mode)] == sessionCompletedAtEpochMillis) return@edit
            val sessionsSinceChange = (preferences[sinceChangeKey(language, mode)] ?: 0) + 1
            val recommended = decide(current, sessionsSinceChange)
            preferences[sinceChangeKey(language, mode)] = sessionsSinceChange
            // The newest verdict replaces the old one: a suggestion the latest sessions no longer
            // support must not sit on home waiting to be accepted on stale grounds.
            if (recommended != current) preferences[suggestedKey(language, mode)] = recommended else preferences.remove(suggestedKey(language, mode))
            preferences[lastSessionKey(language, mode)] = sessionCompletedAtEpochMillis
        }
    }

    private fun MutablePreferences.writeLevel(language: Language, mode: ContentMode, level: Int) {
        this[levelKey(language, mode)] = level.coerceIn(DifficultyPolicy.MIN_DIFFICULTY, DifficultyPolicy.MAX_DIFFICULTY)
        this[sinceChangeKey(language, mode)] = 0
        remove(suggestedKey(language, mode))
    }

    private fun Preferences.readLevel(language: Language, mode: ContentMode): ModeLevel? {
        val level = this[levelKey(language, mode)]
            ?: when (language) {
                Language.ENGLISH -> this[CURRENT_DIFFICULTY]?.let(DifficultyPolicy::initialLevel)
                // No diagnosis for Chinese (백로그 066): an intermediate learner starts at 3.
                Language.CHINESE -> CHINESE_INITIAL_LEVEL
            }
            ?: return null
        return ModeLevel(
            level = level,
            sessionsSinceChange = this[sinceChangeKey(language, mode)] ?: 0,
            suggestedLevel = this[suggestedKey(language, mode)],
        )
    }

    private fun toAppSettings(preferences: Preferences): AppSettings {
        val language = Language.fromWire(preferences[LANGUAGE])
        return AppSettings(
            isAssessmentComplete = preferences[ASSESSMENT_COMPLETE] ?: false,
            language = language,
            levels = ContentMode.entries.mapNotNull { mode ->
                preferences.readLevel(language, mode)?.let { mode to it }
            }.toMap(),
        )
    }

    private companion object {
        const val CHINESE_INITIAL_LEVEL = 3
        val ASSESSMENT_COMPLETE = booleanPreferencesKey("assessment_complete")
        /** The diagnosis result (1..3) as stored before 백로그 034; still written for the record. */
        val CURRENT_DIFFICULTY = intPreferencesKey("current_difficulty")
        val LANGUAGE = stringPreferencesKey("selected_language")
        /** English keeps the keys from before 066 ("level_story"); Chinese gets its own ("level_zh_story"). */
        private fun suffix(language: Language, mode: ContentMode) =
            if (language == Language.ENGLISH) mode.wireValue else "${language.wireValue}_${mode.wireValue}"
        fun levelKey(language: Language, mode: ContentMode) = intPreferencesKey("level_${suffix(language, mode)}")
        fun lastSessionKey(language: Language, mode: ContentMode) = longPreferencesKey("last_counted_session_${suffix(language, mode)}")
        fun sinceChangeKey(language: Language, mode: ContentMode) = intPreferencesKey("sessions_since_level_change_${suffix(language, mode)}")
        fun suggestedKey(language: Language, mode: ContentMode) = intPreferencesKey("suggested_level_${suffix(language, mode)}")
    }
}
