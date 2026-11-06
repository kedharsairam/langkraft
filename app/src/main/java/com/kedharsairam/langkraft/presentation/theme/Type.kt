package com.kedharsairam.langkraft.presentation.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// UI = system sans (Roboto on Android). Utterances/numbers = monospace.
val LangTypography = Typography(
    displayLarge = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 20.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    headlineMedium = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 17.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 16.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
)

// Operator Deck utterance — mono caps, bold, dots already in data (KA.feh).
val YouSayStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 28.sp,
    lineHeight = 36.sp,
    fontWeight = FontWeight.Bold,
    letterSpacing = 0.5.sp
)

val MonoNumeric = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 20.sp,
    lineHeight = 26.sp,
    fontWeight = FontWeight.Bold
)

object PhraseSizes {
    val English = 17.sp
    val YouSay = 28.sp
    val Meta = 12.sp
}
