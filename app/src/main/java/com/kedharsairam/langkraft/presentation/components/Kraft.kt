package com.kedharsairam.langkraft.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kedharsairam.langkraft.presentation.theme.*
import compose.icons.TablerIcons
import compose.icons.tablericons.ArrowLeft
import compose.icons.tablericons.ChevronLeft
import compose.icons.tablericons.ChevronRight
import compose.icons.tablericons.Star
import compose.icons.tablericons.World

/** Flat press feedback — no ripple, no elevation. */
@Composable
fun Modifier.kraftClick(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return this.clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
}

/** Rounded navy card with a quiet 1px border — the discrete object on screen. */
@Composable
fun Modifier.kraftPanel(fill: Color = LangColors.Surface, border: Dp = Spacing.Border): Modifier = this
    .background(fill, RoundedCornerShape(Spacing.CardRadius))
    .border(BorderStroke(border, LangColors.Border), RoundedCornerShape(Spacing.CardRadius))

/** Hairline separator between rows inside one group. */
@Composable
fun RowDivider() {
    Box(Modifier.fillMaxWidth().height(Spacing.Hairline).background(LangColors.Border.copy(alpha = 0.7f)))
}

/** Gold caps label — small, tracked out, never a sentence. Scales down at large font. */
@Composable
fun KLabel(text: String, modifier: Modifier = Modifier, color: Color = LangColors.Muted, align: TextAlign? = null) {
    val scale = LocalDensity.current.fontScale
    val style = if (scale > 1.3f) MonoLabelStyle.copy(fontSize = 10.sp, letterSpacing = 0.sp) else MonoLabelStyle
    Text(text, style = style, color = color, modifier = modifier, textAlign = align, maxLines = 1)
}

/** Serif section heading. */
@Composable
fun KSection(title: String, modifier: Modifier = Modifier) {
    Text(title, style = LangTypography.titleMedium, color = LangColors.Ink, modifier = modifier)
}

/** Numbered ledger row — used in lists of lessons/languages. */
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
        Text(index, style = MonoNumeric, color = LangColors.Accent, modifier = Modifier.width(44.dp), maxLines = 1)
        Text(title, style = LangTypography.bodyLarge, color = LangColors.Ink, modifier = Modifier.weight(1f), maxLines = 2)
        Spacer(Modifier.width(Spacing.S))
        KLabel(meta, align = TextAlign.End)
    }
    RowDivider()
}

/** Outlined secondary action — navy fill, gold label, 56dp. */
@Composable
fun KSecondary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    Box(modifier.heightIn(min = Spacing.MinTarget).fillMaxWidth()
        .let { if (selected) it.background(LangColors.Accent, RoundedCornerShape(10.dp)) else it.kraftPanel() }
        .kraftClick(onClick), contentAlignment = Alignment.Center) {
        Text(text,
            style = MonoLabelStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = if (selected) LangColors.Ground else LangColors.Accent)
    }
}

/** The single gold block on a screen. */
@Composable
fun KPrimary(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().heightIn(min = Spacing.MinTarget)
        .background(LangColors.Accent, RoundedCornerShape(10.dp)).kraftClick(onClick),
        contentAlignment = Alignment.Center) {
        Text(text,
            style = MonoLabelStyle.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = LangColors.Ground)
    }
}

/** Top bar — serif title, gold back arrow, optional actions. */
@Composable
fun KTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth().heightIn(min = Spacing.MinTarget), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            Box(Modifier.size(Spacing.MinTarget).kraftClick(onBack), contentAlignment = Alignment.Center) {
                Icon(TablerIcons.ArrowLeft, contentDescription = "Back", tint = LangColors.Accent, modifier = Modifier.size(26.dp))
            }
        }
        Text(title, style = LangTypography.titleMedium, color = LangColors.Ink,
            modifier = Modifier.weight(1f).padding(horizontal = Spacing.Xs),
            maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (actions != null) Row(content = actions)
    }
}

/** Faint dotted world-map texture drawn behind brand screens — no image asset. */
@Composable
fun Modifier.worldMapDots(): Modifier = this.drawBehind {
    val step = 22.dp.toPx()
    val r = 1.3.dp.toPx()
    var x = step / 2
    var row = 0
    while (x < size.width) {
        var y = step / 2
        var col = 0
        while (y < size.height) {
            // uneven density reads as continents rather than a flat grid
            if ((row * 7 + col * 3) % 5 != 0) {
                drawCircle(LangColors.Accent.copy(alpha = 0.07f), radius = r, center = Offset(x, y))
            }
            y += step; col++
        }
        x += step; row++
    }
}

/** The gold globe mark — a ring with the Tabler world glyph inside. */
@Composable
fun GlobeMark(size: Dp = 64.dp, alpha: Float = 1f) {
    Box(Modifier.size(size).alpha(alpha)
        .border(BorderStroke(1.dp, LangColors.Accent.copy(alpha = 0.5f)), CircleShape),
        contentAlignment = Alignment.Center) {
        Icon(TablerIcons.World, contentDescription = null, tint = LangColors.Accent,
            modifier = Modifier.size(size * 0.62f))
    }
}

/** Editorial section head: gold rule, caps label, optional count. */
@Composable
fun SignalSection(title: String, count: Int? = null) {
    Box(Modifier.fillMaxWidth().height(2.dp).background(LangColors.Accent))
    Spacer(Modifier.height(Spacing.S))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title.uppercase(), style = MonoLabelStyle.copy(color = LangColors.Accent, fontSize = 13.sp),
            modifier = Modifier.weight(1f), maxLines = 1)
        if (count != null) KLabel("$count", color = LangColors.Accent)
    }
    Spacer(Modifier.height(Spacing.Xs))
}

