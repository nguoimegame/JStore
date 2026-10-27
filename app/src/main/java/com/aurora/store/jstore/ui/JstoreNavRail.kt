/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.aurora.store.R

/** One destination of the main screen. [badge] above zero shows as a count. */
data class JstoreNavItem(@StringRes val label: Int, @DrawableRes val icon: Int, val badge: Int = 0)

/**
 * The main screen's destinations as a rail on the left. Screens at least 1200dp wide use it
 * instead of the bottom bar, which would cost a row the list needs more; narrower screens keep
 * the width for the app tiles (see `@bool/jstore_nav_rail`).
 */
@Composable
fun JstoreNavRail(
    items: List<JstoreNavItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .width(dimensionResource(R.dimen.jstore_nav_rail_width))
    ) {
        items.forEachIndexed { index, item ->
            NavigationRailItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                icon = {
                    if (item.badge > 0) {
                        BadgedBox(badge = { Badge { Text("${item.badge}") } }) {
                            Icon(painter = painterResource(item.icon), contentDescription = null)
                        }
                    } else {
                        Icon(painter = painterResource(item.icon), contentDescription = null)
                    }
                },
                label = { Text(stringResource(item.label)) }
            )
        }
    }
}
