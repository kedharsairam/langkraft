package com.kedharsairam.langkraft.presentation.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

@Composable
fun LangKraftTheme(content: @Composable () -> Unit) {
    // Dark only. System light ignored. Operator Deck.
    val colors = darkColorScheme(
        primary = LangColors.AccentGold,
        onPrimary = LangColors.Background,
        surface = LangColors.Background,
        surfaceVariant = LangColors.Surface,
        onSurface = LangColors.PrimaryText,
        onSurfaceVariant = LangColors.SecondaryText,
        outline = LangColors.Border,
        error = LangColors.NoticeAmber,
        onError = LangColors.Background,
        surfaceContainer = LangColors.Surface
    )
    MaterialTheme(colorScheme = colors, typography = LangTypography, content = content)
}
