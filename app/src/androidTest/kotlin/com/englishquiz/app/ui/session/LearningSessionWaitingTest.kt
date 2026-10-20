package com.englishquiz.app.ui.session

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.ContextualMeaning
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.season.SeasonFixtures
import com.englishquiz.app.ui.theme.EnglishQuizTheme
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 백로그 075: a tap with no episode ready shows "준비 중이에요" and the library instead of a long
 * loading, never generates, and opens the episode on its own once the pipeline has stored it.
 */
@RunWith(AndroidJUnit4::class)
class LearningSessionWaitingTest {
    @get:Rule val compose = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtures = SeasonFixtures(context)

    @After
    fun tearDown() = fixtures.close()

    @Test
    fun noEpisodeReadyShowsTheWaitingScreenAndTheEpisodeOpensWhenItArrives() {
        var generations = 0
        var libraryOpened = 0
        // The coordinator asks nothing of the pipeline here: the test stores the episode itself, as the pipeline would.
        val seasons = com.englishquiz.app.data.season.SeasonCoordinator(fixtures.repository) { }
        compose.setContent {
            EnglishQuizTheme {
                LearningSessionRoute(
                    mode = ContentMode.STORY,
                    repository = fixtures.repository,
                    difficulty = 2,
                    generateContent = { generations++; error("the waiting screen never generates") },
                    explainMeaning = { ContextualMeaning(it.expression, "뜻") },
                    onExit = {},
                    seasons = seasons,
                    language = Language.ENGLISH,
                    onLibrary = { libraryOpened++ },
                )
            }
        }
        compose.awaitText(WAITING_TITLE)
        compose.onNodeWithText(WAITING_LIBRARY).performClick()
        assertEquals(1, libraryOpened)
        assertEquals(0, generations)

        // The pipeline stores episode 1: the waiting screen leaves on its own and the Reader shows it.
        runBlocking { fixtures.storeLegacyEpisode(Language.ENGLISH, seasonNumber = 1, number = 1) }
        // The fixture's story ends "...together in S1E1."; the Reader shows it word by word.
        compose.awaitText("S1E1.")
        assertEquals(0, generations)
        assertTrue(runBlocking { fixtures.dao.countUnread(Language.ENGLISH.wireValue) } == 0)
    }
}
