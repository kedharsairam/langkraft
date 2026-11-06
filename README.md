# LangKraft (rebuild, clean slate)

Personal, offline, romanization-only survival phrasebook for Kedhar.
One user. No accounts. No network. No scripts, ever.

## What it is
- Production-only trainer: hide → say aloud → reveal. No fake listening tag, no quizzes, no streaks.
- 40-phrase universal kit (8 themes ×5) + 3 glue phrases + numbers grid + 4 dialogues, same for every language.
- 12 committed + 8 bench. Free extras credited (Urdu via Hindi, Malay via Indonesian, etc.).
- Star = hard deck. Private note per phrase. Position = progress.
- Tiny text-only APK. Content as JSONL. Adding a language = add data, no code change.

## What it is not
- Not a tutor, not a crowd app, not a cloud sync app.
- No audio, no mic, no typing to the app (except search + private note).
- No "this failed" crowd button. No gamification. Plain factual UI copy.

## Languages
Committed: Spanish, Indonesian, Portuguese, Hindi, French, Arabic (Egyptian), Mandarin, Bengali, Swahili, Russian, Vietnamese, Thai.
Bench: Turkish, Japanese, Korean, German, Italian, Farsi, Serbian, Tamil.
Pilot in this build: Spanish end-to-end. Other languages arrive as data files only.

## Privacy
- Zero `<uses-permission>` in manifest. No INTERNET, no mic, no tracking.
- No Play Services, no Firebase.
- Works on GrapheneOS. Sideload APK or F-Droid.
- Export/import is a plain local JSON file via SAF.

## Build (local only — no push without owner approval)
Requires JDK 21, Android SDK 35.
```
.\gradlew.bat assembleDebug
.\gradlew.bat testDebugUnitTest
node pipeline/lint.mjs
aapt dump permissions app\build\outputs\apk\debug\app-debug.apk
```
Do not commit or push until Kedhar approves diff and gate results.

## Content originals
JSONL in `app/src/main/assets/langkraft/*.jsonl` are originals.
Each row: source + checked date required. Provenance gate fails build on bare values.

See `docs/YOU_SAY_STYLE_GUIDE.md`, `docs/BRIEF.md`, `pipeline/lint.mjs`.

Old repo (`kedharsairam/langkraft`, deleted 2026-11-06) is reference only. No carry-over of its data, buttons, or tag system.
