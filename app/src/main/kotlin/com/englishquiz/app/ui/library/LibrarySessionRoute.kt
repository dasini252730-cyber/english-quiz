package com.englishquiz.app.ui.library

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.ui.session.SessionStatusScreen
import kotlinx.coroutines.CancellationException
import com.englishquiz.app.data.repository.findDailyContent

/**
 * Loads the library passage named by [pick] (`"<learningDate>|<mode>"`, as the list hands it
 * over) and gives it to [session], which runs the ordinary reader → quiz → result flow on it
 * (백로그 026). A pick that cannot be read says so and offers the way back; it never generates.
 */
@Composable
fun LibrarySessionRoute(
    repository: LearningRepository,
    pick: String?,
    onBack: () -> Unit,
    session: @Composable (LearningContent) -> Unit,
) {
    val loaded by produceState<Result<LearningContent?>?>(initialValue = null, repository, pick) {
        value = try {
            val (date, wire) = pick?.split("|", limit = 2)?.takeIf { it.size == 2 } ?: listOf("", "")
            val mode = ContentMode.entries.firstOrNull { it.wireValue == wire }
            Result.success(mode?.let { repository.findDailyContent(date, it) })
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Result.failure(error)
        }
    }
    val content = loaded?.getOrNull()
    when {
        loaded == null -> SessionStatusScreen(title = TITLE, message = "이야기를 불러오고 있어요.", onBack = onBack)
        content == null -> SessionStatusScreen(title = TITLE, message = "이 이야기를 불러오지 못했어요.", onBack = onBack)
        else -> session(content)
    }
}

private const val TITLE = "다시 읽기"
