package com.krafttools.langkraft.ui

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.krafttools.langkraft.R

/**
 * Bundled type for the scripts the device does not draw well.
 *
 * **Why these are bundled rather than left to the system.** Non-Latin text was falling back
 * to `FontFamily.Serif`, which on a stock Android device resolves to Noto Serif Thai and
 * Noto Serif Tamil. Both are legible, and both look wrong in a specific way:
 *
 *  - Noto Serif Thai carries loops and spurs on the letterforms. Thai's own convention is
 *    a loopless face, so it reads as a different typographic tradition from the Latin text
 *    beside it — the two look like they came from different books.
 *  - Noto Serif Tamil renders conjuncts (க்ஷ, ஞா) smaller than the surrounding letters and
 *    the dotted ring sits low. It does not look broken, but it looks like a fallback, which
 *    is exactly the impression a phrasebook cannot afford.
 *
 * Beyond appearance, leaving the app's most heavily-weighted element to the system's font
 * configuration means the look of the whole app can change under the user. For a reference
 * work that is read rather than interacted with, that should be fixed by the app.
 *
 * **Latin is deliberately NOT bundled.** Roboto is on every Android device and the Latin
 * text is the chrome, not the content; shipping a Latin face would add weight for no gain.
 *
 * Four weights each, ~2.1 MB total. The variable-font originals are 218 KB and 340 KB but
 * Android would flatten every weight in a variable font to its default instance, which
 * costs the bold/medium distinction the type scale relies on. The static instances were
 * measured, not assumed.
 *
 * SIL Open Font License 1.1. Recorded in the in-app credits, as a licence obligation.
 */
val NotoSansThai = FontFamily(
    Font(R.font.notosansthairegular, FontWeight.Normal),
    Font(R.font.notosansthaimedium, FontWeight.Medium),
    Font(R.font.notosansthaisemibold, FontWeight.SemiBold),
    Font(R.font.notosansthaibold, FontWeight.Bold),
)

val NotoSansTamil = FontFamily(
    Font(R.font.notosanstamilregular, FontWeight.Normal),
    Font(R.font.notosanstamilmedium, FontWeight.Medium),
    Font(R.font.notosanstamilsemibold, FontWeight.SemiBold),
    Font(R.font.notosanstamilbold, FontWeight.Bold),
)

/**
 * The face for a language's own script.
 *
 * Spec-driven, not locale-driven, for the same reason the layout direction is: the script
 * belongs to the language being read, not to the phone's language. A Telugu reader on an
 * English phone must still get Noto Sans Tamil.
 */
fun scriptFontFamily(primaryScript: String): FontFamily = when {
    primaryScript.contains("Thai", ignoreCase = true) -> NotoSansThai
    primaryScript.contains("Tamil", ignoreCase = true) -> NotoSansTamil
    else -> FontFamily.Default
}