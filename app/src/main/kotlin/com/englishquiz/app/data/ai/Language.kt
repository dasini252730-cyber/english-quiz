package com.englishquiz.app.data.ai

import java.util.Locale

/**
 * The language being learnt (백로그 066). Everything the learner produces — passages, seasons,
 * saved expressions, quiz records, levels — is kept apart by it; the streak and the game are
 * shared, because the learner is one person. Rows and settings from before 066 are English.
 */
enum class Language(val wireValue: String, val locale: Locale, val label: String) {
    ENGLISH("en", Locale.US, "English"),
    CHINESE("zh", Locale.SIMPLIFIED_CHINESE, "中文"),
    ;

    /** The word for this language in the app's Korean copy ("영어 표현", "중국어 음성"). */
    val korean: String get() = if (this == CHINESE) "중국어" else "영어"

    companion object {
        val DEFAULT = ENGLISH

        fun fromWire(value: String?): Language = entries.firstOrNull { it.wireValue == value } ?: DEFAULT
    }
}
