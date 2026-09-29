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
            Bubble(segment, expressions, index, onRight, onTokenTap)
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
        SegmentTokens(segment, expressions, index, FontFamily.SansSerif, onTokenTap)
    }
}

/** One story sentence: a play button beside serif body text. */
@Composable
internal fun StorySegment(
    segment: ContentSegment,
    expressions: List<ContentExpression>,
    index: Int,
    canPlay: Boolean,
    onPlay: () -> Unit,
    onTokenTap: (ReaderToken) -> Unit,
) {
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
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/**
 * Draws a segment as separate word tokens so a tap lands on one expression. The AI-annotated
 * expressions carry the canvas's yellow marker; everything else is plain body text.
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
) {
    val tokens = remember(segment, expressions, index) {
        tokenize(segment.text, expressions, index)
    }
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    .padding(horizontal = 2.dp),
            )
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
