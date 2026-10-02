package com.englishquiz.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.englishquiz.app.domain.game.Badge
import com.englishquiz.app.ui.badge.BadgeScreen
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 백로그 039: every badge is listed, earned ones with their emoji and the rest locked. */
@RunWith(AndroidJUnit4::class)
class BadgeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun listsEarnedAndLockedBadgesAndGoesBack() {
        var back = false
        compose.setContent {
            EnglishQuizTheme {
                BadgeScreen(earned = setOf(Badge.FIRST_SESSION), onBack = { back = true })
            }
        }

        compose.onNodeWithText("배지 1 / ${Badge.entries.size}").assertIsDisplayed()
        compose.onNodeWithText(Badge.FIRST_SESSION.emoji).assertIsDisplayed()
        compose.onNodeWithText("첫 학습을 끝냈어요").assertIsDisplayed()
        // A locked badge still shows its title and what it takes, behind a lock.
        compose.onNodeWithText("일주일 연속").assertIsDisplayed()
        compose.onNodeWithText("7일 연속으로 학습했어요").assertIsDisplayed()

        compose.onNodeWithContentDescription("홈으로").performClick()
        assertTrue(back)
    }
}
