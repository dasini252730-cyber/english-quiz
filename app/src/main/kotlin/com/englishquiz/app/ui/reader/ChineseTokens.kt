package com.englishquiz.app.ui.reader

/**
 * Cuts the plain (not annotated) part of a Chinese segment into tappable units (백로그 071).
 *
 * Chinese has no spaces between words, so the text is read left to right and, at each position,
 * the longest of [knownWords] that starts there — a glossary word or a saved expression — is one
 * token; a character nobody has named is a token of its own. Punctuation runs are tokens that
 * cannot be tapped, so a lookup never carries a "。" or "，" into the meaning request. Latin
 * letters and digits inside a Chinese line ("WiFi", "3") stay together as one token, as in English.
 */
internal fun chineseTokens(
    text: String,
    from: Int,
    to: Int,
    segmentIndex: Int,
    knownWords: Collection<String>,
): List<ReaderToken> {
    val dictionary = KnownWords(knownWords)
    val tokens = mutableListOf<ReaderToken>()
    var index = from
    while (index < to) {
        val character = text[index]
        val end = when {
            character.isWhitespace() -> {
                index++
                continue
            }
            else -> dictionary.longestMatchEnd(text, index, to) ?: when {
                character.isReaderPunctuation() -> runEnd(text, index, to) { it.isReaderPunctuation() }
                character.isHan() -> index + 1
                else -> runEnd(text, index, to) { !it.isWhitespace() && !it.isReaderPunctuation() && !it.isHan() }
            }
        }
        val piece = text.substring(index, end)
        tokens += ReaderToken(
            display = piece,
            lookup = if (piece.all { it.isReaderPunctuation() }) "" else piece.trimEdgePunctuation(),
            segmentIndex = segmentIndex,
            startIndex = index,
            endIndex = end,
            isExpression = false,
        )
        index = end
    }
    return tokens
}

/** The index just past the run of characters from [start] that satisfy [inRun]. */
private inline fun runEnd(text: String, start: Int, to: Int, inRun: (Char) -> Boolean): Int {
    var end = start
    while (end < to && inRun(text[end])) end++
    return end
}

/** The learner's known words grouped by length, so the longest match at a position is one set lookup per length. */
private class KnownWords(words: Collection<String>) {
    private val byLength: Map<Int, Set<String>> = words
        .asSequence()
        .map { it.trimEdgePunctuation() }
        .filter { it.isNotEmpty() }
        .groupBy { it.length }
        .mapValues { (_, same) -> same.toSet() }
    private val lengths: List<Int> = byLength.keys.sortedDescending()

    /** The end index of the longest known word starting at [start] and ending by [to], or null. */
    fun longestMatchEnd(text: String, start: Int, to: Int): Int? {
        for (length in lengths) {
            val end = start + length
            if (end > to) continue
            if (text.substring(start, end) in byLength.getValue(length)) return end
        }
        return null
    }
}

private fun Char.isHan(): Boolean = Character.UnicodeScript.of(code) == Character.UnicodeScript.HAN

/** Any punctuation, ASCII or CJK: the reader's own edge marks plus everything Unicode files under punctuation. */
internal fun Char.isReaderPunctuation(): Boolean {
    if (this in EDGE_PUNCTUATION) return true
    return when (Character.getType(this).toByte()) {
        Character.CONNECTOR_PUNCTUATION,
        Character.DASH_PUNCTUATION,
        Character.START_PUNCTUATION,
        Character.END_PUNCTUATION,
        Character.INITIAL_QUOTE_PUNCTUATION,
        Character.FINAL_QUOTE_PUNCTUATION,
        Character.OTHER_PUNCTUATION,
        -> true
        else -> false
    }
}
