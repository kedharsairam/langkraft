package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.presentation.theme.Spacing
import com.kedharsairam.langkraft.presentation.viewmodels.NumbersUiState
import com.kedharsairam.langkraft.presentation.viewmodels.NumbersViewModel

@Composable
fun NumbersScreen(code: String, onBack: () -> Unit, vm: NumbersViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    LaunchedEffect(code) { vm.load(code) }
    Column(modifier = Modifier.padding(horizontal = Spacing.Screen)) {
        Spacer(Modifier.height(Spacing.S))
        TextButton(onClick = onBack, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) { Text("← ${code.uppercase()}") }
        Text("Numbers", style = MaterialTheme.typography.titleLarge)
        Text("Tap to hide. Say it aloud.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.M))
        when (val s = state) {
            NumbersUiState.Loading -> Text("Loading…")
            is NumbersUiState.Failed -> Text("Could not load numbers.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            is NumbersUiState.Ready -> LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                verticalArrangement = Arrangement.spacedBy(Spacing.S)
            ) {
                items(s.entries.take(20)) { entry ->
                    var hidden by remember(entry.value) { mutableStateOf(false) }
                    Column(modifier = Modifier.clickable { hidden = !hidden }.padding(vertical = Spacing.S)) {
                        Text(entry.value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (hidden) "• • •" else entry.youSay, style = MaterialTheme.typography.bodyMedium)
                        if (!hidden) Text(entry.note, style = MaterialTheme.typography.labelMedium)
                    }
                }
                item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(5) }) {
                    Text("21-99: tens + y + units. 100 = cien, 1000 = mil.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = Spacing.M))
                }
                items(s.entries.drop(20)) { entry ->
                    var hidden by remember(entry.value) { mutableStateOf(false) }
                    Column(modifier = Modifier.clickable { hidden = !hidden }.padding(vertical = Spacing.S)) {
                        Text(entry.value, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (hidden) "• • •" else entry.youSay, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
