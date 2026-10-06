package com.krafttools.langkraft.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

/**
 * One top bar for every screen that has somewhere to go back to.
 *
 * This exists because the first version of these screens passed an `onBack` lambda into
 * a `navigationIcon` slot and rendered a bare `Text("‹")`. It looked correct, and a
 * `uiautomator dump` on a Realme RMX3998 showed the chevron had **no clickable node at
 * all** — drawn, correct size, completely inert. The compiler could not catch it,
 * because the parameter was accepted and then ignored.
 *
 * Two things are enforced here rather than left to each screen:
 *
 *  1. The back control is an `IconButton`, so it is clickable by construction.
 *  2. `onBack` is required, not optional. There is no way to render this bar without
 *     wiring the handler.
 *
 * `automirrored` is deliberate: the arrow has to point the right way in the Arabic and
 * Arabic screens that come later, and a hardcoded back arrow is wrong in every one of them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KraftTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    TopAppBar(
        title = {
            if (subtitle == null) {
                Text(title)
            } else {
                Text("$title · $subtitle")
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                )
            }
        },
        actions = {
            // Search is the one thing a reader needs mid-sentence: you are standing in
            // front of something and you need one specific phrase in two seconds.
            if (actionLabel != null && onAction != null) {
                TextButton(onClick = onAction) { Text(actionLabel) }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}