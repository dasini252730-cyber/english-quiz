package com.englishquiz.app.data.repository

import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.isKoreanPassage
import com.englishquiz.app.data.ai.parseContentResponse
import com.englishquiz.app.data.local.DailyContentEntity
import com.englishquiz.app.data.local.LearningSessionEntity
import com.englishquiz.app.data.local.SavedExpressionEntity
import com.englishquiz.app.domain.review.ReviewProgress
import com.englishquiz.app.domain.session.LearningSessionSummary
import org.json.JSONObject

/*
 * Row <-> domain shapes for [LearningRepository]. The repository decides what to read and write;
 * these only say how a Room row looks to the review policy, the session policies and the reader.
 */

/** The review schedule a row carries, in the shape the review policy reads. */
internal fun SavedExpressionEntity.toReviewProgress(): ReviewProgress = ReviewProgress(
    lastReviewedAtEpochMillis = lastReviewedAtEpochMillis,
    nextReviewAtEpochMillis = nextReviewAtEpochMillis,
    consecutiveCorrectCount = consecutiveCorrectCount,
    incorrectCount = incorrectCount,
    isMastered = isMastered,
)

/** The same row with [progress] written over its review schedule; everything else stays. */
internal fun SavedExpressionEntity.withProgress(progress: ReviewProgress): SavedExpressionEntity = copy(
    lastReviewedAtEpochMillis = progress.lastReviewedAtEpochMillis,
    nextReviewAtEpochMillis = progress.nextReviewAtEpochMillis,
    consecutiveCorrectCount = progress.consecutiveCorrectCount,
    incorrectCount = progress.incorrectCount,
    isMastered = progress.isMastered,
)

/** A finished session as the domain policies read it (백로그 013/034/038). */
internal fun LearningSessionEntity.toSummary(): LearningSessionSummary = LearningSessionSummary(
    learnedExpressionCount = learnedExpressionCount,
    newlySavedExpressionCount = newlySavedExpressionCount,
    quizCorrectCount = quizCorrectCount,
    quizQuestionCount = quizQuestionCount,
    score = score,
    maxCombo = maxCombo,
)

/**
 * The passage a stored row holds, or null when this build can no longer read it or it was
 * generated in Korean (백로그 051): either is as good as no row, and the next save replaces it.
 */
internal fun DailyContentEntity.toContentOrNull(mode: ContentMode): LearningContent? = try {
    parseContentResponse(JSONObject(contentJson), mode).takeIf { !it.isKoreanPassage }
} catch (_: Exception) {
    null
}
