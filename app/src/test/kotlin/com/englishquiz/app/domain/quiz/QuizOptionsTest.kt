package com.englishquiz.app.domain.quiz

import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.domain.quiz.QuizFixtures.SEED
import com.englishquiz.app.domain.quiz.QuizFixtures.expression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a question offers as options, and when it is dropped for want of an honest one. */
class QuizOptionsTest {
    @Test
    fun theFirstSessionsSingleSavedExpressionStillGetsAQuestion() {
        // Found by the 014 end-to-end run: with one saved expression there was no second option,
        // the question was dropped, and the learner was told there was nothing to review — which
        // is what every first session looks like.
        val saved = expression("call it a day", "그만하다", contextSentence = "Let's call it a day.")
        val content = LearningContent(
            "After work",
            ContentMode.CONVERSATION,
            listOf(ContentSegment("Emma", "Let's call it a day.")),
            listOf(
                ContentExpression("call it a day", "그만하다", 0, 6, 19),
                ContentExpression("hang out", "놀다", 0, 0, 8),
            ),
        )

        val result = QuizBuilder.build(content, listOf(saved), listOf(saved), SEED)

        assertEquals(1, result.questions.size)
        val question = result.questions.single()
        assertEquals(QuizQuestionType.FILL_IN_BLANK, question.type)
        assertEquals(1, question.options.count { it.isCorrect })
        // The wrong option is the other phrase from today's passage, not a second AI call.
        assertEquals(
            listOf("hang out"),
            question.options.filter { !it.isCorrect }.map { it.text },
        )
    }

    @Test
    fun aMeaningQuestionNeverMixesPassagePhrasesIntoItsOptions() {
        // No context sentence, so this is a MULTIPLE_CHOICE question and the options are meanings.
        // Nothing else is saved, so the only possible wrong option comes from the passage.
        val saved = expression("sketchy", "수상한")
        val content = LearningContent(
            "A cafe",
            ContentMode.CONVERSATION,
            listOf(ContentSegment("Emma", "That sounds sketchy.")),
            listOf(
                ContentExpression("sketchy", "수상한", 0, 11, 18),
                ContentExpression("hang out", "놀다", 0, 0, 8),
            ),
        )

        val result = QuizBuilder.build(content, listOf(saved), listOf(saved), SEED)

        // A saved meaning is a full explanatory sentence and a passage annotation is a short
        // phrase, so mixing them would let the learner pick the answer by its length. A meaning
        // question therefore takes options only from saved expressions - here, none.
        assertTrue(result.questions.isEmpty())
    }

    @Test
    fun theSamePhraseSpelledWithACurlyApostropheIsNeverOfferedAsAWrongOption() {
        // The Reader saves the passage's own spelling (curly); the model annotates its own
        // (straight). Without folding both, the learner sees the same phrase twice — once marked
        // correct, once wrong — and tapping the wrong copy records a wrong answer against them.
        val saved = expression(
            "I’m all ears",
            "귀 기울이고 있다",
            contextSentence = "I’m all ears, tell me.",
        )
        val content = LearningContent(
            "A chat",
            ContentMode.CONVERSATION,
            listOf(ContentSegment("Emma", "I’m all ears, tell me.")),
            listOf(ContentExpression("I'm all ears", "귀 기울이고 있다", 0, 0, 12)),
        )

        val result = QuizBuilder.build(content, listOf(saved), listOf(saved), SEED)

        // With no honest second option the question is dropped, rather than offering a choice
        // between two spellings of the same answer.
        assertTrue(result.questions.all { question ->
            question.options.map { normalizedForTest(it.text) }.distinct().size ==
                question.options.size
        })
        assertTrue(result.questions.isEmpty())
    }

    @Test
    fun aWrongOptionThatOnlyDiffersByTrailingPunctuationIsRejected() {
        val saved = expression("call it a day", "그만하다")
        val content = LearningContent(
            "After work",
            ContentMode.CONVERSATION,
            listOf(ContentSegment("Emma", "Let's call it a day.")),
            // Same meaning, differently punctuated: not a real alternative to choose between.
            listOf(ContentExpression("wrap up", "그만하다.", 0, 0, 7)),
        )

        val result = QuizBuilder.build(content, listOf(saved), listOf(saved), SEED)

        assertTrue(result.questions.isEmpty())
    }

    @Test
    fun aLoneExpressionWithNothingElseToOfferStillProducesNoQuestion() {
        // Honesty check: without any second option there is no fair question, so an empty quiz
        // is still the right answer rather than a one-option "choice".
        val saved = expression("call it a day", "그만하다")

        val result = QuizBuilder.build(null, listOf(saved), listOf(saved), SEED)

        assertTrue(result.questions.isEmpty())
    }

    @Test
    fun aSavedExpressionsOwnMeaningWinsOverThePassagesWording() {
        val saved = expression("sketchy", "수상한")
        val other = expression("hang out", "놀다")
        val content = LearningContent(
            "A cafe",
            ContentMode.CONVERSATION,
            listOf(ContentSegment("Emma", "That sounds sketchy.")),
            // The passage words the same phrase differently; the saved meaning is the learner's.
            listOf(ContentExpression("hang out", "어울리다", 0, 0, 8)),
        )

        val result = QuizBuilder.build(content, listOf(saved), listOf(saved, other), SEED)

        // "hang out" is in the pool and in the passage, so it becomes a question of its own;
        // this asserts on the one under test.
        val options = result.questions.single { it.expression == "sketchy" }.options.map { it.text }
        assertTrue("놀다" in options)
        assertTrue("어울리다" !in options)
    }

    @Test
    fun optionsShrinkToTwoWhenDistractorsAreScarce() {
        val target = expression("sketchy", "수상한")
        val onlyOtherMeaning = expression("hang out", "놀다")

        val result = QuizBuilder.build(null, listOf(target), listOf(target, onlyOtherMeaning), SEED)

        assertEquals(2, result.questions.single().options.size)
    }

    @Test
    fun expressionWithoutAnyDistractorIsDroppedInsteadOfAskedWithOneOption() {
        // A single-option question would be correct no matter what the learner taps.
        val target = expression("sketchy", "수상한")

        val result = QuizBuilder.build(null, listOf(target), listOf(target), SEED)

        assertTrue(result.questions.isEmpty())
    }

    @Test
    fun expressionWhoseOnlyOtherMeaningIsIdenticalIsDropped() {
        val target = expression("sketchy", "수상한")
        val sameMeaning = expression("shady", "수상한")

        val result = QuizBuilder.build(null, listOf(target), listOf(target, sameMeaning), SEED)

        assertTrue(result.questions.isEmpty())
    }

    @Test
    fun fillInBlankIsDroppedWhenNoOtherExpressionCanFillTheBlank() {
        val target = expression("sketchy", "수상한", contextSentence = "That sounds sketchy.")

        val result = QuizBuilder.build(null, listOf(target), listOf(target), SEED)

        assertTrue(result.questions.isEmpty())
    }

    private fun normalizedForTest(text: String): String =
        text.trim().lowercase().trim('.', ',', '!', '?', '\'', '"', '’')
}
