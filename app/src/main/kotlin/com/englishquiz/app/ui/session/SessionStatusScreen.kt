package com.englishquiz.app.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleIconButton
import com.englishquiz.app.ui.theme.MongleIcons
import com.englishquiz.app.ui.theme.mongleScreenInsets

/** The waiting and failure screens a learning session shows between its real screens. */
@Composable
fun SessionStatusScreen(
    title: String,
    message: String,
    onBack: () -> Unit,
    onRetry: (() -> Unit)? = null,
) {
    BackHandler(onBack = onBack)
    Column(modifier = Modifier.fillMaxSize().background(MongleColor.Cream).mongleScreenInsets()) {
        MongleIconButton(MongleIcons.ChevronLeft, "홈으로", onBack, Modifier.padding(8.dp))
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodyLarge,
                color = MongleColor.InkMuted,
                textAlign = TextAlign.Center,
            )
            if (onRetry == null) WaitingDots() else MongleButton("다시 시도", onRetry)
        }
    }
}

/** The three resting dots of the canvas's loading screen; still art, so it carries no semantics. */
@Composable
private fun WaitingDots() {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(MongleColor.Purple, Color(0xFFA79EF2), Color(0xFFD9D5F7)).forEach { color ->
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        }
    }
}
