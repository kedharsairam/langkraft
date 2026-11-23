package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.presentation.components.KraftTopBar
import com.kedharsairam.langkraft.presentation.components.MonoLabel
import com.kedharsairam.langkraft.presentation.components.borderedCard
import com.kedharsairam.langkraft.presentation.components.displaySource
import com.kedharsairam.langkraft.presentation.components.flatClickable
import com.kedharsairam.langkraft.domain.NumberEntry
import com.kedharsairam.langkraft.presentation.theme.LangColors
import com.kedharsairam.langkraft.presentation.theme.Spacing
import com.kedharsairam.langkraft.presentation.viewmodels.NumbersUiState
import com.kedharsairam.langkraft.presentation.viewmodels.NumbersViewModel

@Composable
fun NumbersScreen(code: String, onBack: () -> Unit, vm: NumbersViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    LaunchedEffect(code) { vm.load(code) }
    var patternOpen by remember { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = Spacing.Screen).fillMaxSize()) {
        KraftTopBar("Numbers · ${code.uppercase()}", onBack = onBack)
        Spacer(Modifier.height(Spacing.S))
        when (val s = state) {
            NumbersUiState.Loading -> MonoLabel("LOADING…")
            is NumbersUiState.Failed -> MonoLabel("COULD NOT LOAD NUMBERS.")
            is NumbersUiState.Ready -> {
                // Pattern hint built from this language's own data, never a hardcoded example.
                val one = s.entries.firstOrNull { it.value == "1" }?.word ?: ""
                val ten = s.entries.firstOrNull { it.value == "10" }?.word ?: ""
                val twenty = s.entries.firstOrNull { it.value == "20" }?.word ?: ""
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S),
                    contentPadding = PaddingValues(bottom = Spacing.Screen)
                ) {
                    items(s.entries.take(21), key = { "n-${it.value}" }) { entry -> NumberCell(entry) }
                    item(span = { GridItemSpan(2) }) {
                        Column(
                            Modifier.fillMaxWidth().borderedCard().flatClickable { patternOpen = !patternOpen }.padding(Spacing.M)
                        ) {
                            MonoLabel("21–99 PATTERN  ${if (patternOpen) "−" else "+"}", color = LangColors.Ink)
                            if (patternOpen) {
                                Spacer(Modifier.height(Spacing.S))
                                Text(
                                    "Build the rest from these: $one, $ten, $twenty. The connector between tens and units differs by language — learn it by ear.",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = LangColors.Muted
                                )
                            }
                        }
                    }
                    items(s.entries.drop(21), key = { "n-${it.value}" }) { entry -> NumberCell(entry) }
                }
            }
        }
    }
}

@Composable
private fun NumberCell(entry: NumberEntry) {
    Column(Modifier.fillMaxWidth().borderedCard().padding(Spacing.M)) {
        MonoLabel(entry.value)
        Spacer(Modifier.height(Spacing.Xs))
        // The actual target-language spelling — the thing being learned.
        Text(entry.word, fontFamily = FontFamily.Monospace, fontSize = 18.sp, color = LangColors.Ink,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(Spacing.Xs))
        Text(entry.youSay, fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold,
            color = LangColors.Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (entry.note.isNotBlank()) {
            Spacer(Modifier.height(Spacing.Xs))
            Text(entry.note, style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = LangColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(Spacing.Xs))
        MonoLabel("${displaySource(entry.source)}  ·  ${entry.checked}")
    }
}
