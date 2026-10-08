package com.englishquiz.app.domain.quiz

import com.englishquiz.app.data.ai.foldTypography

/** Matches the set the Reader strips when it turns a tapped token into a saved expression. */
private val EDGE_PUNCTUATION = charArrayOf(
    '.', ',', '!', '?', ';', ':', '"', '\'', '(', ')', '[', ']', '…', '-',
)

/**
 * The key two spellings of one expression have to agree on.
 *
 * A saved expression is the text the Reader cut out of the passage with its edge punctuation
 * removed; a content annotation is the string the model sent, trimmed and nothing else. The two
 * differ in exactly the ways [foldTypography] folds — a curly apostrophe against a straight one —
 * and in trailing punctuation. Without folding both, the same phrase can be offered as the correct
 * answer and as a wrong option at once, indistinguishable on screen. A typed answer (백로그 043)
 * is held to the same key, so "Hang out." matches "hang out".
 */
internal fun normalizeQuizText(text: String): String = text
    .foldTypography()
    .trim(*EDGE_PUNCTUATION)
    .trim()
    .split(Regex("""\s+"""))
    .filter { it.isNotEmpty() }
    .joinToString(" ")

/** Whether what the learner typed is the expression (백로그 043), ignoring case, edge punctuation and spacing. */
fun typedAnswerMatches(typed: String, expression: String): Boolean {
    val key = normalizeQuizText(typed)
    return key.isNotEmpty() && key == normalizeQuizText(expression)
}

/**
 * The hint for a typed blank: the first letter of every word with the rest blanked, so
 * "hang out" shows as "h___ o__". Punctuation inside a word is kept as it is.
 */
fun typedAnswerHint(expression: String): String =
    expression.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }.joinToString(" ") { word ->
        word.first() + "_".repeat(word.length - 1)
    }
