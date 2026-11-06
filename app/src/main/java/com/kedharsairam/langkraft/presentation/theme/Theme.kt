package com.kedharsairam.langkraft.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun LangKraftTheme(content: @Composable () -> Unit) {
    // Dark only — ignore system light.
    val colors = darkColorScheme(
        primary = LangColors.Accent,
        onPrimary = LangColors.Background,
        surface = LangColors.Background,
        onSurface = LangColors.Label,
        surfaceVariant = LangColors.SecondaryBackground,
        onSurfaceVariant = LangColors.SecondaryLabel,
        outline = LangColors.Separator,
        surfaceContainer = LangColors.SecondaryBackground
    )
    MaterialTheme(colorScheme = colors, typography = LangTypography, content = content)
}
