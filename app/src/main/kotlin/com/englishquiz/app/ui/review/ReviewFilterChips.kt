package com.englishquiz.app.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.englishquiz.app.domain.review.ReviewFilter
import com.englishquiz.app.ui.theme.MongleColor

/** The three weak-spot views of the review box (백로그 044), one chip each. */
@Composable
internal fun ReviewFilterChips(selected: ReviewFilter, onSelect: (ReviewFilter) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ReviewFilter.entries.forEach { filter ->
            val isSelected = filter == selected
            Text(
                text = filter.label,
                style = MaterialTheme.typography.labelLarge,
                color = if (isSelected) MongleColor.Surface else MongleColor.PurpleDeep,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (isSelected) MongleColor.Purple else MongleColor.PurpleSoft)
                    .semantics { this.selected = isSelected }
                    .clickable(role = Role.Tab) { onSelect(filter) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
    }
}
