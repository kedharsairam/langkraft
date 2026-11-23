package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kedharsairam.langkraft.presentation.components.*
import com.kedharsairam.langkraft.presentation.components.ReloadOnResume
import com.kedharsairam.langkraft.presentation.theme.*
import com.kedharsairam.langkraft.presentation.viewmodels.HomeUiState
import com.kedharsairam.langkraft.presentation.viewmodels.HomeViewModel
import compose.icons.TablerIcons
import compose.icons.tablericons.Search
import compose.icons.tablericons.Star
import compose.icons.tablericons.Bookmark

@Composable
fun HomeScreen(onLang: (String) -> Unit, onSearch: () -> Unit, onHard: () -> Unit, vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    ReloadOnResume { vm.load() }
    Column(Modifier.padding(horizontal = Spacing.Screen).fillMaxSize()) {
        // Wordmark with gold square signature mark.
        Row(Modifier.fillMaxWidth().height(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(14.dp).background(LangColors.Accent))
            Spacer(Modifier.width(Spacing.S))
            Text("Langkraft", style = LangTypography.displayLarge, color = LangColors.Ink, modifier = Modifier.weight(1f))
            Box(Modifier.size(56.dp).kraftClick(onSearch), Alignment.Center) {
                Icon(TablerIcons.Search, "Search", Modifier.size(26.dp), LangColors.Ink)
            }
        }
        when (val s = state) {
            HomeUiState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { KLabel("LOADING…") }
            is HomeUiState.Failed -> Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Text("Could not load languages.", color = LangColors.Ink); Spacer(Modifier.height(Spacing.S)); KSecondary("RETRY", { vm.load() })
            }
            is HomeUiState.Ready -> LazyColumn(Modifier.weight(1f)) {
                // Quick access — textured panel, no plain fill.
                item {
                    Column(Modifier.fillMaxWidth().kraftPanel().dotGrid().padding(Spacing.M)) {
                        KLabel("QUICK ACCESS", color = LangColors.Accent)
                        Spacer(Modifier.height(Spacing.S))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                            Quick("Search all", TablerIcons.Search, Modifier.weight(1f), onSearch)
                            Quick("Hard", TablerIcons.Star, Modifier.weight(1f), onHard)
                            val resume = s.continue_?.first
                            Quick("Resume", TablerIcons.Bookmark, Modifier.weight(1f), { if (resume != null) onLang(resume) else onSearch() })
                        }
                    }
                    Spacer(Modifier.height(Spacing.M))
                }
                item { SignalSection("Committed", s.languages.count { it.status == "committed" }) }
                val committed = s.languages.filter { it.status == "committed" }.sortedBy { it.name }
                items(committed, key = { it.code }) { info ->
                    val (done, total) = s.progress[info.code] ?: (0 to 0)
                    SignalLanguageRow(committed.indexOf(info) + 1, info.name,
                        "${s.phraseCounts[info.code] ?: 0} PHRASES",
                        if (total > 0) done.toFloat() / total else 0f) { onLang(info.code) }
                }
                item { Spacer(Modifier.height(Spacing.M)); SignalSection("Bench", s.languages.count { it.status != "committed" }) }
                val bench = s.languages.filter { it.status != "committed" }.sortedBy { it.name }
                items(bench, key = { it.code }) { info ->
                    SignalLanguageRow(bench.indexOf(info) + 1, info.name,
                        "${s.phraseCounts[info.code] ?: 0} PHRASES", 0f) { onLang(info.code) }
                }
                item { Spacer(Modifier.height(Spacing.M)); KLabel("OFFLINE · ZERO PERMISSIONS · 19 LANGUAGES") }
            }
        }
    }
}

@Composable
private fun Quick(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(76.dp).kraftClick(onClick), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, Modifier.size(24.dp), LangColors.Accent)
        Spacer(Modifier.height(Spacing.Xs))
        KLabel(label.uppercase())
    }
}
