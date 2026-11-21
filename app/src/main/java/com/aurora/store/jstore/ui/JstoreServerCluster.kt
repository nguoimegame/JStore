/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import com.aurora.gplayapi.data.models.App
import com.aurora.store.R

/** The "Apps for Jaecoo" cluster: apps from the Jaecoo server. Draws nothing when empty. */
@Composable
fun JstoreServerCluster(modifier: Modifier, apps: List<App>, onAppClick: (App) -> Unit) {
    if (apps.isEmpty()) return
    JstoreClusterCard(
        modifier = modifier,
        title = stringResource(R.string.jstore_featured_title),
        onSeeAll = null
    ) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = dimensionResource(R.dimen.jstore_space_lg)),
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.jstore_space_sm))
        ) {
            items(items = apps, key = { it.packageName }) { app ->
                JstoreAppTile(app = app, onClick = { onAppClick(app) })
            }
        }
    }
}
