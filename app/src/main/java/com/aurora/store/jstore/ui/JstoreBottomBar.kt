/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.aurora.store.R
import com.aurora.store.compose.preview.ThemePreviewProvider

/**
 * The main screen's destinations as a tall bottom bar for screens at least 1024dp tall; short
 * ones use [JstoreNavRail]. Each item is a large icon left of its label; the selected one sits
 * in a pill.
 */
@Composable
fun JstoreBottomBar(
    items: List<JstoreNavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Surface(modifier = modifier.fillMaxWidth(), color = colors.surfaceContainer) {
        Column(modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)) {
            HorizontalDivider(color = colors.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(dimensionResource(R.dimen.jstore_bottom_bar_height))
                    .selectableGroup()
            ) {
                items.forEachIndexed { index, item ->
                    val isSelected = index == selected
                    val content = if (isSelected) colors.primary else colors.onSurfaceVariant
                    val pill = if (isSelected) colors.secondaryContainer else Color.Transparent
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = isSelected,
                                role = Role.Tab,
                                onClick = { onSelect(index) }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        // Icon left of the label, the pill around both marks the selected item
                        Row(
                            modifier = Modifier
                                .background(pill, RoundedCornerShape(percent = 50))
                                .padding(
                                    horizontal = dimensionResource(R.dimen.jstore_space_lg),
                                    vertical = dimensionResource(R.dimen.jstore_space_sm) / 2
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(
                                dimensionResource(R.dimen.jstore_space_sm)
                            )
                        ) {
                            BadgedBox(
                                badge = { if (item.badge > 0) Badge { Text("${item.badge}") } }
                            ) {
                                Icon(
                                    modifier = Modifier.size(
                                        dimensionResource(R.dimen.jstore_bottom_bar_icon)
                                    ),
                                    painter = painterResource(item.icon),
                                    contentDescription = null,
                                    tint = content
                                )
                            }
                            Text(
                                text = stringResource(item.label),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (isSelected) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                                color = content,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview(showBackground = true)
@Composable
private fun JstoreBottomBarPreview() {
    JstoreBottomBar(
        items = listOf(
            JstoreNavItem(R.string.title_apps, R.drawable.ic_apps),
            JstoreNavItem(R.string.title_games, R.drawable.ic_games),
            JstoreNavItem(R.string.title_updates, R.drawable.ic_updates, badge = 3)
        ),
        selected = 0,
        onSelect = {}
    )
}
