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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.kedharsairam.langkraft.domain.Phrase
import com.kedharsairam.langkraft.presentation.components.*
import com.kedharsairam.langkraft.presentation.theme.*
import com.kedharsairam.langkraft.presentation.viewmodels.SearchUiState
import com.kedharsairam.langkraft.presentation.viewmodels.SearchViewModel

@Composable
fun SearchScreen(onOpenLang: (String, Int) -> Unit, onBack: (() -> Unit)? = null, vm: SearchViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    var query by remember { mutableStateOf("") }
    Box(Modifier.fillMaxSize().worldMapDots()) {
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
                is SearchUiState.Failed -> Text("Search unavailable — local data could not be read.", color = LangColors.Muted)
                is SearchUiState.Ready -> {
                    if (s.results.isEmpty()) Text("No matches.", color = LangColors.Muted)
                    else LazyColumn(verticalArrangement = Arrangement.spacedBy(Spacing.S)) {
                        s.results.groupBy { it.lang }.forEach { (lang, list) ->
                            item(key = "h-$lang") {
                                Spacer(Modifier.height(Spacing.S))
                                Text("${lang.uppercase()}  ·  ${languageName(lang)}",
                                    style = MonoLabelStyle.copy(color = LangColors.Accent))
                                Spacer(Modifier.height(Spacing.S))
                            }
                            items(list, key = { it.id }) { p -> ResultRow(p) { onOpenLang(lang, p.theme) } }
                        }
                        if (s.truncated) item { Spacer(Modifier.height(Spacing.S)); KLabel("SHOWING FIRST 60 — NARROW YOUR SEARCH") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(p: Phrase, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().kraftPanel().kraftClick(onClick).padding(Spacing.M)) {
        KLabel("%02d".format(1), color = LangColors.Accent)
        Spacer(Modifier.height(Spacing.Xs))
        Text(p.english, style = LangTypography.bodyMedium, color = LangColors.Ink)
        Spacer(Modifier.height(Spacing.Xs))
        Text(p.romanization, style = LangTypography.titleMedium, color = LangColors.Ink)
        Spacer(Modifier.height(Spacing.Xs))
        Text(p.youSay.replace("-", "-\u200B"), style = YouSayStyle, fontSize = 18.sp, color = LangColors.Accent)
    }
    Spacer(Modifier.height(Spacing.S))
}

private fun languageName(code: String): String = when (code) {
    "spa" -> "Spanish"; "ind" -> "Indonesian"; "por" -> "Portuguese"; "hin" -> "Hindi"
    "fra" -> "French"; "ara" -> "Arabic (Egyptian)"; "cmn" -> "Mandarin"; "swh" -> "Swahili"
    "rus" -> "Russian"; "vie" -> "Vietnamese"; "tha" -> "Thai"; "tur" -> "Turkish"
    "jpn" -> "Japanese"; "kor" -> "Korean"; "deu" -> "German"; "ita" -> "Italian"
    "fas" -> "Farsi"; "srp" -> "Serbian"; "tam" -> "Tamil"; else -> code.uppercase()
}
