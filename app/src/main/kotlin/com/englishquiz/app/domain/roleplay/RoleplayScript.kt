package com.englishquiz.app.domain.roleplay

import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.SpeakerGender

/**
 * One line of the play, in order. On an [isLearner] turn the learner speaks; on any other the
 * device does. [expressions] are the passage's highlighted phrases that fall in this line.
 */
data class RoleplayTurn(
    val index: Int,
    val speaker: String,
    val text: String,
    val isLearner: Boolean,
    val expressions: List<ContentExpression>,
) {
    /** The first highlighted expression of the line, which is the one graded for containment; null when the line has none. */
    val expression: ContentExpression? get() = expressions.firstOrNull()
}

/** Turns a conversation into a script the learner can take a part in (백로그 076). */
object RoleplayScript {
    private const val NARRATOR_NAME = "Narrator"

    /**
     * The roles on offer: every speaker of the passage in order of appearance, minus the narrator,
     * who describes the scene rather than taking part in it. The narrator's lines are still
     * played, as someone else's.
     */
    fun speakers(content: LearningContent): List<String> = content.segments
        .map { it.speaker }
        .distinct()
        .filterNot { it.isNarrator(content) }

    /** Every segment as a turn, the ones spoken by [learner] marked as theirs. */
    fun turns(content: LearningContent, learner: String): List<RoleplayTurn> = content.segments.mapIndexed { index, segment ->
        RoleplayTurn(
            index = index,
            speaker = segment.speaker,
            text = segment.text,
            isLearner = segment.speaker == learner,
            expressions = content.expressions.filter { it.segmentIndex == index },
        )
    }

    private fun String.isNarrator(content: LearningContent): Boolean =
        equals(NARRATOR_NAME, ignoreCase = true) || content.speakers[this] == SpeakerGender.NARRATOR
}
