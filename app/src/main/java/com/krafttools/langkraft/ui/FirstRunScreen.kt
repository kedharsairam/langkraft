package com.krafttools.langkraft.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * First run. Shown once.
 *
 * **The line that matters is the second one.** Without it, the absence of audio reads as a
 * fault rather than a decision, and the tone section is where it reads worst: four Thai
 * words that all romanize to `tao`, presented silently, with no explanation. A learner
 * will reasonably conclude the app failed to load its sound. Every phrasebook with a
 * "tap to hear" button teaches that expectation, and this one breaks it on purpose.
 *
 * `CorpusLoadFailed` sets the standard the rest of the app is measured against: a
 * deliberate absence is stated as a deliberate absence. A first-run screen that did not
 * mention the audio would contradict it immediately.
 *
 * No permissions are requested here, because the app requests none. Nothing on this screen
 * is a gateway to anything.
 */
@Composable
fun FirstRunScreen(onContinue: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier.fillMaxSize().padding(28.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(56.dp),
            ) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "ไทย",
                        fontFamily = NotoSansThai,
                        fontSize = 22.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "LangKraft",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(16.dp))

            Text(
                "Everything is inside the app. No signal is needed, and none is used.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                // Said plainly, because a silent phrasebook reads as a broken one.
                "There is no sound. These phrases are for reading, remembering and " +
                    "showing. Nothing here will speak.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(12.dp))

            Text(
                "To hear a language, listen to one. Films and people are better at it " +
                    "than an app would be.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(32.dp))

            TextButton(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text("Choose a language", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}