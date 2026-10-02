package com.englishquiz.app.domain.game

/**
 * How far a saved expression has grown (백로그 036): one stage per consecutive correct answer,
 * read straight off the review state (`ReviewPolicy` masters an expression at four in a row, which
 * is [FRUIT]). A wrong answer resets the run, so the stage drops back to [SEED] — the loss the
 * learner sees is what makes the next review matter.
 */
enum class GrowthStage(val label: String, val emoji: String) {
    SEED("씨앗", "🌰"),
    SPROUT("새싹", "🌱"),
    LEAF("잎", "🌿"),
    FLOWER("꽃", "🌸"),
    FRUIT("열매", "🍎"),
    ;

    /** "🌱 새싹": the chip text used wherever the stage is shown. */
    val title: String get() = "$emoji $label"

    companion object {
        fun of(consecutiveCorrectCount: Int, isMastered: Boolean): GrowthStage = when {
            isMastered -> FRUIT
            else -> entries[consecutiveCorrectCount.coerceIn(0, entries.lastIndex)]
        }
    }
}
