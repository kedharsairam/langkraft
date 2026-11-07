package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.presentation.theme.Spacing
import com.kedharsairam.langkraft.presentation.viewmodels.HardUiState
import com.kedharsairam.langkraft.presentation.viewmodels.HardViewModel

@Composable
fun HardScreen(onBack: () -> Unit, vm: HardViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    Column(modifier = Modifier.padding(horizontal = Spacing.Screen)) {
        Spacer(Modifier.height(Spacing.S))
        TextButton(onClick = onBack, modifier = Modifier.defaultMinSize(minHeight = Spacing.MinTarget)) { Text("← HOME") }
        Text("Hard", style = MaterialTheme.typography.titleLarge)
        Text("Your starred pile. Review when you want.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Spacing.M))
        when (val s = state) {
            HardUiState.Loading -> Text("Loading…")
            is HardUiState.Failed -> Text("Could not load hard pile.")
            is HardUiState.Ready -> {
                if (s.phrases.isEmpty()) Text("STATUS: ZERO_ENTRIES_FOUND. Tap the star on any phrase to add it here.", fontFamily = FontFamily.Monospace)
                else LazyColumn {
                    items(s.phrases) { phrase ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.S)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(phrase.english, style = MaterialTheme.typography.bodyMedium)
                                Icon(Icons.Filled.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                            Text(phrase.youSay, fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                            Text("${phrase.lang.uppercase()} · ${phrase.id}", fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        HorizontalDivider(thickness = Spacing.Hairline)
                    }
                }
            }
        }
    }
}