/** Outlined list button with a gold icon tile and a chevron — the home quick-access row. */
@Composable
fun KListButton(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().heightIn(min = Spacing.MinTarget).kraftPanel().kraftClick(onClick)
        .padding(horizontal = Spacing.M, vertical = Spacing.S),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(34.dp).background(LangColors.SurfaceAlt, RoundedCornerShape(8.dp)), Alignment.Center) {
            Icon(icon, contentDescription = null, tint = LangColors.Accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Spacing.S))
        Text(label, style = LangTypography.bodyLarge, color = LangColors.Ink, modifier = Modifier.weight(1f), maxLines = 1)
        Icon(TablerIcons.ChevronRight, contentDescription = null, tint = LangColors.Muted, modifier = Modifier.size(20.dp))
    }
}

/** A phrase card: number chip, serif hero, gold italic saying, tip, note, meta, star. */
@Composable
fun SignalRow(
    index: Int,
    english: String,
    romanization: String,
    youSay: String,
    tip: String,
    note: String,
    meta: String,
    starred: Boolean,
    onStar: () -> Unit
) {
    Column(Modifier.fillMaxWidth().kraftPanel().padding(Spacing.M)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(30.dp).background(LangColors.SurfaceAlt, RoundedCornerShape(6.dp)), Alignment.Center) {
                Text("%02d".format(index), style = MonoNumeric.copy(fontSize = 12.sp), color = LangColors.Accent)
            }
            Spacer(Modifier.width(Spacing.S))
            Text(english, style = LangTypography.bodyMedium, color = LangColors.Muted,
                modifier = Modifier.weight(1f), maxLines = 1)
            Box(Modifier.size(48.dp).kraftClick(onStar), Alignment.Center) {
                Icon(TablerIcons.Star, contentDescription = if (starred) "Remove from hard" else "Mark as hard",
                    Modifier.size(22.dp), if (starred) LangColors.StateHard else LangColors.Muted)
            }
        }
        Spacer(Modifier.height(Spacing.S))
        Text(romanization, style = RomanizationStyle, color = LangColors.Ink)
        Text(youSay.replace("-", "-\u200B"), style = YouSayStyle, color = LangColors.Accent)
        if (tip.isNotBlank()) {
            Spacer(Modifier.height(Spacing.S))
            Box(Modifier.fillMaxWidth().height(1.dp).background(LangColors.Border.copy(alpha = 0.6f)))
            Spacer(Modifier.height(Spacing.S))
            KLabel("PRONUNCIATION TIP", color = LangColors.Accent)
            Spacer(Modifier.height(Spacing.Xs))
            Text(tip, style = LangTypography.bodySmall, color = LangColors.Muted)
        }
        if (note.isNotBlank()) {
            Spacer(Modifier.height(Spacing.S))
            Text(note, style = LangTypography.bodySmall, color = LangColors.Ink)
        }
        Spacer(Modifier.height(Spacing.S))
        KLabel(meta)
    }
    Spacer(Modifier.height(Spacing.S))
}

/** Numbered language row with a chevron — dense, one line per language. */
@Composable
fun SignalLanguageRow(index: Int, name: String, count: String, fraction: Float, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).kraftClick(onClick)
        .padding(vertical = Spacing.S), verticalAlignment = Alignment.CenterVertically) {
        Text("%02d".format(index), style = MonoNumeric, color = LangColors.Accent,
            modifier = Modifier.width(40.dp), maxLines = 1)
        Text(name, style = LangTypography.bodyLarge, color = LangColors.Ink,
            modifier = Modifier.weight(1f), maxLines = 1)
        KLabel(count, align = TextAlign.End)
        Spacer(Modifier.width(Spacing.S))
        Icon(TablerIcons.ChevronRight, contentDescription = null, tint = LangColors.Muted, modifier = Modifier.size(18.dp))
    }
    RowDivider()
}

/** Bottom stepper with ‹ › arrows and a page counter. */
@Composable
fun KStepper(position: Int, total: Int, onPrev: () -> Unit, onNext: () -> Unit) {
    val canPrev = position > 1
    val canNext = position < total
    Row(Modifier.fillMaxWidth().heightIn(min = Spacing.MinTarget), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(Spacing.MinTarget).let { if (canPrev) it.kraftClick(onPrev) else it }, Alignment.Center) {
            Icon(TablerIcons.ChevronLeft, contentDescription = "Previous phrase",
                tint = if (canPrev) LangColors.Accent else LangColors.Border, modifier = Modifier.size(26.dp))
        }
        Text("%02d / %02d".format(position.coerceAtLeast(1), total.coerceAtLeast(1)),
            style = MonoLabelStyle, color = LangColors.Muted, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
        Box(Modifier.size(Spacing.MinTarget).let { if (canNext) it.kraftClick(onNext) else it }, Alignment.Center) {
            Icon(TablerIcons.ChevronRight, contentDescription = "Next phrase",
                tint = if (canNext) LangColors.Accent else LangColors.Border, modifier = Modifier.size(26.dp))
        }
    }
}

fun displaySource(source: String): String = when {
    source.startsWith("Wikivoyage", ignoreCase = true) -> "WIKIVOYAGE"
    else -> "AUTHORED"
}
