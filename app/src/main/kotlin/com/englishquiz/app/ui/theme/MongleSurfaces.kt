package com.englishquiz.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The white panel of the canvas: a thin outline whose bottom edge is thicker, which is what gives
 * every surface in this design its slight lift.
 */
@Composable
fun MongleCard(
    modifier: Modifier = Modifier,
    face: Color = MongleColor.Surface,
    border: Color = MongleColor.Border,
    corner: Dp = 20.dp,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(corner))
            .background(border)
            .padding(start = 2.dp, top = 2.dp, end = 2.dp, bottom = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(corner - 2.dp))
                .background(face)
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    }
}

/** One of the three counters on the home screen. */
@Composable
fun StatTile(
    icon: ImageVector,
    value: String,
    label: String,
    face: Color,
    ink: Color,
    iconTint: Color,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(face)
            .padding(vertical = 12.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
        Text(value, style = MaterialTheme.typography.headlineSmall, color = ink)
        Text(label, style = MaterialTheme.typography.labelSmall, color = ink)
    }
}

/** The small state pill beside an expression in the review list. */
@Composable
fun MongleChip(text: String, face: Color, ink: Color, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = ink,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(face)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

/** A back chevron, a centred title and an optional trailing control, all on 44dp targets. */
@Composable
fun MongleTopBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    backDescription: String = "홈으로",
    trailing: @Composable RowScope.() -> Unit = { Box(Modifier.size(44.dp)) },
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        MongleIconButton(MongleIcons.ChevronLeft, backDescription, onBack)
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MongleColor.Ink,
            modifier = Modifier.padding(horizontal = 8.dp),
        )
        trailing()
    }
}

/** The quiz and assessment progress rail. [progress] is clamped to 0..1. */
@Composable
fun MongleProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val filled = progress.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(MongleColor.Border),
    ) {
        if (filled > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(filled)
                    .clip(RoundedCornerShape(7.dp))
                    .background(MongleColor.Purple),
            )
        }
    }
}
