package com.englishquiz.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.englishquiz.app.domain.quiz.QuizOption
import com.englishquiz.app.domain.quiz.QuizQuestion
import com.englishquiz.app.domain.quiz.QuizQuestionType
import com.englishquiz.app.ui.quiz.QuizScreen
import com.englishquiz.app.ui.quiz.QuizUiState
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Rendering tests for the stateless quiz screen. The route's own bookkeeping (recording answers,
 * progress across recreation, the finished summary) is covered by QuizRouteTest.
 */
@RunWith(AndroidJUnit4::class)
class QuizScreenTest {
    @get:Rule val compose = createComposeRule()

    private val meaningQuestion = QuizQuestion(
        expression = "sketchy",
        type = QuizQuestionType.MULTIPLE_CHOICE,
        questionText = "sketchy",
        options = listOf(QuizOption("수상한", true), QuizOption("놀다", false)),
        explanation = EXPLANATION,
    )

    private val blankQuestion = QuizQuestion(
        expression = "sketchy",
        type = QuizQuestionType.FILL_IN_BLANK,
        questionText = "That sounds ____.",
        options = listOf(QuizOption("sketchy", true), QuizOption("hang out", false)),
        explanation = EXPLANATION,
    )

    @Test
    fun showsProgressAndMeaningInstruction() {
        showQuestion(meaningQuestion, questionNumber = 1, selected = null)

        compose.onNodeWithText("1 / 2").assertIsDisplayed()
        compose.onNodeWithText("다음 표현의 뜻으로 알맞은 것을 고르세요.").assertIsDisplayed()
    }

    @Test
    fun correctAnswerShowsFeedbackAndOffersTheNextQuestion() {
        showQuestion(meaningQuestion, questionNumber = 1, selected = meaningQuestion.options[0])

        compose.onNodeWithText("정답이에요!").performScrollTo().assertIsDisplayed()
        // The meaning is a two-line explanation; the card shows all of it, not just the first line.
        compose.onNodeWithText(EXPLANATION).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("다음 문제").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun theLastQuestionOffersTheResultScreenInsteadOfAnotherQuestion() {
        showQuestion(meaningQuestion, questionNumber = 2, selected = meaningQuestion.options[0])

        compose.onNodeWithText("결과 보기").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun incorrectAnswerShowsFeedbackAndLocksTheOptions() {
        showQuestion(meaningQuestion, questionNumber = 2, selected = meaningQuestion.options[1])

        compose.onNodeWithText("2 / 2").assertIsDisplayed()
        compose.onNodeWithText("아쉬워요, 오답이에요.").performScrollTo().assertIsDisplayed()
        // Once answered, neither option can be tapped again.
        compose.onNodeWithText("수상한").assertIsNotEnabled()
        compose.onNodeWithText("놀다").assertIsNotEnabled()
    }

    @Test
    fun fillInBlankAsksForTheExpressionThatFitsTheSentence() {
        showQuestion(blankQuestion, questionNumber = 1, selected = null)

        compose.onNodeWithText("빈칸에 들어갈 표현을 고르세요.").assertIsDisplayed()
        compose.onNodeWithText("That sounds ____.").assertIsDisplayed()
        compose.onNodeWithText("sketchy").assertIsDisplayed()
        compose.onNodeWithText("hang out").assertIsDisplayed()
    }

    @Test
    fun failedRecordingIsSurfacedWithItsOwnRetry() {
        var retried = false
        compose.setContent {
            EnglishQuizTheme {
                QuizScreen(
                    state = QuizUiState.InProgress(
                        questionNumber = 1,
                        totalQuestions = 1,
                        question = meaningQuestion,
                        selectedOption = meaningQuestion.options[0],
                        isCorrect = true,
                        recordFailed = true,
                    ),
                    onSelectOption = {},
                    onNext = {},
                    onRetryRecord = { retried = true },
                    onEmptyContinue = {},
                    onRetry = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("답변을 학습 기록에 저장하지 못했어요.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("답변 다시 기록").performScrollTo().performClick()
        assertTrue(retried)
    }

    @Test
    fun emptyQuizSetShowsGuidanceAndFinishes() {
        var finished = false
        compose.setContent {
            EnglishQuizTheme {
                QuizScreen(
                    state = QuizUiState.Empty,
                    onSelectOption = {},
                    onNext = {},
                    onRetryRecord = {},
                    onEmptyContinue = { finished = true },
                    onRetry = {},
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("오늘 복습할 표현이 없어요.").assertIsDisplayed()
        compose.onNodeWithText("완료").performClick()
        assertTrue(finished)
    }

    @Test
    fun errorStateOffersRetry() {
        var retried = false
        compose.setContent {
            EnglishQuizTheme {
                QuizScreen(
                    state = QuizUiState.Error,
                    onSelectOption = {},
                    onNext = {},
                    onRetryRecord = {},
                    onEmptyContinue = {},
                    onRetry = { retried = true },
                    onBack = {},
                )
            }
        }

        compose.onNodeWithText("문제를 불러오지 못했어요.").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").performClick()
        assertTrue(retried)
    }

    private companion object {
        /** The shape QuizBuilder produces: the expression, then the model's own sentence. */
        const val EXPLANATION = "\"sketchy\"의 뜻이에요.\n수상해 보인다는 뜻입니다."
    }

    private fun showQuestion(question: QuizQuestion, questionNumber: Int, selected: QuizOption?) {
        compose.setContent {
            EnglishQuizTheme {
                QuizScreen(
                    state = QuizUiState.InProgress(
                        questionNumber = questionNumber,
                        totalQuestions = 2,
                        question = question,
                        selectedOption = selected,
                        isCorrect = selected?.isCorrect,
                    ),
                    onSelectOption = {},
                    onNext = {},
                    onRetryRecord = {},
                    onEmptyContinue = {},
                    onRetry = {},
                    onBack = {},
                )
            }
        }
    }
}
