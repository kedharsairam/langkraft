package com.kedharsairam.langkraft.presentation.screens

import com.kedharsairam.langkraft.presentation.components.displaySource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.presentation.components.*
import com.kedharsairam.langkraft.presentation.theme.*
import com.kedharsairam.langkraft.domain.NumberEntry
import com.kedharsairam.langkraft.presentation.viewmodels.NumbersUiState
import com.kedharsairam.langkraft.presentation.viewmodels.NumbersViewModel

@Composable
fun NumbersScreen(code: String, onBack: () -> Unit, vm: NumbersViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    LaunchedEffect(code) { vm.load(code) }
    Box(Modifier.fillMaxSize().worldMapDots()) {
        Column(Modifier.padding(horizontal = Spacing.Screen).fillMaxSize()) {
            KTopBar("Numbers · ${code.uppercase()}", onBack = onBack)
            Spacer(Modifier.height(Spacing.S))
            when (val s = state) {
                NumbersUiState.Loading -> KLabel("LOADING…")
                is NumbersUiState.Failed -> KLabel("COULD NOT LOAD NUMBERS.")
                is NumbersUiState.Ready -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S),
                    contentPadding = PaddingValues(bottom = Spacing.Screen)
                ) {
                    items(s.entries, key = { "n-${it.value}" }) { entry -> NumberCell(entry) }
                }
            }
        }
    }
}

@Composable
private fun NumberCell(entry: NumberEntry) {
    Column(Modifier.fillMaxWidth().kraftPanel().padding(Spacing.M)) {
        Text(entry.value, style = LangTypography.titleLarge, color = LangColors.Accent)
        Spacer(Modifier.height(Spacing.Xs))
        Text(entry.word, style = LangTypography.headlineMedium, color = LangColors.Ink)
        Spacer(Modifier.height(Spacing.Xs))
        Text(entry.youSay.replace("-", "-\u200B"), style = YouSayStyle,
            fontSize = 18.sp, color = LangColors.Accent)
        if (entry.note.isNotBlank()) {
            Spacer(Modifier.height(Spacing.Xs))
            Text(entry.note, style = LangTypography.bodySmall, color = LangColors.Muted)
        }
        Spacer(Modifier.height(Spacing.Xs))
        KLabel("${displaySource(entry.source)} · ${entry.checked}")
    }
}
