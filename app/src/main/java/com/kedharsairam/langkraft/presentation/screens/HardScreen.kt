package com.kedharsairam.langkraft.presentation.screens

import com.kedharsairam.langkraft.presentation.components.displaySource

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kedharsairam.langkraft.presentation.components.*
import com.kedharsairam.langkraft.presentation.theme.LangColors
import com.kedharsairam.langkraft.presentation.theme.Spacing
import com.kedharsairam.langkraft.presentation.viewmodels.HardUiState
import com.kedharsairam.langkraft.presentation.viewmodels.HardViewModel

@Composable
fun HardScreen(
    onBack: () -> Unit,
    onOpenPhrase: (String, Int) -> Unit = { _, _ -> },
    vm: HardViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    Column(Modifier.padding(horizontal = Spacing.Screen).fillMaxSize()) {
        KTopBar("Hard", onBack = onBack)
        Spacer(Modifier.height(Spacing.S))
        Text("Your starred pile. Review when you want.", style = androidx.compose.material3.MaterialTheme.typography.bodySmall, color = LangColors.Muted)
        Spacer(Modifier.height(Spacing.M))
        when (val s = state) {
            HardUiState.Loading -> KLabel("LOADING…")
            is HardUiState.Failed -> Text("Could not load hard pile.", color = LangColors.Muted)
            is HardUiState.Ready -> {
                if (s.phrases.isEmpty()) {
                    Column(Modifier.fillMaxWidth().kraftPanel().padding(Spacing.M)) {
                        Text("No hard phrases yet. Star any phrase to add it here.", color = LangColors.Ink)
                    }
                } else {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        items(s.phrases, key = { it.phrase.id }) { entry ->
                            Column(
                                Modifier.fillMaxWidth().kraftPanel()
                                    .kraftClick { onOpenPhrase(entry.lang, entry.phrase.theme) }.padding(Spacing.M)
                            ) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text(entry.phrase.english, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                                        color = LangColors.Ink, modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.Star, contentDescription = "Hard", tint = LangColors.StateHard, modifier = Modifier.size(24.dp))
                                }
                                Spacer(Modifier.height(Spacing.Xs))
                                Text(entry.phrase.romanization, fontFamily = FontFamily.Monospace, fontSize = 16.sp, color = LangColors.Muted)
                                Spacer(Modifier.height(Spacing.Xs))
                                Text(entry.phrase.youSay, fontFamily = FontFamily.Monospace,
                                    fontSize = 18.sp, fontWeight = FontWeight.Bold, color = LangColors.Ink)
                                Spacer(Modifier.height(Spacing.Xs))
                                KLabel("${entry.lang.uppercase()}  ·  ${displaySource(entry.phrase.source)}  ·  ${entry.phrase.checked}")
                            }
                        }
                    }
                }
            }
        }
    }
}
