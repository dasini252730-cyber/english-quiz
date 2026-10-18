package com.englishquiz.app.domain.roleplay

import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.ai.foldTypography
import com.englishquiz.app.domain.quiz.ChineseText

/** Where a learner's turn stands after an attempt. [RETRY] leaves the turn open; the other two settle it. */
enum class TurnOutcome { RETRY, PASSED, FAILED }

/** How much of the line the learner's attempt covered: [matched] of [total] units, and whether that passes. */
data class RoleplayGrade(val matched: Int, val total: Int) {
    val ratio: Float get() = if (total == 0) 0f else matched.toFloat() / total
    val passed: Boolean get() = total > 0 && ratio >= RoleplayGrader.PASS_RATIO
    val percent: Int get() = (ratio * 100).toInt()
}

/**
 * Grades a role-play attempt (백로그 076) by *what words were said*, never how they sounded: the
 * recogniser's transcript is compared with the line as sets. English compares words; Chinese
 * compares Han characters. No tone, pinyin or pronunciation score exists here on purpose — the
 * transcript is the only evidence, and it carries none of those.
 *
 * Pure Kotlin so the rule is proved on the JVM.
 */
object RoleplayGrader {
    /** Seven words in ten is a pass; below it the learner may try again. */
    const val PASS_RATIO = 0.7f

    /** After the first attempt, this many more before the turn is marked failed and the play moves on. */
    const val MAX_RETRIES = 2

    private val NOT_A_LETTER = Regex("""[^\p{L}\p{N}]+""")
    /** Straight and curly apostrophes alike: "don't" and the recogniser's "dont" are one word. */
    private val APOSTROPHE = Regex("[\u0027\u2018\u2019\u02BC\u00B4]")

    /** The turn after its [attempt]th try (1-based): passed, open for another try, or failed once the retries are spent. */
    fun settle(attempt: Int, passed: Boolean): TurnOutcome = when {
        passed -> TurnOutcome.PASSED
        attempt > MAX_RETRIES -> TurnOutcome.FAILED
        else -> TurnOutcome.RETRY
    }

    /** The share of the line's units the attempt contained; see [englishWords] and [hanCharacters] for the units. */
    fun grade(original: String, attempt: String, language: Language): RoleplayGrade {
        if (language == Language.CHINESE) {
            val expected = hanCharacters(original)
            // A Chinese line with no Han characters at all ("OK!") is judged by its words instead.
            if (expected.isNotEmpty()) return RoleplayGrade(expected.intersect(hanCharacters(attempt)).size, expected.size)
        }
        val expected = englishWords(original).toSet()
        return RoleplayGrade(expected.intersect(englishWords(attempt).toSet()).size, expected.size)
    }

    /**
     * The one normalisation both sides of an English comparison go through: typography folded
     * (a curly apostrophe is a straight one), lower case, apostrophes removed so "don't" and
     * the recogniser's "dont" are one word, and everything that is not a letter or a digit is a
     * word boundary. "I'm fine, thanks!" → [im, fine, thanks].
     */
    fun englishWords(text: String): List<String> = text
        .foldTypography()
        .replace(APOSTROPHE, "")
        .split(NOT_A_LETTER)
        .filter { it.isNotEmpty() }

    /** The distinct Han characters of [text]; punctuation, spaces and Latin letters are not among them. */
    fun hanCharacters(text: String): Set<Char> = text.filter { it.isHan() }.toSet()

    /**
     * The cue shown instead of the line: English gives the first letter of every word with its
     * case kept ("Can you help me?" → "C y h m"); Chinese gives the first Han character only. The
     * words are cut as [englishWords] cuts them, so a dash or a contraction counts the same in the
     * cue as in the grade; a repeated word is cued each time it is said.
     */
    fun hint(original: String, language: Language): String {
        if (language == Language.CHINESE) {
            original.firstOrNull { it.isHan() }?.let { return it.toString() }
        }
        return original.replace(APOSTROPHE, "")
            .split(NOT_A_LETTER)
            .filter { it.isNotEmpty() }
            .joinToString(" ") { it.first().toString() }
    }

    /**
     * Whether the attempt carried the line's highlighted expression: in English its words, in
     * order and next to each other, after the same normalisation as the grade ("pulled it off"
     * does not count — a different word was said); in Chinese its characters as a substring once
     * spaces and punctuation are gone.
     */
    fun containsExpression(attempt: String, expression: String, language: Language): Boolean {
        if (language == Language.CHINESE) {
            val wanted = ChineseText.key(expression)
            return wanted.isNotEmpty() && ChineseText.key(attempt).contains(wanted)
        }
        val wanted = englishWords(expression)
        if (wanted.isEmpty()) return false
        return englishWords(attempt).windowed(wanted.size).any { it == wanted }
    }

    private fun Char.isHan(): Boolean = Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN
}
