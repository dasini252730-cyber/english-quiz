package com.englishquiz.app.domain.quiz

import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.domain.quiz.QuizFixtures.DAY
import com.englishquiz.app.domain.quiz.QuizFixtures.DISTRACTORS
import com.englishquiz.app.domain.quiz.QuizFixtures.NOW
import com.englishquiz.app.domain.quiz.QuizFixtures.SEED
import com.englishquiz.app.domain.quiz.QuizFixtures.expression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which expressions a quiz asks about, in what shape. Option choice lives in [QuizOptionsTest]. */
class QuizBuilderTest {
    @Test
    fun sameSeedProducesIdenticalQuizSets() {
        val candidates = listOf(
            expression("call it a day", "그만하다", contextSentence = "Let's call it a day."),
            expression("sketchy", "수상한", contextSentence = "That sounds sketchy."),
            expression("hang out", "놀다", contextSentence = "Let's hang out."),
        )
        val pool = candidates + expression("piece of cake", "식은 죽 먹기")

        val first = QuizBuilder.build(null, candidates, pool, SEED)
        val second = QuizBuilder.build(null, candidates, pool, SEED)

        assertEquals(first, second)
        assertEquals(3, first.questions.size)
    }

    @Test
    fun duplicateExpressionsAreCollapsedIntoOneQuestion() {
        // The repository folds case and spacing but keeps punctuation, so "sketchy" and "sketchy,"
        // can both be saved and both come due. The learner must not be asked the same thing twice.
        val plain = expression("sketchy", "수상한", contextSentence = "That sounds sketchy.")
        val withComma = expression("sketchy,", "수상한", contextSentence = "Sketchy, isn't it?")

        val result = QuizBuilder.build(null, listOf(plain, withComma), listOf(plain, withComma) + DISTRACTORS, SEED)

        assertEquals(1, result.questions.size)
    }

    @Test
    fun questionCountIsCappedAtTen() {
        val candidates = (1..15).map { expression("word$it", "뜻$it") }

        val result = QuizBuilder.build(null, candidates, candidates, SEED)

        assertEquals(10, result.questions.size)
    }

    @Test
    fun mostOverdueExpressionsAreQuizzedBeforeTheRest() {
        // 12 candidates for 10 slots: the two with the furthest-away next review must be the two
        // left out, so a long review backlog drains instead of being resampled at random.
        val candidates = (1..12).map {
            expression("word$it", "뜻$it", nextReviewAt = NOW - (100 - it) * DAY)
        }

        val quizzed = QuizBuilder.build(null, candidates, candidates, SEED)
            .questions.map { it.expression }.toSet()

        assertEquals(10, quizzed.size)
        assertTrue(quizzed.contains("word1"))
        assertTrue(quizzed.contains("word10"))
        assertTrue(!quizzed.contains("word11"))
        assertTrue(!quizzed.contains("word12"))
    }

    @Test
    fun dueExpressionNotInTodayContentIsStillIncluded() {
        val due = expression("piece of cake", "식은 죽 먹기")
        // Today's content only mentions "hang out", which isn't in the distractor pool either,
        // so it contributes no review expression — but the due expression must still show up.
        val content = LearningContent(
            "A cafe",
            ContentMode.CONVERSATION,
            listOf(ContentSegment("Emma", "Let's hang out.")),
            listOf(ContentExpression("hang out", "놀다", 0, 6, 14)),
        )

        val result = QuizBuilder.build(content, listOf(due), listOf(due) + DISTRACTORS, SEED)

        assertEquals(listOf("piece of cake"), result.questions.map { it.expression })
    }

    @Test
    fun blankSentenceFallsBackToMultipleChoiceWhenContextSentenceIsEmpty() {
        val target = expression("sketchy", "수상한", contextSentence = "")

        val result = QuizBuilder.build(null, listOf(target), listOf(target) + DISTRACTORS, SEED)
        val question = result.questions.single()

        assertEquals(QuizQuestionType.MULTIPLE_CHOICE, question.type)
        // A meaning question is answered with meanings.
        assertEquals("수상한", question.options.single { it.isCorrect }.text)
        assertTrue(question.options.none { it.text == "sketchy" })
    }

    @Test
    fun contextSentenceProducesFillInBlankQuestionAnsweredWithExpressions() {
        val target = expression("sketchy", "수상한", contextSentence = "That sounds sketchy.")

        val result = QuizBuilder.build(null, listOf(target), listOf(target) + DISTRACTORS, SEED)
        val question = result.questions.single()

        assertEquals(QuizQuestionType.FILL_IN_BLANK, question.type)
        assertEquals("That sounds ____.", question.questionText)
        // 요구사항 14.2: the learner picks the expression that fits the blank, not its meaning.
        assertEquals("sketchy", question.options.single { it.isCorrect }.text)
        assertEquals(
            DISTRACTORS.map { it.displayExpression }.toSet(),
            question.options.filter { !it.isCorrect }.map { it.text }.toSet(),
        )
    }

    @Test
    fun explanationKeepsTheModelsSentenceInsteadOfWrappingItInAnotherOne() {
        // Meanings come back from the model as finished sentences. Quoting one inside
        // "…라는 뜻이에요." produced a double ending that every real quiz answer showed.
        val meaning = "상대방의 제안에 동의할 때 쓰는 표현으로, '그거 좋네요'라는 뜻입니다."
        val target = expression("that sounds good", meaning)

        val quiz = QuizBuilder.build(null, listOf(target), listOf(target) + DISTRACTORS, SEED)

        val explanation = quiz.questions.single().explanation
        assertEquals("\"that sounds good\"의 뜻이에요.\n" + meaning, explanation)
        assertTrue(explanation.endsWith(meaning))
    }

    @Test
    fun anExpressionThatIsNotDueIsNotAskedJustBecauseTodaysPassageRepeatsIt() {
        // Re-entering the same mode on the same day shows the stored passage again (백로그 021).
        // "sketchy" was answered this morning and is not due until tomorrow; the passage still
        // contains it. Asking it again would move its review schedule a second time today.
        val answeredThisMorning = expression("sketchy", "수상한", nextReviewAt = NOW + DAY)
        // "hang out" was saved while re-reading and has never been answered, so it is due.
        val savedJustNow = expression("hang out", "놀다")
        val content = LearningContent(
            "A cafe",
            ContentMode.CONVERSATION,
            listOf(ContentSegment("Emma", "That sounds sketchy. Let's hang out.")),
            listOf(
                ContentExpression("sketchy", "수상한", 0, 11, 18),
                ContentExpression("hang out", "놀다", 0, 26, 34),
            ),
        )

        val result = QuizBuilder.build(
            content,
            todayExpressions = listOf(savedJustNow),
            distractorPool = listOf(answeredThisMorning, savedJustNow) + DISTRACTORS,
            seed = SEED,
        )

        // Only the due one is asked; the passage repeating "sketchy" does not put it back.
        assertEquals(listOf("hang out"), result.questions.map { it.expression })
    }

    @Test
    fun emptyInputsProduceEmptyQuizSet() {
        val result = QuizBuilder.build(null, emptyList(), emptyList(), SEED)

        assertTrue(result.questions.isEmpty())
    }
}
