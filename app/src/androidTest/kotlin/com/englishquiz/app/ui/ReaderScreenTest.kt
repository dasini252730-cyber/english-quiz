package com.englishquiz.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.ui.reader.MeaningUiState
import com.englishquiz.app.ui.reader.ReaderScreen
import com.englishquiz.app.ui.reader.SaveStatus
import com.englishquiz.app.ui.reader.SpeechState
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The Reader screen on its own: layout, playback requests and speech state. [ReaderRouteTest] covers the route. */
@RunWith(AndroidJUnit4::class)
class ReaderScreenTest {
    @get:Rule val compose = createComposeRule()
    private val segments = listOf(
        ContentSegment("Emma", "That sounds sketchy."),
        ContentSegment("Sam", "Let's find another place."),
    )
    private val storyContent = LearningContent("A cafe", ContentMode.STORY, segments, emptyList())

    @Test fun conversationShowsSpeakersAndPassesOrderedPlaybackRequests() {
        val requests = mutableListOf<List<String>>()
        var quizCount = 0
        compose.setContent {
            EnglishQuizTheme {
                ReaderScreen(
                    LearningContent("A cafe", ContentMode.CONVERSATION, segments, emptyList()), SpeechState(ready = true),
                    onPlay = { requests += it.map { line -> line.text } }, onStop = {}, onBack = {}, onQuiz = { quizCount++ },
                )
            }
        }
        compose.onNodeWithText("Emma").assertIsDisplayed()
        compose.onNodeWithContentDescription("전체 재생").performClick()
        compose.onNodeWithContentDescription("2번 문장 재생").performClick()
        compose.onNodeWithText("다음 단계: 퀴즈").performClick()
        assertEquals(listOf(segments.map { it.text }, listOf(segments[1].text)), requests)
        assertEquals(1, quizCount)
    }

    @Test fun storyRemainsReadableWhenSpeechIsUnavailable() {
        compose.setContent {
            EnglishQuizTheme {
                ReaderScreen(storyContent, SpeechState(error = "음성 사용 불가"), onPlay = {}, onStop = {}, onBack = {})
            }
        }
        compose.onNodeWithText("Emma").assertDoesNotExist()
        // The segment is now rendered as tappable word tokens instead of one Text node.
        compose.onNodeWithText("That").assertIsDisplayed()
        compose.onNodeWithText("sketchy.").assertIsDisplayed()
        compose.onNodeWithText("음성 사용 불가").assertIsDisplayed()
        compose.onNodeWithContentDescription("전체 재생").assertIsNotEnabled()
        compose.onNodeWithText("다음 단계: 퀴즈").assertDoesNotExist()
    }

    @Test fun pronunciationButtonPlaysOnlyTheExpressionNotTheWholeSentence() {
        val requests = mutableListOf<List<String>>()
        val successState = MeaningUiState.Success("sketchy", "수상쩍다", SaveStatus.SAVED)
        compose.setContent {
            EnglishQuizTheme {
                ReaderScreen(
                    storyContent, SpeechState(ready = true),
                    onPlay = { requests += it.map { line -> line.text } }, onStop = {}, onBack = {}, meaningState = successState,
                )
            }
        }
        compose.onNodeWithContentDescription("발음 듣기").performClick()
        assertEquals(listOf(listOf("sketchy")), requests)
    }
}
