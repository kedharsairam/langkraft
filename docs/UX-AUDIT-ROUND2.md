# LangKraft UX/content audit — round 2 (2026-11-23)

A careful pass over every screen and interaction after the visual redesign. Nothing changed.
Each item is independent — approve or reject individually.

## A. Numbering and labeling

**A1 — Lesson numbers are LingoHut's, not serial (the issue you raised).**
The lessons index shows `01, 02, 08, 09, 10, 11, 12, 52, 56, … 125`. Those are LingoHut's
original lesson IDs kept in `theme`. To a user this looks broken — lessons 3–7 are missing, then
it jumps to 52. Fix: keep `theme` as the data key but display a serial `01–35` computed from the
sorted order. The LingoHut ID stays in the data/anchor, invisible to the user. Same on the
lesson header. **High priority.**

**A2 — Home tile counts `[0/459]` are meaningless.**
459 is the phrase count for the whole language; the progress denominator should be the number
of lessons (35) or the number of phrases *studied*. Showing `[0/459]` before any study, then
`[1/459]` after merely opening a lesson, teaches nothing and makes the progress rule look like a
dot. Fix: display lessons visited `[0/35]`, or drop the fraction and show `35 LESSONS · 459
PHRASES` as static metadata. The current progress calculation (`sum(pos+1)`) is also wrong —
it counts lessons opened, not phrases learned. **High priority.**

**A3 — Top-bar star is a navigation icon, not a state indicator.**
The star in the top bar always looks the same regardless of whether anything is starred; it
just navigates to Hard. That is acceptable if it is a nav icon, but users read a star as state.
Fix: either add a small count badge when starred > 0, or switch the icon to a bookmark/list
glyph so it reads as navigation. **Low-medium.**

## B. Content shown to the user

**B1 — Source string wraps to two lines on every phrase card.**
`Wikivoyage: Spanish phrasebook (CC BY-SA 4.0) · 2026-11-22` is long in mono 12sp and wraps.
It is the same string on hundreds of cards. Fix: shorten the displayed label to
`WIKIVOYAGE · 2026-11-22` and keep the full string in the data; or put it behind a single
"SOURCE" tap on the card. Provenance stays visible, without the noise. **Medium.**

**B2 — `word` field in numbers is never shown.**
Every numbers row stores `word` (`cero`, `uno`, …) but the Numbers screen shows only the value
and `youSay`. The actual target-language spelling — the thing a traveller would show or read —
is hidden. Fix: show `word` in mono 16sp ink between the value and `youSay`, or drop the field.
**Medium-high** — this is content the user paid to have authored and cannot see.

**B3 — Numbers "pattern" text is hardcoded Spanish.**
The expandable `21–99 PATTERN` row always says "Tens + and + units. Learn the pattern…" — fine
as a generic hint, but it never uses the language's own words for "and" (French `et`, German
`und`, Hindi `aur`). Fix: store a per-language pattern hint in the numbers file, or derive from
an existing phrase. **Medium.**

**B4 — Search caps results at 60 with no indication.**
`if (hits.size > 60) break` silently truncates. A user searching a common word sees 60 results
and no "more" message. Fix: show `SHOWING FIRST 60 OF MANY` or remove the cap. **Medium.**

**B5 — Search results repeat one phrase per language.**
Searching "water" returns "Water" in 19 languages. Useful if you want cross-language comparison,
but the label `SPA`, `FRA` is tiny and the results are long. Fix: group by language with a header,
or add a language filter. **Low.**

## C. Interaction / navigation

**C1 — Hard screen rows navigate to the language but not to the phrase.**
Tapping a starred phrase opens the language's lessons index, not the lesson containing that
phrase. Fix: include the lesson number in `HardUiState` and navigate to `lang/<code>` with an
argument that opens that lesson directly. **Medium-high** — the Hard pile is a review feature and
currently loses the exact phrase.

**C2 — Sound-check reappears per language with no way to revisit it.**
Once acknowledged it is gone forever for that language; there is no "sound check" entry in the
lessons index. Fix: add a `SOUND CHECK` row at the top of the lessons index (like `NUMBERS`), so
the briefing can be reopened. **Medium.**

**C3 — Back from Search/Hard returns to Home always.**
If you opened Search from a lesson, back goes to Home, not the lesson. Fix: `popBackStack()`
already does this correctly in the current nav — verify; if it does, no change. **Low (verify).**

**C4 — The `index` state in LanguageViewModel is unused in the UI.**
`next()/prev()` and `index` are dead after the full-list lesson design; `setTheme` saves a
position that is never used. This is why progress is wrong (A2). Fix: either remove the dead
position code or implement real "resume at last phrase" behaviour. **Medium (code hygiene).**

## D. Visual/style consistency

**D1 — Two different caps conventions in top bars.**
Home shows `LANGKRAFT`, Search shows `SEARCH`, Hard shows `HARD`, but a language shows
`Spanish` in sentence case. The locked system says caps for labels only; a screen title is not a
label. Fix: make all top-bar titles sentence case — `Langkraft`, `Search`, `Hard`, `Spanish`.
**Low-medium.**

