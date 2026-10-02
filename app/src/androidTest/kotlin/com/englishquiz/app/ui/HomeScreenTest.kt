package com.englishquiz.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.local.LearningSessionEntity
import com.englishquiz.app.data.preferences.GameProgress
import com.englishquiz.app.domain.game.GameStatsPolicy
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
    fun gameCardsShowLevelMissionsBossAndBadgesAndRouteTheirTaps() {
        val taps = mutableListOf<String>()
        val stats = GameStatsPolicy.compute(
            sessions = listOf(
                LearningSessionEntity(
                    learningDate = "2026-10-03",
                    completedAtEpochMillis = 1L,
                    learnedExpressionCount = 10,
                    newlySavedExpressionCount = 1,
                    quizCorrectCount = 9,
                    quizQuestionCount = 10,
                    mode = "story",
                    score = 180,
                    maxCombo = 6,
                ),
            ),
            expressions = emptyList(),
            progress = GameProgress(shields = 1, pointsSpent = 0),
            todayIso = "2026-10-03",
        )
        composeRule.setContent {
            EnglishQuizTheme {
                HomeScreen(
                    summary = HomeSummary(
                        streakDays = 1,
                        savedExpressionCount = 0,
                        masteredExpressionCount = 0,
                        game = stats,
                    ),
                    onConversationClick = {},
                    onStoryClick = {},
                    onReviewClick = {},
                    onBuyShield = { taps += "shield" },
                    onBadgesClick = { taps += "badges" },
                    onBossClick = { taps += "boss" },
                )
            }
        }

        // 180 quiz points plus the finished (20) and 80% (30) missions: level 1 with 230 of 300.
        composeRule.onNodeWithText("Lv.1 공항 도착").assertIsDisplayed()
        composeRule.onNodeWithText("230점").assertIsDisplayed()
        composeRule.onNodeWithText("다음 레벨까지 70점").assertIsDisplayed()
        composeRule.onNodeWithText("보호권 1개 · 쓸 수 있는 점수 230점").assertIsDisplayed()
        // Below the shield's price, the shop button does nothing.
        composeRule.onNodeWithText("보호권 사기 (300점)").assertIsNotEnabled()
        composeRule.onNodeWithText("오늘의 미션").assertIsDisplayed()
        composeRule.onNodeWithText("퀴즈 정답률 80% 이상").assertIsDisplayed()
        // A Saturday with no boss session yet: the card opens the boss.
        composeRule.onNodeWithText("주간 보스전", substring = true).performScrollTo().performClick()
        composeRule.onNodeWithText("배지 1 / 10").performScrollTo().performClick()
        composeRule.onNodeWithText("새 배지 1").assertIsDisplayed()
        assertEquals(listOf("boss", "badges"), taps)
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
