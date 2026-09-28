package com.englishquiz.app.ui

import android.view.WindowInsets
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.ui.reader.ReaderScreen
import com.englishquiz.app.ui.reader.SpeechState
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A `targetSdk` 35+ app draws edge to edge whether it asks or not, so without a `safeDrawing`
 * inset the reader's header lands under the status bar and its bottom button under the navigation
 * bar, where the system swallows the tap. Found on a Galaxy S26, whose status bar is taller than
 * the emulator's (백로그 019).
 */
@RunWith(AndroidJUnit4::class)
class SystemBarInsetsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val segments = listOf(
        ContentSegment("Emma", "That sounds sketchy."),
        ContentSegment("Noah", "It really does."),
    )

    @Test
    fun readerKeepsItsHeaderAndBottomButtonClearOfTheSystemBars() {
        compose.setContent {
            EnglishQuizTheme {
                ReaderScreen(
                    LearningContent("A cafe", ContentMode.CONVERSATION, segments, emptyList()),
                    SpeechState(ready = true),
                    onPlay = {},
                    onStop = {},
                    onBack = {},
                    onQuiz = {},
                )
            }
        }

        val bars = systemBarInsets()
        // A device with no bars at all would pass this test vacuously; every supported one has them.
        assertTrue("no system bars to clear on this device", bars.top > 0 || bars.bottom > 0)

        val root = compose.onRoot().getUnclippedBoundsInRoot()
        val density = compose.density
        val statusBottom = with(density) { bars.top.toDp() }
        val navTop = with(density) { root.height - bars.bottom.toDp() }

        val header = compose.onNodeWithContentDescription("전체 재생").getUnclippedBoundsInRoot()
        assertTrue(
            "header top ${header.top} is under the ${statusBottom} status bar",
            header.top >= statusBottom - TOLERANCE,
        )

        val quizButton = compose.onNodeWithText("다음 단계: 퀴즈").getUnclippedBoundsInRoot()
        assertTrue(
            "quiz button bottom ${quizButton.bottom} is under the navigation bar at $navTop",
            quizButton.bottom <= navTop + TOLERANCE,
        )
    }

    /** The real bars of the device the test runs on, read from the host activity's window. */
    private fun systemBarInsets(): android.graphics.Insets {
        var insets: android.graphics.Insets? = null
        compose.activityRule.scenario.onActivity { activity ->
            insets = activity.window.decorView.rootWindowInsets
                ?.getInsets(WindowInsets.Type.systemBars())
        }
        compose.waitForIdle()
        return checkNotNull(insets) { "the host window reported no insets" }
    }

    private companion object {
        /** Rounding between pixel insets and dp bounds, not a licence to overlap. */
        val TOLERANCE = 1.dp
    }
}