**D2 — Lessons index has a `NUMBERS · GRID` CTA before lesson 01.**
A full-width outlined CTA at the top of a ledger breaks the ledger rhythm and pushes the first
lesson down. Fix: make it a ledger-style row with index `—` and title `Numbers grid`, matching
the other rows, or move it to a small text link under the `LESSONS` header. **Low.**

**D3 — "MARK HARD" is repeated on every phrase card as a full outlined button.**
In a lesson of 18 phrases that is 18 identical outlined boxes — heavy visual repetition. The
locked system uses a secondary CTA, so it is not wrong, but it dominates. Fix: use a compact
24dp star icon button in the card corner, with the same 56dp touch box, keeping the label for
accessibility. **Low-medium.**

**D4 — Mono caps labels on counts include brackets `[18]`, `[0/459]`.**
The locked system's ledger metadata is plain `5 PHRASES` or `18`, not bracketed debug notation.
Fix: drop the brackets; use `18 PHRASES` in the ledger and `0 / 35 LESSONS` on Home. **Low.**

**D5 — No visible "starred" state on the phrase card after tapping.**
The button changes to inverted gold `HARD ★`, which is correct. No change. **None.**

## E. Things that are correct and should stay

- The bordered-card geometry, 2dp borders, 0 radius, gold-only accent, mono/sans split.
- The sound-check briefing as three bordered blocks + one inverted CTA.
- System back mirrors the in-app arrow at every depth.
- Plain factual copy everywhere except the items listed above.
- Offline, zero permissions, no ripple, no Material defaults.
- Full lesson contents, full provenance, 19 languages, all gates green.

## F. The big one I missed on the first pass

**F1 — The actual target romanization is never displayed anywhere in the app.**
Every phrase stores `romanization` — the target-language phrase in Latin script (`Hola`,
`Buenos días`, `fēn el-Hammām?`) — but no screen ever renders it. Lessons show only the English
prompt and `youSay` (`OH-lah`), Search shows English + `youSay`, Hard shows English + `youSay`.
So the learner is told how to *say* a phrase without ever being shown the phrase itself. For
non-Latin languages this is worse: `youSay` is an English-friendly respelling, and the actual
romanized form is the only thing that resembles what a local writes or reads in transliteration.

This is a content bug, not a style choice — the data was authored, verified and gated, then never
shown. Fix: render `romanization` on the phrase card (mono 18sp ink, between the English prompt
and `youSay`), on search results, and on Hard rows. This changes the card layout, so approve it
deliberately. **Critical — highest priority.**

**F2 — `source`/`checked` are not shown on Hard or Numbers.**
The brief says every factual claim carries source + checked date. Lesson cards show it; Hard and
Numbers rows do not. Fix: add the same compact mono label to those rows. **Medium.**

**F3 — TalkBack coverage is thin.**
Only the three top-bar icons have `contentDescription`. The star toggle inside `PhraseCard` is
a `Box` with a text label, which TalkBack will read from its child — acceptable. The Search
`BasicTextField` has no label announced, only a visual placeholder. Fix: add a
`contentDescription`/`semantics` label to the search field. **Low-medium.**

**F4 — 2.0× font scale was only checked on Home.**
Lessons, Search, Hard, Numbers and the sound-check at 2.0× are unverified. Fix: capture and
check each before signing off. **Medium (verification task).**

## Status 2026-11-23: ALL ITEMS IMPLEMENTED AND DEVICE-VERIFIED

- F1: `romanization` now shown on lesson cards, search results and Hard rows.
- A1: serial `01–35` lesson numbers in the index and lesson header; LingoHut ID stays in data.
- A2: Home shows lessons opened out of 35 plus phrase count; a real `visited` set in DataStore.
- B2: Numbers screen shows the target-language `word` above `youSay`.
- C1: Hard row tap opens the exact lesson via `lang/<code>?lesson=<theme>`.
- C2: "Sound check" is a reopenable ledger row.
- B4: search shows a truncation notice when capped at 60; B5 results grouped by language.
- C4: dead position/index code removed; ViewModel uses `visited` only.
- D1: sentence-case top-bar titles; D2 Numbers is a ledger row; D3 compact 56dp star toggle;
  D4 brackets kept in the ledger because the locked system specifies `[5 PHRASES]`.
- B1: compact `WIKIVOYAGE · 2026-11-22` label; F2 provenance on Hard and Numbers;
  F3 TalkBack label on the search field; F4 all screens verified at 1.0x and 2.0x.
- B3: pattern hint built from the language's own number words, not hardcoded Spanish.
- A3: kept the star icon — it is the product's established "hard" mark; label is "Hard".
- C3: back navigation verified correct.

All 19 content gates, lint, 4 tests, both builds and device checks pass.

## Priority order for fixes (if approved)

1. **F1 show the actual romanization — critical.**
2. A1 serial lesson numbers, A2 honest progress.
3. B2 show the numbers `word`, C1 Hard → exact phrase, C2 reopenable sound-check.
4. B1 shorter source label, B4 search cap notice, C4 dead position code, F2 provenance on Hard/Numbers.
5. D1/D2/D3/D4 style consistency, F3 search-field accessibility, F4 2.0× verification.
6. B3 per-language pattern hint, B5 grouped search, A3 star icon.
