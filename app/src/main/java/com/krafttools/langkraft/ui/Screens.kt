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
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.drop
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krafttools.langkraft.data.ContentRepository
import com.krafttools.langkraft.data.Entry
import com.krafttools.langkraft.data.Exchange
import com.krafttools.langkraft.data.LanguageSpec
import com.krafttools.langkraft.data.ProgressStore

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
    positions: Map<String, List<ProgressStore.Position>>,
    onOpen: (String) -> Unit,
    onCredits: () -> Unit = {},
    /**
     * Export the failure flags.
     *
     * Offered unconditionally rather than only once flags exist, because a reader who has
     * just discovered the button should not have to fail to find it. It states the count
     * when it opens.
     */
    onExportFlags: () -> Unit = {},
    flagCount: Int = 0,
) {
    Scaffold(
        topBar = {
            // No back destination on the root screen, so this is the one bar that is not
            // KraftTopBar — and it takes no handler by design, because there is nothing
            // to go back to.
            TopAppBar(
                title = { Text("LangKraft") },
                // Credits lives here, in the chrome, rather than three taps deep. The app
                // redistributes CC BY-SA content and two OFL font families and both
                // licences require the credit to be findable; burying it would not
                // discharge the obligation.
                actions = {
                    TextButton(onClick = onCredits) { Text("Credits") }
                    TextButton(onClick = onExportFlags) {
                        Text(if (flagCount == 0) "Flags" else "Flags ($flagCount)")
                    }
                },
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
            item {
                Text(
                    "Tapping “this failed me” on any phrase records it, and nothing else. " +
                        "Those records can be exported and used to correct the phrases " +
                        "themselves — which is the only way a book written by someone who " +
                        "has never stood in the country gets fixed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                )
            }
            items(corpus.specs, key = { it.code }) { spec ->
                LanguageCard(
                    spec = spec,
                    entriesFor = { code, t -> corpus.entriesFor(code, t) },
                    // Which TIER they were last in, by recency. Not by index: index 3 is
                    // the 3rd exchange in one tier and the 2nd phrase in another, so
                    // comparing raw indices across tiers is meaningless. `updated_at` was
                    // written on every save and read by nothing, which is why this had
                    // degraded into a boolean that could not keep its promise.
                    bookmark = positions[spec.code]?.maxByOrNull { it.updatedAt }
                        ?.takeIf { it.itemIndex > 0 }?.tier,
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
            // The language's OWN name, in its own script. This was dead code: every spec
            // declares an endonym, the model parses it, and the tier screen's
            // `else spec.endonym` branch could never run because `defaultVariety` is
            // always populated. So Kiswahili, தமிழ் and ไทย were carried in memory and
            // never shown anywhere. A language app that does not show the language's own
            // name is missing the one piece of texture only a language app can offer.
            if (spec.endonym.isNotBlank() && spec.endonym != spec.name) {
                Text(
                    spec.endonym,
                    fontFamily = scriptFontFamily(spec.scriptPrimary),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
            // The bookmark. Position only: no percentage, no accuracy, no "12 of 48 read",
            // because those claim something about the learner's attention that the app
            // cannot know. What it DOES say is where, because a promise of "continue"
            // that does not continue is worse than no promise at all.
            bookmark?.takeIf { it > 0 }?.let { tier ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "Resume in ${spec.tiers.firstOrNull { it.id == tier }?.name ?: "tier $tier"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (spec.role == "calibration") {
                Spacer(Modifier.height(4.dp))
                Text(
                    "English — what the other three are measured against.",
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
    positions: Map<String, List<ProgressStore.Position>>,
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
                    // The endonym leads, because a language's own name for itself is the
                    // most useful thing on this screen. `th-TH · LTR` was here instead: a
                    // raw BCP-47 tag and a direction acronym, both developer vocabulary,
                    // occupying the most prominent slot above the actual content. Direction
                    // is still stated, but only when it is the unusual case.
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            spec.endonym.ifBlank { spec.name },
                            fontFamily = scriptFontFamily(spec.scriptPrimary),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            buildString {
                                if (spec.defaultVariety.isNotBlank()) append(spec.defaultVariety)
                                if (spec.scriptDirection == "rtl") append("  ·  right to left")
                            }.trim(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // Only tiers that HAVE content are rendered as things you can press.
                // Three of the four tier cards were tappable, rippled, said "0 of 150
                // authored" in pipeline vocabulary, and led to a screen containing one
                // line of grey text. That is a retry affordance that cannot succeed,
                // three times over, on the main navigation — the exact thing the app's
                // own failure screen refuses to do on principle.
                //
                // The tiers still exist. They appear below, unpressable and unadorned,
                // which reads as editorial scope rather than as a fault or a promise.
                val populated = spec.tiers.filter { tierHasContent(corpus, spec.code, it.id) }
                val empty = spec.tiers.filterNot { tierHasContent(corpus, spec.code, it.id) }

                items(populated, key = { it.id }) { tier ->
                    TierCard(
                        number = tier.id,
                        name = tier.name,
                        intent = tier.intent,
                        available = corpus.entriesFor(spec.code, tier.id).size,
                        certainty = tier.certainty,
                        bookmark = positions[spec.code]?.firstOrNull { it.tier == tier.id }
                        ?.takeIf { it.itemIndex > 0 }?.itemIndex,
                        onClick = { onOpenTier(tier.id) },
                    )
                }

                // Varieties. Every spec ships them -- English four, Thai three including
                // Isan and the far South -- and they were parsed into `Variant` objects and
                // never displayed, so a learner in rural Isan had no way to learn the app
                // knows the difference exists.
                //
                // Honest about what this does: the CONTENT is central-variety only. Choosing
                // one does not swap any phrase. It records which variety the reader is
                // travelling in, which is what the per-variety notes below are about, and it
                // is the hook that per-variety content would attach to. It says so on screen
                // rather than implying a switch that is not there.
                if (spec.variants.size > 1) {
                    item {
                        Column(Modifier.padding(top = 12.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Varieties",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Every phrase here is standard central ${spec.name}. " +
                                    "These are the other varieties you may hear, and where " +
                                    "they differ.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(8.dp))
                            spec.variants.forEach { v ->
                                val chosen = v.id == spec.defaultVariety
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                                    verticalAlignment = Alignment.Top,
                                ) {
                                    Text(
                                        if (chosen) "\u25CF " else "\u25CB ",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (chosen) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Column {
                                        Text(
                                            v.label,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (chosen) MaterialTheme.colorScheme.primary
                                                    else MaterialTheme.colorScheme.onSurface,
                                        )
                                        Text(
                                            v.id,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        v.note?.let {
                                            Text(
                                                it,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (empty.isNotEmpty()) {
                    item {
                        Column(Modifier.padding(top = 12.dp)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Not written yet",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            empty.forEach { tier ->
                                Text(
                                    "${tier.name} — ${tier.intent.trim()}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 3.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A domain heading inside the phrase list.
 *
 * A rule plus a name and a count. Not a button, not a collapsible: a traveller looking for
 * "how much?" should be able to see where money phrases end and directions begin, and
 * nothing more is asked of them.
 */
@Composable
private fun DomainDivider(domain: Domain, count: Int) {
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                domain.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Text(
                "$count",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Position within the phrase list, not within the tier.
 *
 * `firstVisibleItemIndex` counts every composed item: the tone sets, the fourteen
 * exchanges, their two section headers, and then the phrases. Reading it raw told a reader
 * arriving at the first phrase that they were at "20 / 63", which is wrong and alarming.
 * The header's own index is the offset, and it is computed from the same three conditionals
 * that build the list, so the two cannot drift.
 */
private fun phrasePosition(
    listState: androidx.compose.foundation.lazy.LazyListState,
    phrasesStartIndex: Int,
    total: Int,
): String {
    val withinList = (listState.firstVisibleItemIndex - phrasesStartIndex + 1).coerceAtLeast(1)
    return "${withinList.coerceAtMost(total)} / $total"
}

/** Whether a tier has anything a reader could open. Tone sets count: they are content. */
private fun tierHasContent(corpus: ContentRepository.Corpus, code: String, tier: Int): Boolean =
    corpus.entriesFor(code, tier).isNotEmpty() ||
        corpus.exchangesFor(code, tier).isNotEmpty() ||
        corpus.toneSetsFor(code, tier).isNotEmpty()

@Composable
private fun TierCard(
    number: Int, name: String, intent: String,
    available: Int, certainty: String, bookmark: Int?, onClick: () -> Unit,
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
            // A plain count. It used to read "48 of 48 authored", where "authored" is
            // pipeline vocabulary and the ratio reads as a shortfall to a reader who has
            // never heard of a spec. Now that only populated tiers are pressable, the
            // count can only ever be a real number, so it is stated as one.
            Text(
                "$available ${if (available == 1) "phrase" else "phrases"}",
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
    onCopy: (Entry) -> Unit = {},
    onBack: () -> Unit,
) {
    // The second tap. `FailureFlagButton` documents "One tap, then an optional country",
    // and on this screen that never happened: three call sites passed a hardcoded null
    // and no picker was ever opened, so the documented flow existed on exactly one of the
    // four surfaces that render the button.
    var flagTarget by remember { mutableStateOf<String?>(null) }
    val countries = remember { CountryList.ALL }
    val requestFlag: (String) -> Unit = { id -> flagTarget = id }

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
    val toneBlockItems = if (corpus.toneSetsFor(spec.code, tier).isNotEmpty()) 2 else 0
    val exchangeBlockItems = if (exchanges.isNotEmpty()) 2 + exchanges.size else 0
    /** Index of the "Phrases" header: everything above it, plus the header itself. */
    val phrasesStartIndex = toneBlockItems + exchangeBlockItems
    val totalItems = buildList {
        add(toneBlockItems)
        add(exchangeBlockItems)
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

    // Whether to offer the way back up. It appears only once the reader is far enough from
    // the top to want it, because a permanent control on a scrolling reference is clutter
    // the reader pays for on every screen where they did not mean to press it.
    val showBackToTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 8 }
    }
    val listScope = rememberCoroutineScope()

    DirectionProvider(spec) {
        Scaffold(
            topBar = { KraftTopBar(title = spec.name, subtitle = TierId.of(tier).title, onBack = onBack) },
            floatingActionButton = {
                // A 63-entry Thai tier is roughly twenty-eight screens with no other way
                // back, and the bookmark can drop the reader into the middle of it.
                if (showBackToTop) {
                    FloatingActionButton(
                        onClick = { listScope.launch { listState.animateScrollToItem(0) } },
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.primary,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowUp,
                            contentDescription = "Back to the top",
                        )
                    }
                }
            },
        ) { padding ->
            LazyColumn(
                state = listState,
                // Makes the bookmark restore visible. A returning reader is dropped into
                // the middle of a 28-screen list with no indication it happened; one
                // animation is the difference between "where am I" and "oh, this is where I
                // was". Nothing else in this app animates.
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
                            onFlag = { requestFlag("tone:" + it.id) },
                        )
                    }
                    item { HorizontalDivider() }
                }
                if (exchanges.isNotEmpty()) {
                    item {
                        SectionLabel("Exchanges", "Half of every conversation is what they say to you.")
                    }
                    items(exchanges, key = { it.id }) { ex ->
                        ExchangeCard(ex, spec, onFlag = { requestFlag("exchange:" + ex.id) })
                    }
                    item { HorizontalDivider() }
                }
                if (entries.isNotEmpty()) {
                    item(key = "phrases-header") {
                        SectionLabel(
                            "Phrases",
                            "${entries.size} items",
                            position = phrasePosition(listState, phrasesStartIndex, entries.size),
                        )
                    }
                    // Grouped by domain, with the domain's own label as a divider.
                    //
                    // Every entry already carried a domain number and the enum has carried
                    // a human label for each of the twelve since the beginning. None of it
                    // was rendered, so 63 Thai entries interleaved "Hello" with "I have
                    // lost my wallet" with nothing between them and the reader scrolled
                    // blind. The data was always there; only the grouping was missing.
                    val grouped = entries.groupBy { Domain.of(it.domain) }
                        .toList()
                        .sortedBy { it.first.number }
                    grouped.forEach { (domain, inDomain) ->
                        item(key = "domain-${domain.number}") {
                            DomainDivider(domain, inDomain.size)
                        }
                        items(inDomain, key = { it.id }) { e ->
                            // animateItem lives on LazyItemScope, so it is applied to the
                            // item's own content rather than to the list. It is what makes
                            // the bookmark restore legible: without it a returning reader
                            // is teleported into the middle of a 28-screen list with no
                            // indication that happened. Nothing else here animates.
                            EntryCard(
                                entry = e,
                                spec = spec,
                                modifier = Modifier.animateItem(),
                                onFlag = { requestFlag("entry:" + e.id) },
                                onCopy = { onCopy(e) },
                            )
                        }
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

        /**
         * The country sheet is a SIBLING of the list, not an item in it.
         *
         * It was authored as `LazyColumn { item { CountryPickerSheet(...) } }`, which does not
         * work: a modal bottom sheet composes in its own window over the content, and putting it
         * inside a lazy list lays it out inline as an ordinary row. The visible effect was that
         * "This failed me" set the flag target, nothing appeared, and the app's only permitted
         * input could not be completed at all. Verified on the device — tapping the button changed
         * nothing on screen.
         *
         * Hoisted here so it overlays the list, which is what a modal sheet is for.
         */
        flagTarget?.let { target ->
            CountryPickerSheet(
                countries = countries,
                onPick = { country ->
                    onFlag(target, country)
                    flagTarget = null
                },
                onDismiss = { flagTarget = null },
            )
        }
    }
}

@Composable
fun SectionLabel(title: String, sub: String, position: String? = null) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            // POSITION, not progress. The app's rule is that progress means where you are
            // and never how well you did, so this is `firstVisibleItemIndex / itemCount`
            // and nothing more: no percentage sign, no bar, no ring, no completion state,
            // no colour change when the list ends.
            //
            // It was absent, and the absence was over-applied. "12 of 48" where 12 is the
            // first visible item makes no claim about attention or comprehension — it is
            // the one number every dictionary and phrasebook in existence shows, and its
            // absence made a working reader feel lost in a 28-screen list.
            position?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
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
fun EntryCard(
    entry: Entry,
    spec: LanguageSpec,
    modifier: Modifier = Modifier,
    onFlag: () -> Unit = {},
    /**
     * Copy the phrase to the clipboard.
     *
     * **A judgement call worth recording.** The product rule says read-only, and the
     * strictest reading forbids any action that leaves the app. Copying does not: it is a
     * read, it involves no network, no permission, and no typing. And it is the single
     * action a phrasebook gets used for -- showing the screen to somebody who does not
     * read the script. Refusing to let a user hand someone the phrase would be a strange
     * place to be strict.
     *
     * What it does NOT do is speak, score, track or sync. Nothing about it is gamified,
     * because there is nothing to gamify.
     */
    onCopy: () -> Unit = {},
) {
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
                fontFamily = scriptFontFamily(spec.scriptPrimary),
                fontSize = if (entry.textRomanized != null) 20.sp else 26.sp,
                lineHeight = if (entry.textRomanized != null) 30.sp else 34.sp,
            )

            entry.textRomanized?.let {
                Spacer(Modifier.height(4.dp))
                Text(
                    it,
                    // Romanization is LATIN text -- RTGS, ISO, whatever the spec names -- so
                    // it wears the app's Latin face. Only the language's own script needs a
                    // bundled font, and giving romanization a serif made it look like a
                    // third language rather than a transcription of the second.
                    fontFamily = FontFamily.Default,
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FailureFlagButton(onClick = onFlag)
                CopyButton(onClick = onCopy)
            }
        }
    }
}

/** Copies the native script, the romanization and the gloss, so any one is usable alone. */
@Composable
private fun CopyButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(top = 12.dp),
    ) {
        Text(
            "Copy",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
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
                            fontFamily = scriptFontFamily(spec.scriptPrimary),
                            fontSize = 20.sp,
                            color = tone,
                        )
                        turn.textRomanized?.let {
                            Text(it, fontFamily = FontFamily.Default, fontSize = 20.sp,
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
        // surfaceContainerHighest rather than `surface`. `surface` IS the page
        // background, so every tag was a black hole punched through a grey card. The
        // radius also matched the card's, which made the hole look like a crop.
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
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
