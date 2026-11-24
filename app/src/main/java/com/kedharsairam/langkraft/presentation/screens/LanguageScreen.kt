package com.kedharsairam.langkraft.presentation.screens

import com.kedharsairam.langkraft.presentation.components.displaySource

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
import compose.icons.tablericons.Activity
import compose.icons.tablericons.WaveSine
import compose.icons.tablericons.AlertTriangle
import compose.icons.tablericons.Book
import compose.icons.tablericons.Clock
import compose.icons.tablericons.ToolsKitchen2
import compose.icons.tablericons.ShoppingCart
import compose.icons.tablericons.Plane
import compose.icons.tablericons.MedicalCross
import compose.icons.tablericons.Briefcase
import compose.icons.tablericons.ChevronRight

private fun title(phrases: List<Phrase>, theme: Int) =
    phrases.firstOrNull { it.theme == theme }?.anchor?.substringAfter(": ")?.takeIf { it.isNotBlank() } ?: "Lesson $theme"

/** Traveller-situation sections over the 35 lesson numbers, with an icon each. */
private data class Section(val name: String, val icon: ImageVector, val ids: Set<Int>)
private val SECTIONS = listOf(
    Section("Basics", TablerIcons.Book, setOf(1, 2, 118)),
    Section("Numbers & time", TablerIcons.Clock, setOf(8, 9, 10, 11, 12, 82, 84)),
    Section("Food & drink", TablerIcons.ToolsKitchen2, setOf(52, 70, 71, 74, 75, 76)),
    Section("Shopping & money", TablerIcons.ShoppingCart, setOf(56, 57, 58)),
    Section("Getting around", TablerIcons.Plane, setOf(78, 79, 81)),
    Section("Health", TablerIcons.MedicalCross, setOf(89, 90, 91, 92)),
    Section("Travel & emergency", TablerIcons.Briefcase, setOf(93, 94, 96, 97, 98, 99, 100, 123, 125))
)

