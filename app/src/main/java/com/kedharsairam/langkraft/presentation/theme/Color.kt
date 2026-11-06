package com.kedharsairam.langkraft.presentation.theme

import androidx.compose.ui.graphics.Color

// Operator Deck — OLED true black, tactical, zero chrome.
object LangColors {
    val Background = Color(0xFF000000)
    val Surface = Color(0xFF0A0A0A)
    val Border = Color(0xFF222222)
    val PrimaryText = Color(0xFFEDEDED)
    val SecondaryText = Color(0xFF888888)
    val AccentGold = Color(0xFFE5C07B)
    val NoticeAmber = Color(0xFFD19A66)

    // Compat aliases for existing screens
    val Accent = AccentGold
    val AccentDark = AccentGold
    val SecondaryBackground = Surface
    val Label = PrimaryText
    val SecondaryLabel = SecondaryText
    val TertiaryLabel = Color(0xFF636366)
    val Separator = Border
    val Fill = Color(0xFF1C1C1E)
}
