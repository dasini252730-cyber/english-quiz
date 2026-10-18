package com.englishquiz.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.englishquiz.app.data.ai.Language
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.englishquiz.app.data.ai.ContentMode
import com.englishquiz.app.data.ai.LearningContent
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleIconButton
import com.englishquiz.app.ui.theme.MongleIcons
import com.englishquiz.app.ui.theme.MongleTopBar
import com.englishquiz.app.ui.theme.mongleScreenInsets

@Composable
fun ReaderScreen(
    content: LearningContent,
    speechState: SpeechState,
    onPlay: (List<SpeechLine>) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onQuiz: (() -> Unit)? = null,
    meaningState: MeaningUiState? = null,
    onTokenTap: (ReaderToken) -> Unit = {},
    onDismissMeaning: () -> Unit = {},
    onRetryMeaning: () -> Unit = {},
    onRetrySave: () -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
    /** "시즌 2 · 3/7" over an episode (백로그 063); null for any other passage. */
    subtitle: String? = null,
    /** Words a Chinese passage is cut along outside its annotated phrases (백로그 066): glossary and saved expressions. */
    knownWords: Collection<String> = emptyList(),
) {
    val isConversation = content.mode == ContentMode.CONVERSATION
    val rightSpeaker = remember(content) { rightHandSpeaker(content) }
    // 백로그 066: the pinyin line under each Chinese segment, off by default so the characters are read first.
    var showPinyin by rememberSaveable { mutableStateOf(false) }
    val hasPinyin = content.language == Language.CHINESE && content.segments.any { it.pinyin.isNotEmpty() }
    Column(Modifier.fillMaxSize().background(MongleColor.Cream).mongleScreenInsets()) {
        MongleTopBar(
            title = if (isConversation) "Conversation" else "Story",
            onBack = onBack,
        ) {
            if (hasPinyin) PinyinToggle(showPinyin) { showPinyin = !showPinyin }
            if (speechState.playing) {
                MongleIconButton(
                    icon = MongleIcons.Close,
                    contentDescription = "재생 중단",
                    onClick = onStop,
                    tint = MongleColor.Surface,
                    background = MongleColor.Purple,
                )
            } else {
                MongleIconButton(
                    icon = MongleIcons.Play,
                    contentDescription = "전체 재생",
                    onClick = { onPlay(content.segments.map { SpeechLine(it.text, it.speaker) }) },
                    enabled = speechState.ready,
                    tint = MongleColor.Surface,
                    background = MongleColor.Purple,
                    iconSize = 18.dp,
                )
            }
        }
        speechState.error?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = MongleColor.Danger,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (isConversation) 16.dp else 6.dp),
        ) {
            if (!isConversation) {
                item(key = "title") {
                    subtitle?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MongleColor.InkMuted) }
                    Text(
                        text = content.title,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 26.sp,
                        lineHeight = 33.sp,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }
            }
            itemsIndexed(content.segments) { index, segment ->
                val play = { onPlay(listOf(SpeechLine(segment.text, segment.speaker))) }
                if (isConversation) {
                    ConversationSegment(
                        segment = segment,
                        expressions = content.expressions,
                        index = index,
                        onRight = segment.speaker == rightSpeaker,
                        canPlay = speechState.ready,
                        onPlay = play,
                        onTokenTap = onTokenTap,
                        language = content.language,
                        knownWords = knownWords,
                        showPinyin = showPinyin,
                    )
                } else {
                    StorySegment(
                        segment = segment,
                        expressions = content.expressions,
                        index = index,
                        canPlay = speechState.ready,
                        onPlay = play,
                        onTokenTap = onTokenTap,
                        language = content.language,
                        knownWords = knownWords,
                        showPinyin = showPinyin,
                    )
                }
            }
        }
        if (onQuiz != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MongleColor.Surface)
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    "노란 표현을 누르면 뜻이 나오고 복습함에 저장돼요",
                    style = MaterialTheme.typography.bodySmall,
                    color = MongleColor.InkMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                MongleButton("다음 단계: 퀴즈", onQuiz)
            }
        }
    }
    meaningState?.let { state ->
        MeaningOverlay(
            state = state,
            canPlay = speechState.ready,
            onDismiss = onDismissMeaning,
            onRetry = onRetryMeaning,
            onRetrySave = onRetrySave,
            // A pronunciation is the learner's own line, not a character's: default voice.
            onPlay = { onPlay(listOf(SpeechLine(it))) },
        )
    }
}

/**
 * Which speaker sits on the right of the conversation: the learner if the content names one,
 * otherwise the second voice to appear. A one-voice content keeps every bubble on the left.
 */
private fun rightHandSpeaker(content: LearningContent): String? {
    val speakers = content.segments.map { it.speaker }.distinct()
    return speakers.firstOrNull { it.equals("Me", ignoreCase = true) || it == "나" || it == "我" }
        ?: speakers.getOrNull(1)
}

/** "拼音" in the top bar of a Chinese passage (백로그 066): filled while the pinyin lines are shown. */
@Composable
private fun PinyinToggle(on: Boolean, onToggle: () -> Unit) {
    Text(
        text = "拼音",
        style = MaterialTheme.typography.labelLarge,
        color = if (on) MongleColor.Surface else MongleColor.Purple,
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) MongleColor.Purple else MongleColor.PurpleSoft)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