@Composable
fun LanguageScreen(
    code: String, onBack: () -> Unit, onNumbers: (String) -> Unit,
    onSearch: () -> Unit = {}, onHard: () -> Unit = {},
    initialLesson: Int? = null, initialPhraseId: String? = null, vm: LanguageViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(code) { vm.load(code) }
    when (val s = state) {
        LanguageUiState.Loading -> Box(Modifier.fillMaxSize().worldMapDots(), Alignment.Center) { KLabel("LOADING…") }
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
            BackHandler { if (openLesson != null) openLesson = null else onBack() }
            LaunchedEffect(initialLesson, initialPhraseId) {
                if (initialLesson != null) {
                    // Wait until load() has produced a Ready state before jumping, otherwise
                    // the deep-link is lost to the initial Loading state.
                    while (vm.state.value !is LanguageUiState.Ready) { kotlinx.coroutines.delay(50) }
                    vm.jumpTo(initialLesson, initialPhraseId)
                    openLesson = initialLesson
                }
            }

            val themes = s.phrases.map { it.theme }.distinct().sorted()
            Box(Modifier.fillMaxSize().worldMapDots()) {
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
                    LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        if (openLesson == null) {
                            item {
                                KLabel("QUICK ACCESS", color = LangColors.Accent)
                                Spacer(Modifier.height(Spacing.S))
                                KSecondary("Sound check", { replay = true })
                                Spacer(Modifier.height(Spacing.S))
                                KSecondary("Numbers", { onNumbers(code) })
                                Spacer(Modifier.height(Spacing.M))
                                KLabel("19 LANGUAGES · 35 LESSONS", color = LangColors.Muted)
                                Spacer(Modifier.height(Spacing.S))
                            }
                            SECTIONS.forEach { section ->
                                val lessons = themes.filter { it in section.ids }
                                if (lessons.isNotEmpty()) {
                                    item(key = "s-${section.name}") {
                                        SectionCard(section.name, section.icon, lessons.size) {
                                            vm.setTheme(lessons.first())
                                            openLesson = lessons.first()
                                        }
                                    }
                                    items(lessons, key = { "l-$it" }) { t ->
                                        val serial = themes.indexOf(t) + 1
                                        LedgerRow("%02d".format(serial), title(s.phrases, t), "[${s.phrases.count { it.theme == t }}]",
                                            { vm.setTheme(t); openLesson = t })
                                    }
                                }
                            }
                        } else {
                            val visible = s.phrases.filter { it.theme == openLesson }
                            val pos = s.index.coerceIn(0, (visible.size - 1).coerceAtLeast(0))
                            item {
                                KLabel("LESSON · %02d PHRASES".format(visible.size), color = LangColors.Accent)
                                Spacer(Modifier.height(Spacing.S))
                            }
                            if (visible.isEmpty()) item { Text("No phrases here.", color = LangColors.Muted) }
                            else {
                                item {
                                    val p = visible[pos]
                                    SignalRow(
                                        index = pos + 1,
                                        english = p.english,
                                        romanization = p.romanization,
                                        youSay = p.youSay,
                                        tip = p.soundTip,
                                        note = if (p.note.isNotBlank() && !p.note.equals(p.anchor?.substringAfter(": "), true)) p.note else "",
                                        meta = "${displaySource(p.source)} · ${p.checked}",
                                        starred = p.id in s.starred
                                    ) { vm.toggleStar(p.id) }
                                    KStepper(pos + 1, visible.size, { vm.prev() }, { vm.next() })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(name: String, icon: ImageVector, count: Int, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().kraftPanel().kraftClick(onClick).padding(Spacing.M), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(LangColors.SurfaceAlt, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)), Alignment.Center) {
            Icon(icon, contentDescription = null, tint = LangColors.Accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(Spacing.S))
        Text(name, style = LangTypography.titleMedium, color = LangColors.Ink, modifier = Modifier.weight(1f), maxLines = 1)
        KLabel("($count)", color = LangColors.Accent)
        Spacer(Modifier.width(Spacing.S))
        Icon(TablerIcons.ChevronRight, contentDescription = null, tint = LangColors.Muted, modifier = Modifier.size(20.dp))
    }
    Spacer(Modifier.height(Spacing.S))
}

@Composable
private fun SoundCheckView(name: String, sound: SoundCheck, onBack: () -> Unit, onAck: () -> Unit) {
    Box(Modifier.fillMaxSize().worldMapDots()) {
        Column(Modifier.padding(horizontal = Spacing.Screen).fillMaxSize()) {
            KTopBar(name, onBack)
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                Spacer(Modifier.height(Spacing.S))
                Text("BE READY", style = LangTypography.displayLarge, color = LangColors.Ink)
                Text("for Any Destination", style = LangTypography.titleLarge, color = LangColors.Accent)
                Spacer(Modifier.height(Spacing.S))
                Text("A few tips before you begin.", style = LangTypography.bodyMedium, color = LangColors.Muted)
                Spacer(Modifier.height(Spacing.S))
                BriefBlock("NEW SOUNDS", sound.newSounds, TablerIcons.WaveSine)
                BriefBlock("RHYTHM & POLITENESS", "${sound.rhythm} — ${sound.registerFirst}", TablerIcons.Activity)
                BriefBlock("TRAP", sound.trap, TablerIcons.AlertTriangle)
                Spacer(Modifier.height(Spacing.S))
                KPrimary("ACKNOWLEDGE & ENTER QUEUE", onAck)
                Spacer(Modifier.height(Spacing.M))
            }
        }
    }
}

@Composable
private fun BriefBlock(label: String, body: String, icon: ImageVector) {
    Row(Modifier.fillMaxWidth().kraftPanel().padding(Spacing.M)) {
        Box(Modifier.size(34.dp).background(LangColors.SurfaceAlt, androidx.compose.foundation.shape.RoundedCornerShape(8.dp)), Alignment.Center) {
            Icon(icon, contentDescription = null, tint = LangColors.Accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(Spacing.S))
        Column(Modifier.weight(1f)) {
            KLabel(label, color = LangColors.Accent)
            Spacer(Modifier.height(Spacing.Xs))
            Text(body, style = LangTypography.bodySmall, color = LangColors.Ink)
        }
    }
}
