package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.data.UserStateRepository
import com.kedharsairam.langkraft.domain.Phrase
import com.kedharsairam.langkraft.presentation.theme.PhraseSizes
import com.kedharsairam.langkraft.presentation.theme.Spacing
import com.kedharsairam.langkraft.presentation.viewmodels.LanguageUiState
import com.kedharsairam.langkraft.presentation.viewmodels.LanguageViewModel
import kotlinx.coroutines.launch

val ThemeNames = mapOf(
    1 to "Greetings", 2 to "Politeness", 3 to "Numbers & money", 4 to "Food",
    5 to "Directions", 6 to "Shopping", 7 to "Emergency", 8 to "Connection"
)

@Composable
fun LanguageScreen(code: String, onBack: () -> Unit, vm: LanguageViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    LaunchedEffect(code) { vm.load(code) }
    Column(modifier = Modifier.padding(Spacing.Screen)) {
        TextButton(onClick = onBack, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            Spacer(Modifier.width(Spacing.S))
            Text("Languages")
        }
        Text(code.uppercase(), style = MaterialTheme.typography.headlineSmall)
        when (val s = state) {
            LanguageUiState.Loading -> Text("Loading…", Modifier.padding(top = Spacing.M))
            is LanguageUiState.Empty -> Text("No phrases yet for this language. Bench languages arrive as data later.", modifier = Modifier.padding(top = Spacing.M))
            is LanguageUiState.Failed -> Text("Could not load phrases (${s.reason::class.simpleName}).", modifier = Modifier.padding(top = Spacing.M), color = MaterialTheme.colorScheme.error)
            is LanguageUiState.Ready -> {
                s.sound?.let { sc ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.S)) {
                        Column(Modifier.padding(Spacing.M)) {
                            Text("Sound check", style = MaterialTheme.typography.titleMedium)
                            Text("New sounds: ${sc.newSounds}", style = MaterialTheme.typography.bodySmall)
                            Text("Rhythm: ${sc.rhythm}", style = MaterialTheme.typography.bodySmall)
                            Text("Learn first: ${sc.registerFirst}", style = MaterialTheme.typography.bodySmall)
                            Text("Trap: ${sc.trap}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    (1..8).forEach { t ->
                        FilterChip(
                            selected = s.theme == t,
                            onClick = { vm.setTheme(t) },
                            label = { Text(ThemeNames[t] ?: t.toString()) }
                        )
                    }
                }
                OutlinedTextField(
                    value = s.query, onValueChange = { vm.setQuery(it) },
                    label = { Text("Search English") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.S),
                    shape = MaterialTheme.shapes.medium
                )
                val visible = vm.visible(s)
                if (visible.isEmpty()) {
                    Text("No phrases match.", modifier = Modifier.padding(top = Spacing.M))
                } else {
                    val idx = s.index.coerceIn(0, visible.lastIndex)
                    val phrase = visible[idx]
                    PhraseCard(phrase = phrase, lang = code, isStarred = phrase.id in s.starred, onToggleStar = { vm.toggleStar(phrase.id) })
                    Row(modifier = Modifier.padding(top = Spacing.M), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = { vm.prev() }, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) { Text("Prev") }
                        Text("${idx + 1} / ${visible.size}", style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { vm.next() }, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) { Text("Next") }
                    }
                }
            }
        }
    }
}

@Composable
fun PhraseCard(phrase: Phrase, lang: String, isStarred: Boolean, onToggleStar: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { UserStateRepository(ctx) }
    val scope = rememberCoroutineScope()
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(Spacing.Card)) {
            Text(phrase.english, style = MaterialTheme.typography.titleLarge, fontSize = PhraseSizes.English)
            var hidden by remember(phrase.id) { mutableStateOf(false) }
            if (!hidden) {
                Text("You say:", style = MaterialTheme.typography.labelLarge)
                Text(phrase.youSay, style = MaterialTheme.typography.headlineMedium, fontSize = PhraseSizes.YouSay)
                Text(phrase.soundTip, style = MaterialTheme.typography.bodySmall)
                Text(phrase.note, style = MaterialTheme.typography.bodySmall)
            } else {
                Text("Hidden — say it aloud.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = Spacing.M))
            }
            Spacer(Modifier.height(Spacing.S))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Button(onClick = { hidden = !hidden }, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) {
                    Text(if (hidden) "Reveal" else "Hide")
                }
                OutlinedButton(onClick = onToggleStar, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) {
                    Icon(if (isStarred) Icons.Filled.Star else Icons.Filled.StarBorder, contentDescription = if (isStarred) "Unstar" else "Mark hard")
                    Spacer(Modifier.width(Spacing.S))
                    Text(if (isStarred) "Hard" else "Mark hard")
                }
            }
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
            Text("Source: ${phrase.source} • Checked ${phrase.checked}", style = MaterialTheme.typography.labelMedium, fontSize = PhraseSizes.Meta)
        }
    }
}
