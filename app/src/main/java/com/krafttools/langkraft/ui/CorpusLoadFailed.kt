package com.krafttools.langkraft.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shown instead of the app when its own bundled content cannot be parsed.
 *
 * **Why this exists.** The corpus is parsed inside `setContent`, and every parse failure
 * was an uncaught exception escaping composition. A truncated `content.jsonl`, a `[]`
 * in `specs.json`, a malformed line — each killed the process before anything rendered,
 * with a stack trace in a logcat the learner will never see. For an app whose entire
 * premise is "every byte ships in the APK and nothing is fetched", a corrupt APK is
 * unrecoverable by the user: there is no download to retry. Refusing to open with a
 * legible reason is the honest outcome.
 *
 * **What it deliberately does not do.**
 *
 *  - No "Try again" button. Retrying cannot help; the bytes are the same bytes. A retry
 *    affordance that cannot succeed is worse than none.
 *  - No fallback to an empty corpus. An empty phrasebook looks like a working app with
 *    nothing in it, and the learner would conclude the language is unsupported.
 *  - No network "repair". The app has no network permission, and adding one to fix a
 *    broken bundle would violate the constraint this whole project is built on.
 *
 * The exception text is shown because it is the only thing that makes the failure
 * diagnosable, and it is a developer message on a screen a developer will be the one
 * reading.
 */
@Composable
fun CorpusLoadFailed(cause: Throwable) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                // A raw Surface gets no inset handling, so the heading drew under the
                // status bar — the same defect SearchBar had. Found on the first real
                // use of this screen, which is the argument for having written it legibly.
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Bundled content is damaged",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                "LangKraft keeps every phrase inside the app, so there is nothing to " +
                    "download and nothing to repair at runtime. This copy did not pass " +
                    "its own checks and cannot be opened safely — showing half a " +
                    "phrasebook would be worse than showing none.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Reinstalling the app should fix it. If it does not, this build's " +
                    "content assets are the problem.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "Details",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                cause.toString(),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
