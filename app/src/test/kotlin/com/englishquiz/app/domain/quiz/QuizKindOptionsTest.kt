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

/** 백로그 059: wrong options of the answer's kind, passage glosses as a last resort, and no favourite answer slot. */
class QuizKindOptionsTest {
    @Test
    fun wrongOptionsAreOfTheAnswersKindWhileEnoughExist() {
        val target = expression("pull it off", "해내다", shortMeaning = "해내다", kind = "verb")
        val pool = listOf(
            target,
            expression("call it a day", "그만하다", shortMeaning = "그만하다", kind = "verb"),
            expression("hang out", "놀다", shortMeaning = "놀다", kind = "verb"),
            expression("wrap up", "마무리하다", shortMeaning = "마무리하다", kind = "verb"),
            expression("a big deal", "대단한 일", shortMeaning = "대단한 일", kind = "noun"),
            expression("sketchy", "수상한", shortMeaning = "수상한", kind = "adjective"),
            expression("no way", "말도 안 돼", shortMeaning = "말도 안 돼", kind = "remark"),
        )

        val options = QuizBuilder.build(null, listOf(target), pool, SEED).questions.single().options

        assertEquals(4, options.size)
        assertEquals(setOf("그만하다", "놀다", "마무리하다"), options.filter { !it.isCorrect }.map { it.text }.toSet())
    }

    @Test
    fun aPhraseOfUnknownKindIsPreferredToOneOfAnotherKind() {
        // A 씨앗-stage question has two options, so exactly one wrong option is drawn.
        val target = expression("pull it off", "해내다", consecutiveCorrect = 0, kind = "verb")
        val pool = listOf(
            target,
            expression("sketchy", "수상한", kind = "adjective"),
            expression("a big deal", "대단한 일", kind = "noun"),
            expression("hang out", "놀다"),
        )

        val options = (1L..20L).map { seed ->
            QuizBuilder.build(null, listOf(target), pool, seed).questions.single().options.single { !it.isCorrect }.text
        }

        assertEquals(setOf("놀다"), options.toSet())
    }

    @Test
    fun aPhraseOfAnotherKindIsNeverOfferedSoTheQuestionShrinksOrIsDropped() {
        // Among nouns the one verb is the answer whether or not its meaning is known.
        val target = expression("pull it off", "해내다", kind = "verb")
        val nouns = listOf("a big deal" to "대단한 일", "next step" to "다음 단계", "first impression" to "첫인상")
            .map { (text, meaning) -> expression(text, meaning, kind = "noun") }
        val oneVerb = expression("call it a day", "그만하다", kind = "verb")

        val dropped = QuizBuilder.build(null, listOf(target), nouns + target, SEED)
        val shrunk = QuizBuilder.build(null, listOf(target), nouns + target + oneVerb, SEED)

        assertTrue(dropped.questions.isEmpty())
        assertTrue(!QuizBuilder.isAskable(target, nouns + target))
        assertEquals(listOf("그만하다"), shrunk.questions.single().options.filter { !it.isCorrect }.map { it.text })
    }

    @Test
    fun anotherInflectionOfTheAnswerInTodaysPassageIsNeverAWrongOption() {
        val saved = expression("pull it off", "해내다", shortMeaning = "해내다", kind = "verb")
        val content = LearningContent(
            "A deal",
            ContentMode.CONVERSATION,
            listOf(ContentSegment("Emma", "She pulled it off and we wrapped up.")),
            listOf(
                ContentExpression("pulled it off", "해냈다", 0, 4, 17, shortMeaning = "해냈다", kind = "verb"),
                ContentExpression("wrapped up", "마무리했다", 0, 25, 35, shortMeaning = "마무리하다", kind = "verb"),
            ),
        )

        val options = QuizBuilder.build(content, listOf(saved), listOf(saved), SEED).questions.single().options

        assertEquals(setOf("해내다", "마무리하다"), options.map { it.text }.toSet())
    }

    @Test
    fun todaysPassageLendsItsShortGlossesWhenTheBoxCannotAsk() {
        // Nothing else is saved, so before 백로그 059 this expression had no honest meaning question.
        val saved = expression("sketchy", "수상한", shortMeaning = "수상한", kind = "adjective")
        val content = LearningContent(
            "A cafe",
            ContentMode.CONVERSATION,
            listOf(ContentSegment("Emma", "That sounds sketchy and pricey, so hang out later.")),
            listOf(
                ContentExpression("sketchy", "수상한", 0, 12, 19, shortMeaning = "수상한", kind = "adjective"),
                ContentExpression("pricey", "비싼", 0, 24, 30, shortMeaning = "비싼", kind = "adjective"),
                // Of another kind, so not a wrong option even now.
                ContentExpression("hang out", "어울려 놀다", 0, 35, 43, shortMeaning = "놀다", kind = "verb"),
            ),
        )

        val result = QuizBuilder.build(content, listOf(saved), listOf(saved), SEED)

        val question = result.questions.single()
        assertEquals(QuizQuestionType.MULTIPLE_CHOICE, question.type)
        assertEquals(setOf("수상한", "비싼"), question.options.map { it.text }.toSet())
        // The box alone still cannot ask it, so the review box marks it 출제 불가 (백로그 057).
        assertTrue(!QuizBuilder.isAskable(saved, listOf(saved)))
    }

