package com.krafttools.langkraft.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.krafttools.langkraft.data.ContentRepository
import com.krafttools.langkraft.data.Entry
import com.krafttools.langkraft.data.Exchange
import com.krafttools.langkraft.data.FailureFlag
import com.krafttools.langkraft.data.ToneSet

/**
 * The one input this app accepts, made visible.
 *
 * WHY THIS SCREEN EXISTS
 *
 * "This failed me" is the only thing a reader can do, and until now its only outlet was a JSON
 * file. That is a fine END of the pipeline and a poor beginning of one: the reader taps a phrase,
 * marks that it did not work in a country, and then has no way to see what they have marked —
 * no list, no count beyond the toolbar, no way to notice they have marked the same thing twice.
 *
 * So this is the reader's own list. Nothing here is scored, ranked or counted upward. There is no
 * total, no streak, no percentage and no comparison between languages, because the moment a flag
 * count becomes a number the reader is asked to raise, it stops being a report and becomes a
 * score, and a reader optimising a score produces noise rather than evidence. The count in the
 * toolbar is the number of things they chose to record, which is a different thing.
 *
 * READ-ONLY, LIKE EVERYTHING ELSE
 *
 * No unflag, no edit, no delete from here. `ProgressStore.clearFlags` exists for the spec-review
 * tool and is deliberately not wired to this screen. The flag is a record of something that
 * happened; letting the reader quietly remove it makes the record unreliable for whoever reviews
 * it later, and the reader cannot know whether they are the only person who will ever see it.
 *
 * WHERE THE COUNTRY GOES
 *
 * It is shown, and it is the most useful field here. "Wrong in Morocco" and "wrong in Iran" are
 * different reports about one phrase, and a regional variety error is invisible without the
 * country. It is optional in the input because the reader may not be standing in the country the
 * phrase failed in, and requiring it would mean recording a guess.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlagsScreen(
    corpus: ContentRepository.Corpus,
    flags: Map<String, List<FailureFlag>>,
    onBack: () -> Unit,
    onExport: () -> Unit,
) {
    val total = flags.values.sumOf { it.size }
    val languages = flags.keys.sorted()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (total == 0) "Flags" else "Flags ($total)") },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Back") }
                },
                actions = {
                    // Offered even when there is nothing to export, because a reader who has just
                    // found the button should not have to fail to find it a second time.
                    TextButton(onClick = onExport) { Text("Export") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        if (total == 0) {
            EmptyFlags(Modifier.padding(padding).fillMaxSize())
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Phrases you marked as not working. These are notes, not a score — " +
                        "there is no total to beat and nothing is compared between languages.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
            }

            for (lang in languages) {
                val spec = corpus.spec(lang)
                val languageName = spec?.name ?: lang
                item(key = "header-$lang") {
                    Text(
                        languageName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }

                items(
                    items = flags[lang].orEmpty(),
                    key = { "${it.entryId}-${it.at}" },
                ) { flag ->
                    val resolved = resolveTarget(corpus, flag.entryId)
                    FlagRow(
                        flag = flag,
                        entry = resolved as? Entry,
                        scriptPrimary = spec?.scriptPrimary.orEmpty(),
                        label = when (resolved) {
                            is Entry -> null
                            is ToneSet -> "Tone set ${resolved.id} (${resolved.syllable ?: "no syllable given"})"
                            is Exchange -> "Exchange ${resolved.id}: ${resolved.scenario}"
                            else -> null
                        },
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            item {
                Spacer(Modifier.height(24.dp))
                Text(
                    "Export writes all of these to a file, with the phrase, the country and the " +
                        "date, so they can be sent on for the content to be revised.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/**
 * Resolves a stored flag target to whatever it points at, or null if it points at nothing.
 *
 * A flag target is NAMESPACED — `entry:ara-t0-0001`, `tone:tha-tone-1`, `exchange:eng-x0012` —
 * because the reader can mark an entry, a tone set or a whole exchange, and one flat column of
 * ids cannot tell them apart.
 *
 * The flags screen looked the raw stored string up in the entry list, so every flag resolved to
 * nothing and the screen reported "This entry is no longer in the app" for an entry that was
 * plainly on the previous screen. A confident wrong answer in the one place the reader is looking
 * for the truth about their own reports, so the namespace is stripped here and each kind is
 * resolved against the corpus it belongs to.
 */
private fun resolveTarget(corpus: ContentRepository.Corpus, target: String): Any? {
    val sep = target.indexOf(':')
    if (sep <= 0) return corpus.entry(target)
    val kind = target.substring(0, sep)
    val id = target.substring(sep + 1)
    return when (kind) {
        "entry" -> corpus.entry(id)
        "tone" -> corpus.toneSets.firstOrNull { it.id == id }
        "exchange" -> corpus.exchanges.firstOrNull { it.id == id }
        // An unrecognised namespace is not an error; it is a flag written by a build that knew
        // about a kind this one does not. Returning null keeps it visible rather than dropping it.
        else -> null
    }
}

@Composable
private fun FlagRow(
    flag: FailureFlag,
    entry: Entry?,
    scriptPrimary: String,
    label: String? = null,
) {
    Column(Modifier.padding(vertical = 12.dp)) {
        if (entry != null) {
            Text(
                entry.textNative,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = scriptFontFamily(scriptPrimary),
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (entry.textRomanized != null) {
                Text(
                    entry.textRomanized,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // Null only where the spec sets gloss_mode: same_as_native. Shown conditionally
            // rather than as a blank line, because an empty row where a caption should be reads
            // as a rendering fault rather than as a deliberate choice in the spec.
            entry.textEnglish?.let { gloss ->
                Text(
                    gloss,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else if (label != null) {
            // A tone set or an exchange, not an entry. Flagged for the same reason and rendered
            // with its own shape rather than squeezed into the entry layout.
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        } else {
            // The flag genuinely points at nothing that is in the app now. Possible when a spec is
            // revised and a phrase is removed between the reader marking it and opening this list.
            // Shown rather than hidden: a flag pointing at nothing is a gap in the content, and
            // the bare id is how that becomes visible instead of silent.
            Text(
                flag.entryId,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                "This is no longer in the app. The flag has been kept.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                // A date with no country is a real report — "wrong somewhere I was" — so the
                // two are shown as separate fields rather than composed into one string that
                // would have to guess at punctuation when one of them is absent.
                flag.country ?: "country not recorded",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "  ·  ${flag.at.take(10)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The script to render a flagged phrase in.
 *
 * Taken from the SPEC, not sniffed from the phrase's own codepoints. The first version of this
 * file enumerated Unicode ranges by hand — `0x0600..0x06FF` for Arabic and so on — which is
 * precisely the mistake `pipeline/harvest/reading.mjs` was rewritten to eliminate, and the one
 * that silently omitted Tamil there. The spec already records the script, it is the same value
 * the rest of the app renders with, and a phrase whose spec has been removed falls back to
 * `scriptFontFamily("")`, which returns the default family rather than guessing.
 */
@Composable
private fun EmptyFlags(modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp),
        ) {
            Text(
                "Nothing marked yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "When a phrase does not work — the person did not understand you, or you were " +
                    "not understood — you can mark it from the phrase itself. Add the country " +
                    "if you know it: \"wrong in Morocco\" and \"wrong in Iran\" are different " +
                    "reports about the same phrase.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
