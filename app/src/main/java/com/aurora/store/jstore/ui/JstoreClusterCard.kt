/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.aurora.gplayapi.data.models.App
import com.aurora.store.R
import com.aurora.store.compose.preview.AppPreviewProvider
import com.aurora.store.compose.preview.ThemePreviewProvider

/** The card every jstore list section sits on, like JaecooApp's `bg_card`. */
@Composable
fun JstoreCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimensionResource(R.dimen.jstore_card_radius)),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(content = content)
    }
}

/**
 * One stream cluster as a card: its title, "See all" when the cluster can be browsed, and the
 * row of apps underneath.
 */
@Composable
fun JstoreClusterCard(
    title: String,
    onSeeAll: (() -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val spaceLg = dimensionResource(R.dimen.jstore_space_lg)
    JstoreCard(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = spaceLg, end = spaceLg / 2, top = spaceLg / 2),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (onSeeAll != null) {
                TextButton(onClick = onSeeAll) { Text(stringResource(R.string.jstore_see_all)) }
            }
        }
        Spacer(Modifier.height(dimensionResource(R.dimen.jstore_space_sm) / 2))
        content()
        Spacer(Modifier.height(dimensionResource(R.dimen.jstore_space_md)))
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview(showBackground = true)
@Composable
private fun JstoreClusterCardPreview(@PreviewParameter(AppPreviewProvider::class) app: App) {
    JstoreClusterCard(title = "Popular apps", onSeeAll = {}) {
        Row { repeat(3) { JstoreAppTile(app = app, onClick = {}) } }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview(showBackground = true)
@Composable
private fun JstoreClusterCardNoBrowsePreview(
    @PreviewParameter(AppPreviewProvider::class) app: App
) {
    JstoreClusterCard(title = "Recommended for you", onSeeAll = null) {
        Row { repeat(3) { JstoreAppTile(app = app, onClick = {}) } }
    }
}
