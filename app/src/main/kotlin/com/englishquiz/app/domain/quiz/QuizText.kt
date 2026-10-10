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

/**
 * Whether what the learner typed is the expression (백로그 043/058). Case, spacing, edge and
 * inner punctuation, a hyphen for a space, a contraction for its long form (either reading of
 * 's and 'd), a leading "to" and a regular inflection of the head word ("hanging out", "called
 * it a day") are not held against the answer: she recalled the words. A different word is still
 * wrong — "hang in" is not "hang out" — and only the first word may change its ending.
 */
fun typedAnswerMatches(typed: String, expression: String): Boolean {
    val typedKeys = typedAnswerKeys(typed).filter { it.isNotEmpty() }
    val expected = typedAnswerKeys(expression)
    return typedKeys.any { key -> expected.any { it == key || sameButForTheHeadWord(key, it) } }
}

/** The lenient forms a typed answer may be read as; see [typedAnswerMatches]. More than one only for 's and 'd. */
internal fun typedAnswerKeys(text: String): Set<String> {
    val plain = normalizeQuizText(text).replace('-', ' ').replace(Regex("""[^\p{L}\p{N}' ]"""), " ")
    val words = plain.split(Regex("""\s+""")).filter { it.isNotEmpty() }
    var readings = listOf(emptyList<String>())
    words.forEach { word ->
        val forms = expandContraction(word)
        readings = readings.flatMap { head -> forms.map { head + it } }
    }
    return readings.mapTo(LinkedHashSet()) { it.joinToString(" ").removePrefix("to ").trim() }
}

/** A word's long forms: one for most contractions, two where the apostrophe hides two verbs. */
private fun expandContraction(word: String): List<String> {
    CONTRACTIONS[word]?.let { return it }
    val suffix = Regex("""'(re|ve|ll|d|m|s)$""").find(word) ?: return listOf(word.replace(Regex("""n't$"""), " not"))
    val stem = word.substring(0, suffix.range.first)
    return LONG_FORMS.getValue(suffix.groupValues[1]).map { "$stem $it" }
}

/** True when two keys differ only by a regular inflection of their first word. */
private fun sameButForTheHeadWord(a: String, b: String): Boolean {
    val wordsA = a.split(' ')
    val wordsB = b.split(' ')
    return wordsA.size == wordsB.size && wordsA.drop(1) == wordsB.drop(1) && stem(wordsA[0]) == stem(wordsB[0])
}

/**
 * The regular English inflections, undone: -ies → y, -ing/-ed/-es/-s dropped when enough of the
 * word remains, a doubled final consonant and a silent e folded. Irregular verbs stay as typed.
 */
internal fun stem(word: String): String {
    var w = word
    when {
        w.endsWith("ies") && w.length > 4 -> w = w.dropLast(3) + "y"
        w.endsWith("ing") && w.length > 5 -> w = w.dropLast(3)
        w.endsWith("ed") && w.length > 4 -> w = w.dropLast(2)
        w.endsWith("es") && w.length > 4 -> w = w.dropLast(2)
        w.endsWith("s") && !w.endsWith("ss") && w.length > 3 -> w = w.dropLast(1)
    }
    if (w.length > 3 && w[w.length - 1] == w[w.length - 2] && w.last() !in "aeiou") w = w.dropLast(1)
    return w.removeSuffix("e")
}

/** Contractions whose long form is not a suffix swap, and the suffix swaps themselves. */
private val CONTRACTIONS = mapOf(
    "can't" to listOf("cannot"), "won't" to listOf("will not"), "shan't" to listOf("shall not"),
    "ain't" to listOf("is not", "am not", "are not"), "let's" to listOf("let us"),
    "gonna" to listOf("going to"), "wanna" to listOf("want to"), "gotta" to listOf("got to"),
)
private val LONG_FORMS = mapOf(
    "re" to listOf("are"), "ve" to listOf("have"), "ll" to listOf("will"), "m" to listOf("am"),
    "d" to listOf("would", "had"), "s" to listOf("is", "has"),
)

/**
 * The hint for a typed blank: the first letter of every word with the rest blanked, so
 * "hang out" shows as "h___ o__". Punctuation inside a word is kept as it is.
 */
fun typedAnswerHint(expression: String): String =
    expression.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }.joinToString(" ") { word ->
        word.first() + "_".repeat(word.length - 1)
    }
