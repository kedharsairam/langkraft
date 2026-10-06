package com.krafttools.langkraft

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import com.krafttools.langkraft.data.FailureFlag
import com.krafttools.langkraft.data.FlagExport
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
import com.krafttools.langkraft.ui.CorpusLoadFailed
import com.krafttools.langkraft.ui.CreditsScreen
import com.krafttools.langkraft.ui.FirstRunScreen
import com.krafttools.langkraft.ui.FlagsScreen
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

    /** Every language that has at least one flag, for the export. */
    fun flaggableLanguages(): Map<String, List<FailureFlag>> =
        corpus.specs.mapNotNull { spec ->
            progress.flagsFor(spec.code).takeIf { it.isNotEmpty() }
                ?.let { spec.code to it }
        }.toMap()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Explicitly dark system bars. The default enableEdgeToEdge() branches on the
        // SYSTEM's dark-mode setting, but this app has no light theme — KraftDark is
        // applied unconditionally. On a device with system dark mode off, the default
        // asks for dark icons on a near-black background, and the clock and battery
        // disappear. It also paints a 90%-white scrim behind a 3-button nav bar.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setContent {
            LangKraftTheme {
                val snackbar = remember { SnackbarHostState() }
                val scope = rememberCoroutineScope()
                // Parsed once, remembered for the process lifetime. The corpus is
                // read-only and shipped in the APK, so there is nothing to refresh and
                // nothing to invalidate.
                //
                // runCatching because this runs inside composition and NOTHING above it
                // catches. A truncated content.jsonl or a `[]` in specs.json used to throw
                // straight out of setContent and kill the process with nothing on screen.
                // For an app that ships all its own bytes and cannot fetch replacements,
                // a corrupt APK is unrecoverable by the learner, so the failure has to be
                // legible rather than a stack trace in a logcat nobody reads.
                val loaded = remember { runCatching { ContentRepository.from(applicationContext).load() } }

                loaded.fold(
                    onSuccess = { corpus ->
                        val progress = ProgressStore(applicationContext)
                        val appState = AppState(corpus, progress)

                        // ACTION_CREATE_DOCUMENT rather than a file path: the app holds no
                        // storage permission, by rule, and this is the only way to hand the
                        // reader a file without asking for one.
                        val exportLauncher = rememberLauncherForActivityResult(
                            ActivityResultContracts.CreateDocument("application/json")
                        ) { uri ->
                            if (uri == null) return@rememberLauncherForActivityResult
                            val flags = appState.flaggableLanguages()
                            if (flags.isEmpty()) return@rememberLauncherForActivityResult
                            val doc = FlagExport.buildDocument(corpus, flags)
                            val n = FlagExport.write(applicationContext, uri, doc)
                            scope.launch {
                                snackbar.showSnackbar(
                                    if (n >= 0) {
                                        "Saved ${flags.values.sumOf { it.size }} flags. " +
                                            "Thank you — this is how the phrases get fixed."
                                    } else {
                                        "Could not write that file."
                                    }
                                )
                            }
                        }

                        Box(Modifier.fillMaxSize()) {
                            LangKraftNav(
                                state = appState,
                                snackbar = snackbar,
                                onExportFlags = { exportLauncher.launch("langkraft-flags.json") },
                                introSeen = progress.meta(ProgressStore.META_INTRO_SEEN) != null,
                                onIntroDismissed = {
                                    progress.setMeta(ProgressStore.META_INTRO_SEEN, "1")
                                },
                            )
                        }
                    },
                    onFailure = { CorpusLoadFailed(it) },
                )
            }
        }
    }
}

private object Routes {
    const val INTRO = "intro"
    const val CREDITS = "credits"
    const val FLAGS = "flags"
    const val LANGUAGES = "languages"
    const val PATH = "path/{lang}"
    const val TIER = "tier/{lang}/{tier}"
    const val SEARCH = "search/{lang}"

    fun path(lang: String) = "path/$lang"
    fun tier(lang: String, tier: Int) = "tier/$lang/$tier"
    fun search(lang: String) = "search/$lang"
}

@Composable
private fun LangKraftNav(
    state: AppState,
    snackbar: SnackbarHostState,
    onExportFlags: () -> Unit,
    introSeen: Boolean,
    onIntroDismissed: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val nav = rememberNavController()
    val corpus = state.corpus

    NavHost(
        navController = nav,
        // The intro is the start destination only until it has been read once. Making it a
        // real destination rather than a boolean gate means back navigation behaves, and it
        // cannot reappear on its own.
        startDestination = if (introSeen) Routes.LANGUAGES else Routes.INTRO,
    ) {
        composable(Routes.INTRO) {
            FirstRunScreen(onContinue = {
                onIntroDismissed()
                nav.navigate(Routes.LANGUAGES) {
                    popUpTo(Routes.INTRO) { inclusive = true }
                }
            })
        }

        composable(Routes.CREDITS) {
            CreditsScreen(
                appCredits = corpus.credits,
                specs = corpus.specs,
                onBack = { nav.popBackStack() },
            )
        }

        composable(Routes.FLAGS) {
            FlagsScreen(
                corpus = corpus,
                flags = state.flaggableLanguages(),
                onBack = { nav.popBackStack() },
                onExport = onExportFlags,
            )
        }

        composable(Routes.LANGUAGES) {
            LanguageListScreen(
                corpus = corpus,
                positions = state.progress.allPositions(),
                onOpen = { lang -> nav.navigate(Routes.path(lang)) },
                onCredits = { nav.navigate(Routes.CREDITS) },
                // Tapping Flags now OPENS THE LIST rather than jumping straight to a file.
                // Export is an action on that screen, where the reader can see what they are
                // about to send. Exporting straight from the toolbar meant the reader's only
                // view of their own notes was a JSON document, which is the end of the pipeline
                // and not a place to start.
                onExportFlags = { nav.navigate(Routes.FLAGS) },
                flagCount = state.flaggableLanguages().values.sumOf { it.size },
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
                    onFlag = { id, country ->
                        state.progress.addFlag(lang, id, country)
                        scope.launch {
                            snackbar.showSnackbar(
                                if (country == null) {
                                    "Noted. Add a country next time and it becomes a fix."
                                } else {
                                    "Noted — $country. Thank you."
                                }
                            )
                        }
                    },
                    onCopy = { entry ->
                        clipboard.setText(AnnotatedString(entry.copyText()))
                        scope.launch { snackbar.showSnackbar("Copied.") }
                    },
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
                        state.progress.addFlag(lang, "entry:" + entry.id, country)
                    },
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}