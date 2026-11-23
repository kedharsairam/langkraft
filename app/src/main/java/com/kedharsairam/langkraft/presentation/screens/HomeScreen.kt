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
import androidx.compose.ui.text.style.TextAlign
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import com.kedharsairam.langkraft.presentation.viewmodels.HomeUiState
import com.kedharsairam.langkraft.presentation.viewmodels.HomeViewModel
import com.kedharsairam.langkraft.presentation.components.*
import com.kedharsairam.langkraft.presentation.theme.*
import compose.icons.TablerIcons
import compose.icons.tablericons.Search
import compose.icons.tablericons.Star
import compose.icons.tablericons.Bookmark

@Composable
fun HomeScreen(
    onLang: (String) -> Unit,
    onSearch: () -> Unit,
    onHard: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    Column(Modifier.padding(horizontal = Spacing.Screen).fillMaxSize()) {
        // Wordmark + search — search is the primary path.
        Row(Modifier.fillMaxWidth().height(Spacing.MinTarget), verticalAlignment = Alignment.CenterVertically) {
            Text("Langkraft", style = LangTypography.titleLarge, color = LangColors.Ink, modifier = Modifier.weight(1f))
            Box(Modifier.size(Spacing.MinTarget).kraftClick(onSearch), contentAlignment = Alignment.Center) {
                Icon(TablerIcons.Search, contentDescription = "Search", modifier = Modifier.size(24.dp), tint = LangColors.Ink)
            }
        }
        when (val s = state) {
            HomeUiState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), Alignment.Center) { KLabel("LOADING…") }
            is HomeUiState.Failed -> Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Text("Could not load languages.", color = LangColors.Ink)
                Spacer(Modifier.height(Spacing.S)); KSecondary("RETRY", { vm.load() })
            }
            is HomeUiState.Ready -> {
                LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    // Quick access strip — one panel, no tiles.
                    item {
                        Column(Modifier.fillMaxWidth().kraftPanel().padding(Spacing.M)) {
                            KLabel("QUICK ACCESS")
                            Spacer(Modifier.height(Spacing.S))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
                                QuickButton("Search all", TablerIcons.Search, Modifier.weight(1f), onSearch)
                                QuickButton("Hard", TablerIcons.Star, Modifier.weight(1f), onHard)
                                val resume = s.continue_?.first
                                QuickButton("Resume", TablerIcons.Bookmark, Modifier.weight(1f),
                                    { if (resume != null) onLang(resume) else onSearch() })
                            }
                        }
                    }
                    val committed = s.languages.filter { it.status == "committed" }.sortedBy { it.name }
                    val bench = s.languages.filter { it.status != "committed" }.sortedBy { it.name }
                    item { KLabel("COMMITTED · ${committed.size}") }
                    items(committed, key = { it.code }) { info ->
                        LanguageRow(info.name, s.progress[info.code], s.phraseCounts[info.code]) { onLang(info.code) }
                    }
                    item {
                        Spacer(Modifier.height(Spacing.S))
                        KLabel("BENCH · ${bench.size}")
                    }
                    items(bench, key = { it.code }) { info ->
                        LanguageRow(info.name, s.progress[info.code], s.phraseCounts[info.code]) { onLang(info.code) }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.height(72.dp).kraftClick(onClick).padding(Spacing.S),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp), tint = LangColors.Accent)
        Spacer(Modifier.height(Spacing.Xs))
        KLabel(label, align = TextAlign.Center)
    }
}

@Composable
private fun LanguageRow(name: String, progress: Pair<Int, Int>?, phrases: Int?, onClick: () -> Unit) {
    val (done, total) = progress ?: (0 to 0)
    Column(Modifier.fillMaxWidth().kraftClick(onClick).padding(vertical = Spacing.S)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(name, style = LangTypography.bodyLarge, color = LangColors.Ink, modifier = Modifier.weight(1f), maxLines = 1)
            if (phrases != null) KLabel("$phrases PHRASES")
        }
        Spacer(Modifier.height(Spacing.Xs))
        Box(Modifier.fillMaxWidth().height(Spacing.Hairline).background(LangColors.Muted.copy(alpha = 0.3f))) {
            if (total > 0 && done > 0) {
                Box(Modifier.fillMaxWidth(done.toFloat() / total).height(Spacing.Hairline).background(LangColors.Accent))
            }
        }
    }
    RowDivider()
}
