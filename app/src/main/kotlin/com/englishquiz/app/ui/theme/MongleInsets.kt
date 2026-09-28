package com.englishquiz.app.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
import android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView

/**
 * Android 15 gives a `targetSdk` 35+ app no choice about drawing edge to edge, so every screen
 * fills the display and would otherwise paint its header under the status bar and its bottom
 * button under the navigation bar. Found on a Galaxy S26, whose status bar (42dp) is taller than
 * the emulator's and than the 28dp of top padding the screens used to rely on.
 *
 * Apply this to a screen's root, after its background so the colour still reaches the edges, and
 * before any scroll or padding so the content is what moves.
 */
@Composable
fun Modifier.mongleScreenInsets(): Modifier = windowInsetsPadding(WindowInsets.safeDrawing)

/**
 * For a sheet pinned to the bottom edge, which only has to clear the navigation bar. The status
 * bar is the scrim's problem, not the sheet's.
 */
@Composable
fun Modifier.mongleBottomSheetInsets(): Modifier = windowInsetsPadding(WindowInsets.navigationBars)

/**
 * Turns the system bar icons white while a dark screen owns the display, and puts them back on the
 * way out. The app draws edge to edge, so the result screen's purple now runs under both bars and
 * the cream canvas's dark icons are unreadable on it.
 */
@Composable
fun DarkBackgroundSystemBars() {
    val view = LocalView.current
    DisposableEffect(view) {
        val controller = view.context.findActivity()?.window?.insetsController
        val lightIcons = APPEARANCE_LIGHT_STATUS_BARS or APPEARANCE_LIGHT_NAVIGATION_BARS
        // Restoring whatever was there beats restoring "light": a second dark screen nested inside
        // this one would otherwise hand the bars back dark while a dark background is still up.
        val previous = controller?.systemBarsAppearance ?: 0
        controller?.setSystemBarsAppearance(0, lightIcons)
        onDispose { controller?.setSystemBarsAppearance(previous, lightIcons) }
    }
}

/** A composable's context is wrapped, so the activity is found by walking back out of it. */
private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
