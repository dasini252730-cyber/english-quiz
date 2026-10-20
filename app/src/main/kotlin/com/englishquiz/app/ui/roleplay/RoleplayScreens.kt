package com.englishquiz.app.ui.roleplay

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.englishquiz.app.ui.theme.MongleButton
import com.englishquiz.app.ui.theme.MongleButtonColors
import com.englishquiz.app.ui.theme.MongleCard
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleOptionButton
import com.englishquiz.app.ui.theme.MongleTopBar
import com.englishquiz.app.ui.theme.mongleScreenInsets

internal const val ROLEPLAY_TITLE = "역할극"

/** The cream page every role-play screen sits on: top bar, scrolling body, and a white bottom bar for the actions. */
@Composable
internal fun RoleplayScaffold(
    onBack: () -> Unit,
    trailing: @Composable () -> Unit = {},
    actions: (@Composable ColumnScope.() -> Unit)? = null,
    body: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize().background(MongleColor.Cream).mongleScreenInsets()) {
        MongleTopBar(title = ROLEPLAY_TITLE, onBack = onBack, backDescription = "읽기로 돌아가기") { trailing() }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = body,
        )
        if (actions != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MongleColor.Surface)
                    .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = actions,
            )
        }
    }
}

/** Which part to take: one option per speaker, narrator excluded. */
@Composable
internal fun SpeakerPickerScreen(speakers: List<String>, onPick: (String) -> Unit, onBack: () -> Unit) {
    RoleplayScaffold(onBack = onBack) {
        Text("어떤 역할을 할까요?", style = MaterialTheme.typography.headlineSmall, color = MongleColor.Ink)
        Text(
            "내 대사는 뜻과 힌트만 보고 말해요. 다른 역할의 대사는 음성으로 들려드려요.",
            style = MaterialTheme.typography.bodyMedium,
            color = MongleColor.InkMuted,
        )
        Spacer(Modifier.height(4.dp))
        if (speakers.isEmpty()) {
            Text("이 대화에는 맡을 수 있는 역할이 없어요.", style = MaterialTheme.typography.bodyMedium, color = MongleColor.Danger)
        }
        speakers.forEach { speaker -> MongleOptionButton(speaker, onClick = { onPick(speaker) }) }
    }
}

/** "통과 N/M", then once more or on to the quiz. */
@Composable
internal fun FinishedScreen(passed: Int, total: Int, onRestart: () -> Unit, onQuiz: () -> Unit, onBack: () -> Unit) {
    RoleplayScaffold(
        onBack = onBack,
        actions = {
            MongleButton("다시 하기", onRestart, colors = MongleButtonColors.Accent, height = 48.dp)
            MongleButton("퀴즈로", onQuiz)
        },
    ) {
        MongleCard(contentPadding = 20.dp) {
            Text("역할극 끝!", style = MaterialTheme.typography.titleLarge, color = MongleColor.Ink)
            Text(
                "통과 $passed/$total",
                style = MaterialTheme.typography.headlineMedium,
                color = MongleColor.Purple,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            )
            Text(
                "내 대사 ${total}개 중 ${passed}개를 말했어요. 점수와 복습 일정에는 영향이 없어요.",
                style = MaterialTheme.typography.bodyMedium,
                color = MongleColor.InkMuted,
            )
        }
    }
}
