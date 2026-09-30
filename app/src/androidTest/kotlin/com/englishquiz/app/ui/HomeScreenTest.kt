package com.englishquiz.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HomeScreenTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun showsSummaryAndOpensSelectedLearningDestination() {
        val destinations = mutableListOf<String>()
        composeRule.setContent {
            EnglishQuizTheme {
                HomeScreen(
                    summary = HomeSummary(
                        streakDays = 3,
                        savedExpressionCount = 12,
                        masteredExpressionCount = 4,
                    ),
                    onConversationClick = { destinations += "conversation" },
                    onStoryClick = { destinations += "story" },
                    onReviewClick = { destinations += "review" },
                )
            }
        }

        composeRule.onNodeWithText("StoryEnglish").assertIsDisplayed()
        composeRule.onNodeWithText("3일").assertIsDisplayed()
        composeRule.onNodeWithText("연속 학습").assertIsDisplayed()
        composeRule.onNodeWithText("12개").assertIsDisplayed()
        composeRule.onNodeWithText("저장 표현").assertIsDisplayed()
        composeRule.onNodeWithText("4개").assertIsDisplayed()
        composeRule.onNodeWithText("암기 완료").assertIsDisplayed()

        composeRule.onNodeWithText("Conversation").performClick()
        composeRule.onNodeWithText("Story").performClick()
        composeRule.onNodeWithText("복습함").performClick()

        assertEquals(listOf("conversation", "story", "review"), destinations)
    }

    @Test
    fun eachModeShowsItsLevelWithButtonsThatAdjustItWithoutStartingASession() {
        val changes = mutableListOf<Pair<ContentMode, Int>>()
        val answers = mutableListOf<Pair<ContentMode, Boolean>>()
        val destinations = mutableListOf<String>()
        composeRule.setContent {
            EnglishQuizTheme {
                HomeScreen(
                    summary = HomeSummary(
                        streakDays = null,
                        savedExpressionCount = 0,
                        masteredExpressionCount = 0,
                        levels = mapOf(
                            ContentMode.CONVERSATION to HomeLevel(level = 3),
                            ContentMode.STORY to HomeLevel(level = 5, suggestedLevel = 4),
                        ),
                    ),
                    onConversationClick = { destinations += "conversation" },
                    onStoryClick = { destinations += "story" },
                    onReviewClick = {},
                    onLevelChange = { mode, level -> changes += mode to level },
                    onSuggestionAnswer = { mode, accepted -> answers += mode to accepted },
                )
            }
        }

        composeRule.onNodeWithText("레벨 3 / 5").assertIsDisplayed()
        composeRule.onNodeWithText("레벨 5 / 5").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Conversation 레벨 낮추기").performClick()
        composeRule.onNodeWithContentDescription("Conversation 레벨 높이기").performClick()
        // At the top of the scale, "higher" is disabled: tapping it changes nothing — and, below,
        // does not fall through to the card either.
        composeRule.onNodeWithContentDescription("Story 레벨 높이기").assertIsNotEnabled().performClick()
        assertEquals(listOf(ContentMode.CONVERSATION to 2, ContentMode.CONVERSATION to 4), changes)

        // The policy's suggestion (백로그 013/034) is answered here, never applied on its own.
        composeRule.onNodeWithText("최근 결과를 보니 레벨 4로 낮춰볼까요?").assertIsDisplayed()
        composeRule.onNodeWithText("레벨 4").performClick()
        composeRule.onNodeWithText("유지").performClick()
        assertEquals(listOf(ContentMode.STORY to true, ContentMode.STORY to false), answers)

        // None of those taps opened a session.
        assertEquals(emptyList<String>(), destinations)
    }
}
