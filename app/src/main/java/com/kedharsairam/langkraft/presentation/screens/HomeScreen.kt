package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
        Spacer(Modifier.height(Spacing.M))
        Text("LANGKRAFT", fontFamily = FontFamily.Monospace, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text("Open where you left it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.M))
        when (val s = state) {
            HomeUiState.Loading -> CircularProgressIndicator()
            is HomeUiState.Failed -> Column {
                Text("ERROR: ${s.reason::class.simpleName}", fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.error)
                TextButton(onClick = { vm.load() }) { Text("[ RELOAD LOCAL DATA ]") }
            }
            is HomeUiState.Ready -> LazyColumn {
                s.continue_?.let { (code, name) ->
                    item {
                        Column(modifier = Modifier.clickable(role = Role.Button) { onLang(code) }.padding(vertical = Spacing.S)) {
                            Text("CONTINUE", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(name, style = MaterialTheme.typography.headlineMedium)
                        }
                        HorizontalDivider(thickness = Spacing.Hairline)
                    }
                }
                item {
                    Text("QUEUE — COMMITTED", fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(Spacing.S))
                }
                val committed = s.languages.filter { it.status == "committed" }
                items(committed) { info ->
                    val (viewed, total) = s.progress[info.code] ?: (0 to 40)
                    Row(
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.MinTarget)
                            .clickable(role = Role.Button) { onLang(info.code) }.padding(vertical = Spacing.S),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("[${info.code.uppercase()}] ${info.name}", fontFamily = FontFamily.Monospace, fontSize = 16.sp)
                        Text("[$viewed/$total]", fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider(thickness = Spacing.Hairline)
                }
                item {
                    Spacer(Modifier.height(Spacing.L))
                    TextButton(onClick = { benchExpanded = !benchExpanded }, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) {
                        Text(if (benchExpanded) "- BENCHED" else "+ 7 BENCHED LANGUAGES (TAP TO EXPAND)")
                    }
                    if (benchExpanded) {
                        val bench = s.languages.filter { it.status != "committed" }
                        bench.forEach { info ->
                            Row(modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = Spacing.MinTarget)
                                .clickable(role = Role.Button) { onLang(info.code) }.padding(vertical = Spacing.S)) {
                                Text("[${info.code.uppercase()}] ${info.name}", fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            HorizontalDivider(thickness = Spacing.Hairline)
                        }
                    }
                }
            }
        }
    }
}
