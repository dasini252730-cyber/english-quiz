package com.englishquiz.app.ui.roleplay

import com.englishquiz.app.domain.roleplay.TurnOutcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 백로그 076: one attempt, two retries, then the line is failed and the play moves on. */
class RoleplayProgressTest {
    private fun inPlay() = RoleplayProgress().apply { choose("Sam") }

    @Test
    fun aLineFailsOnTheThirdMissAndCountsAsNotPassed() {
        val progress = inPlay()
        assertEquals(TurnOutcome.RETRY, progress.attempted("a", passed = false))
        assertEquals(2, progress.retriesLeft)
        assertEquals(TurnOutcome.RETRY, progress.attempted("b", passed = false))
        assertEquals(1, progress.retriesLeft)
        assertFalse(progress.settled)
        assertEquals(TurnOutcome.FAILED, progress.attempted("c", passed = false))
        assertTrue(progress.settled)
        assertEquals(0, progress.passedCount)
        assertEquals("c", progress.transcript)
    }

    @Test
    fun aPassSettlesAtOnceAndASecondSubmissionChangesNothing() {
        val progress = inPlay()
        assertEquals(TurnOutcome.PASSED, progress.attempted("fine", passed = true))
        assertEquals(1, progress.passedCount)
        // A double tap on 제출 is not another attempt: no second count, no second record.
        assertEquals(TurnOutcome.RETRY, progress.attempted("fine", passed = true))
        assertEquals(1, progress.attempts)
        assertEquals(1, progress.passedCount)
    }

    @Test
    fun nextClearsTheLineAndRestartGoesBackToTheChoice() {
        val progress = inPlay()
        progress.revealed = true
        progress.attempted("x", passed = true)
        progress.next()
        assertEquals(1, progress.turnIndex)
        assertEquals(0, progress.attempts)
        assertNull(progress.transcript)
        assertNull(progress.outcome)
        assertFalse(progress.revealed)
        assertEquals(1, progress.passedCount)

        progress.typedInput = true
        progress.restart()
        assertNull(progress.speaker)
        assertEquals(0, progress.turnIndex)
        assertEquals(0, progress.passedCount)
        assertFalse(progress.typedInput)
    }
}
