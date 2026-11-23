package com.kedharsairam.langkraft.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kedharsairam.langkraft.presentation.theme.LangColors
import com.kedharsairam.langkraft.presentation.theme.MonoLabelStyle
import com.kedharsairam.langkraft.presentation.theme.Spacing

/** Flat press feedback — no Material ripple, no elevation. */
@Composable
fun Modifier.flatClickable(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        role = Role.Button,
        onClick = onClick
    )
}

/** The one reusable surface: 2dp ink border, 0 radius, no shadow, no fill trickery. */
@Composable
fun Modifier.borderedCard(
    fill: Color = LangColors.Ground,
    border: Color = LangColors.Ink,
    width: Dp = Spacing.Border
): Modifier = this
    .background(fill)
    .border(BorderStroke(width, border), androidx.compose.foundation.shape.RoundedCornerShape(0.dp))

/** Short provenance label for cards — the full source stays in the data. */
fun displaySource(source: String): String = when {
    source.startsWith("Wikivoyage", ignoreCase = true) -> "WIKIVOYAGE"
    else -> "AUTHORED"
}

/** Mono caps label — the only place caps are allowed. */
@Composable
fun MonoLabel(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = LangColors.Muted,
    align: TextAlign? = null
) {
    val scale = LocalDensity.current.fontScale
    val style = if (scale > 1.3f) MonoLabelStyle.copy(fontSize = 10.sp, letterSpacing = 0.sp) else MonoLabelStyle
    Text(
        text = text,
        style = style,
        color = color,
        modifier = modifier,
        textAlign = align,
        maxLines = 1
    )
}

/** Full-bleed 2dp ink divider. Pass [fullBleed] to ignore screen padding. */
@Composable
fun SectionDivider(fullBleed: Boolean = false) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(if (fullBleed) Spacing.Border else Spacing.Hairline)
            .background(if (fullBleed) LangColors.Ink else LangColors.Muted)
    )
}

/** Back + title + right-side icon actions, 56dp tall, 20dp side padding applied by caller. */
@Composable
fun KraftTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    showSearch: Boolean = false,
    onSearch: () -> Unit = {},
    showHard: Boolean = false,
    onHard: () -> Unit = {}
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(Spacing.MinTarget),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            Box(
                Modifier.size(Spacing.MinTarget).flatClickable(onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = LangColors.Ink,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Text(
            title,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            color = LangColors.Ink,
            modifier = Modifier.weight(1f)
        )
        if (showSearch) {
            Box(
                Modifier.size(Spacing.MinTarget).flatClickable(onSearch),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Search, contentDescription = "Search", tint = LangColors.Ink, modifier = Modifier.size(24.dp))
            }
        }
        if (showHard) {
            Box(
                Modifier.size(Spacing.MinTarget).flatClickable(onHard),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Star, contentDescription = "Hard", tint = LangColors.Ink, modifier = Modifier.size(24.dp))
            }
        }
    }
}

/** Ledger row: fixed index column, flexible title, fixed right-hand metadata. */
@Composable
fun LedgerRow(
    index: String,
    title: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    titleMaxLines: Int = 2
) {
    Row(
        modifier
            .fillMaxWidth()
            .borderedCard()
            .flatClickable(onClick)
            .padding(Spacing.M),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            index,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = LangColors.Muted,
            modifier = Modifier.width(44.dp)
        )
        Text(
            title,
            style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
            color = LangColors.Ink,
            maxLines = titleMaxLines,
            modifier = Modifier.weight(1f).padding(end = Spacing.S)
        )
        Text(
            meta,
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            color = LangColors.Muted,
            textAlign = TextAlign.End,
            maxLines = 1,
            modifier = Modifier.widthIn(min = 44.dp)
        )
    }
}

/** Secondary CTA: 2dp ink border, transparent fill, mono caps, 56dp. */
@Composable
fun SecondaryCta(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Spacing.MinTarget)
            .borderedCard(fill = if (selected) LangColors.Accent else LangColors.Ground)
            .flatClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MonoLabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = if (selected) LangColors.Ground else LangColors.Accent
        )
    }
}

/** Primary CTA: the one inverted block on a screen — ink fill, ground text. */
@Composable
fun InvertedCta(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(Spacing.MinTarget)
            .background(LangColors.Ink)
            .flatClickable(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            style = MonoLabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = LangColors.Ground
        )
    }
}
