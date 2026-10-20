package com.englishquiz.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.local.LearningSessionEntity
import com.englishquiz.app.domain.quiz.QuizMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 백로그 066: an expression, a passage, a session and an answer belong to one language. The same
 * text saved in English and in Chinese is two rows, and every per-language read sees only its own.
 */
@RunWith(AndroidJUnit4::class)
class LearningRepositoryLanguageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database by lazy { LearningDatabase.create(context, DATABASE_NAME) }
    private val repository by lazy { LearningRepository(database) }

    @After
    fun removeDatabase() {
        database.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun theSameTextInTwoLanguagesIsTwoRowsAndEachLanguageReadsOnlyItsOwn() = runBlocking {
        repository.saveExpression(Language.ENGLISH, "hangout", "놀다", 100, "Let's hangout.")
        repository.saveExpression(Language.CHINESE, "hangout", "(중국어 지문 속 영어) 놀다", 200, "我们 hangout 吧。")

        assertEquals(2, database.learningDao().savedExpressionCount())
        val english = repository.listSavedExpressions(Language.ENGLISH).single()
        val chinese = repository.listSavedExpressions(Language.CHINESE).single()
        assertEquals("en" to "놀다", english.language to english.contextMeaning)
        assertEquals("zh" to "(중국어 지문 속 영어) 놀다", chinese.language to chinese.contextMeaning)
        // Both are new, so both are due and boss material — each in its own language only.
        assertEquals(listOf(english), repository.findDueExpressions(Language.ENGLISH, NOW))
        assertEquals(listOf(chinese), repository.findDueExpressions(Language.CHINESE, NOW))
        assertEquals(listOf(english), repository.listBossCandidates(Language.ENGLISH, NOW, 10))
        assertEquals(listOf(chinese), repository.listBossCandidates(Language.CHINESE, NOW, 10))
        assertEquals(listOf(english), repository.observeSavedExpressions(Language.ENGLISH).first())
        assertEquals(listOf(chinese), repository.observeSavedExpressions(Language.CHINESE).first())
        // The game counts the learner's whole collection (백로그 038).
        assertEquals(setOf(english, chinese), repository.listAllSavedExpressions().toSet())

        // Review state, the answer log and the stored meaning follow the language too.
        repository.recordAnswer(Language.ENGLISH, "hangout", wasCorrect = true, evaluatedAtEpochMillis = NOW)
        repository.recordQuizAnswer(Language.ENGLISH, "hangout", QuizMode.DAILY, wasCorrect = true, hintUsed = false, answeredAtEpochMillis = NOW)
        assertEquals(1, repository.listSavedExpressions(Language.ENGLISH).single().consecutiveCorrectCount)
        assertEquals(chinese, repository.listSavedExpressions(Language.CHINESE).single())
        assertEquals(1, repository.listQuizAnswers(Language.ENGLISH, "hangout").size)
        assertEquals(emptyList<Any>(), repository.listQuizAnswers(Language.CHINESE, "hangout"))
        assertEquals("놀다", repository.findSavedMeaning(Language.ENGLISH, "hangout", "Let's hangout."))
        assertNull(repository.findSavedMeaning(Language.CHINESE, "hangout", "Let's hangout."))
    }

    @Test
    fun aChineseKeyIgnoresSpacesAndPunctuationAndKeepsThePinyin() = runBlocking {
        repository.saveExpression(Language.CHINESE, "打电话。", "전화하다", 100, "我们明天打电话。", pinyin = "dǎ diànhuà")
        // The same characters with a space, from another sentence: the row it already has.
        assertFalse(repository.saveExpressionIfNew(Language.CHINESE, "打 电话", "전화를 걸다", 200, "你打电话了吗？", pinyin = "dǎ diàn huà"))
        repository.saveExpression(Language.CHINESE, "打电话", "전화하다", 300, "我们明天打电话。")

        val row = repository.listSavedExpressions(Language.CHINESE).single()
        assertEquals("打电话", row.normalizedExpression)
        assertEquals("打电话。", row.displayExpression)
        assertEquals("전화하다", row.contextMeaning)
        assertEquals("dǎ diànhuà", row.pinyin)
        assertEquals(2, row.tapCount)
        assertEquals("打电话", normalizeExpression(Language.CHINESE, " 打，电话！ "))
        // English still folds case and runs of whitespace and keeps its punctuation (백로그 012).
        assertEquals("pull it off.", normalizeExpression(Language.ENGLISH, "Pull   It Off."))

        // A Chinese row saved without pinyin takes the pinyin a later passage brings, once.
        repository.saveExpressionIfNew(Language.CHINESE, "没关系", "괜찮다", 400, "没关系。")
        repository.saveExpressionIfNew(Language.CHINESE, "没关系", "괜찮다", 500, "没关系。", pinyin = "méi guānxi")
        repository.saveExpressionIfNew(Language.CHINESE, "没关系", "괜찮다", 600, "没关系。", pinyin = "mei guan xi")
        assertEquals("méi guānxi", database.learningDao().findSavedExpression("zh", "没关系")?.pinyin)
    }

    @Test
    fun aDayHasOnePassagePerLanguageAndModeAndTheLibraryAndSessionsAreReadByLanguage() = runBlocking {
        val english = LearningContent(
            title = "A Saturday Plan",
            mode = ContentMode.CONVERSATION,
            segments = listOf(ContentSegment("Alex", "Let's pull it off together.")),
            expressions = listOf(ContentExpression("pull it off", "해내다", 0, 6, 17)),
        )
        val chinese = LearningContent(
            title = "周六的计划",
            mode = ContentMode.CONVERSATION,
            segments = listOf(ContentSegment("小明", "我们明天打电话吧。", pinyin = "Wǒmen míngtiān dǎ diànhuà ba.")),
            expressions = listOf(ContentExpression("打电话", "전화하다", 0, 4, 7, pinyin = "dǎ diànhuà")),
            language = Language.CHINESE,
        )

        repository.saveDailyContent(DATE, english, nowEpochMillis = 100)
        repository.saveDailyContent(DATE, chinese, nowEpochMillis = 200)

        // Same date, same mode: two passages, one per language, neither replacing the other.
        assertEquals(english, repository.findDailyContent(Language.ENGLISH, DATE, ContentMode.CONVERSATION))
        assertEquals(chinese, repository.findDailyContent(Language.CHINESE, DATE, ContentMode.CONVERSATION))
        assertNull(repository.findDailyContent(Language.CHINESE, DATE, ContentMode.STORY))
        assertEquals(listOf("A Saturday Plan" to "en"), repository.listLibrary(Language.ENGLISH).map { it.title to it.language })
        assertEquals(listOf("周六的计划" to "zh"), repository.listLibrary(Language.CHINESE).map { it.title to it.language })

        // A level window reads its language's sessions only; the streak reads them all.
        repository.recordCompletedSession(session(completedAt = 300, questions = 3, language = Language.CHINESE))
        repository.recordCompletedSession(session(completedAt = 400, questions = 2, language = Language.ENGLISH))
        assertEquals(listOf(2), repository.listRecentSessionSummaries(ContentMode.CONVERSATION, 5).map { it.quizQuestionCount })
        assertEquals(listOf(3), repository.listRecentSessionSummaries(ContentMode.CONVERSATION, 5, Language.CHINESE).map { it.quizQuestionCount })
        assertEquals(2, repository.listAllSessions().size)
        assertEquals(listOf(DATE), repository.listLearningDates())
    }

    private fun session(completedAt: Long, questions: Int, language: Language) = LearningSessionEntity(
        learningDate = DATE,
        completedAtEpochMillis = completedAt,
        learnedExpressionCount = questions,
        newlySavedExpressionCount = 1,
        quizCorrectCount = questions,
        quizQuestionCount = questions,
        mode = ContentMode.CONVERSATION.wireValue,
        language = language.wireValue,
    )

    private companion object {
        const val DATABASE_NAME = "test-repository-language.db"
        const val DATE = "2026-10-18"
        const val NOW = 1_000_000L
    }
}
