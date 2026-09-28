package com.englishquiz.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
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
}
