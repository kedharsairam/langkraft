package com.kedharsairam.langkraft.presentation.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LangKraftTheme(content: @Composable () -> Unit) {
    // Dark only. Locked Neo-Brutalism palette and zero-radius geometry.
    val colors = darkColorScheme(
        primary = LangColors.Accent,
        onPrimary = LangColors.Ground,
        secondary = LangColors.Ink,
        onSecondary = LangColors.Ground,
        surface = LangColors.Ground,
        onSurface = LangColors.Ink,
        surfaceVariant = LangColors.Surface,
        onSurfaceVariant = LangColors.Muted,
        surfaceContainer = LangColors.Surface,
        outline = LangColors.Ink,
        outlineVariant = LangColors.Muted,
        error = LangColors.Accent,
        onError = LangColors.Ground,
        background = LangColors.Ground,
        onBackground = LangColors.Ink,
        // Never an accent behind body text over large areas.
        inverseSurface = LangColors.Ink,
        inverseOnSurface = LangColors.Ground,
        scrim = Color.Transparent
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(0.dp),
        small = RoundedCornerShape(0.dp),
        medium = RoundedCornerShape(0.dp),
        large = RoundedCornerShape(0.dp),
        extraLarge = RoundedCornerShape(0.dp)
    )
    MaterialTheme(colorScheme = colors, typography = LangTypography, shapes = shapes, content = content)
}
