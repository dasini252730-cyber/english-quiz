package com.englishquiz.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The face, depth and label colours of a raised button. */
data class MongleButtonColors(val container: Color, val shadow: Color, val content: Color) {
    companion object {
        val Primary = MongleButtonColors(MongleColor.Purple, MongleColor.PurpleDeep, Color.White)
        val Accent = MongleButtonColors(MongleColor.Yellow, MongleColor.YellowDeep, MongleColor.Ink)
        val Positive = MongleButtonColors(MongleColor.GreenDeep, MongleColor.GreenInk, Color.White)
    }
}

/**
 * The canvas's primary action: a solid face sitting on a darker slab that the press sinks into.
 * The whole control is one clickable node so a test can find it by its label and tap it.
 */
@Composable
fun MongleButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: MongleButtonColors = MongleButtonColors.Primary,
    height: Dp = 56.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val depth = 5.dp
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height + depth)
            .clip(shape)
            .background(if (enabled) colors.shadow else MongleColor.BorderStrong)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .offset(y = if (pressed && enabled) depth else 0.dp)
                .clip(shape)
                .background(if (enabled) colors.container else MongleColor.Border),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                color = if (enabled) colors.content else MongleColor.InkFaded,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

/** How an answer option is drawn: before an answer, and after it is marked. */
enum class OptionTone { Idle, Correct, Wrong, Muted }

/**
 * A full-width answer option. The tone only changes the colours, never the size, so marking an
 * answer cannot reflow the list under the reader's finger. The height is a floor rather than a
 * fixed size: an option carries an AI-written meaning, which can run to several lines.
 */
@Composable
fun MongleOptionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: OptionTone = OptionTone.Idle,
) {
    val (face, border, content) = when (tone) {
        OptionTone.Idle -> Triple(MongleColor.Surface, MongleColor.BorderStrong, MongleColor.Ink)
        OptionTone.Correct -> Triple(MongleColor.GreenSoft, MongleColor.Green, MongleColor.GreenInk)
        OptionTone.Wrong -> Triple(MongleColor.Surface, MongleColor.Danger, MongleColor.Danger)
        OptionTone.Muted -> Triple(MongleColor.Surface, MongleColor.Border, MongleColor.InkFaded)
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(border)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(start = 2.dp, top = 2.dp, end = 2.dp, bottom = 4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(face),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                color = content,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }
    }
}

/**
 * An icon-only control. [size] is the touch target and never drops below 44dp; [circleSize] is
 * the painted disc, which the canvas often draws smaller than the target it sits in.
 * [background] null keeps it flat on the page.
 */
@Composable
fun MongleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = MongleColor.InkMuted,
    background: Color? = null,
    size: Dp = 44.dp,
    circleSize: Dp = size,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier = modifier
            .size(size.coerceAtLeast(44.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(circleSize)
                .clip(RoundedCornerShape(circleSize / 2))
                .background(background ?: Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) tint else MongleColor.InkFaded,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}
