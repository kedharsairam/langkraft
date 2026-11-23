package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.presentation.components.KraftTopBar
import com.kedharsairam.langkraft.presentation.components.MonoLabel
import com.kedharsairam.langkraft.presentation.components.SectionDivider
import com.kedharsairam.langkraft.presentation.components.borderedCard
import com.kedharsairam.langkraft.presentation.components.flatClickable
import com.kedharsairam.langkraft.presentation.theme.LangColors
import com.kedharsairam.langkraft.presentation.theme.Spacing
import com.kedharsairam.langkraft.presentation.viewmodels.HomeUiState
import com.kedharsairam.langkraft.presentation.viewmodels.HomeViewModel

@Composable
fun HomeScreen(
    onLang: (String) -> Unit,
    onSearch: () -> Unit,
    onHard: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    Column(Modifier.padding(horizontal = Spacing.Screen).fillMaxSize()) {
        KraftTopBar("Langkraft", showSearch = true, onSearch = onSearch, showHard = true, onHard = onHard)
        when (val s = state) {
            HomeUiState.Loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                MonoLabel("LOADING CATALOGUE…")
            }
            is HomeUiState.Failed -> Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.Center) {
                Text("Could not load languages.", color = LangColors.Ink)
                Spacer(Modifier.height(Spacing.S))
                com.kedharsairam.langkraft.presentation.components.SecondaryCta("RETRY", { vm.load() })
            }
            is HomeUiState.Ready -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S),
                    contentPadding = PaddingValues(top = Spacing.S, bottom = Spacing.Screen),
                    modifier = Modifier.weight(1f)
                ) {
                    val committed = s.languages.filter { it.status == "committed" }.sortedBy { it.name }
                    val bench = s.languages.filter { it.status != "committed" }.sortedBy { it.name }
                    items(committed) { info -> LangTile(info.name, s.progress[info.code], s.phraseCounts[info.code], onClick = { onLang(info.code) }) }
                    item(span = { GridItemSpan(2) }) {
                        Column(Modifier.fillMaxWidth()) {
                            Spacer(Modifier.height(Spacing.S))
                            SectionDivider(fullBleed = false)
                            Spacer(Modifier.height(Spacing.S))
                            MonoLabel("BENCHED")
                        }
                    }
                    items(bench) { info -> LangTile(info.name, s.progress[info.code], s.phraseCounts[info.code], onClick = { onLang(info.code) }) }
                }
            }
        }
    }
}

@Composable
private fun LangTile(name: String, progress: Pair<Int, Int>?, phraseCount: Int?, onClick: () -> Unit) {
    val (viewed, total) = progress ?: (0 to 0)
    val started = viewed > 0 && total > 0
    val frac = if (total > 0) (viewed.toFloat() / total).coerceIn(0f, 1f) else 0f
    Column(
        Modifier
            .fillMaxWidth()
            .borderedCard()
            .flatClickable(onClick)
            .padding(Spacing.M)
    ) {
        val scale = LocalDensity.current.fontScale
        val nameSize = if (scale > 1.3f) 13.sp else 17.sp
        Text(
            name,
            fontSize = nameSize,
            color = LangColors.Ink,
            maxLines = 1
        )
        Spacer(Modifier.height(Spacing.Xs))
        // Honest progress: lessons opened out of lessons available.
        MonoLabel("$viewed / $total LESSONS")
        if (phraseCount != null) {
            Spacer(Modifier.height(Spacing.Xs))
            MonoLabel("$phraseCount PHRASES")
        }
        Spacer(Modifier.height(Spacing.S))
        // 2dp progress rule: hairline track, gold fill. No dot.
        Box(Modifier.fillMaxWidth().height(Spacing.Border).background(LangColors.Muted)) {
            if (frac > 0f) {
                Box(Modifier.fillMaxWidth(frac).height(Spacing.Border).background(LangColors.Accent))
            }
        }
        Spacer(Modifier.height(Spacing.Xs))
        MonoLabel(
            if (!started) "START" else "${(frac * 100).toInt()}% OF LESSONS",
            color = if (!started) LangColors.Accent else LangColors.Muted
        )
    }
}
