package com.englishquiz.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.englishquiz.app.data.ai.Language
import com.englishquiz.app.ui.theme.MongleColor

/**
 * English / 中文 on home (백로그 066): which language every screen below home works in. One
 * pill per language; the selected one is filled. The choice is stored, so the app reopens in it.
 */
@Composable
internal fun HomeLanguageToggle(
    selected: Language,
    onSelect: (Language) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(MongleColor.PurpleSoft)
            .border(1.dp, MongleColor.BorderStrong, shape)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (language in Language.entries) {
            val isSelected = language == selected
            Text(
                text = language.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) MongleColor.Cream else MongleColor.Purple,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) MongleColor.Purple else MongleColor.PurpleSoft)
                    .selectable(selected = isSelected, role = Role.RadioButton) { onSelect(language) }
                    .widthIn(min = 64.dp)
                    .height(32.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
