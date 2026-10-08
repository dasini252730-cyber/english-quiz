package com.englishquiz.app.domain.quiz

import com.englishquiz.app.data.ai.ComprehensionQuestion
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
    fun theLadderGoesOneStepHarderPerGrowthStage() {
        // 백로그 045: never asked → card; 씨앗 → two meanings; 새싹 → four; 잎 → blank by choice; 꽃 → typed.
        val never = expression("brush up", "다시 익히다", contextSentence = "I need to brush up.", reviewed = false)
        val seed = expression("sketchy", "수상한", contextSentence = "That sounds sketchy.", consecutiveCorrect = 0)
        val sprout = expression("hang out", "놀다", contextSentence = "Let's hang out.", consecutiveCorrect = 1)
        val leaf = expression("call it a day", "그만하다", contextSentence = "Let's call it a day.", consecutiveCorrect = 2)
        val flower = expression("piece of cake", "식은 죽 먹기", contextSentence = "It's a piece of cake.", consecutiveCorrect = 3)
        val all = listOf(never, seed, sprout, leaf, flower)

        val questions = QuizBuilder.build(null, all, all + DISTRACTORS, SEED).questions.associateBy { it.expression }

        val card = questions.getValue("brush up")
        assertEquals(QuizQuestionType.LEARN_CARD, card.type)
        assertEquals("I need to brush up.", card.questionText)
        assertTrue(card.options.isEmpty())
        assertEquals(QuizQuestionType.MULTIPLE_CHOICE, questions.getValue("sketchy").type)
        assertEquals(2, questions.getValue("sketchy").options.size)
        assertEquals(QuizQuestionType.MULTIPLE_CHOICE, questions.getValue("hang out").type)
        assertEquals(4, questions.getValue("hang out").options.size)
        assertEquals(QuizQuestionType.FILL_IN_BLANK, questions.getValue("call it a day").type)
        val typed = questions.getValue("piece of cake")
        assertEquals(QuizQuestionType.TYPED_BLANK, typed.type)
        assertEquals("It's a ____.", typed.questionText)
        assertEquals(listOf(QuizOption("piece of cake", true)), typed.options)
    }

    @Test
    fun withoutASentenceTheBlankStepsFallBackToFourMeanings() {
        val leaf = expression("sketchy", "수상한", consecutiveCorrect = 2)
        val flower = expression("hang out", "놀다", consecutiveCorrect = 3)

        val questions = QuizBuilder.build(null, listOf(leaf, flower), listOf(leaf, flower) + DISTRACTORS, SEED).questions

        assertTrue(questions.all { it.type == QuizQuestionType.MULTIPLE_CHOICE && it.options.size == 4 })
    }

    @Test
    fun onlyFiveFirstMeetingsADayAndReviewsComeFirst() {
        // 백로그 048: eight expressions never asked before, three due reviews: 3 reviews + 5 cards.
        val fresh = (1..8).map { expression("new$it", "뜻$it", reviewed = false) }
        val reviews = (1..3).map { expression("old$it", "옛뜻$it", nextReviewAt = NOW - it * DAY) }

        val questions = QuizBuilder.build(null, fresh + reviews, fresh + reviews + DISTRACTORS, SEED).questions

        assertEquals(8, questions.size)
        assertEquals(5, questions.count { it.type == QuizQuestionType.LEARN_CARD })
        assertTrue(reviews.all { review -> questions.any { it.expression == review.displayExpression } })
    }

    @Test
    fun theBossAsksFirstMeetingsAsQuestionsWithNoDailyCap() {
        val fresh = (1..8).map { expression("new$it", "뜻$it", reviewed = false, consecutiveCorrect = 0) }

        val questions = QuizBuilder.build(null, fresh, fresh + DISTRACTORS, SEED, maxQuestions = 15, askCards = false).questions

        assertEquals(8, questions.size)
        assertTrue(questions.all { it.type == QuizQuestionType.MULTIPLE_CHOICE && it.options.size == 2 })
    }

    @Test
    fun cardsShownEarlierTodayReduceTheDaysAllowance() {
        val fresh = (1..8).map { expression("new$it", "뜻$it", reviewed = false) }

        val questions = QuizBuilder.build(null, fresh, fresh + DISTRACTORS, SEED, firstMeetingsShownToday = 3).questions

        assertEquals(2, questions.count { it.type == QuizQuestionType.LEARN_CARD })
        // A row a card left behind today is recognised as such; a judged row is not.
        val seen = expression("seen", "뜻", consecutiveCorrect = 0).copy(lastReviewedAtEpochMillis = NOW)
        assertTrue(QuizBuilder.isFirstMeetingShown(seen, NOW - 1, NOW + 1))
        assertTrue(!QuizBuilder.isFirstMeetingShown(seen.copy(consecutiveCorrectCount = 1), NOW - 1, NOW + 1))
        assertTrue(!QuizBuilder.isFirstMeetingShown(seen, NOW + 1, NOW + 2))
    }

    @Test
    fun shortGlossesMakeTheOptionsOnlyWhenEveryOptionHasOne() {
        // 백로그 046: short for all, or long for all — never a mix that gives the answer away.
        val target = expression("sketchy", "수상한 느낌이 드는 것을 말해요", consecutiveCorrect = 1, shortMeaning = "수상한")
        val shortPool = listOf(
            expression("hang out", "친구와 시간을 보내다", shortMeaning = "놀다"),
            expression("call it a day", "오늘 일을 여기서 끝내다", shortMeaning = "그만하다"),
            expression("on the fence", "결정을 못 하고 있다", shortMeaning = "망설이는"),
        )
        val withShort = QuizBuilder.build(null, listOf(target), listOf(target) + shortPool, SEED).questions.single()
        assertEquals(setOf("수상한", "놀다", "그만하다", "망설이는"), withShort.options.map { it.text }.toSet())

        // One distractor without a gloss: not enough short ones for four options, so all long.
        val mixedPool = shortPool.take(2) + expression("piece of cake", "아주 쉬운 일", shortMeaning = "")
        val withLong = QuizBuilder.build(null, listOf(target), listOf(target) + mixedPool, SEED).questions.single()
        assertTrue(withLong.options.any { it.text == "수상한 느낌이 드는 것을 말해요" })
        assertTrue(withLong.options.none { it.text == "수상한" })
    }

    @Test
    fun thePassagesOwnQuestionsComeFirstAndCountOutsideTheCap() {
        // 백로그 042: comprehension questions open the quiz and belong to no expression.
        val content = LearningContent(
            title = "A cafe",
            mode = ContentMode.CONVERSATION,
            segments = listOf(ContentSegment("Emma", "That sounds sketchy.")),
            expressions = listOf(ContentExpression("sketchy", "수상한", 0, 12, 19)),
            comprehension = listOf(
                ComprehensionQuestion("Emma는 왜?", listOf("의심", "기쁨", "졸림", "배고픔"), 0, "수상하다고 했다."),
                ComprehensionQuestion("다음 대답은?", listOf("Sure.", "No way.", "Why?", "Later."), 1, ""),
            ),
        )
        val candidates = (1..12).map { expression("word$it", "뜻$it") }

        val questions = QuizBuilder.build(content, candidates, candidates, SEED).questions

        assertEquals(12, questions.size)
        assertEquals(QuizQuestionType.COMPREHENSION, questions[0].type)
        assertEquals("Emma는 왜?", questions[0].questionText)
        assertEquals("", questions[0].expression)
        assertEquals(listOf(QuizOption("의심", true), QuizOption("기쁨", false), QuizOption("졸림", false), QuizOption("배고픔", false)), questions[0].options)
        assertEquals("지문의 흐름을 떠올려 보세요.", questions[1].explanation)
        assertTrue(questions.drop(2).none { it.type == QuizQuestionType.COMPREHENSION })
    }

    @Test
    fun aCallerCanRaiseTheCap() {
        val candidates = (1..20).map { expression("word$it", "뜻$it") }

        // The weekend boss (백로그 041) asks fifteen.
        val result = QuizBuilder.build(null, candidates, candidates, SEED, maxQuestions = 15)

        assertEquals(15, result.questions.size)
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
