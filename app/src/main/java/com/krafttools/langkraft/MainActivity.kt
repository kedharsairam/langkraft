package com.krafttools.langkraft

import android.app.Application
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
import com.krafttools.langkraft.data.ProgressStore
import com.krafttools.langkraft.data.SearchIndex
import com.krafttools.langkraft.ui.LangKraftTheme
import com.krafttools.langkraft.ui.LanguageListScreen
import com.krafttools.langkraft.ui.PathScreen
import com.krafttools.langkraft.ui.SearchScreen
import com.krafttools.langkraft.ui.TierScreen

/**
 * Everything mutable the app holds, in one place.
 *
 * Deliberately not a ViewModel hierarchy: there is exactly one store, one index, and one
 * corpus for the whole process lifetime, none of which changes while the app runs. A
 * ViewModel per screen would imply state that does not exist.
 */
class AppState(
    val corpus: ContentRepository.Corpus,
    val progress: ProgressStore,
) {
    val search: SearchIndex by lazy { SearchIndex(corpus) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LangKraftTheme {
                // Parsed once, remembered for the process lifetime. The corpus is
                // read-only and shipped in the APK, so there is nothing to refresh and
                // nothing to invalidate.
                val state = remember {
                    AppState(
                        corpus = ContentRepository.from(applicationContext).load(),
                        progress = ProgressStore(applicationContext),
                    )
                }
                LangKraftNav(state)
            }
        }
    }
}

private object Routes {
    const val LANGUAGES = "languages"
    const val PATH = "path/{lang}"
    const val TIER = "tier/{lang}/{tier}"
    const val SEARCH = "search/{lang}"

    fun path(lang: String) = "path/$lang"
    fun tier(lang: String, tier: Int) = "tier/$lang/$tier"
    fun search(lang: String) = "search/$lang"
}

@Composable
private fun LangKraftNav(state: AppState) {
    val nav = rememberNavController()
    val corpus = state.corpus

    NavHost(navController = nav, startDestination = Routes.LANGUAGES) {
        composable(Routes.LANGUAGES) {
            LanguageListScreen(
                corpus = corpus,
                positions = state.progress.allPositions(),
                onOpen = { lang -> nav.navigate(Routes.path(lang)) },
            )
        }

        composable(
            Routes.PATH,
            arguments = listOf(navArgument("lang") { type = NavType.StringType }),
        ) { backStack ->
            val lang = backStack.arguments?.getString("lang").orEmpty()
            val spec = corpus.spec(lang)
            if (spec == null) {
                // A language with no spec cannot render: the spec decides the variety,
                // the direction and the tiers, all three of which the screens require.
                nav.popBackStack()
            } else {
                PathScreen(
                    spec = spec,
                    corpus = corpus,
                    positions = state.progress.allPositions(),
                    onOpenTier = { tier -> nav.navigate(Routes.tier(lang, tier)) },
                    onOpenSearch = { nav.navigate(Routes.search(lang)) },
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
                val startAt = state.progress.position(lang, tier)
                TierScreen(
                    spec = spec,
                    tier = tier,
                    corpus = corpus,
                    startIndex = startAt,
                    onPosition = { index -> state.progress.setPosition(lang, tier, index) },
                    onFlag = { id, country -> state.progress.addFlag(lang, id, country) },
                    onBack = { nav.popBackStack() },
                )
            }
        }

        composable(
            Routes.SEARCH,
            arguments = listOf(navArgument("lang") { type = NavType.StringType }),
        ) { backStack ->
            val lang = backStack.arguments?.getString("lang").orEmpty()
            val spec = corpus.spec(lang)
            if (spec == null) {
                nav.popBackStack()
            } else {
                SearchScreen(
                    // state.search is a lazy on AppState, so it is built once per process.
                    index = state.search,
                    spec = spec,
                    onFlag = { entry, country ->
                        state.progress.addFlag(lang, entry.id, country)
                    },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}