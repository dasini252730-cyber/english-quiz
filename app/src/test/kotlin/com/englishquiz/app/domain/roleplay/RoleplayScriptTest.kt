package com.englishquiz.app.domain.roleplay

import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.SpeakerGender
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 백로그 076: the roles on offer and whose turn each line is. */
class RoleplayScriptTest {
    private val content = LearningContent(
        title = "At the station",
        mode = ContentMode.CONVERSATION,
        segments = listOf(
            ContentSegment("Narrator", "Emma stops a passer-by."),
            ContentSegment("Emma", "Can you help me find the station?"),
            ContentSegment("Sam", "Sure, it's just around the corner."),
            ContentSegment("Emma", "Thanks a lot!"),
        ),
        expressions = listOf(
            ContentExpression("around the corner", "아주 가까운 곳에", 2, 16, 33),
        ),
    )

    @Test
    fun speakersAreTheCastInOrderWithoutTheNarrator() {
        assertEquals(listOf("Emma", "Sam"), RoleplayScript.speakers(content))
    }

    @Test
    fun aNarratorNamedByTheModelIsLeftOutEvenUnderAnotherName() {
        val described = content.copy(
            segments = listOf(ContentSegment("Voice", "The scene opens.")) + content.segments.drop(1),
            speakers = mapOf("Voice" to SpeakerGender.NARRATOR, "Emma" to SpeakerGender.FEMALE),
        )
        assertEquals(listOf("Emma", "Sam"), RoleplayScript.speakers(described))
    }

    @Test
    fun turnsFollowTheSegmentsAndMarkTheLearnersOwn() {
        val turns = RoleplayScript.turns(content, learner = "Emma")
        assertEquals(listOf(false, true, false, true), turns.map { it.isLearner })
        assertEquals(listOf(0, 1, 2, 3), turns.map { it.index })
        assertEquals("Sam", turns[2].speaker)
        assertEquals("around the corner", turns[2].expression?.text)
        assertNull(turns[1].expression)
    }
}
