package com.kedharsairam.langkraft.presentation.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.domain.Phrase
import com.kedharsairam.langkraft.presentation.components.*
import com.kedharsairam.langkraft.presentation.theme.LangColors
import com.kedharsairam.langkraft.presentation.theme.Spacing
import com.kedharsairam.langkraft.presentation.viewmodels.LanguageUiState
import com.kedharsairam.langkraft.presentation.viewmodels.LanguageViewModel

private fun lessonTitle(phrases: List<Phrase>, theme: Int): String =
    phrases.firstOrNull { it.theme == theme }?.anchor?.substringAfter(": ")?.takeIf { it.isNotBlank() }
        ?: "Lesson $theme"

@Composable
fun LanguageScreen(
    code: String,
    onBack: () -> Unit,
    onNumbers: (String) -> Unit,
    onSearch: () -> Unit = {},
    onHard: () -> Unit = {},
    initialLesson: Int? = null,
    vm: LanguageViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    LaunchedEffect(code) { vm.load(code) }
    when (val s = state) {
        LanguageUiState.Loading -> Column(Modifier.padding(horizontal = Spacing.Screen).fillMaxSize()) {
            KraftTopBar(code.uppercase(), onBack = onBack)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { MonoLabel("LOADING…") }
        }
        is LanguageUiState.Empty -> Column(Modifier.padding(horizontal = Spacing.Screen).fillMaxSize()) {
            KraftTopBar(code.uppercase(), onBack = onBack, showSearch = true, onSearch = onSearch, showHard = true, onHard = onHard)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No phrases yet for this language.", color = LangColors.Ink)
            }
        }
        is LanguageUiState.Failed -> Column(Modifier.padding(horizontal = Spacing.Screen).fillMaxSize()) {
            KraftTopBar(code.uppercase(), onBack = onBack)
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Could not load phrases.", color = LangColors.Ink)
                    Spacer(Modifier.height(Spacing.S))
                    SecondaryCta("RETRY", { vm.load(code) })
                }
            }
        }
        is LanguageUiState.Ready -> {
            var soundReplay by remember(code) { mutableStateOf(false) }
            if (s.sound != null && (!s.soundAcked || soundReplay)) {
                SoundCheckScreen(s.name.ifBlank { code.uppercase() }, s.sound!!, onBack, {
                    vm.acknowledgeSound(); soundReplay = false
                })
            } else {
                var lessonsOpen by remember(code) { mutableStateOf(initialLesson == null) }
                LaunchedEffect(initialLesson) {
                    if (initialLesson != null) { vm.setTheme(initialLesson); lessonsOpen = false }
                }
                BackHandler { if (lessonsOpen) onBack() else lessonsOpen = true }
                Column(Modifier.padding(horizontal = Spacing.Screen).fillMaxSize()) {
                    KraftTopBar(
                        s.name.ifBlank { code.uppercase() },
                        onBack = { if (lessonsOpen) onBack() else lessonsOpen = true },
                        showSearch = true, onSearch = onSearch, showHard = true, onHard = onHard
                    )
                    if (lessonsOpen) {
                        Spacer(Modifier.height(Spacing.S))
                        MonoLabel("LESSONS")
                        Spacer(Modifier.height(Spacing.S))
                        Column(
                            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(Spacing.S)
                        ) {
                            val themes = s.phrases.map { it.theme }.distinct().sorted()
                            // Reopenable sound-check and numbers grid, as ledger rows.
                            if (s.sound != null) {
                                LedgerRow(index = "—", title = "Sound check", meta = "",
                                    onClick = { soundReplay = true })
                            }
                            LedgerRow(index = "—", title = "Numbers grid", meta = "",
                                onClick = { onNumbers(code) })
                            themes.forEach { t ->
                                val serial = themes.indexOf(t) + 1
                                val count = s.phrases.count { it.theme == t }
                                LedgerRow(
                                    index = "%02d".format(serial),
                                    title = lessonTitle(s.phrases, t),
                                    meta = "[$count]",
                                    onClick = { vm.setTheme(t); lessonsOpen = false }
                                )
                            }
                        }
                    } else {
                        Spacer(Modifier.height(Spacing.S))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val themes = s.phrases.map { it.theme }.distinct().sorted()
                            val serial = themes.indexOf(s.theme) + 1
                            Text(
                                "%02d  %s".format(serial, lessonTitle(s.phrases, s.theme)),
                                fontFamily = FontFamily.Monospace, fontSize = 13.sp,
                                color = LangColors.Muted, modifier = Modifier.weight(1f)
                            )
                            MonoLabel("[${vm.visible(s).size}]")
                        }
                        Spacer(Modifier.height(Spacing.S))
                        val visible = vm.visible(s)
                        if (visible.isEmpty()) {
                            Text("No phrases match.", color = LangColors.Muted)
                        } else {
                            Column(
                                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(Spacing.S)
                            ) {
                                visible.forEachIndexed { i, p -> PhraseCard(i + 1, p, p.id in s.starred) { vm.toggleStar(p.id) } }
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
private fun PhraseCard(num: Int, phrase: Phrase, isStarred: Boolean, onToggleStar: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().borderedCard().padding(Spacing.M)
    ) {
        Text("$num. ${phrase.english}", style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = LangColors.Muted)
        Spacer(Modifier.height(Spacing.Xs))
        // The actual target phrase — the thing being learned. Never hide it.
        Text(phrase.romanization, fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = LangColors.Ink)
        Spacer(Modifier.height(Spacing.Xs))
        Text(
            phrase.youSay.replace("-", "-\u200B"),
            fontFamily = FontFamily.Monospace,
            fontSize = 26.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = LangColors.Ink,
            lineHeight = 34.sp
        )
        Spacer(Modifier.height(Spacing.Xs))
        if (phrase.soundTip.isNotBlank()) {
            Text(phrase.soundTip, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = LangColors.Muted)
        }
        // Note only when it carries real information — never the lesson name as filler.
        if (phrase.note.isNotBlank() && !phrase.note.equals(phrase.anchor?.substringAfter(": "), ignoreCase = true)) {
            Spacer(Modifier.height(Spacing.Xs))
            Text(phrase.note, style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = LangColors.Muted)
        }
        Spacer(Modifier.height(Spacing.S))
        // Compact star toggle — 56dp touch target, no repeated full-width box.
        Box(
            Modifier
                .size(Spacing.MinTarget)
                .flatClickable(onToggleStar),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Star,
                contentDescription = if (isStarred) "Remove from hard" else "Mark as hard",
                tint = if (isStarred) LangColors.Accent else LangColors.Muted,
                modifier = androidx.compose.ui.Modifier.size(24.dp)
            )
        }
        Spacer(Modifier.height(Spacing.S))
        SectionDivider()
        Spacer(Modifier.height(Spacing.Xs))
        MonoLabel("${displaySource(phrase.source)}  ·  ${phrase.checked}")
    }
}

@Composable
private fun SoundCheckScreen(name: String, sound: com.kedharsairam.langkraft.domain.SoundCheck, onBack: () -> Unit, onAck: () -> Unit) {
    Column(Modifier.padding(horizontal = Spacing.Screen).fillMaxSize()) {
        KraftTopBar(name, onBack = onBack)
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(Spacing.S)
        ) {
            Spacer(Modifier.height(Spacing.S))
            MonoLabel("PRE-FLIGHT BRIEFING")
            Block("NEW SOUNDS", sound.newSounds)
            Block("RHYTHM & POLITENESS", "${sound.rhythm} — ${sound.registerFirst}")
            Block("TRAP", sound.trap)
            Spacer(Modifier.height(Spacing.M))
            InvertedCta("ACKNOWLEDGE & ENTER QUEUE", onAck)
        }
    }
}

@Composable
private fun Block(label: String, body: String) {
    Column(Modifier.fillMaxWidth().borderedCard().padding(Spacing.M)) {
        MonoLabel(label)
        Spacer(Modifier.height(Spacing.S))
        Text(body, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium, color = LangColors.Ink)
    }
}
