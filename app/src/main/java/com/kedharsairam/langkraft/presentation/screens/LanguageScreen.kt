package com.kedharsairam.langkraft.presentation.screens

import com.kedharsairam.langkraft.presentation.components.displaySource

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.sp
import com.kedharsairam.langkraft.domain.Phrase
import com.kedharsairam.langkraft.domain.SoundCheck
import com.kedharsairam.langkraft.presentation.components.*
import com.kedharsairam.langkraft.presentation.theme.*
import com.kedharsairam.langkraft.presentation.viewmodels.LanguageUiState
import com.kedharsairam.langkraft.presentation.viewmodels.LanguageViewModel
import compose.icons.TablerIcons
import compose.icons.tablericons.Search
import compose.icons.tablericons.Star
import compose.icons.tablericons.Bookmark

private fun title(phrases: List<Phrase>, theme: Int) =
    phrases.firstOrNull { it.theme == theme }?.anchor?.substringAfter(": ")?.takeIf { it.isNotBlank() } ?: "Lesson $theme"

/** Traveller-situation sections over the 35 LingoHut lesson numbers. */
private val SECTIONS = listOf(
    "Basics" to setOf(1, 2, 118),
    "Numbers & time" to setOf(8, 9, 10, 11, 12, 82, 84),
    "Food & drink" to setOf(52, 70, 71, 74, 75, 76),
    "Shopping & money" to setOf(56, 57, 58),
    "Getting around" to setOf(78, 79, 81),
    "Health" to setOf(89, 90, 91, 92),
    "Travel & emergency" to setOf(93, 94, 96, 97, 98, 99, 100, 123, 125)
)

@Composable
fun LanguageScreen(
    code: String, onBack: () -> Unit, onNumbers: (String) -> Unit,
    onSearch: () -> Unit = {}, onHard: () -> Unit = {},
    initialLesson: Int? = null, vm: LanguageViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(code) { vm.load(code) }
    when (val s = state) {
        LanguageUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { KLabel("LOADING…") }
        is LanguageUiState.Empty, is LanguageUiState.Failed -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text("No phrases yet for this language.", color = LangColors.Ink)
        }
        is LanguageUiState.Ready -> {
            var replay by remember(code) { mutableStateOf(false) }
            if (s.sound != null && (!s.soundAcked || replay)) {
                SoundCheckView(s.name.ifBlank { code.uppercase() }, s.sound!!, onBack) { vm.acknowledgeSound(); replay = false }
                return
            }
            var openLesson by remember(code) { mutableStateOf<Int?>(initialLesson) }
            var filterHard by remember(code) { mutableStateOf(false) }
            LaunchedEffect(initialLesson) { if (initialLesson != null) { vm.setTheme(initialLesson); openLesson = initialLesson } }
            BackHandler { if (openLesson != null) openLesson = null else onBack() }

            val themes = s.phrases.map { it.theme }.distinct().sorted()
            Column(Modifier.padding(horizontal = Spacing.Screen).fillMaxSize()) {
                KTopBar(if (openLesson == null) s.name.ifBlank { code.uppercase() } else title(s.phrases, openLesson!!),
                    onBack = { if (openLesson != null) openLesson = null else onBack() }) {
                    Box(Modifier.size(Spacing.MinTarget).kraftClick(onSearch), Alignment.Center) {
                        Icon(TablerIcons.Search, "Search", Modifier.size(24.dp), LangColors.Ink)
                    }
                    Box(Modifier.size(Spacing.MinTarget).kraftClick(onHard), Alignment.Center) {
                        Icon(TablerIcons.Star, "Hard", Modifier.size(24.dp), LangColors.Ink)
                    }
                }
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
                    if (openLesson == null) {
                        item {
                            KLabel("QUICK ACCESS")
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                KSecondary("Sound check", { replay = true }, Modifier.weight(1f))
                                KSecondary("Numbers", { onNumbers(code) }, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(Spacing.S))
                        }
                        SECTIONS.forEach { (name, ids) ->
                            val lessons = themes.filter { it in ids }
                            if (lessons.isNotEmpty()) {
                                item { Spacer(Modifier.height(Spacing.S)); SignalSection(name, lessons.size) }
                                items(lessons, key = { "l-$it" }) { t ->
                                    val serial = themes.indexOf(t) + 1
                                    LedgerRow("%02d".format(serial), title(s.phrases, t), "[${s.phrases.count { it.theme == t }}]",
                                        { vm.setTheme(t); openLesson = t })
                                }
                            }
                        }
                    } else {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                KLabel("ALL", color = if (!filterHard) LangColors.Accent else LangColors.Muted,
                                    modifier = Modifier.kraftClick { filterHard = false })
                                Spacer(Modifier.width(Spacing.M))
                                KLabel("HARD", color = if (filterHard) LangColors.Accent else LangColors.Muted,
                                    modifier = Modifier.kraftClick { filterHard = true })
                            }
                            Spacer(Modifier.height(Spacing.S))
                        }
                        val visible = s.phrases.filter { it.theme == openLesson && (!filterHard || it.id in s.starred) }
                        if (visible.isEmpty()) item { Text("No phrases here.", color = LangColors.Muted) }
                        else items(visible, key = { it.id }) { p ->
                            SignalRow(
                                index = visible.indexOf(p) + 1,
                                english = p.english,
                                romanization = p.romanization,
                                youSay = p.youSay,
                                tip = p.soundTip,
                                note = if (p.note.isNotBlank() && !p.note.equals(p.anchor?.substringAfter(": "), true)) p.note else "",
                                meta = "${displaySource(p.source)} · ${p.checked}",
                                starred = p.id in s.starred
                            ) { vm.toggleStar(p.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundCheckView(name: String, sound: SoundCheck, onBack: () -> Unit, onAck: () -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.Screen).fillMaxSize()) {
        KTopBar(name, onBack)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
            Spacer(Modifier.height(Spacing.S))
            KLabel("PRE-FLIGHT BRIEFING")
            Block("NEW SOUNDS", sound.newSounds)
            Block("RHYTHM & POLITENESS", "${sound.rhythm} — ${sound.registerFirst}")
            Block("TRAP", sound.trap)
            Spacer(Modifier.height(Spacing.M))
            KPrimary("ACKNOWLEDGE & ENTER QUEUE", onAck)
        }
    }
}

@Composable
private fun Block(label: String, body: String) {
    Column(Modifier.fillMaxWidth().kraftPanel().dotGrid().padding(Spacing.M)) {
        KLabel(label); Spacer(Modifier.height(Spacing.S))
        Text(body, style = LangTypography.bodyMedium, color = LangColors.Ink)
    }
}
