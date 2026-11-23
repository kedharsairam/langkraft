# LangKraft visual v3 — "signal cards"

Why: v2 was calm and correct but too flat, weak in hierarchy, sparse, and generic. User asked
for all four to be fixed while keeping the search-first IA and the performance work.

## The concept

A phrase is a **signal card**: a dense, printed-field-manual block with a left index rail,
a large romanized hero, a gold pronunciation line, and tight supporting detail. The app reads
like a traveller's reference card, not a web list — but without reverting to a wall of boxes.

## Hierarchy (concrete sizes)

| Role | Face | Size | Colour |
|---|---|---|---|
| Wordmark | Space Grotesk | 34sp bold | ink, with a gold square mark |
| Screen title | Space Grotesk | 26sp semibold | ink |
| Section header | Source Code Pro caps | 13sp + 1sp tracking | gold + 2dp gold rule |
| Card index | Source Code Pro | 15sp bold | gold, in a 28dp square |
| **Romanization (the phrase)** | Source Code Pro | 30sp bold | ink — the hero |
| **youSay (say it)** | Source Code Pro | 22sp semibold | gold — second hero |
| English prompt | Inter | 15sp | muted |
| Sound tip | Inter | 14sp | muted |
| Metadata | Source Code Pro caps | 11sp | muted |

## Components

- **SignalRow** — 3dp gold left rail, index square (`01`), then the four text layers stacked
  tight (8dp gaps), a 48dp star, and metadata. Hairline divider between rows. No enclosing box.
- **SectionHead** — mono caps label + count (`BASICS · 3`) with a 2dp gold rule above.
- **TexturePanel** — a panel with a subtle drawn dot-grid (Canvas, no image, no gradient) for
  Quick Access and the sound-check blocks. Gives surface without a fill difference.
- **ProgressRule** — 3dp track, gold fill, used per language on Home.

## Density

- Row vertical padding 8dp (was 12–20dp). Quick Access 3 actions in one 72dp panel.
- Home: numbered language rows (`01 Arabic (Egyptian)`), progress rule, phrase count, one line
  each instead of tall tiles.
- Lesson: no full-width boxes, no repeated 56dp empty star space — a compact 48dp icon.

## Personality

- Gold square mark (`■`) before the wordmark on Home.
- Section headers in gold caps with a rule — an editorial "index" feel.
- The wordmark and quick-access panel get the drawn dot-grid texture.
- Coral `#FF8A65` only for the hard state.

## What stays

Search-first IA, 7 traveller-situation sections, cached IO repository, debounced search,
all content and gates, zero permissions, offline fonts and icons.

## Status: implemented and device-verified 2026-11-23

Gold signature square on the wordmark, dot-grid textured Quick Access and sound-check panels,
editorial gold section rules with counts, numbered language rows with progress rails, and
signal-card phrase rows (gold index block, 30sp romanized hero, 22sp gold youSay, tight tip
and metadata, compact star). All 19 gates, lint, 4 tests, both builds, 1.0x and 2.0x device
checks pass; no FATAL. Release APK 2.3 MB.
