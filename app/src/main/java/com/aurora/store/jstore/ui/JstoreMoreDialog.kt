/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.aurora.store.R
import com.aurora.store.compose.navigation.Destination
import com.aurora.store.viewmodel.commons.MoreViewModel

private data class MoreEntry(
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val destination: Destination
)

// Same destinations as Aurora's MoreSheet, which this replaces on the main screen.
private val toolEntries = listOf(
    MoreEntry(R.drawable.ic_apps, R.string.title_apps_games, Destination.Installed),
    MoreEntry(R.drawable.ic_blacklist, R.string.title_blacklist_manager, Destination.Blacklist),
    MoreEntry(
        R.drawable.ic_favorite_unchecked,
        R.string.title_favourites_manager,
        Destination.Favourite
    ),
    MoreEntry(R.drawable.ic_spoof, R.string.title_spoof_manager, Destination.Spoof)
)
private val appEntries = listOf(
    MoreEntry(R.drawable.ic_menu_settings, R.string.title_settings, Destination.Settings),
    MoreEntry(R.drawable.ic_menu_about, R.string.title_about, Destination.About)
)

/**
 * The main screen's settings menu as a card in the middle of the screen: the signed-in account
 * on top, then JaecooApp-style menu rows. Tapping outside or Back closes it.
 */
@Composable
fun JstoreMoreDialog(
    onDismiss: () -> Unit,
    onNavigateTo: (Destination) -> Unit,
    viewModel: MoreViewModel = hiltViewModel()
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .padding(dimensionResource(R.dimen.jstore_space_lg))
                .widthIn(max = dimensionResource(R.dimen.jstore_dialog_max_width)),
            shape = RoundedCornerShape(dimensionResource(R.dimen.jstore_card_radius)),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = dimensionResource(R.dimen.jstore_space_sm))
            ) {
                AccountBlock(viewModel)
                JstoreMenuRow(
                    icon = R.drawable.ic_account_manager,
                    label = stringResource(R.string.manage_account),
                    onClick = { onNavigateTo(Destination.Accounts) }
                )
                listOf(toolEntries, appEntries).forEach { group ->
                    HorizontalDivider(
                        modifier = Modifier.padding(
                            vertical = dimensionResource(R.dimen.jstore_space_sm) / 2
                        ),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                    group.forEach { entry ->
                        JstoreMenuRow(
                            icon = entry.icon,
                            label = stringResource(entry.label),
                            onClick = { onNavigateTo(entry.destination) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountBlock(viewModel: MoreViewModel) {
    val profile = viewModel.authProvider.authData?.userProfile
    val name = when {
        viewModel.authProvider.isAnonymous -> stringResource(R.string.account_anonymous)
        else -> profile?.name ?: stringResource(R.string.status_unavailable)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = dimensionResource(R.dimen.jstore_nav_item_padding_start),
                vertical = dimensionResource(R.dimen.jstore_space_md)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(profile?.artwork?.url)
                .crossfade(true)
                .build(),
            contentDescription = null,
            placeholder = painterResource(R.drawable.ic_account),
            error = painterResource(R.drawable.ic_account),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(dimensionResource(R.dimen.jstore_avatar_size))
                .clip(CircleShape)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = dimensionResource(R.dimen.jstore_space_md))
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(dimensionResource(R.dimen.jstore_space_sm) / 3))
            Text(
                text = profile?.email ?: stringResource(R.string.status_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
