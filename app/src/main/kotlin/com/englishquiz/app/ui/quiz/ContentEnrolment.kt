package com.englishquiz.app.ui.quiz

import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.ui.reader.localMeaningFor
import com.englishquiz.app.ui.reader.localShortMeaningFor
import com.englishquiz.app.ui.reader.tokenize
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Saves every annotated expression of [content] exactly as a tap on it would (백로그 031): the
 * token text the Reader cuts out of the segment, with the meaning the passage itself carries.
 * Going through the Reader's tokenizer is what keeps one expression to one row — the model's
 * annotation text may carry edge punctuation the Reader strips, and two spellings of "get a grip"
 * would otherwise sit side by side in the review box (요구사항 22).
 *
 * An expression already saved — tapped today, or learnt on an earlier day — is left as it is.
 * Returns how many were saved by this call, so the session can keep "새로 저장한 표현" as the
 * count of what the learner actually tapped: that number also feeds difficulty adjustment
 * (백로그 013), which must not read the passage's own five as five unfamiliar phrases.
 *
 * The count is only right if every row written is reported, so the whole pass runs to the end
 * even when the caller is cancelled mid-way (the same reason a tap's save is [NonCancellable]),
 * and a row the store refuses is skipped and not counted rather than ending the pass early.
 */
internal suspend fun enrolContentExpressions(
    content: LearningContent,
    repository: LearningRepository,
    savedAtEpochMillis: Long,
): Int = withContext(NonCancellable) {
    var enrolled = 0
    content.segments.forEachIndexed { segmentIndex, segment ->
        tokenize(segment.text, content.expressions, segmentIndex)
            .filter { it.isExpression && it.lookup.isNotBlank() }
            .forEach { token ->
                val meaning = content.localMeaningFor(token) ?: return@forEach
                val added = try {
                    repository.saveExpressionIfNew(
                        token.lookup, meaning, savedAtEpochMillis, segment.text, content.localShortMeaningFor(token),
                    )
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    false
                }
                if (added) enrolled += 1
            }
    }
    enrolled
}
