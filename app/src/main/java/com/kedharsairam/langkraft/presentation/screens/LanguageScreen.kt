package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.data.UserStateRepository
import com.kedharsairam.langkraft.domain.Phrase
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to queue")
                Spacer(Modifier.width(Spacing.S))
                Text("QUEUE")
            }
            TextButton(onClick = { onNumbers(code) }, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) { Text("NUMBERS") }
        }
        Text("[${code.uppercase()}]", fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        when (val s = state) {
            LanguageUiState.Loading -> CircularProgressIndicator(Modifier.padding(top = Spacing.M))
            is LanguageUiState.Empty -> Text("STATUS: ZERO_ENTRIES_FOUND. No phrases yet for this language.", fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = Spacing.M))
            is LanguageUiState.Failed -> Column {
                Text("ERROR: ${s.reason::class.simpleName}", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = Spacing.M))
                TextButton(onClick = { vm.load(code) }) { Text("[ RELOAD LOCAL DATA ]") }
            }
            is LanguageUiState.Ready -> {
                s.sound?.let { sc ->
                    Column(modifier = Modifier.padding(vertical = Spacing.S)) {
                        Text("PRE-FLIGHT BRIEFING", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("NEW SOUNDS: ${sc.newSounds}", style = MaterialTheme.typography.bodySmall)
                        Text("RHYTHM: ${sc.rhythm}", style = MaterialTheme.typography.bodySmall)
                        Text("POLITENESS FIRST: ${sc.registerFirst}", style = MaterialTheme.typography.bodySmall)
                        Text("TRAP: ${sc.trap}", style = MaterialTheme.typography.bodySmall)
                        HorizontalDivider(thickness = Spacing.Hairline, modifier = Modifier.padding(vertical = Spacing.S))
                    }
                }
                GlueStrip(phrases = s.phrases.filter { it.id.startsWith("spa-glue") || it.id == "spa-029" })
                Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                    (1..8).forEach { t ->
                        SegmentButton(label = (ThemeNames[t] ?: t.toString()).uppercase(), selected = s.theme == t, onClick = { vm.setTheme(t) })
                    }
                }
                OutlinedTextField(
                    value = s.query, onValueChange = { vm.setQuery(it) },
                    label = { Text("SEARCH") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.S)
                )
                val visible = vm.visible(s)
                if (visible.isEmpty()) {
                    Text("STATUS: ZERO_MATCHES_FOUND.", fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = Spacing.M))
                } else {
                    val idx = s.index.coerceIn(0, visible.lastIndex)
                    val phrase = visible[idx]
                    PhraseStage(phrase = phrase, lang = code, isStarred = phrase.id in s.starred, onToggleStar = { vm.toggleStar(phrase.id) })
                    Row(modifier = Modifier.fillMaxWidth().padding(top = Spacing.M)) {
                        TextButton(onClick = { vm.prev() }, modifier = Modifier.weight(1f).defaultMinSize(minHeight = Spacing.MinTarget)) { Text("← PREV") }
                        TextButton(onClick = { vm.next() }, modifier = Modifier.weight(1f).defaultMinSize(minHeight = Spacing.MinTarget)) { Text("NEXT →") }
                    }
                    Text("${idx + 1}/${visible.size}", fontFamily = FontFamily.Monospace, modifier = Modifier.padding(top = Spacing.S))
                }
            }
        }
    }
}

@Composable
fun SegmentButton(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(modifier = Modifier.padding(end = Spacing.M)) {
        TextButton(onClick = onClick, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) {
            Text(label, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        if (selected) Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.primary))
        else Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(MaterialTheme.colorScheme.outline))
    }
}

@Composable
fun GlueStrip(phrases: List<Phrase>) {
    if (phrases.isEmpty()) return
    Column(modifier = Modifier.padding(vertical = Spacing.S)) {
        Text("GLUE — REPAIR PHRASES", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        phrases.forEach { p -> Text("${p.english} — ${p.youSay}", fontFamily = FontFamily.Monospace, fontSize = 13.sp) }
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

    // Tactical stage — matte surface, no chrome
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.M)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(phrase.english, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = onToggleStar, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) {
                    Icon(
                        if (isStarred) Icons.Filled.Star else Icons.Filled.StarBorder,
                        contentDescription = if (isStarred) "Star phrase as hard" else "Star phrase as hard",
                        tint = if (isStarred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(Spacing.M))
            Column(
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 140.dp)
                    .clickable { hidden = !hidden }.padding(vertical = Spacing.S)
            ) {
                // English is prompt; YouSay is the utterance. Both shown for tactical clarity,
                // but YouSay is hidden by default for retrieval.
                if (!hidden) {
                    Text("YOU SAY", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(phrase.youSay, fontFamily = FontFamily.Monospace, fontSize = 28.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                    // left-accent tip/note blocks
                    Row(modifier = Modifier.padding(top = Spacing.S)) {
                        Box(modifier = Modifier.width(2.dp).height(36.dp).background(MaterialTheme.colorScheme.primary))
                        Spacer(Modifier.width(Spacing.S))
                        Column {
                            Text(phrase.soundTip, style = MaterialTheme.typography.bodySmall)
                            Text(phrase.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    Text("[ TAP TO REVEAL UTTERANCE ]", fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            HorizontalDivider(thickness = Spacing.Hairline)
            TextButton(onClick = { noteExpanded = !noteExpanded }) { Text(if (noteExpanded) "HIDE NOTE" else "PRIVATE NOTE") }
            if (noteExpanded) {
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { changed ->
                        noteText = changed
                        saveJob?.cancel()
                        saveJob = scope.launch { delay(500); repo.saveNote(lang, phrase.id, changed) }
                    },
                    label = { Text("PRIVATE NOTE") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Text("SOURCE: ${phrase.source} • CHECKED ${phrase.checked}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
