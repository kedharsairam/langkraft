package com.kedharsairam.langkraft.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.domain.Phrase
import com.kedharsairam.langkraft.presentation.theme.Spacing
import com.kedharsairam.langkraft.presentation.viewmodels.LanguageUiState
import com.kedharsairam.langkraft.presentation.viewmodels.LanguageViewModel

val ThemeNames = mapOf(
    1 to "Greetings", 2 to "Politeness", 3 to "Numbers", 4 to "Food",
    5 to "Directions", 6 to "Shopping", 7 to "Emergency", 8 to "Connection"
)

@Composable
fun LanguageScreen(
    code: String,
    onBack: () -> Unit,
    onNumbers: (String) -> Unit,
    onSearch: () -> Unit = {},
    onHard: () -> Unit = {},
    vm: LanguageViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(code) { vm.load(code) }
    when (val s = state) {
        LanguageUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) { CircularProgressIndicator() }
        is LanguageUiState.Empty -> Column(Modifier.padding(horizontal = Spacing.Screen)) {
            Spacer(Modifier.height(Spacing.M))
            Text("No phrases yet for this language.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = onBack) { Text("Back to queue") }
        }
        is LanguageUiState.Failed -> Column(Modifier.padding(horizontal = Spacing.Screen)) {
            Spacer(Modifier.height(Spacing.M))
            Text("Could not load phrases.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { vm.load(code) }) { Text("Retry") }
        }
        is LanguageUiState.Ready -> {
            if (s.sound != null && !s.soundAcked) {
                Column(modifier = Modifier.padding(horizontal = Spacing.Screen)) {
                    Spacer(Modifier.height(Spacing.M))
                    Text("PRE-FLIGHT BRIEFING", fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Text("[${code.uppercase()}] — ONE SCREEN. NO AUDIO.", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.L))
                    Text("NEW SOUNDS:", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Text(s.sound.newSounds, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Spacing.M))
                    Text("RHYTHM & POLITENESS:", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Text("${s.sound.rhythm} — ${s.sound.registerFirst}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Spacing.M))
                    Text("TRAP:", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    Text(s.sound.trap, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(Spacing.L))
                    Button(onClick = { vm.acknowledgeSound() }, modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.MinTarget)) { Text("[ ACKNOWLEDGE & ENTER QUEUE ]") }
                }
            } else {
                var lessonsOpen by remember(code) { mutableStateOf(true) }
                BackHandler { if (lessonsOpen) onBack() else lessonsOpen = true }
                // Lessons list first (LingoHut-style index). Phrase stage only after a lesson is tapped.
                Column(modifier = Modifier.padding(horizontal = Spacing.Screen)) {
                    Row(
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            IconButton(onClick = { if (lessonsOpen) onBack() else lessonsOpen = true }, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                            Text(s.name.ifBlank { code.uppercase() }, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            IconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Search, contentDescription = "Search")
                            }
                            IconButton(onClick = onHard, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Default.Star, contentDescription = "Hard")
                            }
                        }
                    }
                    if (lessonsOpen) {
                        Text("LESSONS", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(Spacing.S))
                        Column(modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            (1..8).forEach { t ->
                                val count = s.phrases.count { it.theme == t }
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.medium,
                                    modifier = Modifier.fillMaxWidth().wrapContentHeight().clickable {
                                        if (t == 3) onNumbers(code) else { vm.setTheme(t); lessonsOpen = false }
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(Spacing.M),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                            Text("%02d".format(t), fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            Spacer(Modifier.width(Spacing.S))
                                            Text(ThemeNames[t] ?: "", style = MaterialTheme.typography.titleLarge)
                                        }
                                        Text(if (t == 3) "OPEN" else "[$count PHRASES]", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }

                    } else {
                        Text(
                            "%02d %s · [%d PHRASES]".format(s.theme, ThemeNames[s.theme] ?: "", vm.visible(s).size),
                            fontFamily = FontFamily.Monospace, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(vertical = Spacing.S)
                        )
                        val visible = vm.visible(s)
                        if (visible.isEmpty()) {
                            Text("No phrases match.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Column(
                                modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(Spacing.S)
                            ) {
                                visible.forEach { p ->
                                    PhraseRow(phrase = p, isStarred = p.id in s.starred, onToggleStar = { vm.toggleStar(p.id) })
                                }
                                Spacer(Modifier.height(Spacing.S))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GlueStrip(phrases: List<Phrase>) {
    if (phrases.isEmpty()) return
    Column(modifier = Modifier.padding(vertical = Spacing.S)) {
        Text("GLUE — REPAIR PHRASES", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        phrases.forEach { p -> Text("${p.english} — ${p.youSay}", fontFamily = FontFamily.Monospace, fontSize = 12.sp, maxLines = 2) }
        HorizontalDivider(thickness = Spacing.Hairline, modifier = Modifier.padding(vertical = Spacing.S))
    }
}

@Composable
fun PhraseRow(phrase: Phrase, isStarred: Boolean, onToggleStar: () -> Unit) {
    var hidden by remember(phrase.id) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.S)) {
            Text(phrase.english, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Crossfade(targetState = hidden, animationSpec = tween(150), label = "hide") { isHidden ->
                Column(modifier = Modifier.fillMaxWidth().clickable { hidden = !hidden }.padding(vertical = Spacing.Xs)) {
                    if (!isHidden) {
                        Text(phrase.youSay, fontFamily = FontFamily.Monospace, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("[ TAP TO REVEAL — or tap REVEAL ]", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (phrase.soundTip.isNotBlank()) Text(phrase.soundTip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (phrase.note.isNotBlank()) Text(phrase.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Button(onClick = { hidden = !hidden }, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) { Text(if (hidden) "REVEAL" else "HIDE") }
                OutlinedButton(onClick = onToggleStar, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) { Text(if (isStarred) "Hard ★" else "Mark hard") }
            }
        }
    }
}

@Composable
fun PhraseStage(phrase: Phrase, isStarred: Boolean, onToggleStar: () -> Unit) {
    var hidden by remember(phrase.id) { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.M)) {
            // Q10: English small above
            Text(phrase.english, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.S))
            // Q12: both tap + button + 150ms fade
            Crossfade(targetState = hidden, animationSpec = tween(150), label = "hide") { isHidden ->
                Column(modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 140.dp).clickable { hidden = !hidden }.padding(vertical = Spacing.S)) {
                    if (!isHidden) {
                        Text("YOU SAY", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(phrase.youSay, fontFamily = FontFamily.Monospace, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("[ TAP TO REVEAL — or tap REVEAL ]", fontFamily = FontFamily.Monospace, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            // Q13: footnote small, not left bar
            Text(phrase.soundTip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(phrase.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(Spacing.S))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Button(onClick = { hidden = !hidden }, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) { Text(if (hidden) "REVEAL" else "HIDE") }
                // Q14: mark hard as text button
                OutlinedButton(onClick = onToggleStar, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) { Text(if (isStarred) "Hard ★" else "Mark hard") }
            }
            // Q15 no note, Q16 no source — removed from UI.
        }
    }
}
