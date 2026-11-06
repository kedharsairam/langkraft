package com.kedharsairam.langkraft.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

@Composable
fun LangKraftTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val colors = if (dark) {
        darkColorScheme(
            primary = LangColors.AccentDark,
            onPrimary = LangColors.DarkBackground,
            surface = LangColors.DarkBackground,
            onSurface = LangColors.DarkLabel,
            surfaceVariant = LangColors.DarkSecondaryBackground,
            onSurfaceVariant = LangColors.DarkSecondaryLabel,
            outline = LangColors.DarkSeparator
        )
    } else {
        lightColorScheme(
            primary = LangColors.Accent,
            onPrimary = LangColors.LightBackground,
            surface = LangColors.LightBackground,
            onSurface = LangColors.LightLabel,
            surfaceVariant = LangColors.LightSecondaryBackground,
            onSurfaceVariant = LangColors.LightSecondaryLabel,
            outline = LangColors.LightSeparator
        )
    }
    MaterialTheme(colorScheme = colors, typography = LangTypography, content = content)
}
