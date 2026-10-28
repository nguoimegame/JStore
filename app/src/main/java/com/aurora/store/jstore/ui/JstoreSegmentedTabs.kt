/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.aurora.store.R
import com.aurora.store.compose.preview.ThemePreviewProvider

/** Tabs as a pill-shaped segment track, like JaecooApp's store switch. */
@Composable
fun JstoreSegmentedTabs(
    titles: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pill = RoundedCornerShape(percent = 50)
    val colors = MaterialTheme.colorScheme
    val isDark = colors.background.luminance() < 0.5f
    val selectedBg = if (isDark) colors.surfaceContainerHighest else colors.surfaceContainer
    val selectedBorder = if (isDark) BorderStroke(1.dp, colors.outline) else null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(pill)
            .background(colors.surfaceVariant)
            .padding(4.dp)
            .selectableGroup()
    ) {
        titles.forEachIndexed { index, title ->
            val isSelected = index == selected
            val boxModifier = Modifier
                .weight(1f)
                .height(dimensionResource(R.dimen.jstore_segment_height))
                .clip(pill)
                .then(
                    if (isSelected) {
                        if (selectedBorder != null) {
                            Modifier.background(selectedBg).border(selectedBorder, pill)
                        } else {
                            Modifier.background(selectedBg)
                        }
                    } else {
                        Modifier.background(Color.Transparent)
                    }
                )
                .selectable(
                    selected = isSelected,
                    role = Role.Tab,
                    onClick = { onSelect(index) }
                )

            Box(
                modifier = boxModifier,
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) colors.primary else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview(showBackground = true)
@Composable
private fun JstoreSegmentedTabsPreview() {
    JstoreSegmentedTabs(
        titles = listOf("For you", "Top charts", "Categories"),
        selected = 0,
        onSelect = {}
    )
}
