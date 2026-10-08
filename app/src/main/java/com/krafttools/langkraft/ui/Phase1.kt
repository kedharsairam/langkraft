package com.krafttools.langkraft.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.krafttools.langkraft.data.Entry
import com.krafttools.langkraft.data.LanguageSpec
import com.krafttools.langkraft.data.SearchIndex
import com.krafttools.langkraft.data.ToneSet
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import com.kraft.ui.tokens.KraftSpacing
import com.krafttools.langkraft.ui.LangMetrics

/**
 * Phase 1 surfaces: tones, the failure flag, search, and the bookmark.
 *
 * They live together because each is small and they share the same three rules — no audio,
 * no exercises, no counters — and one file makes it obvious that none of them has quietly
 * broken one.
 */

// ---------------------------------------------------------------------------
// Tones
// ---------------------------------------------------------------------------

/**
 * A minimal contrast set, shown before any phrase.
 *
 * **The app cannot teach what a tone sounds like, and this does not pretend to.** There is
 * no audio by design and adding some would be worse than useless. What this shows is the
 * half that is genuinely available in writing: the tone mark is the *only* difference
 * between two words, and the two words mean different things. That is the fact which makes
 * a phrasebook unusable without it, and it is honest to present it as typography rather
 * than as pronunciation.
 *
 * Tone sets therefore render BEFORE phrases, not as an optional extra: a learner who
 * cannot see that ก้า and กา differ will misread every phrase they meet afterwards.
 */
@Composable
fun ToneSection(sets: List<ToneSet>, spec: LanguageSpec, onFlag: (ToneSet) -> Unit) {
    if (sets.isEmpty()) return
    DirectionProvider(spec) {
        Column(Modifier.fillMaxWidth()) {
            SectionLabel("Tones", "These marks change what every other word means.")
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            sets.forEach { set -> ToneSetCard(set, spec, onFlag) }
        }
    }
}

@Composable
private fun ToneSetCard(set: ToneSet, spec: LanguageSpec, onFlag: (ToneSet) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(KraftSpacing.Spacing16)) {
            set.syllable?.let {
                Text(
                    it,
                    fontFamily = scriptFontFamily(spec.scriptPrimary),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(KraftSpacing.Spacing8))
            set.variants.forEach { v ->
                Row(
                    Modifier.fillMaxWidth().padding(vertical = KraftSpacing.Spacing6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The tone number sits with the syllable, inside the weighted group.
                    // It used to be an unweighted sibling AFTER the gloss, so at 2.0x font
                    // scale the gloss took the remaining width first and the number was
                    // laid out at maxWidth 0 -- drawn invisibly. It is the one element
                    // here that survives being read aloud, so it must not be the one
                    // squeezed. The gloss moves below rather than competing.
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                v.textNative,
                                fontFamily = scriptFontFamily(spec.scriptPrimary),
                                style = MaterialTheme.typography.headlineMedium,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            // Number AND name. `toneName` was parsed and populated for
                            // every Thai variant and then never rendered, so the app
                            // asserted that tones matter while showing the reader a
                            // numeral. The name is what someone can actually hold on to.
                            Column(Modifier.padding(start = KraftSpacing.Spacing12)) {
                                Text(
                                    "${v.tone}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.tertiary,
                                )
                                v.toneName?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        Text(v.textEnglish, style = MaterialTheme.typography.bodyLarge)
                        v.textRomanized?.let {
                            // All four variants of a set romanize IDENTICALLY, which is
                            // the whole lesson. Rendering that at the same size as the
                            // gloss above it was the one choice that hid the finding.
                            Text(
                                it,
                                style = MaterialTheme.typography.titleMedium,
                                // Romanization is LATIN text -- RTGS, ISO, whatever the spec names -- so
                    // it wears the app's Latin face. Only the language's own script needs a
                    // bundled font, and giving romanization a serif made it look like a
                    // third language rather than a transcription of the second.
                    fontFamily = FontFamily.Default,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                v.textNote?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = KraftSpacing.Spacing4),
                    )
                }
            }
            FailureFlagButton(onClick = { onFlag(set) })
        }
    }
}

// ---------------------------------------------------------------------------
// The one input
// ---------------------------------------------------------------------------

/**
 * The "this failed me" flag.
 *
 * The only input the app is ever allowed to take, and the only one that changes anything
 * about how the product improves: a spec is a guess about what a traveller needs, made
 * without standing in the country. This turns that guess into something measured.
 *
 * **One tap, then an optional country.** No free text, no score, nothing counted on the
 * home screen. Tapping again is allowed and records a second observation, because
 * "this failed me twice in Morocco" is stronger evidence than either alone.
 */
@Composable
fun FailureFlagButton(onClick: () -> Unit, enabled: Boolean = true) {
    // Surface(onClick =) rather than Surface(...).clickable(). The latter left the touch
    // target at 16dp tall — uiautomator measured 248x48px on a 480dpi device — which is
    // a third of the 48dp minimum and genuinely hard to hit one-handed in a queue. The
    // onClick overload applies minimumInteractiveComponentSize and makes the handler
    // impossible to forget, because it is a required parameter rather than a modifier.
    Surface(
        onClick = onClick,
        enabled = enabled,
        // surfaceContainerHighest, not `surface`. `surface` IS the page background, so
        // every tag and flag button was a black hole punched through a grey card, with a
        // 4dp radius inside a 12dp one. It read as a rendering gap rather than a control.
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.padding(top = KraftSpacing.Spacing12),
    ) {
        Text(
            "This failed me",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = KraftSpacing.Spacing8, vertical = KraftSpacing.Spacing6),
        )
    }
}

