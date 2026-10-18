package com.englishquiz.app.ui.reader

import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.Language

/**
 * One tappable (or non-tappable) unit of a reader segment.
 *
 * [display] is the original text shown on screen, punctuation included.
 * [lookup] is the text used for the meaning request: leading/trailing punctuation stripped.
 * A token whose [lookup] is empty (e.g. a lone punctuation mark) cannot be tapped.
 */
data class ReaderToken(
    val display: String,
    val lookup: String,
    val segmentIndex: Int,
    val startIndex: Int,
    val endIndex: Int,
    val isExpression: Boolean,
) {
    val isTappable: Boolean get() = lookup.isNotEmpty()
}

/** Marks stripped from a token's edges before lookup: ASCII first, then the CJK marks of a Chinese passage (백로그 071). */
internal val EDGE_PUNCTUATION = charArrayOf(
    '.', ',', '!', '?', ';', ':', '"', '\'', '“', '”', '‘', '’', '(', ')', '[', ']', '…', '-', '—',
    '。', '，', '！', '？', '、', '：', '；', '（', '）', '《', '》', '「', '」', '『', '』', '【', '】',
)

internal fun String.trimEdgePunctuation(): String = trim(*EDGE_PUNCTUATION).trim()

/**
 * Splits [segmentText] into [ReaderToken]s. Any [ContentExpression] that targets [segmentIndex]
 * and whose range is valid and non-overlapping with an earlier one becomes a single token; the
 * remaining text is split into whitespace-delimited word tokens in English, or by [chineseTokens]
 * — longest match against [knownWords], then single characters — in Chinese (백로그 071).
 */
fun tokenize(
    segmentText: String,
    expressions: List<ContentExpression>,
    segmentIndex: Int,
    language: Language = Language.ENGLISH,
    knownWords: Collection<String> = emptyList(),
): List<ReaderToken> {
    if (segmentText.isEmpty()) return emptyList()

    val spans = expressions
        .asSequence()
        .filter { it.segmentIndex == segmentIndex }
        .filter { it.startIndex in 0 until it.endIndex && it.endIndex <= segmentText.length }
        .sortedBy { it.startIndex }
        .fold(mutableListOf<ContentExpression>()) { accepted, candidate ->
            val previous = accepted.lastOrNull()
            if (previous == null || candidate.startIndex >= previous.endIndex) accepted.add(candidate)
            accepted
        }

    val plain: (Int, Int) -> List<ReaderToken> = when (language) {
        Language.ENGLISH -> { from, to -> wordTokens(segmentText, from, to, segmentIndex) }
        Language.CHINESE -> { from, to -> chineseTokens(segmentText, from, to, segmentIndex, knownWords) }
    }
    val tokens = mutableListOf<ReaderToken>()
    var cursor = 0
    for (span in spans) {
        if (span.startIndex > cursor) tokens += plain(cursor, span.startIndex)
        val text = segmentText.substring(span.startIndex, span.endIndex)
        tokens += ReaderToken(
            display = text,
            lookup = text.trimEdgePunctuation(),
            segmentIndex = segmentIndex,
            startIndex = span.startIndex,
            endIndex = span.endIndex,
            isExpression = true,
        )
        cursor = span.endIndex
    }
    if (cursor < segmentText.length) tokens += plain(cursor, segmentText.length)
    return tokens
}

private fun wordTokens(text: String, from: Int, to: Int, segmentIndex: Int): List<ReaderToken> {
    val tokens = mutableListOf<ReaderToken>()
    var index = from
    while (index < to) {
        while (index < to && text[index].isWhitespace()) index++
        if (index >= to) break
        var end = index
        while (end < to && !text[end].isWhitespace()) end++
        val word = text.substring(index, end)
        tokens += ReaderToken(
            display = word,
            lookup = word.trimEdgePunctuation(),
            segmentIndex = segmentIndex,
            startIndex = index,
            endIndex = end,
            isExpression = false,
        )
        index = end
    }
    return tokens
}