    @Test
    fun theCorrectAnswerTakesEveryPositionAcrossSessions() {
        val target = expression("pull it off", "해내다", kind = "verb")
        val pool = listOf(
            target,
            expression("call it a day", "그만하다", kind = "verb"),
            expression("hang out", "놀다", kind = "verb"),
            expression("wrap up", "마무리하다", kind = "verb"),
        )

        val positions = (1L..200L).map { seed ->
            QuizBuilder.build(null, listOf(target), pool, seed).questions.single().options.indexOfFirst { it.isCorrect }
        }

        val counts = positions.groupingBy { it }.eachCount()
        assertEquals(setOf(0, 1, 2, 3), counts.keys)
        counts.values.forEach { assertTrue("answer slot drawn $it times of 200", it >= 25) }
    }

    @Test
    fun withinOneSessionTheCorrectAnswerDoesNotSettleOnOneNumber() {
        // What the learner noticed was one session's ten questions, so the draw is checked
        // along one session's Random, not only across sessions.
        val verbs = listOf(
            "pull it off" to "해내다", "call it a day" to "그만하다", "hang out" to "놀다", "wrap up" to "마무리하다",
            "show up" to "나타나다", "give in" to "굴복하다", "run into" to "우연히 만나다", "put off" to "미루다",
            "look into" to "조사하다", "turn down" to "거절하다", "figure out" to "알아내다", "bring up" to "꺼내다",
        ).map { (text, meaning) -> expression(text, meaning, kind = "verb") }

        val sessions = (1L..20L).map { seed ->
            val questions = QuizBuilder.build(null, verbs, verbs, seed).questions
            assertEquals(QuizBuilder.DEFAULT_MAX_QUESTIONS, questions.size)
            questions.map { it.options.indexOfFirst { option -> option.isCorrect } }
        }

        sessions.forEachIndexed { index, positions -> assertTrue("session ${index + 1} put the answer at $positions", positions.distinct().size >= 2) }
        // Twenty sessions of ten: a slot favoured even mildly would fall short of its quarter.
        val counts = sessions.flatten().groupingBy { it }.eachCount()
        assertEquals(setOf(0, 1, 2, 3), counts.keys)
        counts.forEach { (slot, count) -> assertTrue("slot $slot drawn $count times of 200", count >= 30) }
    }

    @Test
    fun aRowFromBeforeKindsWereKeptIsAskedAgainstKindedPhrasesAndLendsItselfToThem() {
        // Right after the migration every saved row has kind "" and only new passages bring one.
        val old = expression("pull it off", "해내다", shortMeaning = "해내다")
        val kinded = listOf(
            expression("a big deal", "대단한 일", shortMeaning = "대단한 일", kind = "noun"),
            expression("sketchy", "수상한", shortMeaning = "수상한", kind = "adjective"),
            expression("hang out", "놀다", shortMeaning = "놀다", kind = "verb"),
        )

        val oldAsked = QuizBuilder.build(null, listOf(old), kinded + old, SEED).questions.single()
        val kindedAsked = QuizBuilder.build(null, listOf(kinded[2]), listOf(kinded[2], old), SEED).questions.single()

        assertEquals(4, oldAsked.options.size)
        assertEquals(setOf("해내다", "놀다"), kindedAsked.options.map { it.text }.toSet())
    }

    @Test
    fun anIrregularPastFormOfTheAnswerIsNeverAWrongOption() {
        val saved = expression("take off", "이륙하다", shortMeaning = "이륙하다", kind = "verb")
        val content = LearningContent(
            "Airport",
            ContentMode.CONVERSATION,
            listOf(ContentSegment("Emma", "We took off late and made up time.")),
            listOf(
                ContentExpression("took off", "이륙했다", 0, 3, 11, shortMeaning = "이륙했다", kind = "verb"),
                ContentExpression("made up", "만회했다", 0, 21, 28, shortMeaning = "만회하다", kind = "verb"),
            ),
        )

        val options = QuizBuilder.build(content, listOf(saved), listOf(saved), SEED).questions.single().options

        assertEquals(setOf("이륙하다", "만회하다"), options.map { it.text }.toSet())
    }
}
