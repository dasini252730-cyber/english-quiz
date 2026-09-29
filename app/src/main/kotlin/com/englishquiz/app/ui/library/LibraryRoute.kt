package com.englishquiz.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.local.LibraryItem
import com.englishquiz.app.data.repository.LearningRepository
import com.englishquiz.app.domain.library.LibraryEntry
import com.englishquiz.app.domain.library.LibraryPolicy
import com.englishquiz.app.ui.theme.MongleCard
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleIconButton
import com.englishquiz.app.ui.theme.MongleIcons
import com.englishquiz.app.ui.theme.mongleScreenInsets
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

/**
 * The library: every passage generated on an earlier day, offered to be read again (백로그 026).
 * Reading one costs no generation, and two to three weeks after the first read it is a review.
 */
@Composable
fun LibraryRoute(
    repository: LearningRepository,
    onOpen: (LibraryItem) -> Unit,
    onBack: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    BackHandler(onBack = onBack)
    val entries by produceState<List<LibraryEntry>?>(initialValue = null, repository, today) {
        value = try {
            LibraryPolicy.arrange(repository.listLibrary(), today)
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
    }
    LibraryScreen(entries = entries, onOpen = onOpen, onBack = onBack)
}

@Composable
internal fun LibraryScreen(
    entries: List<LibraryEntry>?,
    onOpen: (LibraryItem) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MongleColor.Cream).mongleScreenInsets()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MongleIconButton(MongleIcons.ChevronLeft, "홈으로", onBack)
            Text(
                "지난 이야기",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
        }
        when {
            entries == null -> Message("지난 이야기를 찾고 있어요.")
            entries.isEmpty() -> Message("아직 다시 읽을 이야기가 없어요. 내일부터 오늘의 이야기가 여기에 쌓여요.")
            else -> LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(entries, key = { "${it.item.learningDate}|${it.item.mode}" }) { entry ->
                    LibraryRow(entry) { onOpen(entry.item) }
                }
            }
        }
    }
}

@Composable
private fun Message(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyLarge,
        color = MongleColor.InkMuted,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
    )
}

@Composable
private fun LibraryRow(entry: LibraryEntry, onClick: () -> Unit) {
    val mode = ContentMode.entries.firstOrNull { it.wireValue == entry.item.mode }
    val modeLabel = when (mode) {
        ContentMode.CONVERSATION -> "Conversation"
        ContentMode.STORY -> "Story"
        null -> entry.item.mode
    }
    MongleCard(
        modifier = Modifier.clickable(role = Role.Button, onClick = onClick),
        border = if (entry.isSweetSpot) MongleColor.HighlightBorder else MongleColor.BorderStrong,
        corner = 18.dp,
    ) {
        Text(entry.item.title, style = MaterialTheme.typography.titleSmall)
        Text(
            "${entry.daysAgo}일 전 · $modeLabel",
            style = MaterialTheme.typography.bodyMedium,
            color = MongleColor.InkMuted,
        )
        if (entry.isSweetSpot) {
            Text(
                "다시 읽기 좋은 때",
                style = MaterialTheme.typography.labelLarge,
                color = MongleColor.AmberInk,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
