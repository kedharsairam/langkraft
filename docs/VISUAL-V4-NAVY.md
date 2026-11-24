# LangKraft visual spec — v4 "dark navy" (2026-11-24)

Supersedes `VISUAL-V3.md`. This is the shipped direction as of 2.0.0. It came from
AI-generated concept mockups that Kedhar reviewed and selected, constrained to dark navy only.

## Palette

| Token | Hex | Use |
|---|---|---|
| Ground | `#0A1220` | every screen background |
| Surface | `#121F33` | cards, always with a border |
| SurfaceAlt | `#17253C` | icon tiles, inset wells |
| Ink | `#F5F1E8` | primary text |
| Muted | `#9AA8BE` | secondary text, labels |
| Accent | `#E6B870` | the single gold accent |
| StateHard | `#F2C57C` | starred phrases |
| Border | `#263954` | 1px card hairline |

No light surface anywhere. Cards are 16dp rounded with a 1px border. A faint dotted world-map
texture (`worldMapDots`) sits behind brand and list screens; no images, gradients, or blur.

## Type

Source Serif 4 (OFL, bundled, full Latin Extended coverage) for display, the romanization hero,
and the italic gold `youSay`. Inter for body. Source Code Pro for labels/meta. Space Grotesk
was removed. All fonts offline; licence in `assets/OFL-Source-Serif-4.txt`.

## Screens

- **Splash** — globe mark, wordmark, gold rule, "A Romanization-Only Travel Phrasebook",
  "19 LANGUAGES · 35 LESSONS / 100% OFFLINE". 1.2s auto-advance to Home.
- **Home** — one scroll container: brand block, QUICK ACCESS (Search / Hard / Resume as
  full-width outlined rows with chevrons), COMMITTED and BENCH numbered language lists.
  Everything scrolls so landscape can reach every row.
- **Briefing** — "BE READY / for Any Destination", three icon cards (NEW SOUNDS,
  RHYTHM & POLITENESS, TRAP), gold ACKNOWLEDGE button. Scrollable.
- **Language index** — 7 icon-led section cards over the 35 lessons; QUICK ACCESS for
  sound check and numbers.
- **Lesson** — a single phrase card (number chip, serif romanization hero, gold italic
  youSay, pronunciation tip, note, source/date) with a ‹ › stepper and live counter.
- **Numbers** — 2-column grid of romanization cards.
- **Search** — grouped by language ("SPA · Spanish"), result cards with romanization hero.
- **Hard** — starred phrase cards with gold star; no Clear All.

## Decision notes

Resume keeps the ▶ glyph from the mockup but there is still no audio. The stepper uses the
real `next()`/`prev()`/`index` ViewModel helpers. No landmark backgrounds — only the neutral
dotted map, to avoid wrong geography.
