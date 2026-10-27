/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.aurora.store.R
import com.aurora.store.compose.preview.ThemePreviewProvider

/** One destination of the main screen. [badge] above zero shows as a count. */
data class JstoreNavItem(@StringRes val label: Int, @DrawableRes val icon: Int, val badge: Int = 0)

/**
 * The main screen's destinations as JaecooApp's left rail: a full-height column of rows, each
 * an icon and its label. The selected row gets a square band that runs to the rail's edge and
 * ends in a cyan bar, so selection is not shown by colour alone. Screens under 1024dp tall use
 * it instead of [JstoreBottomBar] (see `@bool/jstore_nav_rail`).
 */
@Composable
fun JstoreNavRail(
    items: List<JstoreNavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(dimensionResource(R.dimen.jstore_nav_rail_width))
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(vertical = dimensionResource(R.dimen.jstore_space_sm))
            .verticalScroll(rememberScrollState())
            .selectableGroup(),
        verticalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.jstore_nav_item_gap))
    ) {
        items.forEachIndexed { index, item ->
            RailItem(item = item, isSelected = index == selected, onClick = { onSelect(index) })
        }
    }
}

@Composable
private fun RailItem(item: JstoreNavItem, isSelected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val band = when {
        !isSelected -> Color.Transparent
        colors.background.luminance() < 0.5f -> JstoreRailSelectedDark
        else -> JstoreRailSelectedLight
    }
    val content = if (isSelected) colors.onSurface else colors.onSurfaceVariant
    val labelSize = with(LocalDensity.current) {
        dimensionResource(R.dimen.jstore_nav_label_text).toSp()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.jstore_nav_item_height))
            .background(band)
            .selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
            .padding(start = dimensionResource(R.dimen.jstore_space_lg)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(dimensionResource(R.dimen.jstore_nav_icon)),
            painter = painterResource(item.icon),
            contentDescription = null,
            tint = if (isSelected) colors.primary else content
        )
        Spacer(Modifier.width(dimensionResource(R.dimen.jstore_space_sm)))
        Text(
            modifier = Modifier.weight(1f),
            text = stringResource(item.label),
            fontSize = labelSize,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (item.badge > 0) {
            Badge(modifier = Modifier.padding(end = dimensionResource(R.dimen.jstore_space_sm))) {
                Text("${item.badge}")
            }
        }
        Box(
            modifier = Modifier
                .width(dimensionResource(R.dimen.jstore_nav_indicator_width))
                .fillMaxHeight()
                .background(
                    if (isSelected) JstoreRailIndicator else Color.Transparent,
                    RoundedCornerShape(dimensionResource(R.dimen.jstore_nav_indicator_width) / 2)
                )
        )
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview(showBackground = true, heightDp = 400)
@Composable
private fun JstoreNavRailPreview() {
    JstoreNavRail(
        items = listOf(
            JstoreNavItem(R.string.title_apps, R.drawable.ic_apps),
            JstoreNavItem(R.string.title_games, R.drawable.ic_games),
            JstoreNavItem(R.string.title_updates, R.drawable.ic_updates, badge = 3)
        ),
        selected = 0,
        onSelect = {}
    )
}
