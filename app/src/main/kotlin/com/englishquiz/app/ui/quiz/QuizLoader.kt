package com.englishquiz.app.ui.quiz

import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.local.SavedExpressionEntity
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.domain.quiz.QuizBuilder
import com.englishquiz.app.domain.quiz.QuizQuestion
import com.englishquiz.app.domain.review.ReviewQueue

/**
 * Enrols today's passage expressions (백로그 031) and builds the question list for [QuizRoute].
 * Kept out of the route so its state handling stays readable; see the route for what each
 * argument means.
 */
internal suspend fun loadQuizQuestions(
    repository: LearningRepository,
    todayContent: LearningContent?,
    nowEpochMillis: Long,
    shuffleSeed: Long,
    enrolExpressions: Boolean,
    onExpressionsEnrolled: (Int) -> Unit,
    questionSource: suspend (LearningRepository, Long) -> List<SavedExpressionEntity>,
    maxQuestions: Int,
    skipComprehension: suspend () -> Boolean,
    comprehensionDone: Int,
    askCards: Boolean,
): QuizLoad {
    // Re-runs (retry, recreation) find the rows already there and report zero.
    if (enrolExpressions && todayContent != null) {
        onExpressionsEnrolled(enrolContentExpressions(todayContent, repository, nowEpochMillis))
    }
    val targets = questionSource(repository, nowEpochMillis)
    val pool = repository.listSavedExpressions()
    val content = if (todayContent != null && skipComprehension()) todayContent.copy(comprehension = emptyList()) else todayContent
    // Cards already shown today (백로그 048) count against the day's five, on a second entry
    // into the quiz as much as after a recreation in the middle of it.
    val queue = ReviewQueue.of(pool, nowEpochMillis)
    val questions = QuizBuilder.build(content, targets, pool, shuffleSeed, maxQuestions, askCards, queue.firstMeetingsShownToday)
        .questions.drop(comprehensionDone)
    // The daily quiz tells the learner what today holds (백로그 060); the boss and the practice do not.
    val notice = if (askCards && enrolExpressions) queue.todayPlan(maxQuestions, QuizBuilder.NEW_PER_DAY).let { plan ->
        listOfNotNull(plan.notice(queue), plan.reason(queue)).joinToString("\n").ifEmpty { null }
    } else {
        null
    }
    return QuizLoad(questions, notice)
}

/** What the quiz starts with: the questions and, for the daily quiz, the one-line plan for today. */
internal data class QuizLoad(val questions: List<QuizQuestion>, val notice: String?)
