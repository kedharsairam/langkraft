package com.kedharsairam.langkraft.presentation.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LangKraftTheme(content: @Composable () -> Unit) {
    // Dark navy only. Rounded 12dp cards, quiet borders.
    val colors = darkColorScheme(
        primary = LangColors.Accent,
        onPrimary = LangColors.Ground,
        secondary = LangColors.Ink,
        onSecondary = LangColors.Ground,
        surface = LangColors.Surface,
        onSurface = LangColors.Ink,
        surfaceVariant = LangColors.SurfaceAlt,
        onSurfaceVariant = LangColors.Muted,
        surfaceContainer = LangColors.Surface,
        outline = LangColors.Border,
        outlineVariant = LangColors.Border,
        error = LangColors.StateHard,
        onError = LangColors.Ground,
        background = LangColors.Ground,
        onBackground = LangColors.Ink,
        inverseSurface = LangColors.Ink,
        inverseOnSurface = LangColors.Ground,
        scrim = Color.Transparent
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(20.dp)
    )
    MaterialTheme(colorScheme = colors, typography = LangTypography, shapes = shapes, content = content)
}
