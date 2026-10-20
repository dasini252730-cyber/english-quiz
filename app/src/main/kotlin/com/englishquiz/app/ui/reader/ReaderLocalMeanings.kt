package com.englishquiz.app.ui.reader

import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.foldTypography

/**
 * The meaning the passage itself carries for [token], or null (백로그 024). An annotated expression
 * is matched by position first, so the meaning belongs to the very phrase that was tapped, then
 * by text; a glossary word is matched by folded text with its edge punctuation removed, so
 * "sounds." finds "sounds". None of this costs a call.
 */
internal fun LearningContent.localMeaningFor(token: ReaderToken): String? {
    expressions.firstOrNull {
        it.segmentIndex == token.segmentIndex &&
            it.startIndex == token.startIndex &&
            it.endIndex == token.endIndex
    }?.let { return it.meaning }
    val key = token.lookup.lookupKey()
    if (key.isEmpty()) return null
    expressions.firstOrNull { it.text.lookupKey() == key }?.let { return it.meaning }
    return glossary.firstOrNull { it.word.lookupKey() == key }?.meaning
}

/** The passage's short gloss for an annotated [token] (백로그 046), or "" for a glossary word or none. */
internal fun LearningContent.localShortMeaningFor(token: ReaderToken): String = localAnnotationFor(token)?.shortMeaning ?: ""

/** The passage's kind for an annotated [token] (백로그 059), or "" for a glossary word or none. */
internal fun LearningContent.localKindFor(token: ReaderToken): String = localAnnotationFor(token)?.kind ?: ""

/** The annotated expression [token] is, by position first and then by text, or null for a glossary word. */
private fun LearningContent.localAnnotationFor(token: ReaderToken): ContentExpression? {
    expressions.firstOrNull {
        it.segmentIndex == token.segmentIndex &&
            it.startIndex == token.startIndex &&
            it.endIndex == token.endIndex
    }?.let { return it }
    val key = token.lookup.lookupKey()
    if (key.isEmpty()) return null
    return expressions.firstOrNull { it.text.lookupKey() == key }
}

/** The marks a lookup key may still carry at its edges after [foldTypography]: ASCII, then CJK (백로그 071). */
private val LOOKUP_EDGE_PUNCTUATION = charArrayOf(
    '.', ',', '!', '?', ';', ':', '"', '\'', '(', ')',
    '。', '，', '！', '？', '、', '：', '；', '（', '）', '《', '》', '「', '」', '『', '』', '【', '】',
)

private fun String.lookupKey(): String = foldTypography().trim().trim(*LOOKUP_EDGE_PUNCTUATION).trim()
