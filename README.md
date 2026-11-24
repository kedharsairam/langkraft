# LangKraft

An offline, romanization-only travel phrasebook for one user. Nineteen languages, 35 lessons
each, 8,731 phrases — all bundled into the app. No accounts, no network, no microphone, no
native scripts, no exercises, no scoring.

Built for the moment you need to say something in a language you don't read: you see the phrase
in Latin letters, a CAPS pronunciation to say out loud, and a short note on the sounds that
matter. That's the whole product.

## Screens

- **Splash → Home** — a globe wordmark, quick access to Search, Hard and Resume, and the
  numbered language list (11 committed, 8 bench).
- **Pre-flight briefing** — a one-time-per-language card set covering new sounds, rhythm and
  politeness, and the common trap. Replayable from any language.
- **Language index** — seven traveller-situation sections (Basics, Numbers & time, Food & drink,
  Shopping & money, Getting around, Health, Travel & emergency) over the 35 lessons, plus a
  numbers grid.
- **Lesson** — one phrase at a time in a large serif card: romanization hero, gold italic
  CAPS pronunciation, pronunciation tip, note, source and date. ‹ › step through the lesson;
  tap the star to add a phrase to Hard.
- **Search** — across all 19 languages, grouped by language, opening straight into the phrase's
  lesson.
- **Hard** — your starred pile, with each phrase linked back to where it lives.

The design is a single dark-navy editorial system with one gold accent, Source Serif 4 for the
phrase hero, Inter for body text and Source Code Pro for labels. All fonts are bundled under
the SIL Open Font License. No light theme, no images, no gradients.

## What it deliberately is not

- No audio in or out, no TTS, no microphone. Listening practice happens outside the app.
- No native scripts anywhere in the phrase data, including numbers and sound-checks. Telugu
  script is used only as a sound reference in the briefing copy.
- No typing except search, no quizzes, no streaks, points, badges, cloud sync or accounts.
- No export/import. The JSONL files in the repo are the originals.

## Where the content comes from

LingoHut supplied the **lesson topics and English meanings** as anchors only. No LingoHut text
was copied — their terms prohibit copying and public display. Target-language wording was
written for LangKraft and checked against Wikivoyage phrasebooks (CC BY-SA 4.0) at authoring
time; rows marked "LangKraft authored" were composed from that vocabulary and reviewed for
register and current usage.

Every phrase and number row carries its own `source` and `checked` date. The research snapshots
were kept outside this repo and are no longer available, so the attribution records what was
consulted, not a re-verification of every row. Full attribution is in
`app/src/main/assets/ATTRIBUTION.txt`, and the Source Serif 4 OFL notice in
`app/src/main/assets/OFL-Source-Serif-4.txt`.

## Privacy

The source manifest has zero `<uses-permission>` lines. The built APK contains only the
AndroidX-generated self-permission; there is no INTERNET, mic, location or tracking permission,
no Play Services, Firebase or analytics. `allowBackup` is false and cleartext traffic is
disabled. CI asserts the permission and size gates on every build. It works fully offline.

## Languages

| Status | Languages |
|---|---|
| Committed (11) | Spanish, Indonesian, Portuguese, Hindi, French, Arabic (Egyptian), Mandarin, Swahili, Russian, Vietnamese, Thai |
| Bench (8) | Turkish, Japanese, Korean, German, Italian, Farsi, Serbian, Tamil |

Bengali was removed from the catalogue. Free cluster add-ons (Urdu via Hindi, Malay via
Indonesian, Croatian/Bosnian/Montenegrin via Serbian) are credited but have no separate course.

Adding a language or a phrase is a **data change**, not a code change: edit the JSONL assets under
`app/src/main/assets/langkraft/`.

## Build and verify

Requires JDK 21, Android SDK 35, Node 20+ and the Gradle wrapper (8.11.1). Debug builds work
without signing credentials; release signing reads a gitignored `keystore.properties`.

```bash
./gradlew testDebugUnitTest assembleDebug assembleRelease
node pipeline/lint.mjs
for c in spa ind por hin fra ara cmn swh rus vie tha tur jpn kor deu ita fas srp tam; do
  python3 pipeline/validate_content.py "$c"
done
aapt dump permissions app/build/outputs/apk/release/app-release.apk
```

`pipeline/validate_content.py` is the hard content gate: 35 distinct lessons, sequential unique
IDs, Latin-only visible fields, CAPS stress, valid dates, correct anchors and source strings,
plus numbers coverage and CAPS. CI (`.github/workflows/pipeline.yml`) runs the lint, the hard
gate for all 19 languages, unit tests, both builds, a real `aapt` permission assertion and a
release size gate.

## Design and history

v2.0.0 is a full dark-navy redesign (v4) selected from generated concept mockups; it supersedes
the v3 "signal cards" and the earlier Neo-Brutalism direction. The decision is recorded in the
shared knowledge base and `docs/VISUAL-V4-NAVY.md`; earlier specs remain in `docs/` as history.

The original v0.1.0 repository was deleted on 2026-11-06. This is a clean rebuild with no
carry-over of the old tiers, catalogue or tag system. Releases are tagged `v1.0.0`, `v1.1.0`
and `v2.0.0`.
