package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.presentation.theme.Spacing
import com.kedharsairam.langkraft.presentation.viewmodels.NumbersUiState
import com.kedharsairam.langkraft.presentation.viewmodels.NumbersViewModel

@Composable
fun NumbersScreen(code: String, onBack: () -> Unit, vm: NumbersViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    LaunchedEffect(code) { vm.load(code) }
    var patternOpen by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = Spacing.Screen)) {
        Spacer(Modifier.height(Spacing.S))
        TextButton(onClick = onBack, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) { Text("← ${code.uppercase()}") }
        Text("Numbers", style = MaterialTheme.typography.titleLarge)
        Text("Tap to hide. Say it aloud.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.M))
        when (val s = state) {
            NumbersUiState.Loading -> Text("Loading…")
            is NumbersUiState.Failed -> Text("Could not load numbers. [ RELOAD LOCAL DATA ]", fontFamily = FontFamily.Monospace)
            is NumbersUiState.Ready -> {
                // 2 columns so mono caps do not wrap mid-syllable (Operator Deck dense but legible)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                    verticalArrangement = Arrangement.spacedBy(Spacing.S),
                    contentPadding = PaddingValues(bottom = 16.dp)
                ) {
                    items(s.entries.take(21)) { entry ->
                        var hidden by remember(entry.value) { mutableStateOf(false) }
                        Column(
                            modifier = Modifier.defaultMinSize(minHeight = 56.dp)
                                .clickable { hidden = !hidden }.padding(vertical = Spacing.S)
                        ) {
                            Text(entry.value, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (hidden) "• • •" else entry.youSay, fontFamily = FontFamily.Monospace, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (!hidden) Text(entry.note, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    item(span = { GridItemSpan(2) }) {
                        Column(modifier = Modifier.clickable { patternOpen = !patternOpen }.padding(vertical = Spacing.S)) {
                            Text("21–99 PATTERN  ${if (patternOpen) "−" else "+"}", fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                            if (patternOpen) Text("TENS + y + UNITS. Example: 31 = TREHN-tah y OO-noh.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    items(s.entries.drop(21).filter { it.value in setOf("100", "1000") }) { entry ->
                        var hidden by remember(entry.value) { mutableStateOf(false) }
                        Column(modifier = Modifier.defaultMinSize(minHeight = 56.dp).clickable { hidden = !hidden }.padding(vertical = Spacing.S)) {
                            Text(entry.value, fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if (hidden) "• • •" else entry.youSay, fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
