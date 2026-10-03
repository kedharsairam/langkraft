package com.krafttools.langkraft

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.krafttools.langkraft.data.ContentRepository
import com.krafttools.langkraft.ui.LangKraftTheme
import com.krafttools.langkraft.ui.LanguageListScreen
import com.krafttools.langkraft.ui.PathScreen
import com.krafttools.langkraft.ui.TierScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LangKraftTheme {
                // Parsed once, remembered for the process lifetime. The corpus is
                // read-only and shipped in the APK, so there is nothing to refresh
                // and nothing to invalidate.
                val corpus = remember { ContentRepository.from(applicationContext).load() }
                LangKraftNav(corpus)
            }
        }
    }
}

private object Routes {
    const val LANGUAGES = "languages"
    const val PATH = "path/{lang}"
    const val TIER = "tier/{lang}/{tier}"

    fun path(lang: String) = "path/$lang"
    fun tier(lang: String, tier: Int) = "tier/$lang/$tier"
}

@Composable
private fun LangKraftNav(corpus: ContentRepository.Corpus) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = Routes.LANGUAGES) {
        composable(Routes.LANGUAGES) {
            LanguageListScreen(corpus) { lang -> nav.navigate(Routes.path(lang)) }
        }

        composable(
            Routes.PATH,
            arguments = listOf(navArgument("lang") { type = NavType.StringType }),
        ) { backStack ->
            val lang = backStack.arguments?.getString("lang").orEmpty()
            val spec = corpus.spec(lang)
            if (spec == null) {
                // A language with no spec cannot render: the spec decides the variety,
                // the script and the tiers, all three of which the screens require.
                nav.popBackStack()
            } else {
                PathScreen(
                    spec = spec,
                    corpus = corpus,
                    onOpenTier = { tier -> nav.navigate(Routes.tier(lang, tier)) },
                    onBack = { nav.popBackStack() },
                )
            }
        }

        composable(
            Routes.TIER,
            arguments = listOf(
                navArgument("lang") { type = NavType.StringType },
                navArgument("tier") { type = NavType.IntType },
            ),
        ) { backStack ->
            val lang = backStack.arguments?.getString("lang").orEmpty()
            val tier = backStack.arguments?.getInt("tier") ?: 0
            val spec = corpus.spec(lang)
            if (spec == null) {
                nav.popBackStack()
            } else {
                TierScreen(spec = spec, tier = tier, corpus = corpus, onBack = { nav.popBackStack() })
            }
        }
    }
}