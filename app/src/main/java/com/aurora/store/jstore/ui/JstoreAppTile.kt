/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewWrapper
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.aurora.gplayapi.data.models.App
import com.aurora.store.R
import com.aurora.store.compose.preview.AppPreviewProvider
import com.aurora.store.compose.preview.ThemePreviewProvider

/**
 * One app in a cluster row: a large icon and the name on up to two lines, wide enough that
 * names are not cut in the middle of a word on the head unit. Replaces Aurora's
 * `AppListItem` in carousels.
 */
@Composable
fun JstoreAppTile(app: App, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .width(dimensionResource(R.dimen.jstore_tile_width))
            .clip(RoundedCornerShape(dimensionResource(R.dimen.jstore_tile_icon_radius)))
            .clickable(onClick = onClick)
            .padding(vertical = dimensionResource(R.dimen.jstore_space_sm) / 2),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            modifier = Modifier
                .size(dimensionResource(R.dimen.jstore_tile_icon))
                .clip(RoundedCornerShape(dimensionResource(R.dimen.jstore_tile_icon_radius))),
            model = ImageRequest.Builder(LocalContext.current)
                .data(app.iconArtwork.url)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop
        )
        Text(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimensionResource(R.dimen.jstore_space_sm)),
            text = app.displayName,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (app.labeledRating.isNotBlank()) {
            Text(
                text = "${app.labeledRating} ★",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview(showBackground = true)
@Composable
private fun JstoreAppTilePreview(@PreviewParameter(AppPreviewProvider::class) app: App) {
    JstoreAppTile(app = app, onClick = {})
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview(showBackground = true)
@Composable
private fun JstoreAppTileLongNamePreview(@PreviewParameter(AppPreviewProvider::class) app: App) {
    JstoreAppTile(app = app.copy(displayName = "Bookinglandiaplus Travel Planner"), onClick = {})
}
