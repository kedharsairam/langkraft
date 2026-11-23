# LangKraft visual audit and proposed design — 2026-11-23

Audited every screen in code and on a Realme RMX3998 (1080x2400). Against the locked
Neo-Brutalism system in `data/shared-kb/neo-brutalism-system.md`.

**Status 2026-11-23: IMPLEMENTED.** All eight bugs fixed, tokens switched to the locked palette,
shared primitives added (`Kraft.kt` — kraftPanel, LedgerRow, KTopBar, KLabel, RowDivider,
KSecondary, KPrimary, kraftClick with no ripple). All seven screens
restyled and device-verified at 1.0x and 2.0x font scale. Filler notes cleared from the data
(8,731 rows) and the gates relaxed so note/soundTip may be empty — matching the brief's
"only when it matters". Numbers screen made reachable from the lessons index; search now holds
its query; hard empty state is plain copy. Full gates, 4 tests, both builds and device pass.

## Verdict

The current UI is an early scaffold wearing Material 3 defaults with a dark palette. It does
not follow the locked system: no 2dp borders, rounded cards, wrong grounds, Material ripple,
Material spinner, and several real content/layout bugs. The good news: the information
architecture is right (Home → lessons index → lesson, plus Search and Hard) and spacing is
already 20/12. The fix is a skin pass plus a handful of bug fixes, not a rebuild.

## What is actually broken (not just style)

| # | Screen | Problem | Fix |
|---|--------|---------|-----|
| 1 | Lessons index | Long titles run into the count column — "Numbers 1 through 10[11]" is unreadable. `titleLarge` with no width constraint. | Count in a fixed-width box (`weight(1f)` on title, count `maxLines=1`), or put count on its own line. Count must never overlap. |
| 2 | Search | `value = ""` is hardcoded — typed text never appears. Search technically works because `onValueChange` fires, but the field looks broken. | Hold query in state. |
| 3 | Search | Idle text says "Currently only Spanish has content" — false, all 19 have content. | Rewrite to "Search across all 19 languages." |
| 4 | Lesson | Every phrase shows the lesson name as its note ("Meeting someone.") — repeated filler from `note_for()`. The note field is supposed to be a real usage note. | Data fix: clear/note-only-if-real. UI: hide empty notes. |
| 5 | Hard | Empty state reads `STATUS: ZERO_ENTRIES_FOUND. Tap the star on any phrase to add it here.` — debug copy, breaks "plain factual UI copy". | "No hard phrases yet. Star a phrase to add it here." |
| 6 | Numbers | Screen exists but has no entry point — `onNumbers` is no longer called after lessons became data-driven. It is unreachable. | Add a "Numbers" row to the lessons index for the 5 number lessons, or drop the screen. |
| 7 | Home | Counts show `[0/459]`, progress shows a single dot — a 459-denominator makes progress meaningless at 1 phrase. | Use lesson position or phrase count honestly; a 1px progress dot is not useful. |
| 8 | All | Material ripple visible on icon buttons (circle highlight in screenshots), Material spinner, Material outlined text field. Locked system forbids all three. | Remove ripple/elevation; custom flat pressed state; plain text field. |

## Token audit (current → locked)

| Role | Current | Locked | Action |
|---|---|---|---|
| Ground | `#000000` | `#0B0B0C` | change |
| Surface | `#0A0A0A` | `#141416` + 2dp border | change + border |
| Border | `#222222` (unused as stroke) | `#F2F1EE` 2dp | change, actually draw it |
| Ink | `#EDEDED` | `#F2F1EE` | change |
| Muted | `#888888` | `#888888` | keep |
| Accent | `#E5C07B` gold | `#E5C07B` gold | keep — the one allowed per-app accent |
| Radius | cards ~12dp | 0dp | change |
| Shadows | none | 4dp hard only from inverted blocks | add only where inverted |
| Spacing | 20/12 | 20/12 | keep |

`Color.kt` has a third ink level (`TertiaryLabel #636366`, `Fill #1C1C1E`, `NoticeAmber`) —
the locked system says two inks, one accent. Remove the extras (error can use the accent or ink).

## Proposed design, screen by screen

### Global frame
- Ground `#0B0B0C`. Ink `#F2F1EE`. Muted `#888888`. Gold only for counts, selections,
  one primary CTA per screen, and the progress mark. No third ink.
- Status bar padding stays. No bottom bar. Top bar is a 56dp row inside 20dp side padding:
  left = back + screen title (sans 22sp semibold); right = Search, Hard icon buttons,
  each 56dp touch box with a 24dp outlined icon.
- Every card/tile: ground or surface fill, **2dp ink border, 0 radius, 20dp padding**. No shadow
  unless the block is inverted. Divider: full-bleed 2dp ink.
- Icons: one outlined stroke set, 24dp, consistent weight. No filled two-tone Material icons.
- Press state: flat 100ms fill change, no ripple. Fades only, 150ms, honour reduce-motion.