/**
 * The optional second tap. A country turns a vague complaint into a spec correction that
 * points at a specific dialect or register call — "this failed me" cannot be acted on,
 * "this failed me in Morocco" can.
 */
@Composable
fun CountryPickerSheet(
    countries: List<String>,
    onPick: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    /**
     * Forced to LTR, because this dialog is chrome and the app's chrome is English.
     *
     * It is shown from inside a tier screen, which sets its layout direction from the SPEC's
     * language — correctly, for the phrase list. But the dialog's own words are English, and a
     * trailing `?` inside an RTL paragraph is reordered to the FRONT by the bidi algorithm. On an
     * Arabic screen the title rendered as "?Where did it fail", which reads as a typo in
     * a language the reader cannot check.
     *
     * Found by looking at the screen on the device rather than by reading the code, which looks
     * correct. The invariant is the same one the whole app holds — the direction belongs to the
     * content being read, never to the interface around it — and the interface around it is
     * always English.
     */
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Where did it fail?") },
        text = {
            LazyColumn(
                Modifier.heightIn(max = LangMetrics.FailureSheetMaxHeight),
                contentPadding = PaddingValues(vertical = KraftSpacing.Spacing4),
                verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing2),
            ) {
                item {
                    TextButton(onClick = { onPick(null) }, modifier = Modifier.fillMaxWidth()) {
                        Text("No country — just the phrase")
                    }
                }
                items(countries, key = { it }) { c ->
                    TextButton(onClick = { onPick(c) }, modifier = Modifier.fillMaxWidth()) {
                        Text(c, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    }
}

// ---------------------------------------------------------------------------
// Search
// ---------------------------------------------------------------------------

/**
 * Search across script, romanization and meaning at once.
 *
 * All three fields matter and dropping any of them makes search useless for a real
 * learner: you may know a word's sound (romanization), its shape (script), or its meaning
 * (English), and in practice you rarely know all three on the first attempt.
 *
 * This is the one screen in the app with a text field, and it is worth being explicit
 * about why that does not break the no-input rule: the product rule forbids the app
 * *asking* the learner to demonstrate knowledge. Typing a query to look something up is the
 * opposite — it is the learner getting help, not being tested. It is also the only way a
 * phrasebook is usable while standing in front of something.
 */
@Composable
fun SearchScreen(
    index: SearchIndex,
    spec: LanguageSpec,
    onFlag: (Entry, String?) -> Unit,
    onBack: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var flagTarget by remember { mutableStateOf<Entry?>(null) }

    val results = remember(query, spec.code) {
        if (query.isBlank()) emptyList() else index.search(query, lang = spec.code, limit = 30)
    }

    DirectionProvider(spec) {
        // A Scaffold, not a bare Column. `targetSdk 37` enforces edge-to-edge, so a screen
        // that does not go through Scaffold draws its first row under the status bar — and
        // the search field is unusable when it is there. Found by looking at the screen,
        // not by a test.
        Scaffold(topBar = {
            SearchBar(value = query, onValueChange = { query = it }, onBack = onBack)
        }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (query.isBlank()) {
                Text(
                    "Search by what you hear, what it looks like, or what it means.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(KraftSpacing.Spacing20),
                )
            } else if (results.isEmpty()) {
                Text(
                    "Nothing matches “$query”.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(KraftSpacing.Spacing20),
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(KraftSpacing.Spacing16),
                    verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing12),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    items(results, key = { it.entry.id }) { hit ->
                        Card(
                            modifier = Modifier.fillMaxWidth().widthIn(max = LangMetrics.CardMaxWidth),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                        ) {
                            Column(Modifier.padding(KraftSpacing.Spacing16)) {
                                // Same hierarchy as EntryCard: romanization is what the
                                // reader says aloud and carries the accent; the script is
                                // the check. This screen had it the other way round, so the
                                // same phrase was ranked differently on two screens of the
                                // same language.
                                hit.entry.textRomanized?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.titleLarge,
                                        // Romanization is LATIN text -- RTGS, ISO, whatever the spec names -- so
                    // it wears the app's Latin face. Only the language's own script needs a
                    // bundled font, and giving romanization a serif made it look like a
                    // third language rather than a transcription of the second.
                    fontFamily = FontFamily.Default,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Text(
                                    hit.entry.textNative,
                                    fontFamily = scriptFontFamily(spec.scriptPrimary),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                if (!spec.glossIsNative) {
                                    hit.entry.textEnglish?.let {
                                        Text(it, style = MaterialTheme.typography.bodyLarge)
                                    }
                                }
                                Text(
                                    "matched on ${hit.matchedOn.label}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                FailureFlagButton(onClick = { flagTarget = hit.entry })
                            }
                        }
                    }
                }
            }
        }
        }

        flagTarget?.let { target ->
            CountryPickerSheet(
                countries = remember { CountryList.ALL },
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
private fun SearchBar(value: String, onValueChange: (String) -> Unit, onBack: () -> Unit) {
    // A real back control, not a text glyph. The glyph version was drawn and completely
    // inert — the first defect a `uiautomator dump` ever found in this app.
    Row(
        Modifier
            .fillMaxWidth()
            // Scaffold insets its CONTENT, not a custom topBar. Material3's own TopAppBar
            // consumes the status-bar inset itself, which is why KraftTopBar never needed
            // this; a hand-rolled bar does, and without it the field sits under the clock.
            .statusBarsPadding()
            .padding(horizontal = KraftSpacing.Spacing8, vertical = KraftSpacing.Spacing8),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            // AutoMirrored, same as KraftTopBar: a hardcoded left arrow is wrong in the
            // RTL screens that come later. `Icons.AutoMirrored` is an extension property
            // and cannot be reached by a fully-qualified name, so it needs the import.
            Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            // A label, not just a placeholder: a placeholder is not the field's
            // accessible name, so TalkBack announced an unlabelled edit box. This is the
            // app's only text input.
            label = { Text("Search") },
            singleLine = true,
        )
    }
}