# LangKraft

An offline phrasebook for spoken travel. 20 languages, four tiers, one tap per phrase to say it
or mark it as not working.

No audio. No microphone. No exercises. No streaks. No accounts. **Zero permissions** — not
"we chose not to use any", there is no `<uses-permission>` in the manifest at all, so there is
nothing to revoke and nothing the device is doing on your behalf.

<p align="center">
  <a href="https://github.com/kedharsairam/langkraft/releases/latest"><img src="https://img.shields.io/github/v/release/kedharsairam/langkraft?style=for-the-badge&label=Download" alt="Download APK"></a>
  <img src="https://img.shields.io/badge/License-CC%20BY--SA%204.0-lightgrey?style=for-the-badge" alt="CC BY-SA 4.0">
  <img src="https://img.shields.io/badge/Android-8.0%2B-blue?style=for-the-badge" alt="Android 8.0 and newer">
  <img src="https://img.shields.io/badge/APK-2.3%20MB-success?style=for-the-badge" alt="2.3 MB">
</p>

---

## What it does

- **Gives you the order.** Every language arrives as four tiers, each independently useful, so
  stopping after the first still leaves you able to do something real. Greet, thank, apologise,
  recover from not understanding — then prices and tickets, then a problem you did not plan for,
  then fifteen minutes with a stranger.
- **Tags every item for speaking or listening.** You understand far more than you produce. You
  are the visitor, and you get spoken to faster than you speak. Signs are recognition text and are
  never held against you for not learning to say them.
- **Shows the script and a romanisation.** So it is usable if you cannot read the target script
  yet. Eight script families are bundled and subset to the characters actually used — nothing is
  substituted by the device.
- **Takes one input.** One tap on any phrase, with an optional country. "Wrong in Morocco" and
  "wrong in Iran" are different reports about one phrase, and the country is the field that tells
  them apart. It is the only way a phrasebook written by someone who has never stood in the
  country gets fixed.
- **Shows you your own flags.** Listed under Flags, exportable to a file. No total, no streak, no
  comparison between languages — the moment a flag count becomes a number to beat, it stops being
  a report and becomes a score, and a reader optimising a score produces noise instead of
  evidence.
- **Works with the radio off.** Every byte is in the APK. Nothing is fetched to read a phrase.

## The tiers

| Tier | Name | Size | Intent |
| --- | --- | ---: | --- |
| 0 | Courtesy | ~50 | Not rude. Greet, thank, apologise, recover from not understanding. |
| 1 | Transaction | ~150 | Prices, food, tickets, directions, a room. |
| 2 | Independence | ~500 | Handle an unexpected problem unaided. |
| 3 | Conversation | ~1,200 | Fifteen minutes with a stranger. |

Sizes are per language. An isolating language needs more items to reach the same capability than
a synthetic one, so a single number for all twenty would be wrong in both directions. Each one is
derived from measured evidence yield, not chosen — German supports the cap, Tamil gets what it
has, and the difference is recorded rather than averaged away.

## What is in the app today

| | |
| --- | --- |
| Languages | 20 — 19 to learn, plus English, which is the calibration tier that measures whether the tier sizes are right before they are applied to the rest |
| Tier 0 entries | 1,008, plus 42 exchanges and Thai tone sets |
| Tier 0 range | Hindi 30 → Thai 77 |
| Records emitted | 1,055 |
| Pipeline tests | 284 |
| Release APK | 2.3 MB, debug-signed |
| Script families | Latin, Arabic, Cyrillic, Devanagari, Thai, Tamil, Han, Hangul, Kana |

**Tiers 1, 2 and 3 are specified and not yet written.** That is the honest gap, and it is the
next thing this needs.

**Dari was cut from this release.** Tatoeba has no Dari: the code that looks like it should
reports 721 results and returns an unrelated scatter of languages, and the other reports 3,465
while delivering zero Persian. Only `pes` filters, and that is Iranian Persian — a different
variety from Afghan Dari. Eighty-one verified pairs were available and were not used, because
filling a language from a source that does not say what it is is the one thing this app exists not
to do. The reasoning is in `catalogue/ATTESTATION_LIMITS.md`.

## Permissions

None. There is no `<uses-permission>` element in `AndroidManifest.xml` and no `INTERNET`.

| Permission | Used by | Why |
| --- | --- | --- |
| _(none)_ | — | The app cannot reach the network, read the microphone, or write off the device. |

The only thing it writes is `progress.db`: your position in each language and the flags you set.
It stays on the phone.

## What it never does

It never asks you to retrieve anything. No quizzes, no typing, no self-grading, no progress bar
that rewards returning. It shows you content; practice happens outside it, with real people.

That is a deliberate trade with a name: **this app makes you prepared.** It will not make you
fluent and it does not imply otherwise.

## How it is built

The engine is small and the content is the product. No inputs, no adaptive scheduling, no audio,
so twenty languages is a folder of data files rather than twenty code paths.

Every factual claim carries a source and a confidence level *derived in code* rather than
hand-written, and the build fails on content that lacks either — it is deliberately red rather
than shipping plausible text of unknown standing. Regeneration cannot destroy existing work:
content already in the repository is a seed and the harvest only fills gaps up to the measured
depth, so it can raise a language's tier and never lower one.

<details>
<summary>Two mistakes this project made, kept here rather than deleted</summary>

**The bundled fonts were not fonts.** Eight files in `res/font/` were GitHub 404 HTML pages saved
with a `.ttf` extension — committed, and referenced in a code comment as solving a problem they
were supposed to have solved. Thai rendered on the device because Android supplied a serif Thai
face, which is precisely the substitution the comment claimed to prevent, so the problem was
documented and simultaneously unobserved. What found it was a glyph-coverage checker written to
look for something else, which threw `no cmap table` on a file everyone believed was working.

**Six attempts to fix missing romanisations by widening a character class.** Each fixed one
language and broke another. The seventh reads the structure of the page instead, and was verified
against all 9,117 phrase rows on twenty phrasebooks rather than against fixtures chosen after the
fact. That pass also discarded 2,918 readings the old parser had *invented* — English glosses
like "to sleep" filed as pronunciations — and gained 185 real ones.

</details>

## Licence

| Component | Licence |
| --- | --- |
| Source, specs, original content | CC BY-SA 4.0 |
| [Wikivoyage](https://en.wikivoyage.org/)-derived content | CC BY-SA 4.0, attributed in-app |
| [Tatoeba](https://tatoeba.org/)-derived content | CC BY 2.0 FR, attributed per entry |
| 8 bundled Noto families | SIL Open Font License 1.1 — text and every source URL ship in the APK |

AttributionShareAlike is common to all three, which is why one repository licence covers them.
Android and Jetpack Compose are Apache 2.0.
