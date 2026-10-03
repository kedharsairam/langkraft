package com.krafttools.langkraft.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krafttools.langkraft.data.ContentRepository
import com.krafttools.langkraft.data.Entry
import com.krafttools.langkraft.data.Exchange
import com.krafttools.langkraft.data.LanguageSpec

/**
 * The language list. The home screen, and the whole app at one language.
 *
 * There is deliberately nothing else on it: no streak, no daily goal, no progress ring,
 * no "continue". The app has no retention mechanism by design — it works if discipline
 * holds and only then — and a home screen full of counters would imply otherwise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageListScreen(corpus: ContentRepository.Corpus, onOpen: (String) -> Unit) {
    Scaffold(
        topBar = {
            // No back destination on the root screen, so this is the one bar that is not
            // KraftTopBar — and it takes no handler by design, because there is nothing
            // to go back to.
            TopAppBar(
                title = { Text("LangKraft") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(corpus.specs, key = { it.code }) { spec ->
                LanguageCard(spec = spec, onClick = { onOpen(spec.code) })
            }
        }
    }
}

@Composable
private fun LanguageCard(spec: LanguageSpec, onClick: () -> Unit) {
    val tierZero = spec.tiers.firstOrNull { it.id == 0 }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(spec.name, style = MaterialTheme.typography.titleLarge)
            if (spec.defaultVariety.isNotBlank()) {
                Text(
                    spec.defaultVariety,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            tierZero?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Tier 0 · ${tierZero.size} items",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (spec.role == "calibration") {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Calibration language — not a course to take.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

/** The four tiers for one language. Sizes differ per language and are not comparable. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PathScreen(
    spec: LanguageSpec,
    corpus: ContentRepository.Corpus,
    onOpenTier: (Int) -> Unit,
    onBack: () -> Unit,
) {
    // Arabic, Dari and Urdu are RTL. Without this the whole screen inherits the device's
    // LTR default and every card is mirrored wrongly. Driven by the SPEC, not the device:
    // an English-locale phone learning Arabic still needs an RTL screen.
    DirectionProvider(spec) {
        Scaffold(
            topBar = { KraftTopBar(title = spec.name, onBack = onBack) },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        if (spec.defaultVariety.isNotBlank()) "${spec.defaultVariety} · ${spec.scriptDirection.uppercase()}"
                        else spec.endonym,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                items(spec.tiers, key = { it.id }) { tier ->
                    val entries = corpus.entriesFor(spec.code, tier.id)
                    TierCard(
                        number = tier.id,
                        name = tier.name,
                        intent = tier.intent,
                        available = entries.size,
                        declared = tier.size,
                        certainty = tier.certainty,
                        onClick = { onOpenTier(tier.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TierCard(
    number: Int, name: String, intent: String,
    available: Int, declared: Int, certainty: String, onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            // One Text, not two. Rendering the number and the name separately made a
            // screen reader announce "0" and "Courtesy" as unrelated items, and left the
            // number unassociated with the tier it labels.
            Text(
                text = "$number · $name",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(6.dp))
            Text(intent, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(
                "$available of $declared authored",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // `certainty` is surfaced rather than hidden. Tier 0 is built to be safe and
            // the higher tiers are hypotheses; the app says which is which instead of
            // presenting a guess with the same confidence as a fact.
            if (certainty == "low") {
                Text(
                    "Unproven estimate",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.tertiary,
                )
            }
        }
    }
}

/** Everything in one tier: exchanges first, because a sequence is what you actually need. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TierScreen(
    spec: LanguageSpec,
    tier: Int,
    corpus: ContentRepository.Corpus,
    onBack: () -> Unit,
) {
    val entries = corpus.entriesFor(spec.code, tier)
    // Order comes from the content, curated by usefulness. Sorting here by scenario
    // name was arbitrary: "Asking a local..." before "Bumping into someone" tells a
    // reader nothing about which exchange they will actually need.
    val exchanges = corpus.exchangesFor(spec.code, tier)

    DirectionProvider(spec) {
        Scaffold(
            topBar = { KraftTopBar(title = spec.name, subtitle = TierId.of(tier).title, onBack = onBack) },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (exchanges.isNotEmpty()) {
                    item {
                        SectionLabel("Exchanges", "Half of every conversation is what they say to you.")
                    }
                    items(exchanges, key = { it.id }) { ex -> ExchangeCard(ex, spec) }
                    item { HorizontalDivider() }
                }
                if (entries.isNotEmpty()) {
                    item { SectionLabel("Phrases", "${entries.size} items") }
                    items(entries, key = { it.id }) { e -> EntryCard(e, spec) }
                }
                if (entries.isEmpty() && exchanges.isEmpty()) {
                    item {
                        Text(
                            "Nothing authored at this tier yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(title: String, sub: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * One phrase.
 *
 * The rendering rule, and it is the one thing here that must not be wrong:
 *
 *  - A **Latin-script** language shows one line. There is no romanisation, because the
 *    text already is one.
 *  - A **non-Latin** language shows the native script AND the romanisation. The
 *    romanisation is rendered larger and more prominently, because that is what gets
 *    read while speaking; the script is the check against it.
 *  - When the spec sets `gloss_mode: same_as_native` — the calibration language, whose
 *    gloss IS its native text — the gloss line is omitted. Rendering "Hello — Hello"
 *    would be noise.
 */
@Composable
fun EntryCard(entry: Entry, spec: LanguageSpec) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                entry.textNative,
                // Non-Latin needs a font with the right conjuncts. A fallback font
                // substitutes silently and produces a wrong glyph rather than a
                // crash, so this is deliberately NOT the default font family.
                fontFamily = if (spec.isLatinScript) FontFamily.Default else FontFamily.Serif,
                fontSize = if (entry.textRomanized != null) 20.sp else 26.sp,
                lineHeight = if (entry.textRomanized != null) 30.sp else 34.sp,
            )

            entry.textRomanized?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    fontFamily = FontFamily.Serif,
                    fontSize = 24.sp,
                    lineHeight = 30.sp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            // One line of prominence for the romanisation only; the script is the check.
            if (entry.textRomanized != null) {
                Spacer(Modifier.height(8.dp))
            }

            if (!spec.glossIsNative) {
                entry.textEnglish?.let {
                    Text(it, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                entry.why,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            entry.caution?.let {
                Spacer(Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(if (entry.direction.name == "SAY") "say" else "understand")
                if (entry.register != "neutral") Tag(entry.register)
            }
        }
    }
}

@Composable
private fun ExchangeCard(ex: Exchange, spec: LanguageSpec) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(ex.scenario, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            ex.turns.forEach { turn ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        // An optional turn can be skipped without breaking the exchange.
                        // Real interactions have these, and teaching them as mandatory
                        // teaches the learner to stall.
                        if (turn.optional) " · " else if (turn.isYou) "you" else "them",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (turn.isYou) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        val tone =
                            if (turn.optional) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onSurface
                        Text(
                            turn.textNative,
                            fontFamily = if (spec.isLatinScript) FontFamily.Default else FontFamily.Serif,
                            fontSize = 20.sp,
                            color = tone,
                        )
                        turn.textRomanized?.let {
                            Text(it, fontFamily = FontFamily.Serif, fontSize = 20.sp,
                                color = if (turn.optional) MaterialTheme.colorScheme.onSurfaceVariant
                                        else MaterialTheme.colorScheme.primary)
                        }
                        if (!spec.glossIsNative) {
                            turn.textEnglish?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (turn.optional) {
                            Text("optional — the exchange works without it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Tag(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
