package com.englishquiz.app.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.ai.AiLearningException
import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.ContextualMeaning
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.ai.MeaningRequest
import com.englishquiz.app.data.local.LearningDatabase
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.ui.reader.MeaningUiState
import com.englishquiz.app.ui.reader.ReaderRoute
import com.englishquiz.app.ui.reader.ReaderScreen
import com.englishquiz.app.ui.reader.SCRIM_DESCRIPTION
import com.englishquiz.app.ui.reader.SaveStatus
import com.englishquiz.app.ui.reader.SpeechState
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReaderScreenTest {
    @get:Rule val compose = createComposeRule()
    private val segments = listOf(
        ContentSegment("Emma", "That sounds sketchy."),
        ContentSegment("Sam", "Let's find another place."),
    )
    private val storyContent = LearningContent("A cafe", ContentMode.STORY, segments, emptyList())
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: LearningDatabase? = null

    @After
    fun closeDatabase() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
        context.deleteDatabase(BROKEN_DATABASE_NAME)
    }

    private fun openRepository(): LearningRepository {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        return LearningRepository(db)
    }

    /** Renders [storyContent] (or [content]) through ReaderRoute with the given collaborators. */
    private fun setStoryReader(
        repository: LearningRepository,
        content: LearningContent = storyContent,
        nowEpochMillis: () -> Long = System::currentTimeMillis,
        explainMeaning: suspend (MeaningRequest) -> ContextualMeaning,
    ) {
        compose.setContent {
            EnglishQuizTheme {
                ReaderRoute(content, repository, onBack = {}, nowEpochMillis = nowEpochMillis, explainMeaning = explainMeaning)
            }
        }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test fun conversationShowsSpeakersAndPassesOrderedPlaybackRequests() {
        val requests = mutableListOf<List<String>>()
        var quizCount = 0
        compose.setContent {
            EnglishQuizTheme {
                ReaderScreen(
                    LearningContent("A cafe", ContentMode.CONVERSATION, segments, emptyList()), SpeechState(ready = true),
                    onPlay = { requests += it.map { line -> line.text } }, onStop = {}, onBack = {}, onQuiz = { quizCount++ },
                )
            }
        }
        compose.onNodeWithText("Emma").assertIsDisplayed()
        compose.onNodeWithContentDescription("전체 재생").performClick()
        compose.onNodeWithContentDescription("2번 문장 재생").performClick()
        compose.onNodeWithText("다음 단계: 퀴즈").performClick()
        assertEquals(listOf(segments.map { it.text }, listOf(segments[1].text)), requests)
        assertEquals(1, quizCount)
    }

    @Test fun storyRemainsReadableWhenSpeechIsUnavailable() {
        compose.setContent {
            EnglishQuizTheme {
                ReaderScreen(storyContent, SpeechState(error = "음성 사용 불가"), onPlay = {}, onStop = {}, onBack = {})
            }
        }
        compose.onNodeWithText("Emma").assertDoesNotExist()
        // The segment is now rendered as tappable word tokens instead of one Text node.
        compose.onNodeWithText("That").assertIsDisplayed()
        compose.onNodeWithText("sketchy.").assertIsDisplayed()
        compose.onNodeWithText("음성 사용 불가").assertIsDisplayed()
        compose.onNodeWithContentDescription("전체 재생").assertIsNotEnabled()
        compose.onNodeWithText("다음 단계: 퀴즈").assertDoesNotExist()
    }

    @Test fun tappingWordShowsContextualMeaningAndSavesFeedback() {
        val repository = openRepository()
        setStoryReader(repository) { request -> ContextualMeaning(request.expression, "수상쩍다") }
        compose.onNodeWithText("sketchy.").performClick()
        waitForText("수상쩍다")
        compose.onNodeWithText("수상쩍다").assertIsDisplayed()
        compose.onNodeWithText("복습함에 저장했어요").assertIsDisplayed()
        val saved = runBlocking { repository.listSavedExpressions() }
        assertEquals(1, saved.size)
        assertEquals("sketchy", saved[0].displayExpression)
        assertEquals("수상쩍다", saved[0].contextMeaning)
    }

    @Test fun lookupFailureShowsRetryWithoutClaimingItWasSaved() {
        val repository = openRepository()
        setStoryReader(repository) { throw AiLearningException("network_error") }
        compose.onNodeWithText("sounds").performClick()
        waitForText("다시 시도")
        compose.onNodeWithText("네트워크 연결을 확인해 주세요.").assertIsDisplayed()
        compose.onNodeWithText("다시 시도").assertIsDisplayed()
        compose.onNodeWithText("복습함에 저장했어요").assertDoesNotExist()
        val saved = runBlocking { repository.listSavedExpressions() }
        assertTrue(saved.isEmpty())
    }

    @Test fun duplicateExpressionKeepsFirstSavedDateAndReviewState() {
        val repository = openRepository()
        runBlocking {
            repository.saveExpression("pull it off", "기존 뜻", 100L, "원래 문맥")
            repository.saveReviewProgress("pull it off", 150L, 200L, 3, 1, true)
        }
        val sentence = "I knew you could pull it off."
        val start = sentence.indexOf("pull it off")
        val expression = ContentExpression("pull it off", "already known", 0, start, start + "pull it off".length)
        val content = LearningContent("A story", ContentMode.STORY, listOf(ContentSegment("", sentence)), listOf(expression))
        setStoryReader(repository, content, nowEpochMillis = { 9_999L }) { request ->
            ContextualMeaning(request.expression, "새로 조회된 뜻")
        }
        compose.onNodeWithText("pull it off").performClick()
        waitForText("복습함에 저장했어요")
        val saved = runBlocking { repository.listSavedExpressions() }
        assertEquals(1, saved.size)
        assertEquals(100L, saved[0].firstSavedAtEpochMillis)
        assertEquals("기존 뜻", saved[0].contextMeaning)
        assertEquals(200L, saved[0].nextReviewAtEpochMillis)
        assertEquals(3, saved[0].consecutiveCorrectCount)
        assertEquals(1, saved[0].incorrectCount)
        assertTrue(saved[0].isMastered)
    }

    @Test fun explainMeaningReceivesFullSentenceAndSavesSameSentenceAsContext() {
        val repository = openRepository()
        var capturedRequest: MeaningRequest? = null
        setStoryReader(repository) { request ->
            capturedRequest = request
            ContextualMeaning(request.expression, "수상쩍다")
        }
        compose.onNodeWithText("sketchy.").performClick()
        waitForText("복습함에 저장했어요")
        // The AI must see the whole segment sentence; QuizBuilder's fill-in-the-blank relies on it.
        assertEquals("sketchy", capturedRequest?.expression)
        assertEquals(segments[0].text, capturedRequest?.context)
        val saved = runBlocking { repository.listSavedExpressions() }
        assertEquals(segments[0].text, saved[0].contextSentence)
    }

    @Test fun dismissButtonClosesOverlayAndKeepsReaderVisible() {
        val repository = openRepository()
        setStoryReader(repository) { request -> ContextualMeaning(request.expression, "수상쩍다") }
        compose.onNodeWithText("sketchy.").performClick()
        waitForText("수상쩍다")
        compose.onNodeWithText("닫기").performClick()
        compose.onNodeWithText("수상쩍다").assertDoesNotExist()
        compose.onNodeWithText("That").assertIsDisplayed()
        compose.onNodeWithText("sketchy.").assertIsDisplayed()
    }

    @Test fun tappingOutsideTheSheetClosesTheMeaningOverlay() {
        val repository = openRepository()
        setStoryReader(repository) { request -> ContextualMeaning(request.expression, "수상쩍다") }
        compose.onNodeWithText("sketchy.").performClick()
        waitForText("수상쩍다")
        compose.onNodeWithContentDescription(SCRIM_DESCRIPTION).performClick()
        compose.onNodeWithText("수상쩍다").assertDoesNotExist()
        compose.onNodeWithText("sketchy.").assertIsDisplayed()
    }

    @Test fun pronunciationButtonPlaysOnlyTheExpressionNotTheWholeSentence() {
        val requests = mutableListOf<List<String>>()
        val successState = MeaningUiState.Success("sketchy", "수상쩍다", SaveStatus.SAVED)
        compose.setContent {
            EnglishQuizTheme {
                ReaderScreen(
                    storyContent, SpeechState(ready = true),
                    onPlay = { requests += it.map { line -> line.text } }, onStop = {}, onBack = {}, meaningState = successState,
                )
            }
        }
        compose.onNodeWithContentDescription("발음 듣기").performClick()
        assertEquals(listOf(listOf("sketchy")), requests)
    }

    @Test fun saveFailureShowsRetryAndRetryOnlyRetriesSaveNotTheAiCall() {
        // A database whose table is gone fails every read and write deterministically.
        val brokenDatabase = LearningDatabase.create(context, BROKEN_DATABASE_NAME)
        val brokenRepository = LearningRepository(brokenDatabase)
        brokenDatabase.openHelper.writableDatabase.execSQL("DROP TABLE saved_expressions")
        val workingRepository = openRepository()
        val repositoryState = mutableStateOf<LearningRepository>(brokenRepository)
        var aiCallCount = 0
        compose.setContent {
            EnglishQuizTheme {
                ReaderRoute(storyContent, repositoryState.value, onBack = {}) { request ->
                    aiCallCount++
                    ContextualMeaning(request.expression, "수상쩍다")
                }
            }
        }
        compose.onNodeWithText("sketchy.").performClick()
        waitForText("복습함에 저장하지 못했어요")
        compose.onNodeWithText("다시 저장").assertIsDisplayed()
        assertEquals(1, aiCallCount)
        assertTrue(runBlocking { workingRepository.listSavedExpressions() }.isEmpty())
        compose.runOnIdle { repositoryState.value = workingRepository }
        compose.onNodeWithText("다시 저장").performClick()
        waitForText("복습함에 저장했어요")
        assertEquals(1, aiCallCount) // retrying save must not repeat the paid AI lookup
        val saved = runBlocking { workingRepository.listSavedExpressions() }
        assertEquals(1, saved.size)
        assertEquals("sketchy", saved[0].displayExpression)
    }

    private companion object {
        const val DATABASE_NAME = "test-reader-screen.db"
        const val BROKEN_DATABASE_NAME = "test-reader-screen-broken.db"
    }
}
