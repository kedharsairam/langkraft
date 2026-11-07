package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
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
        Spacer(Modifier.height(Spacing.S))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("LANGKRAFT", fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text("Open where you left it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
                IconButton(onClick = onHard, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Star, contentDescription = "Hard")
                }
            }
        }
        Spacer(Modifier.height(Spacing.S))
        when (val s = state) {
            HomeUiState.Loading -> CircularProgressIndicator(Modifier.padding(top = Spacing.M))
            is HomeUiState.Failed -> Column {
                Text("Could not load languages.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { vm.load() }) { Text("Retry") }
            }
            is HomeUiState.Ready -> {
                Spacer(Modifier.height(Spacing.S))
                LazyVerticalGrid(columns = GridCells.Fixed(2), horizontalArrangement = Arrangement.spacedBy(Spacing.S), verticalArrangement = Arrangement.spacedBy(Spacing.S), modifier = Modifier.weight(1f)) {
                    val committed = s.languages.filter { it.status == "committed" }
                    items(committed) { info ->
                        val (viewed, total) = s.progress[info.code] ?: (0 to 40)
                        val frac = if (total > 0) viewed.toFloat() / total else 0f
                        Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.medium) {
                            Column(modifier = Modifier.clickable(role = Role.Button) { onLang(info.code) }.padding(Spacing.S).defaultMinSize(minHeight = 72.dp)) {
                                Text("[${info.code.uppercase()}]", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(info.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                                Text("[$viewed/$total]", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(Spacing.Xs))
                                LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth().height(4.dp), color = MaterialTheme.colorScheme.primary, trackColor = MaterialTheme.colorScheme.surface)
                                if (viewed == 0) Text("START", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                else Text("${((frac * 100).toInt())}%", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
