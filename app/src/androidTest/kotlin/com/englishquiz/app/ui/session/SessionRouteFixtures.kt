package com.englishquiz.app.ui.session

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.LearningContent

/** Shared by the LearningSessionRoute test classes, which are split by what they prove. */
internal object SessionRouteFixtures {
    // The Reader splits a segment into word tokens, so assert on one of them, not the sentence.
    const val READER_TOKEN = "together."
    const val QUIZ_BUTTON = "다음 단계: 퀴즈"
    // A fresh store has nothing due, so the quiz opens on its empty state - still a plain
    // scrolling screen, which is what a restoration or a teardown in this harness needs.
    const val EMPTY_QUIZ = "오늘 복습할 표현이 없어요."

    /** The passage's one expression is met for the first time, so the quiz opens on its card (백로그 045). */
    const val FIRST_CARD = "처음 보는 표현이에요. 뜻과 문장을 읽어 보세요."
    const val TIMEOUT_MILLIS = 5_000L

    fun content(title: String) = LearningContent(
        title = title,
        mode = ContentMode.CONVERSATION,
        segments = listOf(ContentSegment("Alex", "We can pull it off together.")),
        expressions = listOf(ContentExpression("pull it off", "해내다", 0, 7, 18)),
    )
}

internal fun ComposeContentTestRule.awaitText(text: String) {
    waitUntil(SessionRouteFixtures.TIMEOUT_MILLIS) {
        onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
    }
}
