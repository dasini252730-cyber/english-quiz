package com.englishquiz.app.ui.session

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleIconButton
import com.englishquiz.app.ui.theme.MongleIcons
import com.englishquiz.app.ui.theme.mongleScreenInsets

/** Shown while no episode is ready (백로그 075); the route leaves it on its own once one arrives. */
internal const val WAITING_TITLE = "준비 중이에요"
internal const val WAITING_LIBRARY = "지난 회차 다시 읽기"

/**
 * No episode is ready yet (백로그 075): the pipeline is making the next one in the background, and
 * the screen offers the library meanwhile instead of a long loading. It never starts a generation.
 */
@Composable
internal fun SessionWaitingScreen(title: String, onBack: () -> Unit, onLibrary: (() -> Unit)?) {
    BackHandler(onBack = onBack)
    Column(modifier = Modifier.fillMaxSize().background(MongleColor.Cream).mongleScreenInsets()) {
        MongleIconButton(MongleIcons.ChevronLeft, "홈으로", onBack, Modifier.padding(8.dp))
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(WAITING_TITLE, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
            Text(
                "다음 회차를 뒤에서 만들고 있어요. 준비되면 이 화면이 바로 열려요.",
                style = MaterialTheme.typography.bodyLarge,
                color = MongleColor.InkMuted,
                textAlign = TextAlign.Center,
            )
            if (onLibrary != null) MongleButton(WAITING_LIBRARY, onLibrary)
        }
    }
}
