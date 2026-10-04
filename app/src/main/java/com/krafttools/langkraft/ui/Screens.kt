package com.krafttools.langkraft.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
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
fun LanguageListScreen(
    corpus: ContentRepository.Corpus,
    positions: Map<String, Map<Int, Int>>,
    onOpen: (String) -> Unit,
) {
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
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(corpus.specs, key = { it.code }) { spec ->
                LanguageCard(
                    spec = spec,
                    entriesFor = { code, t -> corpus.entriesFor(code, t) },
                    // "Have you started this language", not "how far". Comparing raw
                    // indices across tiers is meaningless: index 3 is the 3rd exchange in
                    // one tier and the 2nd phrase in another. Which TIER they were last in
                    // is answerable, via updated_at, but the value here was only ever used
                    // as a boolean, so a false comparison bought nothing.
                    bookmark = positions[spec.code]?.values?.any { it > 0 }?.let { 1 },
                    onClick = { onOpen(spec.code) },
                )
            }
        }
    }
}

@Composable
private fun LanguageCard(
    spec: LanguageSpec,
    entriesFor: (String, Int) -> List<Entry>,
    bookmark: Int?,
    onClick: () -> Unit,
) {
    val tierZero = spec.tiers.firstOrNull { it.id == 0 }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
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
                    // What SHIPPED, not what the spec claims. The path screen already
                    // showed available-vs-declared; the home screen claimed the declared
                    // number, so a language shipping short advertised content that is not
                    // in the APK.
                    "Tier 0 · ${entriesFor(spec.code, 0).size} items",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // The bookmark, and ONLY the bookmark. No percentage, no accuracy, no
            // "12 of 48 read" — progress in this app means position and nothing else, so
            // there is deliberately nothing here that could be misread as a score.
            bookmark?.takeIf { it > 0 }?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Continue where you left off",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    positions: Map<String, Map<Int, Int>>,
    onOpenTier: (Int) -> Unit,
    onOpenSearch: () -> Unit,
    onBack: () -> Unit,
) {
    // Arabic, Dari and Urdu are RTL. Without this the whole screen inherits the device's
    // LTR default and every card is mirrored wrongly. Driven by the SPEC, not the device:
    // an English-locale phone learning Arabic still needs an RTL screen.
    DirectionProvider(spec) {
        Scaffold(
            topBar = {
            KraftTopBar(
                title = spec.name,
                onBack = onBack,
                actionLabel = "Search",
                onAction = onOpenSearch,
            )
        },
        ) { padding ->
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
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
                        bookmark = positions[spec.code]?.get(tier.id)?.takeIf { it > 0 },
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
    available: Int, declared: Int, certainty: String, bookmark: Int?, onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
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
            bookmark?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Pick up where you stopped",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
    startIndex: Int = 0,
    onPosition: (Int) -> Unit = {},
    onFlag: (String, String?) -> Unit = { _, _ -> },
    onBack: () -> Unit,
) {
    val entries = corpus.entriesFor(spec.code, tier)
    // Order comes from the content, curated by usefulness. Sorting here by scenario
    // name was arbitrary: "Asking a local..." before "Bumping into someone" tells a
    // reader nothing about which exchange they will actually need.
    val exchanges = corpus.exchangesFor(spec.code, tier)

    // The bookmark. `startIndex` is where the learner was last time; the list is restored
    // there rather than at the top, which is the entire point of storing a position.
    //
    // Position is the FIRST VISIBLE ITEM, not "items read". Those differ the moment a
    // learner scrolls back to re-read something, and "read" is a claim about their
    // attention, which the app has no business making.
    // The composed item count, derived from the same three conditionals the list below
    // uses. Both the seed and the clamp read it, so they cannot drift.
    val totalItems = buildList {
        if (corpus.toneSetsFor(spec.code, tier).isNotEmpty()) add(2)
        if (exchanges.isNotEmpty()) add(2 + exchanges.size)
        if (entries.isNotEmpty()) add(1 + entries.size)
        if (entries.isEmpty() && exchanges.isEmpty()) add(1)
    }.sum()

    val listState = rememberLazyListState(
        // Clamp BOTH ends. An index stored against a larger content set -- a learner who
        // had 200 items before an update shipped fewer -- is not a valid index, and
        // handing it to the list produced an out-of-range seed that the collector below
        // then persisted, so the bad bookmark never healed.
        initialFirstVisibleItemIndex = startIndex.coerceIn(0, (totalItems - 1).coerceAtLeast(0)),
    )

    // Reported when the scroll settles, never on a timer. A timer would write rows the
    // learner never scrolled to and make the bookmark drift toward wherever they idled.
    LaunchedEffect(listState, spec.code, tier) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            // `drop(1)` skips the initial emission. Without it, merely OPENING a tier
            // rewrote the bookmark to the seed value, so a stored position was destroyed
            // by the act of restoring it and a clamped index was silently overwritten.
            // A position is recorded when the learner MOVES, not when they arrive.
            .drop(1)
            .collect { onPosition(it) }
    }

    DirectionProvider(spec) {
        Scaffold(
            topBar = { KraftTopBar(title = spec.name, subtitle = TierId.of(tier).title, onBack = onBack) },
        ) { padding ->
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val toneSets = corpus.toneSetsFor(spec.code, tier)
                if (toneSets.isNotEmpty()) {
                    item {
                        ToneSection(
                            sets = toneSets,
                            spec = spec,
                            onFlag = { onFlag("tone:" + it.id, null) },
                        )
                    }
                    item { HorizontalDivider() }
                }
                if (exchanges.isNotEmpty()) {
                    item {
                        SectionLabel("Exchanges", "Half of every conversation is what they say to you.")
                    }
                    items(exchanges, key = { it.id }) { ex ->
                        ExchangeCard(ex, spec, onFlag = { onFlag("exchange:" + ex.id, null) })
                    }
                    item { HorizontalDivider() }
                }
                if (entries.isNotEmpty()) {
                    item { SectionLabel("Phrases", "${entries.size} items") }
                    items(entries, key = { it.id }) { e ->
                        EntryCard(e, spec, onFlag = { onFlag("entry:" + e.id, null) })
                    }
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
fun SectionLabel(title: String, sub: String) {
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
fun EntryCard(entry: Entry, spec: LanguageSpec, onFlag: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
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
            // FlowRow, not Row. Three tags at 2.0x font scale need ~430dp of a 328dp
            // card; a Row cannot wrap, so "failed you before" broke onto three lines
            // beside two single-line pills. That tag appears as soon as the learner taps
            // the flag once, so this was reachable in shipped content, not latent.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Tag(if (entry.direction.name == "SAY") "say" else "understand")
                if (entry.register != "neutral") Tag(entry.register)
                if (entry.failureFlags.isNotEmpty()) Tag("failed you before")
            }
            FailureFlagButton(onClick = onFlag)
        }
    }
}

@Composable
private fun ExchangeCard(ex: Exchange, spec: LanguageSpec, onFlag: () -> Unit = {}) {
    Card(
        modifier = Modifier.fillMaxWidth().widthIn(max = 640.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(ex.scenario, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            ex.turns.forEach { turn ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Text(
                        // Speaker, always. This used to render a bare middle dot for
                        // optional turns, discarding `speaker` entirely — so 21 shipped
                        // turns showed " · " where the reader needed to know who speaks.
                        // Optionality is carried by the caption below, not by hiding the
                        // speaker: an unattributed line is not obviously yours to say.
                        if (turn.isYou) "you" else "them",
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
