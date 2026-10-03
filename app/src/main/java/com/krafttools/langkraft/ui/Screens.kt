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
        topBar = { TopAppBar(title = { Text("LangKraft") }, colors = kraftBarColors()) },
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(spec.name) },
                navigationIcon = { Text("‹", Modifier.padding(16.dp), fontSize = 28.sp) },
                colors = kraftBarColors(),
            )
        },
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$number", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(0.dp))
                Text("  $name", style = MaterialTheme.typography.titleMedium)
            }
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
    val exchanges = corpus.exchangesFor(spec.code, tier)
        .sortedBy { it.scenario }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("${TierId.of(tier).title} · ${spec.name}") },
                navigationIcon = { Text("‹", Modifier.padding(16.dp), fontSize = 28.sp) },
                colors = kraftBarColors(),
            )
        },
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
                        if (turn.isYou) "you" else "them",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (turn.isYou) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            turn.textNative,
                            fontFamily = if (spec.isLatinScript) FontFamily.Default else FontFamily.Serif,
                            fontSize = 20.sp,
                        )
                        turn.textRomanized?.let {
                            Text(it, fontFamily = FontFamily.Serif, fontSize = 20.sp,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        if (!spec.glossIsNative) {
                            turn.textEnglish?.let {
                                Text(it, style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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

@Composable
fun kraftBarColors() = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.background,
    titleContentColor = MaterialTheme.colorScheme.onBackground,
)