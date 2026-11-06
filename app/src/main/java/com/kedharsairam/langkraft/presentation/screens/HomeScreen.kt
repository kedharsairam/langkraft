package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.presentation.theme.Spacing
import com.kedharsairam.langkraft.presentation.viewmodels.HomeUiState
import com.kedharsairam.langkraft.presentation.viewmodels.HomeViewModel

// Your pick Q1: two-row tiles. Q2: LingoHut flag-grid feel — simple lesson tiles, click-to-go.
// No continuous list. 2 columns.
@Composable
fun HomeScreen(
    onLang: (String) -> Unit,
    onSearch: () -> Unit,
    onHard: () -> Unit,
    vm: HomeViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    var benchExpanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.padding(horizontal = Spacing.Screen)) {
        Spacer(Modifier.height(Spacing.M))
        Text("LANGKRAFT", fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Open where you left it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.M))
        when (val s = state) {
            HomeUiState.Loading -> CircularProgressIndicator()
            is HomeUiState.Failed -> Column {
                Text("Could not load languages.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { vm.load() }) { Text("Retry") }
            }
            is HomeUiState.Ready -> {
                s.continue_?.let { (code, name) ->
                    Text("CONTINUE", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.clickable(role = Role.Button) { onLang(code) })
                    Spacer(Modifier.height(Spacing.M))
                }
                Text("QUEUE — COMMITTED", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(Spacing.S))
                LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                    val committed = s.languages.filter { it.status == "committed" }
                    items(committed) { info ->
                        val (viewed, total) = s.progress[info.code] ?: (0 to 40)
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                            Column(modifier = Modifier.clickable(role = Role.Button) { onLang(info.code) }.padding(Spacing.M).defaultMinSize(minHeight = 96.dp)) {
                                Text("[${info.code.uppercase()}]", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(info.name, style = MaterialTheme.typography.headlineMedium, maxLines = 1)
                                Text("[$viewed/$total]", fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                TextButton(onClick = { benchExpanded = !benchExpanded }, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) {
                    Text(if (benchExpanded) "- BENCHED" else "+ 7 BENCHED LANGUAGES")
                }
                if (benchExpanded) {
                    val bench = s.languages.filter { it.status != "committed" }
                    bench.forEach { info ->
                        Text("[${info.code.uppercase()}] ${info.name}", fontFamily = FontFamily.Monospace, fontSize = 15.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.clickable { onLang(info.code) }.padding(vertical = Spacing.S))
                    }
                }
            }
        }
    }
}
