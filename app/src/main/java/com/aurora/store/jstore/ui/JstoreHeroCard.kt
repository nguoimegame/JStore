/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewWrapper
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.aurora.gplayapi.data.models.App
import com.aurora.gplayapi.data.models.StreamCluster
import com.aurora.store.R
import com.aurora.store.compose.preview.AppPreviewProvider
import com.aurora.store.compose.preview.ThemePreviewProvider

/**
 * The app the For you page leads with. Play has no featured slot for a third-party client, so
 * this is the first app of the first cluster, or null when that cluster has none.
 */
fun heroApp(clusters: List<StreamCluster>): App? =
    clusters.firstOrNull()?.clusterAppList?.firstOrNull()

/**
 * Strips the hero app out of the first cluster when [showHero] is true so the hero app is not
 * shown twice right next to each other on the head unit.
 */
fun deduplicateClusters(clusters: List<StreamCluster>, showHero: Boolean): List<StreamCluster> {
    if (!showHero) return clusters
    val hero = heroApp(clusters) ?: return clusters
    return clusters.mapIndexed { index, cluster ->
        if (index == 0) {
            cluster.copy(clusterAppList = cluster.clusterAppList.filter { it.packageName != hero.packageName })
        } else {
            cluster
        }
    }
}

/** A large card at the top of For you, on JaecooApp's blue gradient. */
@Composable
fun JstoreHeroCard(app: App, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(dimensionResource(R.dimen.jstore_card_radius))
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.secondary, colors.primary)))
            .clickable(onClick = onClick)
            .padding(dimensionResource(R.dimen.jstore_space_lg)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.jstore_space_lg))
    ) {
        AsyncImage(
            modifier = Modifier
                .size(dimensionResource(R.dimen.jstore_hero_icon))
                .clip(RoundedCornerShape(dimensionResource(R.dimen.jstore_tile_icon_radius)))
                .background(Color.White),
            model = ImageRequest.Builder(LocalContext.current)
                .data(app.iconArtwork.url)
                .crossfade(true)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colors.onPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = app.developerName,
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onPrimary.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        FilledTonalButton(
            onClick = onClick,
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = colors.onPrimary,
                contentColor = colors.secondary
            )
        ) {
            Text(stringResource(R.string.jstore_open))
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview(showBackground = true)
@Composable
private fun JstoreHeroCardPreview(@PreviewParameter(AppPreviewProvider::class) app: App) {
    JstoreHeroCard(app = app, onClick = {})
}
