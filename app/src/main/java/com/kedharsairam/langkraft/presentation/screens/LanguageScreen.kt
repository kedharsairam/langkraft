package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.data.UserStateRepository
import com.kedharsairam.langkraft.domain.Phrase
import com.kedharsairam.langkraft.presentation.theme.PhraseSizes
import com.kedharsairam.langkraft.presentation.theme.Spacing
import com.kedharsairam.langkraft.presentation.viewmodels.LanguageUiState
import com.kedharsairam.langkraft.presentation.viewmodels.LanguageViewModel
import kotlinx.coroutines.launch

val ThemeNames = mapOf(
    1 to "Greetings", 2 to "Politeness", 3 to "Numbers", 4 to "Food",
    5 to "Directions", 6 to "Shopping", 7 to "Emergency", 8 to "Connection"
)

@Composable
fun LanguageScreen(code: String, onBack: () -> Unit, vm: LanguageViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    LaunchedEffect(code) { vm.load(code) }
    Column(modifier = Modifier.padding(horizontal = Spacing.Screen)) {
        Spacer(Modifier.height(Spacing.S))
        TextButton(
            onClick = onBack,
            modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget),
            shape = MaterialTheme.shapes.extraSmall,
            contentPadding = PaddingValues(horizontal = 0.dp)
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            Spacer(Modifier.width(Spacing.S))
            Text("Languages")
        }
        Text(code.uppercase(), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Spacing.S))
        when (val s = state) {
            LanguageUiState.Loading -> Text("Loading…", Modifier.padding(top = Spacing.M), style = MaterialTheme.typography.bodyMedium)
            is LanguageUiState.Empty -> Text("No phrases yet for this language. Bench languages arrive as data later.", Modifier.padding(top = Spacing.M), style = MaterialTheme.typography.bodyMedium)
            is LanguageUiState.Failed -> Text("Could not load phrases.", Modifier.padding(top = Spacing.M), color = MaterialTheme.colorScheme.error)
            is LanguageUiState.Ready -> {
                s.sound?.let { sc ->
                    Column(modifier = Modifier.padding(vertical = Spacing.S)) {
                        Text("SOUND CHECK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("New sounds: ${sc.newSounds}", style = MaterialTheme.typography.bodySmall)
                        Text("Rhythm: ${sc.rhythm}", style = MaterialTheme.typography.bodySmall)
                        Text("Learn first: ${sc.registerFirst}", style = MaterialTheme.typography.bodySmall)
                        Text("Trap: ${sc.trap}", style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(thickness = Spacing.Hairline, color = MaterialTheme.colorScheme.outline, modifier = Modifier.padding(vertical = Spacing.S))
                    }
                }
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S)
                ) {
                    (1..8).forEach { t ->
                        FilterChip(
                            selected = s.theme == t,
                            onClick = { vm.setTheme(t) },
                            label = { Text(ThemeNames[t] ?: t.toString()) },
                            shape = MaterialTheme.shapes.small
                        )
                    }
                }
                OutlinedTextField(
                    value = s.query, onValueChange = { vm.setQuery(it) },
                    label = { Text("Search") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.S),
                    shape = MaterialTheme.shapes.medium
                )
                val visible = vm.visible(s)
                if (visible.isEmpty()) {
                    Text("No phrases match.", Modifier.padding(top = Spacing.M))
                } else {
                    val idx = s.index.coerceIn(0, visible.lastIndex)
                    val phrase = visible[idx]
                    PhraseStage(phrase = phrase, lang = code, isStarred = phrase.id in s.starred, onToggleStar = { vm.toggleStar(phrase.id) })
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = Spacing.M),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { vm.prev() }, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) { Text("‹ Prev") }
                        Text("${idx + 1} / ${visible.size}", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { vm.next() }, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) { Text("Next ›") }
                    }
                }
            }
        }
    }
}

@Composable
fun PhraseStage(phrase: Phrase, lang: String, isStarred: Boolean, onToggleStar: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { UserStateRepository(ctx) }
    val scope = rememberCoroutineScope()
    var hidden by remember(phrase.id) { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(phrase.english, style = MaterialTheme.typography.titleLarge, fontSize = PhraseSizes.English)
            IconButton(onClick = onToggleStar, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) {
                Icon(
                    if (isStarred) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isStarred) "Unstar" else "Mark hard",
                    tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(Spacing.M))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 120.dp)
                .clickable(role = Role.Button) { hidden = !hidden }
                .semantics { contentDescription = if (hidden) "Reveal You say" else "Hide You say" }
                .padding(vertical = Spacing.S)
        ) {
            if (!hidden) {
                Text("You say", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(phrase.youSay, style = MaterialTheme.typography.displayLarge, fontSize = PhraseSizes.YouSay)
                Text(phrase.soundTip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(phrase.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Hidden — say it aloud. Tap to reveal.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(thickness = Spacing.Hairline, color = MaterialTheme.colorScheme.outline)
        var noteText by remember(phrase.id) { mutableStateOf("") }
        LaunchedEffect(phrase.id) { repo.note(lang, phrase.id).collect { noteText = it } }
        OutlinedTextField(
            value = noteText, onValueChange = { noteText = it },
            label = { Text("Private note") },
            modifier = Modifier.fillMaxWidth().padding(top = Spacing.S)
        )
        TextButton(
            onClick = { scope.launch { repo.saveNote(lang, phrase.id, noteText) } },
            modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)
        ) { Text("Save note") }
        Text("Source: ${phrase.source} • Checked ${phrase.checked}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
