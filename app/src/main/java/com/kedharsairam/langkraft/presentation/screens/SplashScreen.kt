package com.kedharsairam.langkraft.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kedharsairam.langkraft.presentation.components.GlobeMark
import com.kedharsairam.langkraft.presentation.components.worldMapDots
import com.kedharsairam.langkraft.presentation.theme.*

/** Brand splash — the ledger-and-globe poster from the visual spec. */
@Composable
fun SplashScreen() {
    Box(Modifier.fillMaxSize().worldMapDots()) {
        Column(Modifier.fillMaxSize().padding(horizontal = Spacing.Screen),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center) {
            GlobeMark(88.dp)
            Spacer(Modifier.height(Spacing.L))
            Text("Langkraft", style = LangTypography.displayLarge, color = LangColors.Ink)
            Spacer(Modifier.height(Spacing.S))
            Box(Modifier.size(160.dp, 2.dp).background(LangColors.Accent))
            Spacer(Modifier.height(Spacing.M))
            Text("A Romanization-Only Travel Phrasebook",
                style = LangTypography.titleMedium, color = LangColors.Accent)
            Spacer(Modifier.height(Spacing.Xl))
            Text("19 LANGUAGES  ·  35 LESSONS", style = MonoLabelStyle, color = LangColors.Muted)
            Spacer(Modifier.height(Spacing.Xs))
            Text("100% OFFLINE", style = MonoLabelStyle, color = LangColors.Accent)
        }
    }
}
