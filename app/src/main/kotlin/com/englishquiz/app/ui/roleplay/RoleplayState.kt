package com.englishquiz.app.ui.roleplay

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import com.englishquiz.app.domain.roleplay.RoleplayGrader
import com.englishquiz.app.domain.roleplay.TurnOutcome

/**
 * The play's position (백로그 076): which role, which line, how many tries this line has had, and
 * how the learner's lines went so far. Plain values so [Saver] can carry it across a rotation
 * without re-asking the learner which role they took.
 */
internal class RoleplayProgress(
    speaker: String? = null,
    turnIndex: Int = 0,
    attempts: Int = 0,
    transcript: String? = null,
    outcome: TurnOutcome? = null,
    revealed: Boolean = false,
    results: String = "",
    typedInput: Boolean = false,
) {
    /** The role taken; null while the learner is still choosing. */
    var speaker by mutableStateOf(speaker)
    var turnIndex by mutableIntStateOf(turnIndex)
    /** Attempts made on the current line. */
    var attempts by mutableIntStateOf(attempts)
    /** What the recogniser heard (or the learner typed) last on this line. */
    var transcript by mutableStateOf(transcript)
    var outcome by mutableStateOf(outcome)
    /** Whether "원문 보기" was used on this line. */
    var revealed by mutableStateOf(revealed)
    /** One character per settled learner line: '1' passed, '0' failed. */
    var results by mutableStateOf(results)
        private set
    /** True once the microphone is out of reach (no permission, no recogniser) or the learner chose to type. */
    var typedInput by mutableStateOf(typedInput)

    val passedCount: Int get() = results.count { it == '1' }
    /** True once this line has its verdict; a further attempt is not taken. */
    val settled: Boolean get() = outcome != null && outcome != TurnOutcome.RETRY
    val retriesLeft: Int get() = RoleplayGrader.MAX_RETRIES - (attempts - 1).coerceAtLeast(0)

    fun choose(role: String) {
        restart()
        speaker = role
    }

    /** Back to the role choice with nothing kept; the microphone is tried again, in case it was granted since. */
    fun restart() {
        speaker = null
        turnIndex = 0
        results = ""
        typedInput = false
        clearTurn()
    }

    /**
     * Settles or reopens the line after an attempt judged [passed]; the caller records the settled
     * ones. A second submission of a settled line (a double tap, IME Done and 제출 together) is
     * not a new attempt: it neither re-counts nor re-records.
     */
    fun attempted(heard: String, passed: Boolean): TurnOutcome {
        if (settled) return TurnOutcome.RETRY
        attempts++
        transcript = heard
        val result = RoleplayGrader.settle(attempts, passed)
        if (result != TurnOutcome.RETRY) results += if (passed) '1' else '0'
        outcome = result
        return result
    }

    fun next() {
        turnIndex++
        clearTurn()
    }

    private fun clearTurn() {
        attempts = 0
        transcript = null
        outcome = null
        revealed = false
    }

    companion object {
        val Saver = listSaver<RoleplayProgress, Any?>(
            save = {
                listOf(it.speaker, it.turnIndex, it.attempts, it.transcript, it.outcome?.name, it.revealed, it.results, it.typedInput)
            },
            restore = {
                RoleplayProgress(
                    speaker = it[0] as String?,
                    turnIndex = it[1] as Int,
                    attempts = it[2] as Int,
                    transcript = it[3] as String?,
                    outcome = (it[4] as String?)?.let(TurnOutcome::valueOf),
                    revealed = it[5] as Boolean,
                    results = it[6] as String,
                    typedInput = it[7] as Boolean,
                )
            },
        )
    }
}
