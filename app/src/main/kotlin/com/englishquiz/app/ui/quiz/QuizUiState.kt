package com.englishquiz.app.ui.quiz

import com.englishquiz.app.domain.game.GrowthStage
import com.englishquiz.app.domain.game.QuizScore
import com.englishquiz.app.domain.quiz.QuizOption
import com.englishquiz.app.domain.quiz.QuizQuestion

/** UI-facing quiz state. [QuizScreen] renders each variant; see that file for the layout. */
sealed interface QuizUiState {
    data object Loading : QuizUiState
    data object Error : QuizUiState

    /** 오늘 복습할 표현이 없을 때. Not an error, so the screen offers to finish immediately. */
    data object Empty : QuizUiState

    data class InProgress(
        val questionNumber: Int,
        val totalQuestions: Int,
        val question: QuizQuestion,
        val selectedOption: QuizOption?,
        val isCorrect: Boolean?,
        val recordFailed: Boolean = false,
        /** The running score (백로그 035); [QuizScore.lastEarned] is what the shown answer paid. */
        val score: QuizScore = QuizScore(),
        /** How the answered expression grew or fell back (백로그 036), once its record is written. */
        val growth: GrowthChange? = null,
    ) : QuizUiState
}

/** The expression's stage before and after the answer the learner just gave. */
data class GrowthChange(val expression: String, val before: GrowthStage, val after: GrowthStage) {
    val grew: Boolean get() = after.ordinal > before.ordinal
}

internal sealed interface QuizLoadState {
    data object Loading : QuizLoadState
    data object Error : QuizLoadState
    data class Loaded(val questions: List<QuizQuestion>) : QuizLoadState
}

internal fun quizUiState(
    loadState: QuizLoadState,
    questions: List<QuizQuestion>?,
    index: Int,
    totalQuestions: Int,
    selectedOption: QuizOption?,
    recordFailed: Boolean,
    score: QuizScore,
    growth: GrowthChange?,
): QuizUiState = when (loadState) {
    QuizLoadState.Loading -> QuizUiState.Loading
    QuizLoadState.Error -> QuizUiState.Error
    is QuizLoadState.Loaded -> {
        val question = questions?.getOrNull(index)
        if (question == null) {
            QuizUiState.Empty
        } else {
            // Questions already recorded have dropped out of a rebuilt list, so the number the
            // learner sees comes from how many of the original set are left.
            val answeredBefore = (totalQuestions - questions.size).coerceAtLeast(0)
            QuizUiState.InProgress(
                questionNumber = answeredBefore + index + 1,
                totalQuestions = totalQuestions.coerceAtLeast(questions.size),
                question = question,
                selectedOption = selectedOption,
                isCorrect = selectedOption?.isCorrect,
                recordFailed = recordFailed,
                score = score,
                growth = growth,
            )
        }
    }
}
