package com.englishquiz.app.domain.game

import org.junit.Assert.assertEquals
import org.junit.Test

class GrowthStageTest {
    @Test
    fun oneStagePerConsecutiveCorrectAnswer() {
        assertEquals(GrowthStage.SEED, GrowthStage.of(0, isMastered = false))
        assertEquals(GrowthStage.SPROUT, GrowthStage.of(1, isMastered = false))
        assertEquals(GrowthStage.LEAF, GrowthStage.of(2, isMastered = false))
        assertEquals(GrowthStage.FLOWER, GrowthStage.of(3, isMastered = false))
        assertEquals(GrowthStage.FRUIT, GrowthStage.of(4, isMastered = true))
    }

    @Test
    fun masteryIsAlwaysTheFruitAndNegativeCountsAreSeeds() {
        assertEquals(GrowthStage.FRUIT, GrowthStage.of(0, isMastered = true))
        assertEquals(GrowthStage.FRUIT, GrowthStage.of(9, isMastered = false))
        assertEquals(GrowthStage.SEED, GrowthStage.of(-1, isMastered = false))
    }

    @Test
    fun titleJoinsTheEmojiAndTheLabel() {
        assertEquals("🌱 새싹", GrowthStage.SPROUT.title)
    }
}
