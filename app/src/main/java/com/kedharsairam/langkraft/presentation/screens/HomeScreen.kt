package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import compose.icons.tablericons.PlayerPlay

@Composable
fun HomeScreen(onLang: (String) -> Unit, onSearch: () -> Unit, onHard: () -> Unit, vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    ReloadOnResume { vm.load() }
    Box(Modifier.fillMaxSize().worldMapDots()) {
        when (val s = state) {
            HomeUiState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { KLabel("LOADING…") }
            is HomeUiState.Failed -> Column(Modifier.fillMaxSize().padding(horizontal = Spacing.Screen),
                verticalArrangement = Arrangement.Center) {
                Text("Could not load languages.", color = LangColors.Ink)
                Spacer(Modifier.height(Spacing.S)); KSecondary("RETRY", { vm.load() })
            }
            is HomeUiState.Ready -> LazyColumn(
                Modifier.fillMaxSize().padding(horizontal = Spacing.Screen)
            ) {
                // Everything — brand block, quick access, language lists — lives in one
                // scroll container so short viewports (landscape) can reach every row.
                item {
                    Column(Modifier.fillMaxWidth().padding(top = Spacing.M), horizontalAlignment = Alignment.CenterHorizontally) {
                        GlobeMark(58.dp)
                        Spacer(Modifier.height(Spacing.S))
                        Text("Langkraft", style = LangTypography.displayLarge, color = LangColors.Ink)
                        Spacer(Modifier.height(2.dp))
                        Text("Travel. Speak. Anywhere.", style = LangTypography.bodyMedium.copy(
                            fontFamily = KraftFonts.Serif), color = LangColors.Accent)
                        Spacer(Modifier.height(Spacing.M))
                    }
                }
                item {
                    KLabel("QUICK ACCESS", color = LangColors.Accent)
                    Spacer(Modifier.height(Spacing.S))
                    val resume = s.continue_?.first
                    KListButton("Search all", TablerIcons.Search, onSearch)
                    Spacer(Modifier.height(Spacing.S))
                    KListButton("Hard", TablerIcons.Star, onHard)
                    Spacer(Modifier.height(Spacing.S))
                    KListButton("Resume", TablerIcons.PlayerPlay, { if (resume != null) onLang(resume) else onSearch() })
                    Spacer(Modifier.height(Spacing.M))
                }
                item { SignalSection("Committed", s.languages.count { it.status == "committed" }) }
                val committed = s.languages.filter { it.status == "committed" }.sortedBy { it.name }
                items(committed, key = { it.code }) { info ->
                    SignalLanguageRow(committed.indexOf(info) + 1, info.name,
                        "[${s.phraseCounts[info.code] ?: 0}]", 0f) { onLang(info.code) }
                }
                item { Spacer(Modifier.height(Spacing.M)); SignalSection("Bench", s.languages.count { it.status != "committed" }) }
                val bench = s.languages.filter { it.status != "committed" }.sortedBy { it.name }
                items(bench, key = { it.code }) { info ->
                    SignalLanguageRow(bench.indexOf(info) + 1, info.name,
                        "[${s.phraseCounts[info.code] ?: 0}]", 0f) { onLang(info.code) }
                }
                item { Spacer(Modifier.height(Spacing.M)); KLabel("OFFLINE · ZERO PERMISSIONS · 19 LANGUAGES") }
            }
        }
    }
}
