package com.englishquiz.app.data.repository

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.englishquiz.app.data.local.LearningDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 백로그 059: the kind of a phrase is kept with the row, filled in once for an older row and never overwritten. */
@RunWith(AndroidJUnit4::class)
class LearningRepositoryKindTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var database: LearningDatabase? = null

    @After
    fun removeDatabase() {
        database?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun aKindIsStoredFilledInOnceAndNeverReplaced() = runBlocking {
        val db = LearningDatabase.create(context, DATABASE_NAME)
        database = db
        val repository = LearningRepository(db)
        val dao = db.learningDao()

        // A kind outside the list is dropped at the boundary, like the server does.
        repository.saveExpressionIfNew("sketchy", "수상한", 100, "That sounds sketchy.", kind = "phrasal verb")
        assertEquals("", dao.findSavedExpression("sketchy")?.kind)
        // A row from before kinds were kept takes the kind a later passage brings, once.
        repository.saveExpressionIfNew("sketchy", "수상한", 200, "That sounds sketchy.", kind = "adjective")
        assertEquals("adjective", dao.findSavedExpression("sketchy")?.kind)
        repository.saveExpressionIfNew("sketchy", "수상한", 300, "That sounds sketchy.", kind = "noun")
        repository.saveExpression("sketchy", "수상한", 400, "That sounds sketchy.", kind = "verb")
        assertEquals("adjective", dao.findSavedExpression("sketchy")?.kind)

        // A tap saves the kind with the row, in its canonical spelling.
        repository.saveExpression("hang out", "놀다", 500, "Let's hang out.", kind = " Verb ")
        assertEquals("verb", dao.findSavedExpression("hang out")?.kind)
    }

    private companion object {
        const val DATABASE_NAME = "test-repository-kind.db"
    }
}
