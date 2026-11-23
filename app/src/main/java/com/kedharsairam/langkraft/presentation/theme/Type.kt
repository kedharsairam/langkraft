package com.kedharsairam.langkraft.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Locked type split (Neo-Brutalism §6):
// utterances and numbers = mono; everything else = system sans.
// Caps are for labels only — never a sentence.
val LangTypography = Typography(
    displayLarge = TextStyle(fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 18.sp),
    labelMedium = TextStyle(fontSize = 13.sp, lineHeight = 17.sp)
)

// The thing you say: mono bold, 28sp, stress in CAPS and syllables split with hyphens.
val YouSayStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 28.sp,
    lineHeight = 36.sp,
    fontWeight = FontWeight.Bold
)

// Numbers in the grid: mono bold.
val MonoNumeric = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    fontWeight = FontWeight.Bold
)

// Mono caps label: metadata, counts, section names.
val MonoLabelStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    lineHeight = 16.sp,
    fontWeight = FontWeight.Normal,
    letterSpacing = 1.sp
)

object PhraseSizes {
    val English = 17.sp
    val YouSay = 28.sp
    val Meta = 12.sp
}
