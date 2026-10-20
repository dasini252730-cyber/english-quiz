package com.englishquiz.app.domain.quiz

/**
 * The typed-answer rules for a Chinese expression (백로그 070). Simplified characters carry no
 * case, inflection or contraction, so the English leniencies in [QuizText] do not apply: the
 * learner is right when the characters she typed are the expression's characters, in order,
 * with nothing missing and nothing added. Whitespace and punctuation — ASCII and CJK alike,
 * "打电话。" against "打电话" — are not held against her, because the keyboard puts them there.
 */
object ChineseText {
    /**
     * Everything the key ignores: every kind of whitespace (the full-width space U+3000 is a
     * separator, not `\s`), every Unicode punctuation class (。，！？、：；“”‘’（）《》〈〉【】…—·
     * and the ASCII set) and the tildes, which Unicode files under symbols.
     */
    private val IGNORED = Regex("""[\s\p{Z}\p{P}~～]+""")

    /**
     * The characters that matter in [text]: no whitespace, no punctuation, no case folding. A
     * full-width digit or Latin letter (３, ｋ) is the same answer as its ASCII twin: a Chinese
     * IME toggles the width, and the learner should not lose a point to that toggle.
     */
    fun key(text: String): String = text.replace(IGNORED, "").map { it.halfWidth() }.joinToString("")

    /** U+FF01–FF5E are the ASCII printable characters shifted by 0xFEE0. */
    private fun Char.halfWidth(): Char = if (this in '！'..'～') (code - 0xFEE0).toChar() else this

    /** Whether [typed] is [expected] character for character; an empty answer is never right. */
    fun matches(typed: String, expected: String): Boolean {
        val typedKey = key(typed)
        return typedKey.isNotEmpty() && typedKey == key(expected)
    }

    /** The first character of the expression with one "○" per remaining character: "打○○" for 打电话. */
    fun hint(expected: String): String {
        val codePoints = key(expected).codePoints().toArray()
        if (codePoints.isEmpty()) return ""
        return String(codePoints, 0, 1) + "○".repeat(codePoints.size - 1)
    }
}
