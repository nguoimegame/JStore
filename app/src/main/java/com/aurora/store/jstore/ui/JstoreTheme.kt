/*
 * SPDX-FileCopyrightText: 2026 nguoimegame
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.aurora.store.jstore.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import com.aurora.store.R
import com.aurora.store.compose.theme.BrandDarkColorScheme
import com.aurora.store.compose.theme.BrandLightColorScheme

/**
 * jstore's look on the head unit: JaecooApp's colours, a type scale read at arm's length,
 * and touch targets a driver can hit.
 *
 * Sizes come from `dimens_jstore.xml` in `values/` (screens under 1024dp tall) and
 * `values-h1024dp/` (taller screens), the same split JaecooApp uses; nothing here looks at
 * the window size. Dynamic colours, when the user turned them on, are left alone.
 */
@Composable
fun JstoreTheme(colorScheme: ColorScheme, content: @Composable () -> Unit) {
    val scheme = when {
        colorScheme === BrandLightColorScheme -> JstoreLightColorScheme
        colorScheme === BrandDarkColorScheme -> JstoreDarkColorScheme
        else -> colorScheme
    }
    MaterialExpressiveTheme(colorScheme = scheme, typography = jstoreTypography()) {
        CompositionLocalProvider(
            LocalMinimumInteractiveComponentSize provides
                dimensionResource(R.dimen.jstore_touch_min),
            content = content
        )
    }
}

@Composable
private fun jstoreTypography(): Typography {
    val base = Typography()
    val display = size(R.dimen.jstore_text_display)
    val headline = size(R.dimen.jstore_text_headline)
    val title = size(R.dimen.jstore_text_title)
    val body = size(R.dimen.jstore_text_body)
    val bodySmall = size(R.dimen.jstore_text_body_sm)
    val caption = size(R.dimen.jstore_text_caption)
    val label = size(R.dimen.jstore_text_label)
    return base.copy(
        displayLarge = base.displayLarge.scaled(display),
        displayMedium = base.displayMedium.scaled(display * 0.8f),
        displaySmall = base.displaySmall.scaled(display * 0.65f),
        headlineLarge = base.headlineLarge.scaled(headline),
        headlineMedium = base.headlineMedium.scaled(headline * 0.9f),
        headlineSmall = base.headlineSmall.scaled(title),
        titleLarge = base.titleLarge.scaled(title),
        titleMedium = base.titleMedium.scaled(body),
        titleSmall = base.titleSmall.scaled(bodySmall),
        bodyLarge = base.bodyLarge.scaled(body),
        bodyMedium = base.bodyMedium.scaled(bodySmall),
        bodySmall = base.bodySmall.scaled(caption),
        labelLarge = base.labelLarge.scaled(bodySmall),
        labelMedium = base.labelMedium.scaled(caption),
        labelSmall = base.labelSmall.scaled(label)
    )
}

/** A dimen holding a text size in dp, as a plain float of sp. */
@Composable
private fun size(id: Int): Float {
    val dp: Dp = dimensionResource(id)
    return with(LocalDensity.current) { dp.toSp().value }
}

/** Keeps the style's own line-height ratio at the new size. */
private fun TextStyle.scaled(sizeSp: Float): TextStyle {
    val ratio = if (fontSize.isSp && lineHeight.isSp) lineHeight.value / fontSize.value else 1.3f
    return copy(
        fontSize = androidx.compose.ui.unit.TextUnit(
            sizeSp,
            androidx.compose.ui.unit.TextUnitType.Sp
        ),
        lineHeight = androidx.compose.ui.unit.TextUnit(
            sizeSp * ratio,
            androidx.compose.ui.unit.TextUnitType.Sp
        )
    )
}

/** JaecooApp's day palette (values/colors.xml there). */
val JstoreLightColorScheme = lightColorScheme(
    primary = Color(0xFF3292F5),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD5E7FD),
    onPrimaryContainer = Color(0xFF0B3A73),
    secondary = Color(0xFF1657BE),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFDDE5F4),
    onSecondaryContainer = Color(0xFF17191E),
    tertiary = Color(0xFF0E8A5F),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFE5EAEF),
    onBackground = Color(0xFF17191E),
    surface = Color(0xFFE5EAEF),
    onSurface = Color(0xFF17191E),
    surfaceVariant = Color(0xFFDEE3E8),
    onSurfaceVariant = Color(0xFF5D6066),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF7F9FB),
    surfaceContainer = Color(0xFFFFFFFF),
    surfaceContainerHigh = Color(0xFFF1F4F7),
    surfaceContainerHighest = Color(0xFFDEE3E8),
    outline = Color(0xFFB4BCC6),
    outlineVariant = Color(0xFFD1D8E0),
    error = Color(0xFFD22D2D),
    onError = Color(0xFFFFFFFF)
)

/** JaecooApp's night palette (values-night/colors.xml there). */
val JstoreDarkColorScheme = darkColorScheme(
    primary = Color(0xFF4A93EE),
    onPrimary = Color(0xFF0E0E13),
    primaryContainer = Color(0xFF1B3E66),
    onPrimaryContainer = Color(0xFFD5E7FD),
    secondary = Color(0xFF20A8CF),
    onSecondary = Color(0xFF0E0E13),
    secondaryContainer = Color(0xFF24272B),
    onSecondaryContainer = Color(0xFFECEFF4),
    tertiary = Color(0xFF4ECB8F),
    onTertiary = Color(0xFF0E0E13),
    background = Color(0xFF131517),
    onBackground = Color(0xFFECEFF4),
    surface = Color(0xFF131517),
    onSurface = Color(0xFFECEFF4),
    surfaceVariant = Color(0xFF24272B),
    onSurfaceVariant = Color(0xFFA4A8AE),
    surfaceContainerLowest = Color(0xFF0E0F11),
    surfaceContainerLow = Color(0xFF181A1D),
    surfaceContainer = Color(0xFF1D1F22),
    surfaceContainerHigh = Color(0xFF24272B),
    surfaceContainerHighest = Color(0xFF2E3137),
    outline = Color(0xFF4A4F57),
    outlineVariant = Color(0xFF2E3137),
    error = Color(0xFFF2686B),
    onError = Color(0xFF0E0E13)
)
