package com.englishquiz.app.ui.session

import androidx.compose.ui.test.junit4.ComposeContentTestRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentGenerationRequest
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.local.LearningSessionEntity
import com.englishquiz.app.data.repository.toSummary
import com.englishquiz.app.domain.game.DailyMissionPolicy

/**
 * The passage and the quiz-driving steps of [LearningRecordConsistencyTest] (백로그 061), kept apart
 * so the scenario reads as a list of days and checks.
 */
internal object RecordConsistencyFixtures {
    const val PULL_IT_OFF = "pull it off"
    const val HANG_OUT = "hang out"
    const val MEANING_PULL = "해내다"
    const val MEANING_HANG = "놀다"
    const val RESULT_TITLE = "오늘 학습 완료"
    const val SEEN_BUTTON = "뜻 확인 완료"
    const val EMPTY_QUIZ_BUTTON = "완료"
    const val RETRY_HEADER = "다시 풀기 · "
    const val DAY_MILLIS = 24 * 60 * 60 * 1000L

    /** Noon UTC on 2026-10-05: a Monday, so the five days never meet the weekend boss. */
    const val DAY1_NOON = 1_791_201_600_000L

    /** Two expressions in one sentence, so every day's quiz has two 씨앗 questions with two meanings each. */
    fun passage(request: ContentGenerationRequest): LearningContent {
        val sentence = "We can pull it off and hang out together."
        val pull = sentence.indexOf(PULL_IT_OFF)
        val hang = sentence.indexOf(HANG_OUT)
        return LearningContent(
            title = "Day passage ${request.mode.wireValue} ${request.difficulty}",
            mode = request.mode,
            segments = listOf(ContentSegment("Alex", sentence)),
            expressions = listOf(
                ContentExpression(PULL_IT_OFF, MEANING_PULL, 0, pull, pull + PULL_IT_OFF.length, shortMeaning = MEANING_PULL),
                ContentExpression(HANG_OUT, MEANING_HANG, 0, hang, hang + HANG_OUT.length, shortMeaning = MEANING_HANG),
            ),
        )
    }

    /** What the game layer must report as total points: session scores plus each day's mission bonus. */
    fun expectedTotalPoints(sessions: List<LearningSessionEntity>): Int =
        sessions.groupBy { it.learningDate }.values.sumOf { day ->
            day.sumOf { it.score } + DailyMissionPolicy.bonusPoints(day.map { it.toSummary() })
        }
}

/** Reads the passage and opens the quiz. */
internal fun ComposeContentTestRule.readPassageAndOpenQuiz() {
    awaitText(SessionRouteFixtures.READER_TOKEN)
    onNodeWithText(SessionRouteFixtures.QUIZ_BUTTON).performClick()
}

/** Day one: both expressions are first meetings, each a card with the one button (백로그 045/055). */
internal fun ComposeContentTestRule.readTwoCards() {
    awaitText(SessionRouteFixtures.FIRST_CARD)
    awaitText("1 / 2")
    onNodeWithText(RecordConsistencyFixtures.SEEN_BUTTON).performScrollTo().performClick()
    awaitText("2 / 2")
    onNodeWithText(RecordConsistencyFixtures.SEEN_BUTTON).performScrollTo().performClick()
    awaitText(RecordConsistencyFixtures.RESULT_TITLE)
}

/**
 * A review day: two 씨앗 questions answered by always tapping [meaning] (right for one expression,
 * wrong for the other), then the retry round (백로그 047) answered right, then the result screen.
 */
internal fun ComposeContentTestRule.answerBothQuestionsWith(meaning: String) {
    repeat(2) { number ->
        awaitText("${number + 1} / 2")
        onNodeWithText(meaning).performScrollTo().performClick()
        waitUntil(SessionRouteFixtures.TIMEOUT_MILLIS) {
            onAllNodesWithText("정답이에요!").fetchSemanticsNodes().isNotEmpty() ||
                onAllNodesWithText("아쉬워요, 오답이에요.").fetchSemanticsNodes().isNotEmpty()
        }
        onNodeWithText(if (number == 0) "다음 문제" else "틀린 문제 다시 풀기").performScrollTo().performClick()
    }
    // The missed one comes back once: answer it right. This answer must leave no record.
    waitUntil(SessionRouteFixtures.TIMEOUT_MILLIS) {
        onAllNodesWithText(RecordConsistencyFixtures.RETRY_HEADER, substring = true).fetchSemanticsNodes().isNotEmpty()
    }
    val asksPull = onAllNodesWithText(RecordConsistencyFixtures.PULL_IT_OFF).fetchSemanticsNodes().isNotEmpty()
    onNodeWithText(if (asksPull) RecordConsistencyFixtures.MEANING_PULL else RecordConsistencyFixtures.MEANING_HANG)
        .performScrollTo().performClick()
    awaitText("정답이에요!")
    onNodeWithText("결과 보기").performScrollTo().performClick()
    awaitText(RecordConsistencyFixtures.RESULT_TITLE)
}
