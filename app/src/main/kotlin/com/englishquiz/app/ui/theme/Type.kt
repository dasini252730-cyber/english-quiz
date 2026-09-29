package com.englishquiz.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.englishquiz.app.R

/**
 * Jua is the rounded display face of the design canvas; it ships in `res/font` so the app never
 * needs the network or Play services to render a heading. Body text stays on the system sans face,
 * which already carries a good Korean font on the target device.
 */
val JuaFontFamily = FontFamily(Font(R.font.jua_regular, FontWeight.Normal))

private val Body = FontFamily.SansSerif

private fun display(size: Int) = TextStyle(
    fontFamily = JuaFontFamily,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = (size * 1.25).sp,
)

private fun body(size: Int, weight: FontWeight = FontWeight.Normal, height: Double = 1.5) = TextStyle(
    fontFamily = Body,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = (size * height).sp,
)

val MongleTypography = Typography(
    displayLarge = display(40),
    displayMedium = display(34),
    displaySmall = display(32),
    headlineLarge = display(28),
    headlineMedium = display(26),
    headlineSmall = display(22),
    titleLarge = display(20),
    titleMedium = body(18, FontWeight.Bold),
    titleSmall = body(17, FontWeight.Bold),
    bodyLarge = body(16, height = 1.55),
    bodyMedium = body(15, height = 1.5),
    bodySmall = body(13, height = 1.5),
    labelLarge = body(15, FontWeight.Bold, 1.3),
    labelMedium = body(13, FontWeight.Bold, 1.3),
    labelSmall = body(12, FontWeight.Bold, 1.3),
)
