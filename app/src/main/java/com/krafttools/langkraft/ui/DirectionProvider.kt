package com.krafttools.langkraft.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import com.krafttools.langkraft.data.LanguageSpec

/**
 * Adopts the language's writing direction for everything inside.
 *
 * Arabic is on the list, and it had never been rendered:
 * a grep for `LayoutDirection` across the app source returned nothing. Every screen
 * inherited the device's LTR default, which for an RTL language means the script sits
 * hard against the wrong edge, the `you` / `them` labels are on the wrong side of the
 * turn, and every card is mirrored incorrectly.
 *
 * This is applied from the spec rather than from the device, because the device locale
 * is not a proxy for the target language's direction. Someone whose phone is set to
 * English is learning Arabic, and the Arabic screen must still be RTL.
 *
 * `LocalLayoutDirection` is the correct lever rather than per-widget alignment: it flips
 * row order, text alignment, `start`/`end` padding and the arrow on the back control in
 * one move, so a new screen cannot forget to do it.
 */
@Composable
fun DirectionProvider(spec: LanguageSpec, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalLayoutDirection provides
            if (spec.scriptDirection.equals("rtl", ignoreCase = true)) LayoutDirection.Rtl
            else LayoutDirection.Ltr,
        content = content,
    )
}