# LangKraft

A phrasebook that knows what order to read it in, which variety to give you, and what not to
say. Read-only, offline, no accounts. **19 languages.**

**No audio. No microphone. No exercises. No streaks. No network permission.** Every byte ships
in the APK.

## What it does

Every language has a researched floor: the smallest set of real phrases that lets you
transact, navigate and ask for help, in the variety you will actually hear. Delivered as four
tiers, each independently useful, so stopping early still leaves you able to do something real.

| Tier | Name | Size | Intent |
|---|---|---|---|
| 0 | Courtesy | ~50 | Not rude. Greet, thank, apologise, recover from not understanding. |
| 1 | Transaction | ~150 | Prices, food, tickets, directions, a room. |
| 2 | Independence | ~500 | Handle an unexpected problem unaided. |
| 3 | Conversation | ~1,200 | Fifteen minutes with a stranger. |

Sizes are **per language** — an isolating language needs more items to reach the same capability
than a synthetic one — and they are derived from measured evidence yield, not chosen.

## Status — what actually exists

| | |
|---|---|
| Languages | **19**, each with a researched spec |
| Tier 0 | **1,013 entries**, shipping |
| Tiers 1–3 | Specs defined, content not yet written |
| APK | Builds, installs, verified on device |
| Tests | 284 pipeline tests, green in CI |

**Not built yet**, despite appearing in earlier planning: country packs, dialect switching, and
HTML/PDF export. The only export is the failure-flag file, described below.

## What it never does

It never asks you to retrieve anything. No quizzes, no typing, no self-grading. It shows you
content; practice happens outside it, with real people.

That is a deliberate trade: **this app makes you prepared.** It will not make you fluent, and it
says so rather than implying otherwise.

## Two things that are different

**Say-it / understand-it.** Every item is tagged for production or reception. You need to
understand far more than you produce — you're the visitor, and you get spoken to faster than you
speak. Recognition text is tagged separately and never held against you for not learning to say it.

**Both scripts.** Native script plus a romanisation, so the app is usable without reading the
target script at all. Eight script families are bundled and subset to the characters actually
used: Latin, Arabic, Cyrillic, Devanagari, Thai, Tamil, Han, Hangul and Kana. Nothing is
substituted by the device.

## "This failed me"

The only input the app accepts. One tap on any phrase, with an optional country. "Wrong in
Morocco" and "wrong in Iran" are different reports about one phrase.

Your flags are listed under **Flags** and export to a file. There is no total, no streak and no
comparison between languages — the moment a flag count becomes a number to beat, it stops being
a report and becomes a score, and a reader optimising a score produces noise instead of evidence.

It is the only way a phrasebook written by someone who has never stood in the country gets fixed.

## How it is built

**The engine is small; the content is the product.** No inputs, no adaptive scheduling, no
audio. Nineteen languages is a folder of data files rather than nineteen code paths, so adding
one is a data change and not a rewrite.

**Every claim carries its evidence.** Each spec is a per-language contract with a linter that
rejects bare numbers, and every content entry carries its source, licence and a confidence level
*derived in code* rather than hand-written. A reader can see which phrases are attested and which
are authored.

**The build is deliberately red on bad content.** If an entry lacks evidence or lacks a
romanisation its script requires, the build fails rather than shipping plausible text of unknown
standing. Gaps are recorded in [`catalogue/known-gaps.json`](catalogue/known-gaps.json).

**Regeneration cannot destroy work.** Existing content is a seed; the harvest only fills gaps up
to the measured depth. It can raise a language's tier and never lower one.

<details>
<summary>Two things this project got wrong, and what they cost</summary>

**The bundled fonts were not fonts.** Eight files in `res/font/` were GitHub 404 HTML pages saved
with a `.ttf` extension. They were committed, and a comment in the code described a fallback
problem they were supposed to have solved. Thai rendered on the device because Android supplied a
serif Thai face — precisely the substitution the code claimed to prevent. So the problem was
documented and simultaneously unobserved, and no non-Latin script had ever rendered from a
bundled font. The check that found it was written to look for something else: a glyph-coverage
checker, which threw `no cmap table` on a file everyone believed was working. A plausibly-named
file in the right directory is not evidence.

**Six attempts were made to fix the missing-romanisation problem by widening a character class.**
Each fixed one language and broke another. The seventh replaced it with structural reading of the
page, verified against all 9,117 phrase rows on twenty phrasebooks. That pass also found 2,918
readings the old parser had *invented* — English glosses like "to sleep" and "less oil/butter/lard"
filed as pronunciations, and confidently wrong.

</details>

## Licence

Source code and specs: **CC BY-SA 4.0**.

Content derived from [Wikivoyage](https://en.wikivoyage.org/) is **CC BY-SA 4.0**, credited in
the app. Content from [Tatoeba](https://tatoeba.org/) is **CC BY 2.0 FR**, credited per entry.

The eight bundled Noto families are under the **SIL Open Font License 1.1**. The licence text and
every source URL ship in the APK, as OFL requires, and are readable on the Credits screen.

Android and Jetpack Compose are Apache 2.0.
