/*
 * SPDX-License-Identifier: MIT
 * Copyright (c) 2026 Kedhar Sairam
 */
package com.krafttools.langkraft.ui

import androidx.compose.ui.unit.dp

/**
 * The metrics that belong to this app and to no other.
 *
 * Spacing, radius, type and touch targets come from kraft-foundation and are shared across the
 * portfolio — that is what makes nine apps look like one body of work. The values here are the
 * ones that would be wrong in any other app, which is the same test that decides an app's
 * accent belongs to the app and not to the foundation.
 *
 * Each was a literal repeated at its point of use: five 640s that were five chances to type
 * 640 differently. Declared once, with the reason each has the value it has.
 *
 * This file sits beside the theme and is therefore exempt from `spacing.no-raw-dp`, on the
 * same footing as the foundation's own token file: a file whose content *is* the numbers is
 * not a file that should be asked to use them.
 */
object LangMetrics {

    /**
     * The widest a card may grow before its text wraps.
     *
     * On a phone this never binds. On a tablet or in landscape it is the difference between a
     * phrase card and a paragraph-shaped block. 640dp is roughly two short phrases side by
     * side at the native size — beyond that the eye has to travel, and a phrasebook is read
     * in glances, not in passages.
     */
    val CardMaxWidth = 640.dp

    /**
     * The tallest the failure sheet may grow before it scrolls.
     *
     * A diagnostics sheet that fills the screen stops being a sheet. 320dp shows the first
     * few failures and makes the rest reachable by scrolling, which is the correct shape for
     * a list the user hopes is short.
     */
    val FailureSheetMaxHeight = 320.dp
}
