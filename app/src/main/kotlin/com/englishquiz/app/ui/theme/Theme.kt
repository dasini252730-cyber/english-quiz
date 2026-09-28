package com.englishquiz.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val EnglishQuizColors = lightColorScheme(
    primary = MongleColor.Purple,
    onPrimary = Color.White,
    primaryContainer = MongleColor.PurpleSoft,
    onPrimaryContainer = MongleColor.PurpleDeep,
    secondary = MongleColor.Yellow,
    onSecondary = MongleColor.Ink,
    secondaryContainer = MongleColor.Highlight,
    onSecondaryContainer = MongleColor.AmberLabel,
    tertiary = MongleColor.Green,
    onTertiary = Color.White,
    tertiaryContainer = MongleColor.GreenSoft,
    onTertiaryContainer = MongleColor.GreenInk,
    background = MongleColor.Cream,
    onBackground = MongleColor.Ink,
    surface = MongleColor.Surface,
    onSurface = MongleColor.Ink,
    surfaceVariant = MongleColor.Border,
    onSurfaceVariant = MongleColor.InkMuted,
    outline = MongleColor.BorderStrong,
    outlineVariant = MongleColor.Border,
    error = MongleColor.Danger,
    onError = Color.White,
    scrim = MongleColor.Scrim,
)

private val EnglishQuizShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun EnglishQuizTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = EnglishQuizColors,
        typography = MongleTypography,
        shapes = EnglishQuizShapes,
        content = content,
    )
}
