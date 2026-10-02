package com.englishquiz.app.data.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.domain.game.Badge
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 백로그 039/040: what the game layer stores survives, and a shield is paid for exactly once. */
@RunWith(AndroidJUnit4::class)
class GameProgressRepositoryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val handles = mutableListOf<Pair<Job, File>>()

    @After
    fun releaseDataStores() {
        handles.forEach { (job, file) ->
            job.cancel()
            file.delete()
        }
        handles.clear()
    }

    @Test
    fun startsEmpty() = runBlocking {
        val progress = GameProgressRepository(newDataStore()).progress.first()

        assertEquals(GameProgress(), progress)
        assertEquals(120, progress.balance(totalPointsEarned = 120))
    }

    @Test
    fun aShieldCostsItsPriceAndOnlySellsWhenTheBalanceCoversIt() = runBlocking {
        val repository = GameProgressRepository(newDataStore())

        assertFalse(repository.buyShield(totalPointsEarned = 299))
        assertTrue(repository.buyShield(totalPointsEarned = 300))
        // The balance is what was earned minus what was spent, so the second one is unaffordable.
        assertFalse(repository.buyShield(totalPointsEarned = 300))

        val progress = repository.progress.first()
        assertEquals(1, progress.shields)
        assertEquals(300, progress.pointsSpent)
        assertEquals(0, progress.balance(300))
    }

    @Test
    fun usingAShieldCoversTheDayOnceAndNeverGoesBelowZero() = runBlocking {
        val repository = GameProgressRepository(newDataStore())
        repository.buyShield(totalPointsEarned = 300)

        assertTrue(repository.useShield("2026-10-01"))
        // The same day again is already covered: nothing more is spent.
        assertTrue(repository.useShield("2026-10-01"))
        assertFalse(repository.useShield("2026-10-02"))

        val progress = repository.progress.first()
        assertEquals(0, progress.shields)
        assertEquals(setOf("2026-10-01"), progress.shieldedDates)
    }

    @Test
    fun seenBadgesAccumulate() = runBlocking {
        val repository = GameProgressRepository(newDataStore())

        repository.markBadgesSeen(listOf(Badge.FIRST_SESSION))
        repository.markBadgesSeen(listOf(Badge.STREAK_7, Badge.FIRST_SESSION))
        repository.markBadgesSeen(emptyList())

        assertEquals(setOf("FIRST_SESSION", "STREAK_7"), repository.progress.first().badgesSeen)
    }

    private fun newDataStore(): DataStore<Preferences> {
        val file = File(context.filesDir, "game-progress-${System.nanoTime()}.preferences_pb")
        val job = SupervisorJob()
        handles += job to file
        return PreferenceDataStoreFactory.create(
            scope = CoroutineScope(job + Dispatchers.IO),
            produceFile = { file },
        )
    }
}
