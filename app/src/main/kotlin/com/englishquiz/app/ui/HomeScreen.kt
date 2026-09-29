package com.englishquiz.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.englishquiz.app.ui.theme.MongleCard
import com.englishquiz.app.ui.theme.MongleColor
import com.englishquiz.app.ui.theme.MongleIcons
import com.englishquiz.app.ui.theme.StatTile
import com.englishquiz.app.ui.theme.mongleScreenInsets

data class HomeSummary(
    val streakDays: Int?,
    val savedExpressionCount: Int,
    val masteredExpressionCount: Int,
)

@Composable
fun HomeScreen(
    summary: HomeSummary,
    onConversationClick: () -> Unit,
    onStoryClick: () -> Unit,
    onReviewClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLibraryClick: () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MongleColor.Cream)
            .mongleScreenInsets()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Text(
            "StoryEnglish",
            style = MaterialTheme.typography.headlineMedium,
            color = MongleColor.Purple,
        )
        SummaryTiles(summary)
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text("오늘 뭐 할까요?", style = MaterialTheme.typography.headlineLarge)
                Text(
                    "콘텐츠 10분 + 퀴즈 10분, 하루 20분이면 충분해요",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MongleColor.InkMuted,
                )
            }
            ModeCard(
                title = "Conversation",
                subtitle = "여행, 카페, 직장… 진짜 쓰는 대화",
                icon = MongleIcons.Chat,
                face = MongleColor.Purple,
                shadow = MongleColor.PurpleDeep,
                ink = Color.White,
                onClick = onConversationClick,
            )
            ModeCard(
                title = "Story",
                subtitle = "위트 있는 어른용 짧은 이야기",
                icon = MongleIcons.Book,
                face = MongleColor.Yellow,
                shadow = MongleColor.YellowDeep,
                ink = MongleColor.Ink,
                onClick = onStoryClick,
            )
        }
        EntryRow(icon = MongleIcons.Cards, label = "복습함", onClick = onReviewClick)
        // 백로그 026: a passage read again weeks later is a review that costs no generation.
        EntryRow(icon = MongleIcons.Book, label = "지난 이야기 다시 읽기", onClick = onLibraryClick)
    }
}

@Composable
private fun SummaryTiles(summary: HomeSummary) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        StatTile(
            icon = MongleIcons.Flame,
            value = summary.streakDays?.let { "${it}일" } ?: "–",
            label = "연속 학습",
            face = MongleColor.AmberSoft,
            ink = MongleColor.AmberInk,
            iconTint = MongleColor.AmberIcon,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            icon = MongleIcons.Book,
            value = "${summary.savedExpressionCount}개",
            label = "저장 표현",
            face = MongleColor.BlueSoft,
            ink = MongleColor.BlueInk,
            iconTint = MongleColor.BlueIcon,
            modifier = Modifier.weight(1f),
        )
        StatTile(
            icon = MongleIcons.CheckCircle,
            value = "${summary.masteredExpressionCount}개",
            label = "암기 완료",
            face = MongleColor.GreenSoft,
            ink = MongleColor.GreenInk,
            iconTint = MongleColor.GreenDeep,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    face: Color,
    shadow: Color,
    ink: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(shadow)
            .padding(bottom = 6.dp)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(face)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MongleColor.Surface),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = face, modifier = Modifier.size(30.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.headlineLarge, color = ink)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = ink)
            }
        }
    }
}

@Composable
private fun EntryRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    MongleCard(
        modifier = Modifier
            .clickable(role = Role.Button, onClick = onClick),
        border = MongleColor.BorderStrong,
        corner = 18.dp,
        contentPadding = 0.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MongleColor.Purple,
                    modifier = Modifier.size(24.dp),
                )
                Text(label, style = MaterialTheme.typography.titleSmall)
            }
            Icon(
                MongleIcons.ChevronRight,
                contentDescription = null,
                tint = MongleColor.InkMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
