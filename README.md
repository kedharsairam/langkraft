# LangKraft

Personal, offline, romanization-only travel phrasebook. One user. No accounts, no network,
no scripts, no exercises, no scoring.

## What it is

- **19 languages**, 35 lessons each, ~8,700 phrases. 11 committed, 8 benched.
- Lessons are **plain readable lists**: English prompt above, romanized target in large mono below,
  numbered from 1. No hide→say→reveal, no quizzes, no scoring. Tap the star to mark a phrase hard.
- Home is a two-column grid; a language opens to a lessons index, then a full-list lesson.
- Each language has a sound-check briefing, a numbers grid (0–29, tens, 100, 1000), and search.
- Content is JSONL assets. Adding a language or a phrase is a data change, not a code change.

## Where the content comes from

LingoHut is an **anchor only** — its 125 lessons supplied the list of meanings to cover. We
selected 35 travel-survival lessons common to every language and wrote our own wording, verified
against Wikivoyage phrasebooks (CC BY-SA 4.0) and original composition. No LingoHut text was
copied. Every row carries a source, a checked date, and the lesson it is anchored to.

Romanization only: no native script is shown anywhere, including sound-checks and numbers.
Telugu script appears only as a sound reference, per the style guide.

## What it is not

- No audio in or out, no microphone, no TTS. Listening is practised outside the app.
- No typing except search, no reading or writing, no scripts.
- No "this failed" button, no streaks, points, badges, or crowd features.
- No export/import — the JSONL files are the originals and live in the repo.

## Privacy

Zero `<uses-permission>` lines in the manifest. No INTERNET, no Play Services, no Firebase, no
analytics, no account. CI verifies with `aapt dump permissions`. Works offline on GrapheneOS.

## Build

Requires JDK 21, Android SDK 35, Node (for the content lint), Gradle wrapper 8.11.1.

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease
node pipeline/lint.mjs
python3 pipeline/validate_content.py <code>   # e.g. spa, tam
aapt dump permissions app/build/outputs/apk/debug/app-debug.apk
```

`pipeline/validate_content.py` is the hard gate: it checks full lesson coverage, Latin-only
visible fields, CAPS stress, provenance and anchors. Every language must pass it.

## Languages

Committed: Spanish, Indonesian, Portuguese, Hindi, French, Arabic (Egyptian), Mandarin,
Swahili, Russian, Vietnamese, Thai.
Bench: Turkish, Japanese, Korean, German, Italian, Farsi, Serbian, Tamil.
Bengali was removed from the catalogue. Free cluster add-ons (Urdu via Hindi, Malay via
Indonesian, Croatian/Bosnian/Montenegrin via Serbian) are credited but have no separate course.

## History

The v0.1.0 repo was deleted on 2026-11-06. This is a clean rebuild with no carry-over of the old
tiers, catalogue, buttons or tag system. The old repo is reference only.

See `docs/YOU_SAY_STYLE_GUIDE.md`, `docs/BRIEF.md`, `pipeline/lint.mjs`,
`pipeline/validate_content.py`.
