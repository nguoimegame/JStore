/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import androidx.compose.ui.unit.dp
import com.aurora.store.R
import com.aurora.store.compose.preview.ThemePreviewProvider

/** An icon button at the end of [JstoreMainHeader]. [badge] above zero shows as a count. */
data class JstoreHeaderAction(
    @DrawableRes val icon: Int,
    @StringRes val label: Int,
    val badge: Int = 0,
    val onClick: () -> Unit
)

/**
 * The main screen's header, JaecooApp style: no back button, a large title with the signed-in
 * account under it, and the actions at the end as large icons a driver can hit. The subtitle
 * line is kept even when empty so the header does not change height between accounts. With
 * [onSearch], a [JstoreSearchField] sits under it.
 */
@Composable
fun JstoreMainHeader(
    title: String,
    subtitle: String?,
    actions: List<JstoreHeaderAction>,
    modifier: Modifier = Modifier,
    onSearch: (() -> Unit)? = null
) {
    Column(modifier = modifier.windowInsetsPadding(TopAppBarDefaults.windowInsets)) {
        HeaderRow(title = title, subtitle = subtitle) {
            actions.forEach { HeaderActionButton(it) }
        }
        if (onSearch != null) JstoreSearchField(onClick = onSearch)
    }
}

@Composable
private fun HeaderRow(title: String, subtitle: String?, actions: @Composable RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = dimensionResource(R.dimen.jstore_space_lg),
                end = dimensionResource(R.dimen.jstore_space_lg),
                top = dimensionResource(R.dimen.jstore_space_md),
                bottom = dimensionResource(R.dimen.jstore_space_sm)
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.jstore_space_sm))
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        actions()
    }
}

@Composable
private fun HeaderActionButton(action: JstoreHeaderAction) {
    IconButton(
        modifier = Modifier.size(dimensionResource(R.dimen.jstore_header_action_size)),
        onClick = action.onClick
    ) {
        BadgedBox(badge = { if (action.badge > 0) Badge { Text("${action.badge}") } }) {
            Icon(
                modifier = Modifier.size(dimensionResource(R.dimen.jstore_header_icon)),
                painter = painterResource(action.icon),
                contentDescription = stringResource(action.label)
            )
        }
    }
}

/** A search box that only opens the search screen; typing happens there. */
@Composable
fun JstoreSearchField(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = dimensionResource(R.dimen.jstore_space_lg))
            .height(dimensionResource(R.dimen.jstore_touch_min)),
        shape = RoundedCornerShape(percent = 50),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = dimensionResource(R.dimen.jstore_space_lg)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensionResource(R.dimen.jstore_space_sm))
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_round_search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = stringResource(R.string.jstore_search_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview(showBackground = true)
@Composable
private fun JstoreMainHeaderPreview() {
    JstoreMainHeader(
        title = "Apps",
        subtitle = "someone@gmail.com",
        actions = listOf(
            JstoreHeaderAction(R.drawable.ic_notifications, R.string.title_notifications, 2) {},
            JstoreHeaderAction(R.drawable.ic_download_manager, R.string.title_download_manager) {}
        ),
        onSearch = {}
    )
}

@PreviewWrapper(ThemePreviewProvider::class)
@Preview(showBackground = true)
@Composable
private fun JstoreMainHeaderNoAccountPreview() {
    JstoreMainHeader(title = "Apps", subtitle = "", actions = emptyList())
}