### Home — "the shelf"
- Keep the 2-column tile grid, alphabetised, with `BENCHED` divider + subheading.
- Replace rounded filled tiles with bordered ledger tiles. Each tile:
  - top line: zero-padded index? No — Home is a shelf, not a ledger. Put language name
    (sans 20sp semibold, ink) and below it `[0/459]` in mono 12sp muted.
  - a 2dp ink progress rule under it; the filled portion is gold. For 0 progress show an
    empty rule and `START` in gold mono caps — not a dot.
- Search/Hard icons top-right, 56dp boxes.
- Loading: centred plain text `LOADING CATALOGUE…` in mono caps muted, not a spinner.

### Lessons index — the ledger
- This is where the numbered ledger belongs, and it is the most reusable pattern:
  `01  Meeting someone            [18]`
  - index: mono 13sp bold, muted, fixed 48dp column, left edge always aligned.
  - title: sans 17sp, ink, `weight(1f)`, `maxLines=2`, ellipsis. **Count in a separate fixed
    box** so long titles can never overlap it: mono 12sp muted, right-aligned.
  - row = full-width bordered card, 2dp ink, 0 radius, 20dp padding, 12dp gap.
- Section header `LESSONS` mono caps 12sp muted. 35 rows scroll.
- Longest title "Numbers 100 through 1000" must fit in two lines with the count intact.

### Lesson — the reading surface
- Header: `01  Meeting someone` (mono 13sp index + sans title), `[18]` right in fixed box.
- Each phrase = bordered card, 2dp ink, 0 radius, 20dp padding, 12dp gap:
  - `1. Hello.` — sans 15sp muted (prompt).
  - `OH-lah` — mono bold 28sp ink. This is the hero. Stress caps + syllable hyphens already in data.
  - sound tip — sans 14sp muted, one line.
  - note — sans 13sp muted, **only if it carries real information**. Hide blank/filler notes.
  - "Hard" action: secondary CTA = 2dp ink border, transparent, mono caps 13sp, 56dp tall.
    When starred, invert: gold fill, ground text, no outline doubling, no checkmark badge.
  - Source + checked date: mono 11sp muted, on a thin 1dp hairline under the card, always
    visible rather than behind a toggle (provenance is a selling point, not a secret).
- Back: system back mirrors the in-app arrow at every depth (already works — keep).

### Sound-check briefing
- First screen when opening a language, as now. Structure it as three bordered blocks,
  each with a mono caps label (`NEW SOUNDS`, `RHYTHM & POLITENESS`, `TRAP`) and sans body.
- The acknowledge CTA: **the one inverted block per screen** — fill ink, text ground,
  mono caps 14sp bold, full width, 56dp. No shadow needed on dark.
- Copy stays plain and factual.

### Search
- Back arrow + title in top bar. Text field: 2dp ink border, 0 radius, transparent fill,
  mono 15sp ink input, muted placeholder. No gold outline label, no Material floating label.
- Query held in state; results appear as bordered ledger rows: English (sans 15sp) + YouSay
  (mono bold 18sp) + language code in a fixed right box. Tapping opens that language.
- Idle copy: "Search across 19 languages by English, romanization or You say."
- No matches: "No matches." Keep it factual. Keyboard inset respected (already is).

### Hard
- Top bar with back + title "Hard". Subtitle "Your starred pile. Review when you want."
- Empty: a bordered block with plain copy "No hard phrases yet. Star any phrase to add it here."
- Populated: bordered rows, English + YouSay + `SPA · spa-001` meta in mono 11sp muted,
  a star icon in gold, divider between. Tapping a row opens its language.
- No debug strings, no `STATUS:` tokens.

### Numbers
- Make it reachable from the lessons index (a "Numbers" card), or remove the screen.
- 2-column grid of bordered cells, each: value mono 12sp muted, YouSay mono bold 18sp ink,
  note sans 12sp muted. 2dp ink border, 0 radius, 20dp padding, 12dp gap.
- "21–99 PATTERN" as a bordered expandable row with `+`/`−` in mono, body sans 14sp.

## What I would NOT change

- The information architecture and navigation (already right).
- 20/12 spacing and 56dp targets (already right).
- The gold accent, the mono/sans split, the "caps for labels only" rule.
- Plain factual copy — just remove the debug strings.

## Order of work (if approved)

1. Fix the 8 real bugs (overlap, search state, stale copy, filler notes, unreachable Numbers).
2. Tokens + theme: switch to locked grounds/ink, remove third ink, disable Material ripple
   and default shapes in the theme.
3. Build one shared set of composables: `BorderedCard`, `LedgerRow`, `TopBar`, `MonoLabel`,
   `SecondaryCta`, `InvertedCta`, `SectionDivider`. Replace per-screen ad-hoc layouts.
4. Restyle each screen against the spec above, Home → Lessons → Lesson → Sound-check →
   Search → Hard → Numbers.
5. Device check at 1.0x and 2.0x font scale, TalkBack, and re-run all gates.
