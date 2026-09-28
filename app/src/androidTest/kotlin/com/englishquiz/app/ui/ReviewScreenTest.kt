package com.englishquiz.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.englishquiz.app.data.local.SavedExpressionEntity
import com.englishquiz.app.ui.reader.SpeechState
import com.englishquiz.app.ui.review.ReviewScreen
import com.englishquiz.app.ui.review.ReviewUiState
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Covers 백로그 009's acceptance criteria on the list itself: a saved expression shows with its
 * contextual meaning and review state, pronunciation plays only that expression, and an empty
 * store explains itself instead of showing a blank page.
 */
@RunWith(AndroidJUnit4::class)
class ReviewScreenTest {
    @get:Rule val compose = createComposeRule()

    private val newlySaved = SavedExpressionEntity(
        id = 1,
        normalizedExpression = "questionable",
        displayExpression = "questionable",
        contextMeaning = "조금 의심스러운",
        firstSavedAtEpochMillis = 3_000L,
    )
    private val inProgress = SavedExpressionEntity(
        id = 2,
        normalizedExpression = "sketchy",
        displayExpression = "sketchy",
        contextMeaning = "수상한",
        firstSavedAtEpochMillis = 2_000L,
        lastReviewedAtEpochMillis = 2_500L,
        nextReviewAtEpochMillis = 9_000L,
    )
    private val mastered = SavedExpressionEntity(
        id = 3,
        normalizedExpression = "not my thing",
        displayExpression = "not my thing",
        contextMeaning = "내 취향은 아니다",
        firstSavedAtEpochMillis = 1_000L,
        lastReviewedAtEpochMillis = 1_500L,
        isMastered = true,
    )

    private fun show(
        state: ReviewUiState,
        speechState: SpeechState = SpeechState(ready = true),
        onPlay: (String) -> Unit = {},
        onStop: () -> Unit = {},
        onRetry: () -> Unit = {},
        onBack: () -> Unit = {},
    ) {
        compose.setContent {
            EnglishQuizTheme {
                ReviewScreen(state, speechState, onPlay, onStop, onRetry, onBack)
            }
        }
    }

    @Test
    fun showsExpressionMeaningAndReviewStateForEachSavedExpression() {
        show(ReviewUiState.Ready(listOf(newlySaved, inProgress, mastered)))

        compose.onNodeWithText("questionable").assertIsDisplayed()
        compose.onNodeWithText("조금 의심스러운").assertIsDisplayed()
        compose.onNodeWithText("새로 저장").assertIsDisplayed()

        compose.onNodeWithText("sketchy").assertIsDisplayed()
        compose.onNodeWithText("수상한").assertIsDisplayed()
        compose.onNodeWithText("학습 중").assertIsDisplayed()

        compose.onNodeWithText("not my thing").assertIsDisplayed()
        compose.onNodeWithText("내 취향은 아니다").assertIsDisplayed()
        compose.onNodeWithText("암기 완료").assertIsDisplayed()

        // Only an expression still in rotation shows a scheduled date, so a single node matches.
        // The date itself is device-timezone dependent, so the prefix is what this asserts.
        compose.onNodeWithText("다음 복습: ", substring = true).assertIsDisplayed()

        compose.onNodeWithText("3개 · 최근 저장순").assertIsDisplayed()
    }

    @Test
    fun pronunciationPlaysOnlyTheTappedExpression() {
        val played = mutableListOf<String>()
        show(ReviewUiState.Ready(listOf(newlySaved, inProgress)), onPlay = { played += it })

        compose.onNodeWithContentDescription("sketchy 발음 듣기").performClick()

        assertEquals(listOf("sketchy"), played)
    }

    @Test
    fun pronunciationIsDisabledUntilSpeechIsReady() {
        show(
            state = ReviewUiState.Ready(listOf(newlySaved)),
            speechState = SpeechState(ready = false, error = "음성 사용 불가"),
        )

        compose.onNodeWithText("음성 사용 불가").assertIsDisplayed()
        compose.onNodeWithContentDescription("questionable 발음 듣기").assertIsNotEnabled()
    }

    @Test
    fun emptyStoreExplainsHowExpressionsGetSaved() {
        show(ReviewUiState.Ready(emptyList()))

        compose.onNodeWithText(
            "아직 저장한 표현이 없어요. Conversation이나 Story에서 모르는 표현의 뜻을 확인하면 자동으로 저장돼요.",
        ).assertIsDisplayed()
    }

    @Test
    fun loadFailureOffersRetryInsteadOfAnEmptyList() {
        var retries = 0
        show(ReviewUiState.Error, onRetry = { retries++ })

        compose.onNodeWithText("저장한 표현을 불러오지 못했어요.").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").performClick()

        assertEquals(1, retries)
    }

    @Test
    fun backLeavesTheReviewList() {
        var backs = 0
        show(ReviewUiState.Ready(listOf(newlySaved)), onBack = { backs++ })

        compose.onNodeWithContentDescription("홈으로").performClick()

        assertEquals(1, backs)
    }
}
