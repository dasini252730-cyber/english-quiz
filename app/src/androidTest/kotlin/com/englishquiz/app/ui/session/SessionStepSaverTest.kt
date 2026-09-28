package com.englishquiz.app.ui.session

import androidx.compose.runtime.saveable.SaverScope
import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.domain.session.LearningSessionSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The saver is what stops a screen rotation from throwing away a generated passage and paying for
 * another one, so every step has to survive the round trip byte for byte.
 */
class SessionStepSaverTest {
    private val scope = SaverScope { true }

    private val content = LearningContent(
        title = "A Saturday Plan",
        mode = ContentMode.CONVERSATION,
        segments = listOf(
            ContentSegment("Alex", "We can pull it off together."),
            ContentSegment("Sam", "It’s a long shot – but sure."),
        ),
        expressions = listOf(
            ContentExpression("pull it off", "해내다", 0, 7, 18),
            ContentExpression("a long shot", "가능성이 낮은 시도", 1, 5, 16),
        ),
    )

    private val summary = LearningSessionSummary(
        learnedExpressionCount = 4,
        newlySavedExpressionCount = 2,
        quizCorrectCount = 3,
        quizQuestionCount = 5,
        masteredExpressionCount = 1,
    )

    private fun roundTrip(step: SessionStep): SessionStep? {
        val saved = with(SessionStepSaver) { scope.save(step) }
        return SessionStepSaver.restore(checkNotNull(saved))
    }

    @Test
    fun theReadingStepKeepsTheWholePassageIncludingExpressionOffsets() {
        val restored = roundTrip(SessionStep.Reading(content)) as SessionStep.Reading

        assertEquals(content.title, restored.content.title)
        assertEquals(content.mode, restored.content.mode)
        assertEquals(content.segments, restored.content.segments)
        // The offsets are recomputed by the parser, so this also proves they still land correctly.
        assertEquals(content.expressions, restored.content.expressions)
    }

    @Test
    fun theQuizStepKeepsBothItsPassageAndItsRunningSummary() {
        val restored = roundTrip(SessionStep.Quiz(content, summary)) as SessionStep.Quiz

        assertEquals(content.segments, restored.content.segments)
        assertEquals(summary, restored.base)
    }

    @Test
    fun theRemainingStepsRoundTripAsThemselves() {
        assertEquals(SessionStep.Preparing, roundTrip(SessionStep.Preparing))
        assertEquals(
            SessionStep.Failed("네트워크 연결을 확인해 주세요."),
            roundTrip(SessionStep.Failed("네트워크 연결을 확인해 주세요.")),
        )
        assertEquals(SessionStep.Finished(summary), roundTrip(SessionStep.Finished(summary)))
    }

    @Test
    fun anUnreadableSavedValueRestartsTheSessionInsteadOfCrashing() {
        // Restoring returns null, and Compose then falls back to the initial step.
        assertNull(SessionStepSaver.restore("not json at all"))
        assertNull(SessionStepSaver.restore("""{"step":"nonsense"}"""))
        assertNull(SessionStepSaver.restore("""{"step":"reading"}"""))
    }
}
