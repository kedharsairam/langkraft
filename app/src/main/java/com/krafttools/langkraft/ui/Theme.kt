package com.krafttools.langkraft.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.kraft.ui.tokens.KraftTypeScale

/**
 * Dark only, deliberately.
 *
 * This app is read in the dark — on a train, before a flight, in a queue — and every
 * other Kraft app is dark. There is no theme switch because there is nothing to switch
 * between, and offering one would imply a choice that does not exist.
 *
 * The palette is KraftDark, identical to EnglishKraft's, so the portfolio reads as one
 * body of work rather than a set of lookalikes.
 *
 * Contrast note: text carries everything. A phrasebook is a wall of script and romanised
 * text, and chrome that competes with it is chrome that costs legibility. Anything
 * decorative must be able to lose.
 */
private val KraftDark = darkColorScheme(
    primary = Color(0xFF9CCBFF),
    onPrimary = Color(0xFF00315C),
    primaryContainer = Color(0xFF00477F),
    onPrimaryContainer = Color(0xFFD3E4FF),

    secondary = Color(0xFFBBC7DB),
    onSecondary = Color(0xFF253140),
    secondaryContainer = Color(0xFF3B4858),
    onSecondaryContainer = Color(0xFFD7E3F7),

    tertiary = Color(0xFFD5BDE4),
    onTertiary = Color(0xFF38293F),
    tertiaryContainer = Color(0xFF4F3F57),
    onTertiaryContainer = Color(0xFFF1DAFF),

    background = Color(0xFF0D1117),
    onBackground = Color(0xFFE2E6EC),
    surface = Color(0xFF0D1117),
    onSurface = Color(0xFFE2E6EC),
    surfaceVariant = Color(0xFF21262D),
    onSurfaceVariant = Color(0xFF9AA4B2),

    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    onErrorContainer = Color(0xFF93000A),
    errorContainer = Color(0xFF690005),

    outline = Color(0xFF3A424C),
    // Was UNSET, so it fell back to the Material 3 baseline dark value #45464F — a
    // purple-grey divider sitting inside a blue-grey app. Anything not named here is not
    // a Kraft colour, it is somebody else's, and the gap shows.
    outlineVariant = Color(0xFF262C34),

    // The tonal ramp between the page and the cards. Previously absent entirely, so every
    // card in the app shared the single `surfaceVariant` and there was no step between
    // page, card and control. Tags and the flag button in particular had nowhere to sit
    // except `surface`, which IS the page background — a hole rather than a chip.
    surfaceDim = Color(0xFF090C10),
    surfaceContainerLowest = Color(0xFF0D1117),
    surfaceContainerLow = Color(0xFF161B22),
    surfaceContainer = Color(0xFF1B2129),
    surfaceContainerHigh = Color(0xFF21262D),
    surfaceContainerHighest = Color(0xFF2A313A),
    surfaceBright = Color(0xFF21262D),

    // Was falling back to `primary`, so any component applying tonal elevation tinted a
    // surface blue. There is no elevation to tint here; the ramp is explicit.
    surfaceTint = Color(0x00000000),

    inverseSurface = Color(0xFFE2E6EC),
    inverseOnSurface = Color(0xFF1B2129),
    inversePrimary = Color(0xFF00315C),
    scrim = Color(0xFF000000),
)

