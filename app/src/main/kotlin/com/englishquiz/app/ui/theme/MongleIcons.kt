package com.englishquiz.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The icon set of the design canvas, kept as hand-built vectors so the app pulls in no icon
 * artifact and every glyph matches the canvas path data exactly. Each is drawn in black and
 * recoloured by `Icon(tint = …)`.
 */
object MongleIcons {
    val Flame = filled("Flame", "M12 2c1 4 5 6 5 11a5 5 0 0 1-10 0c0-2 1-3 2-4 0 2 1 3 2 3 0-4-1-6 1-10z")
    val Play = filled("Play", "M6 4l14 8-14 8z")
    val Book = stroked("Book", "M4 5a2 2 0 0 1 2-2h13v16H6a2 2 0 0 0-2 2zM4 19a2 2 0 0 1 2-2h13", width = 2.2f)
    val Chat = stroked("Chat", "M4 5h16v11H10l-4 4v-4H4z", width = 2.2f)
    val Check = stroked("Check", "M5 12l5 5 9-10", width = 4f)
    val CheckCircle = stroked("CheckCircle", "M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM8 12l3 3 5-6", width = 2.6f)
    val ChevronLeft = stroked("ChevronLeft", "M15 5l-7 7 7 7", width = 3f)
    val ChevronRight = stroked("ChevronRight", "M9 5l7 7-7 7", width = 3f)
    val Close = stroked("Close", "M6 6l12 12M18 6L6 18", width = 3f)
    val Cards = stroked("Cards", "M3 4h18v16H3zM3 10h18", width = 2.2f)

    /** A speaker cone plus one sound wave: the pronunciation button of the canvas. */
    val Speaker: ImageVector = ImageVector.Builder("Speaker", 24.dp, 24.dp, 24f, 24f).apply {
        addPath(
            pathData = nodes("M4 9v6h4l5 4V5L8 9H4z"),
            fill = SolidColor(Color.Black),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
        addPath(
            pathData = nodes("M16 9a4 4 0 0 1 0 6"),
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        )
    }.build()

    private fun nodes(pathData: String) = PathParser().parsePathString(pathData).toNodes()

    private fun filled(name: String, pathData: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(pathData = nodes(pathData), fill = SolidColor(Color.Black))
        }.build()

    private fun stroked(name: String, pathData: String, width: Float): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            addPath(
                pathData = nodes(pathData),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = width,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }.build()
}
