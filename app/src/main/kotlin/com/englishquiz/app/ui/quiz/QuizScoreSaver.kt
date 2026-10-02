package com.englishquiz.app.ui.quiz

import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import com.englishquiz.app.domain.game.QuizScore

/** Keeps the running score across activity recreation, so a rotation never resets the combo. */
internal val QuizScoreSaver: Saver<QuizScore, Any> = listSaver(
    save = { listOf(it.points, it.combo, it.maxCombo, it.lastEarned) },
    restore = { QuizScore(points = it[0], combo = it[1], maxCombo = it[2], lastEarned = it[3]) },
)
