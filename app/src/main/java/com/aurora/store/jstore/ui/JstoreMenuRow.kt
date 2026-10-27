/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.aurora.store.R
import com.aurora.store.compose.preview.ThemePreviewProvider

/**
 * One row of a JaecooApp menu: an icon and its label on a full-width 72dp row. In the rail a
 * row can be [selected] (a square band ending in a cyan bar); in a plain menu it only reacts to
 * the press. [badge] above zero shows as a count.
 */
@Composable
fun JstoreMenuRow(
    @DrawableRes icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean? = null,
    badge: Int = 0
) {
    val colors = MaterialTheme.colorScheme
    val isSelected = selected == true
    val band = when {
        !isSelected -> Color.Transparent
        colors.background.luminance() < 0.5f -> JstoreRailSelectedDark
        else -> JstoreRailSelectedLight
    }
    val content = if (selected == false) colors.onSurfaceVariant else colors.onSurface
    val labelSize = with(LocalDensity.current) {
        dimensionResource(R.dimen.jstore_nav_label_text).toSp()
    }
    val action = if (selected != null) {
        Modifier.selectable(selected = isSelected, role = Role.Tab, onClick = onClick)
    } else {
        Modifier.clickable(role = Role.Button, onClick = onClick)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(dimensionResource(R.dimen.jstore_nav_item_height))
            .background(band)
            .then(action)
            .padding(start = dimensionResource(R.dimen.jstore_nav_item_padding_start)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            modifier = Modifier.size(dimensionResource(R.dimen.jstore_nav_icon)),
            painter = painterResource(icon),
            contentDescription = null,
            tint = if (isSelected) colors.primary else content
        )
        Spacer(Modifier.width(dimensionResource(R.dimen.jstore_nav_icon_gap)))
        Text(
            modifier = Modifier.weight(1f),
            text = label,
            fontSize = labelSize,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (badge > 0) {
            Badge(modifier = Modifier.padding(end = dimensionResource(R.dimen.jstore_space_sm))) {
                Text("$badge")
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
@Preview(showBackground = true)
@Composable
private fun JstoreMenuRowPreview() {
    JstoreMenuRow(icon = R.drawable.ic_menu_settings, label = "Settings", onClick = {})
}
