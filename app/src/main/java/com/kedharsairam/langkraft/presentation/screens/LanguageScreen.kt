package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.data.UserStateRepository
import com.kedharsairam.langkraft.domain.Phrase
import com.kedharsairam.langkraft.presentation.theme.PhraseSizes
import com.kedharsairam.langkraft.presentation.theme.Spacing
import com.kedharsairam.langkraft.presentation.viewmodels.LanguageUiState
import com.kedharsairam.langkraft.presentation.viewmodels.LanguageViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val ThemeNames = mapOf(
    1 to "Greetings", 2 to "Politeness", 3 to "Numbers", 4 to "Food",
    5 to "Directions", 6 to "Shopping", 7 to "Emergency", 8 to "Connection"
)

@Composable
fun LanguageScreen(
    code: String,
    onBack: () -> Unit,
    onNumbers: (String) -> Unit,
    vm: LanguageViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(code) { vm.load(code) }
    Column(modifier = Modifier.padding(horizontal = Spacing.Screen)) {
        Spacer(Modifier.height(Spacing.S))
        TextButton(onClick = onBack, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            Spacer(Modifier.width(Spacing.S))
            Text("Languages")
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(code.uppercase(), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { onNumbers(code) }, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) { Text("Numbers") }
        }
        when (val s = state) {
            LanguageUiState.Loading -> CircularProgressIndicator(Modifier.padding(top = Spacing.M))
            is LanguageUiState.Empty -> Text("No phrases yet for this language. Bench languages arrive as data later.", Modifier.padding(top = Spacing.M))
            is LanguageUiState.Failed -> Column {
                Text("Could not load phrases.", Modifier.padding(top = Spacing.M), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { vm.load(code) }) { Text("Retry") }
            }
            is LanguageUiState.Ready -> {
                s.sound?.let { sc ->
                    Column(modifier = Modifier.padding(vertical = Spacing.S)) {
                        Text("SOUND CHECK", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("New sounds: ${sc.newSounds}", style = MaterialTheme.typography.bodySmall)
                        Text("Rhythm: ${sc.rhythm}", style = MaterialTheme.typography.bodySmall)
                        Text("Learn first: ${sc.registerFirst}", style = MaterialTheme.typography.bodySmall)
                        Text("Trap: ${sc.trap}", style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(thickness = Spacing.Hairline, modifier = Modifier.padding(vertical = Spacing.S))
                    }
                }
                // Pinned glue phrases — always visible
                GlueStrip(phrases = s.phrases.filter { it.id.startsWith("spa-glue") || it.id == "spa-029" })
                // Segmented theme control — accent underline, no fill
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    (1..8).forEach { t ->
                        SegmentButton(label = ThemeNames[t] ?: t.toString(), selected = s.theme == t, onClick = { vm.setTheme(t) })
                    }
                }
                OutlinedTextField(
                    value = s.query, onValueChange = { vm.setQuery(it) },
                    label = { Text("Search English") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.S)
                )
                val visible = vm.visible(s)
                if (visible.isEmpty()) {
                    Text("No phrases match.", Modifier.padding(top = Spacing.M))
                } else {
                    val idx = s.index.coerceIn(0, visible.lastIndex)
                    val phrase = visible[idx]
                    PhraseStage(phrase = phrase, lang = code, isStarred = phrase.id in s.starred, onToggleStar = { vm.toggleStar(phrase.id) })
                    Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.M), horizontalArrangement = Arrangement.SpaceBetween) {
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
fun SegmentButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.padding(end = Spacing.M)) {
        TextButton(onClick = onClick, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) {
            Text(label, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        if (selected) Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.primary))
        else Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outline))
    }
}

@Composable
fun GlueStrip(phrases: List<Phrase>) {
    if (phrases.isEmpty()) return
    Column(modifier = Modifier.padding(vertical = Spacing.S)) {
        Text("GLUE — REPAIR PHRASES", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        phrases.forEach { p ->
            Text("${p.english} — ${p.youSay}", style = MaterialTheme.typography.bodySmall)
        }
        HorizontalDivider(thickness = Spacing.Hairline, modifier = Modifier.padding(vertical = Spacing.S))
    }
}

@Composable
fun PhraseStage(phrase: Phrase, lang: String, isStarred: Boolean, onToggleStar: () -> Unit) {
    val ctx = LocalContext.current
    val repo = remember { UserStateRepository(ctx) }
    var hidden by remember(phrase.id) { mutableStateOf(false) }
    var noteExpanded by remember(phrase.id) { mutableStateOf(false) }
    var noteText by remember(phrase.id) { mutableStateOf("") }
    var saveJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(phrase.id) { repo.note(lang, phrase.id).collect { noteText = it } }

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
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 120.dp)
                .clickable { hidden = !hidden }.padding(vertical = Spacing.S)
        ) {
            if (!hidden) {
                Text("You say", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(phrase.youSay, style = MaterialTheme.typography.displayLarge, fontSize = PhraseSizes.YouSay)
                Text(phrase.soundTip, style = MaterialTheme.typography.bodyMedium)
                Text(phrase.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text("Hidden — say it aloud. Tap to reveal.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(thickness = Spacing.Hairline)
        // Collapsed note — tap to expand, autosaves
        TextButton(onClick = { noteExpanded = !noteExpanded }) { Text(if (noteExpanded) "Hide note" else "Private note") }
        if (noteExpanded) {
            OutlinedTextField(
                value = noteText,
                onValueChange = { changed ->
                    noteText = changed
                    saveJob?.cancel()
                    saveJob = scope.launch {
                        delay(500)
                        repo.saveNote(lang, phrase.id, changed)
                    }
                },
                label = { Text("Private note") },
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text("Source: ${phrase.source} • Checked ${phrase.checked}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
