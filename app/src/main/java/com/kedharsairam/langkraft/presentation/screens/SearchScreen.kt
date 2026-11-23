package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.sp
import com.kedharsairam.langkraft.domain.Phrase
import com.kedharsairam.langkraft.presentation.components.*
import com.kedharsairam.langkraft.presentation.theme.*
import com.kedharsairam.langkraft.presentation.viewmodels.SearchUiState
import com.kedharsairam.langkraft.presentation.viewmodels.SearchViewModel

@Composable
fun SearchScreen(onOpenLang: (String) -> Unit, onBack: (() -> Unit)? = null, vm: SearchViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var query by remember { mutableStateOf("") }
    Column(Modifier.padding(horizontal = Spacing.Screen).fillMaxSize()) {
        KTopBar("Search", onBack)
        Spacer(Modifier.height(Spacing.S))
        Box(Modifier.fillMaxWidth().kraftPanel().padding(Spacing.M)) {
            androidx.compose.foundation.text.BasicTextField(
                value = query, onValueChange = { query = it; vm.search(it) }, singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = KraftFonts.Mono, fontSize = 16.sp, color = LangColors.Ink),
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Search phrases" },
                decorationBox = { inner ->
                    if (query.isEmpty()) Text("English, romanization, or you say", color = LangColors.Muted,
                        fontFamily = KraftFonts.Mono, fontSize = 16.sp)
                    inner()
                })
        }
        Spacer(Modifier.height(Spacing.S))
        when (val s = state) {
            SearchUiState.Idle -> Text("Search across 19 languages.", color = LangColors.Muted)
            SearchUiState.Loading -> KLabel("SEARCHING…")
            is SearchUiState.Ready -> {
                if (s.results.isEmpty()) Text("No matches.", color = LangColors.Muted)
                else LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.Xs)) {
                    s.results.groupBy { it.lang }.forEach { (lang, list) ->
                        item(key = "h-$lang") { Spacer(Modifier.height(Spacing.S)); KSection(lang.uppercase()) }
                        items(list, key = { it.id }) { p -> ResultRow(p) { onOpenLang(lang) } }
                    }
                    if (s.truncated) item { Spacer(Modifier.height(Spacing.S)); KLabel("SHOWING FIRST 60 — NARROW YOUR SEARCH") }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(p: Phrase, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().kraftClick(onClick).padding(vertical = Spacing.S)) {
        Text(p.english, style = LangTypography.bodyMedium, color = LangColors.Ink)
        Spacer(Modifier.height(Spacing.Xs))
        Text(p.romanization, style = RomanizationStyle, color = LangColors.Muted)
        Spacer(Modifier.height(Spacing.Xs))
        Text(p.youSay, style = YouSayStyle, color = LangColors.Accent)
    }
    RowDivider()
}
