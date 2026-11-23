package com.kedharsairam.langkraft.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kedharsairam.langkraft.presentation.theme.*
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft

/** Flat press feedback. No ripple, no elevation. */
@Composable
fun Modifier.kraftClick(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
}

/** Border only where a discrete object needs one — never on every row. */
@Composable
fun Modifier.kraftPanel(fill: Color = LangColors.Ground, border: Dp = Spacing.Border): Modifier = this
    .background(fill)
    .border(BorderStroke(border, LangColors.Ink), RoundedCornerShape(0.dp))

/** A hairline separator between rows inside one group. */
@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(Spacing.Hairline).background(LangColors.Muted.copy(alpha = 0.35f)))
}

/** Mono caps label — labels only, never sentences. Scales down at large font. */
@Composable
fun KLabel(text: String, modifier: Modifier = Modifier, color: Color = LangColors.Muted, align: TextAlign? = null) {
    val scale = LocalDensity.current.fontScale
    val style = if (scale > 1.3f) MonoLabelStyle.copy(fontSize = 10.sp, letterSpacing = 0.sp) else MonoLabelStyle
    androidx.compose.material3.Text(text, style = style, color = color, modifier = modifier,
        textAlign = align, maxLines = 1)
}

/** Section heading in the display face. */
@Composable
fun KSection(title: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(title, style = LangTypography.titleMedium, color = LangColors.Ink, modifier = modifier)
}

/** Ledger row — a hairline-divided line, not a box. Used in lists of lessons/languages. */
@Composable
fun LedgerRow(
    index: String,
    title: String,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null
) {
    Row(
        modifier.fillMaxWidth().kraftClick(onClick).padding(vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(Spacing.S)) }
        androidx.compose.material3.Text(index, style = MonoNumeric, color = LangColors.Muted,
            modifier = Modifier.width(44.dp), maxLines = 1)
        androidx.compose.material3.Text(title, style = LangTypography.bodyLarge, color = LangColors.Ink,
            modifier = Modifier.weight(1f), maxLines = 2)
        Spacer(Modifier.width(Spacing.S))
        KLabel(meta, align = TextAlign.End)
    }
    RowDivider()
}

/** Secondary action: outlined, mono caps, 56dp. */
@Composable
fun KSecondary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    Box(modifier.fillMaxWidth().height(Spacing.MinTarget)
        .let { if (selected) it.background(LangColors.Accent) else it.kraftPanel() }
        .kraftClick(onClick), contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(text,
            style = MonoLabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = if (selected) LangColors.Ground else LangColors.Accent)
    }
}

/** The single inverted block on a screen. */
@Composable
fun KPrimary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(Spacing.MinTarget).background(LangColors.Ink).kraftClick(onClick),
        contentAlignment = Alignment.Center) {
        androidx.compose.material3.Text(text,
            style = MonoLabelStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = LangColors.Ground)
    }
}

/** Top bar with Tabler icons. */
@Composable
fun KTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth().height(Spacing.MinTarget), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Box(Modifier.size(Spacing.MinTarget).kraftClick(onBack), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(
                    TablerIcons.ArrowLeft,
                    contentDescription = "Back", tint = LangColors.Ink, modifier = Modifier.size(24.dp))
            }
        }
        androidx.compose.material3.Text(title, style = LangTypography.titleLarge,
            color = LangColors.Ink, modifier = Modifier.weight(1f), maxLines = 1)
        if (actions != null) Row(content = actions)
    }
}


fun displaySource(source: String): String = when {
    source.startsWith("Wikivoyage", ignoreCase = true) -> "WIKIVOYAGE"
    else -> "AUTHORED"
}
