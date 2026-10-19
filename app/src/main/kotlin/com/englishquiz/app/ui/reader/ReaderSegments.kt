package com.englishquiz.app.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.englishquiz.app.data.ai.ContentExpression
import com.englishquiz.app.data.ai.ContentSegment
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.data.ai.SpeakerGender
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleIconButton
import com.englishquiz.app.ui.theme.MongleIcons

/** One line of dialogue: the speaker's name, the bubble, and its own play button. */
@Composable
internal fun ConversationSegment(
    segment: ContentSegment,
    expressions: List<ContentExpression>,
    index: Int,
    onRight: Boolean,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onTokenTap: (ReaderToken) -> Unit,
    language: Language = Language.ENGLISH,
    knownWords: Collection<String> = emptyList(),
    /** Whether the Chinese line's pinyin is shown under it (백로그 066). */
    showPinyin: Boolean = false,
) {
    val playButton: @Composable () -> Unit = {
        MongleIconButton(
            icon = MongleIcons.Play,
            contentDescription = "${index + 1}번 문장 재생",
            onClick = onPlay,
            enabled = canPlay,
            tint = MongleColor.BlueIcon,
            background = MongleColor.BlueSoft,
            circleSize = 32.dp,
            iconSize = 14.dp,
        )
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (onRight) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = segment.speaker,
            style = MaterialTheme.typography.labelMedium,
            color = MongleColor.InkMuted,
            modifier = Modifier.padding(horizontal = 6.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (onRight) playButton()
            Bubble(segment, expressions, index, onRight, onTokenTap, language, knownWords, showPinyin)
            if (!onRight) playButton()
        }
    }
}

@Composable
private fun Bubble(
    segment: ContentSegment,
    expressions: List<ContentExpression>,
    index: Int,
    onRight: Boolean,
    onTokenTap: (ReaderToken) -> Unit,
    language: Language,
    knownWords: Collection<String>,
    showPinyin: Boolean,
) {
    val face = if (onRight) MongleColor.PurpleSoft else MongleColor.Surface
    val border = if (onRight) MongleColor.PurpleSoftBorder else MongleColor.Border
    val shape = if (onRight) {
        RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
    }
    Column(
        modifier = Modifier
            .widthIn(max = 250.dp)
            .clip(shape)
            .background(border)
            .padding(2.dp)
            .clip(shape)
            .background(face)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        SegmentTokens(segment, expressions, index, FontFamily.SansSerif, onTokenTap, language = language, knownWords = knownWords, showPinyin = showPinyin)
    }
}

/**
 * One story sentence: a play button beside serif body text. A line of dialogue carries its
 * speaker's name above it ([speakerLabel]); narration carries none, so who is talking is visible
 * without the bubbles of the conversation.
 */
@Composable
internal fun StorySegment(
    segment: ContentSegment,
    expressions: List<ContentExpression>,
    index: Int,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onTokenTap: (ReaderToken) -> Unit,
    language: Language = Language.ENGLISH,
    knownWords: Collection<String> = emptyList(),
    showPinyin: Boolean = false,
    speakerLabel: String? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
    if (speakerLabel != null) {
        Text(
            text = speakerLabel,
            style = MaterialTheme.typography.labelMedium,
            color = MongleColor.Purple,
            modifier = Modifier.padding(start = 34.dp, top = 6.dp),
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MongleIconButton(
            icon = MongleIcons.Play,
            contentDescription = "${index + 1}번 문장 재생",
            onClick = onPlay,
            enabled = canPlay,
            tint = MongleColor.BlueIcon,
            background = MongleColor.BlueSoft,
            circleSize = 28.dp,
            iconSize = 12.dp,
        )
        SegmentTokens(
            segment = segment,
            expressions = expressions,
            index = index,
            fontFamily = FontFamily.Serif,
            onTokenTap = onTokenTap,
            language = language,
            knownWords = knownWords,
            showPinyin = showPinyin,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
    }
}

/** Whether a story segment is narration (백로그 050's `narrator` gender, or the name itself) rather than someone's line. */
internal fun isNarration(speaker: String, speakers: Map<String, SpeakerGender>): Boolean =
    speakers[speaker] == SpeakerGender.NARRATOR || speaker.trim().lowercase() in NARRATOR_NAMES

private val NARRATOR_NAMES = setOf("narrator", "narration", "내레이터", "나레이터", "旁白", "叙述者")

/**
 * Draws a segment as separate word tokens so a tap lands on one expression. The AI-annotated
 * expressions carry the canvas's yellow marker; everything else is plain body text. English
 * words keep the space the tokeniser removed between them; Chinese characters sit flush, as
 * written, because the gap would read as a word boundary that is not there (백로그 071).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SegmentTokens(
    segment: ContentSegment,
    expressions: List<ContentExpression>,
    index: Int,
    fontFamily: FontFamily,
    onTokenTap: (ReaderToken) -> Unit,
    modifier: Modifier = Modifier,
    language: Language = Language.ENGLISH,
    knownWords: Collection<String> = emptyList(),
    showPinyin: Boolean = false,
) {
    val tokens = remember(segment, expressions, index, language, knownWords) {
        tokenize(segment.text, expressions, index, language, knownWords)
    }
    val gap = if (language == Language.CHINESE) 0.dp else 4.dp
    val sidePadding = if (language == Language.CHINESE) 0.dp else 2.dp
    Column(modifier = modifier) {
    // 백로그 066: the line's pinyin, above the characters as in a textbook, only while the reader asks for it.
    if (showPinyin && segment.pinyin.isNotEmpty()) {
        Text(segment.pinyin, style = MaterialTheme.typography.bodySmall, color = MongleColor.InkMuted, modifier = Modifier.padding(bottom = 2.dp))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(gap)) {
        for (token in tokens) {
            Text(
                text = token.display,
                style = MaterialTheme.typography.bodyLarge,
                fontFamily = fontFamily,
                fontWeight = if (token.isExpression) FontWeight.Bold else FontWeight.Normal,
                modifier = Modifier
                    .then(if (token.isExpression) Modifier.highlight() else Modifier)
                    .then(
                        if (token.isTappable) {
                            Modifier.clickable { onTokenTap(token) }
                        } else {
                            Modifier
                        },
                    )
                    .padding(horizontal = sidePadding),
            )
        }
    }
    }
}

/** The marker-pen background plus its darker underline, as drawn on the canvas. */
private fun Modifier.highlight(): Modifier = this
    .clip(RoundedCornerShape(4.dp))
    .background(MongleColor.Highlight)
    .drawBehind {
        val thickness = 2.dp.toPx()
        drawLine(
            color = MongleColor.HighlightBorder,
            start = Offset(0f, size.height - thickness / 2),
            end = Offset(size.width, size.height - thickness / 2),
            strokeWidth = thickness,
        )
    }
