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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.krafttools.langkraft.data.Credit
import com.krafttools.langkraft.data.LanguageSpec
import com.kraft.ui.tokens.KraftSpacing

/**
 * Credits and licences.
 *
 * **This screen exists because of a licence obligation, not because it looked nice.**
 *
 * The app redistributes material it does not own: Thai and Swahili phrasing from
 * Wikivoyage under CC BY-SA 4.0, cross-checked against Tatoeba under CC BY 2.0 FR, and
 * two Noto script families under the SIL Open Font License 1.1. Both CC and the OFL require
 * the licence to travel with the work. Until this screen existed, every spec carried a
 * correctly-worded `attribution` block that went nowhere, and the OFL obligation was
 * created by bundling fonts in the first place.
 *
 * Per-language attribution is read from the spec, so adding a language discharges its own
 * obligations without anyone remembering to edit a second file.
 *
 * **No URLs, deliberately.** The product rules forbid external links and the app has no
 * network permission. Naming the author and the licence is what the licences actually
 * require; a tappable link is not.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreditsScreen(
    appCredits: List<Credit>,
    specs: List<LanguageSpec>,
    onBack: () -> Unit,
) {
    val languageCredits: List<Pair<LanguageSpec, List<Credit>>> = remember(specs) {
        specs.mapNotNull { spec ->
            spec.attribution
                .takeIf { it.isNotEmpty() }
                ?.let { spec to it.map { a -> Credit(a.source, a.licence, a.authorCredit) } }
        }
    }

    Scaffold(
        topBar = { KraftTopBar(title = "Credits", onBack = onBack) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(KraftSpacing.Spacing20),
            verticalArrangement = Arrangement.spacedBy(KraftSpacing.Spacing12),
        ) {
            item {
                Text(
                    "Everything this app shows was written or licensed by someone. " +
                        "None of it was scraped.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }

            item { SectionHeading("The app") }
            items(appCredits) { credit -> CreditBlock(credit) }

            if (languageCredits.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(KraftSpacing.Spacing8))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(Modifier.height(KraftSpacing.Spacing8))
                    SectionHeading("Content")
                }
                items(languageCredits) { (spec, credits) ->
                    Column {
                        Text(
                            spec.endonym.ifBlank { spec.name },
                            fontFamily = scriptFontFamily(spec.scriptPrimary),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onBackground,
                        )
                        // English's endonym IS its name; printing both showed "English"
                        // twice in a row.
                        if (spec.endonym != spec.name) {
                            Text(
                                spec.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.height(KraftSpacing.Spacing8))
                        credits.forEach { CreditBlock(it) }
                        Spacer(Modifier.height(KraftSpacing.Spacing8))
                    }
                }
            }

            item {
                Spacer(Modifier.height(KraftSpacing.Spacing8))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(KraftSpacing.Spacing12))
                Text(
                    "Font licences require the full licence text to travel with the font. " +
                        "It is bundled in the APK at res/raw/noto_licence.txt, and every " +
                        "family\'s source URL is listed beside it in noto_attribution.json.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(KraftSpacing.Spacing12))
                Text(
                    "LangKraft has no network permission. Nothing it displays was fetched, " +
                        "and nothing you do in it leaves the device.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = KraftSpacing.Spacing8),
    )
}

@Composable
private fun CreditBlock(credit: Credit) {
    Column(Modifier.padding(vertical = KraftSpacing.Spacing4)) {
        Text(
            credit.source,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            credit.licence,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Default,
            color = MaterialTheme.colorScheme.primary,
        )
        credit.authorCredit?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        credit.note?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}