/**
 * The type scale.
 *
 * There was none. Every `MaterialTheme.typography.*` in the app was the stock Material 3
 * value, which is tuned for short English UI strings — not for a phrasebook, where the
 * longest text on screen is a 300-character explanation of why a tone mark matters.
 *
 * What changed, and why it matters for THIS app specifically:
 *
 *  - **Line height is the load-bearing change.** Thai and Tamil stack marks well above
 *    and below the baseline. At the stock 1.2 ratio on a 20sp line, a Thai line with
 *    tone marks collides with the line above it. The non-Latin sizes get 1.5.
 *  - `displaySmall` and `headlineMedium` exist because the native phrase is the largest
 *    text in the app and needs a slot of its own, rather than borrowing `titleLarge`.
 *
 * Sizes are in `sp` so they scale with the system font setting, and every line height is
 * proportional to its own size rather than copied from the default.
 *
 * Where a size matches the shared scale it is taken from `KraftTypeScale`, so a change
 * there reaches this app. Three sizes do not match and are stated literally, each for a
 * reason rather than by accident:
 *
 *  - `displaySmall` is 32sp against the shared 34. The native phrase is set in bundled
 *    script fonts at this size; 34 crowds the tone marks in Thai at this weight.
 *  - `headlineSmall` is 24sp. It renders endonyms — a language's own name for itself —
 *    and 22 renders Devanagari and Thai conjuncts small enough to misread at a glance.
 *  - `bodySmall` is 14sp. It is the most-used slot in the app (the rationale line on
 *    every card), and 13 crowds stacked marks while 15 pushes the card past one screen
 *    on small phones.
 *
 * Weights stay Medium throughout for the same reason: many of these scripts render
 * poorly in Bold at these sizes, and the app bundles its fonts precisely so the type
 * does not change under the user.
 */
private val KraftType = Typography(
    displaySmall = TextStyle(
        fontSize = 32.sp, lineHeight = 44.sp, fontWeight = FontWeight.Medium,
    ),
    headlineMedium = TextStyle(
        fontSize = KraftTypeScale.Title1, lineHeight = 38.sp, fontWeight = FontWeight.Medium,
    ),
    headlineSmall = TextStyle(
        fontSize = 24.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium,
    ),
    titleLarge = TextStyle(
        fontSize = KraftTypeScale.Title2, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold,
    ),
    titleMedium = TextStyle(
        fontSize = KraftTypeScale.Headline, lineHeight = 24.sp, fontWeight = FontWeight.Medium,
    ),
    bodyLarge = TextStyle(
        fontSize = KraftTypeScale.Body, lineHeight = 26.sp,
    ),
    bodyMedium = TextStyle(
        fontSize = KraftTypeScale.Subheadline, lineHeight = 23.sp,
    ),
    // The rationale text. It used to be `bodySmall`, making the LONGEST text on a card
    // the SMALLEST, which is exactly backwards for a phrasebook whose value is the prose.
    bodySmall = TextStyle(
        fontSize = 14.sp, lineHeight = 21.sp,
    ),
    labelLarge = TextStyle(
        fontSize = KraftTypeScale.Subheadline, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
    ),
    labelMedium = TextStyle(
        fontSize = KraftTypeScale.Footnote, lineHeight = 18.sp, fontWeight = FontWeight.Medium,
    ),
    labelSmall = TextStyle(
        fontSize = KraftTypeScale.Caption1, lineHeight = 17.sp,
    ),
  )

  /**
   * The phrase content styles — the app's equivalent of a verse face.
   *
   * These are not slots in `KraftType` because no slot matches them, and they should not be
   * bent to fit: the native phrase at 26sp is the largest text in the app, the compact 20sp
   * is what it becomes when a romanization shares the card, and the romanization at 24sp is
   * Latin text wearing the app's Latin face. Each is sized for script legibility — the same
   * reason the scale's own weights stay Medium — and each carries the line height its size
   * needs for stacked marks rather than the ratio a slot would give it.
   *
   * The call sites apply `fontFamily` themselves, because the family depends on the content
   * (the entry's script) rather than the style. A style that named a family would be wrong
   * for every script but one.
   */

  /** The native phrase, set large — the single most prominent text in the app. */
  val PhraseNative = TextStyle(fontSize = 26.sp, lineHeight = 34.sp)

  /** The native phrase when a romanization shares the card. Smaller, because two large
   * texts on one card compete rather than reinforce. */
  val PhraseNativeCompact = TextStyle(fontSize = 20.sp, lineHeight = 30.sp)

  /** The romanization: Latin text, so it wears the default face, at a size that reads as a
   * transcription of the phrase above it rather than a second phrase. */
  val PhraseRomanized = TextStyle(fontSize = 24.sp, lineHeight = 30.sp)

  /** A native phrase inside running content — drill turns, variant rows — where the card
   * styles would overwhelm. Same family rule as above: the call site supplies the script. */
  val PhraseInline = TextStyle(fontSize = 20.sp, lineHeight = 30.sp)

@Composable
fun LangKraftTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = KraftDark, typography = KraftType, content = content)
}