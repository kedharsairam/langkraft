# LangKraft — the locked brief, in one page

Goal: one offline app for **very basic spoken travel conversation**. Not a whole language, no
reading, no writing, no native scripts, no listening practice inside the app.

Owner: Kedhar. One user. Everything below is decided; do not reintroduce retired features.

## Product

- 19 languages: 11 committed, 8 benched. One shared set of 35 travel-survival lessons.
- A language opens to a lessons index, then a full-list lesson: every phrase visible at once,
  numbered `1.` upward, English small above, romanized target large mono below.
- Star marks a phrase hard. Position is progress. Search is local in memory.
- Per-language sound-check briefing, numbers grid, and search. No other screens.

## Retired — do not bring back

- Hide → say aloud → reveal (removed 2026-11-08; a lesson is plain readable text).
- Glue-phrase strip and per-lesson search field (same date).
- "This failed me" crowd button, listening tag, quizzes, streaks, points, badges, scoring.
- Four mini-dialogues — no dialogues in this version.
- The old four-tier spine and the 40-phrase universal kit.

## Non-negotiables

- Romanization only. No target-language script anywhere in the app, including numbers and
  sound-checks. Telugu is allowed as a sound reference in notes.
- No audio in or out (including TTS), no microphone, no typing except search.
- No network permission, no Play Services, no Firebase, no analytics, no account.
- Plain factual UI copy. No hype, no emojis, no childish praise.
- Polite register first in every language; informal noted but never the default.

## Content

- LingoHut supplies the **meaning anchors** only; its text is never copied (their terms forbid
  copying and public display). Wording is authored from Wikivoyage (CC BY-SA 4.0) and original
  writing, checked for current, region-neutral usage.
- Every row has: id, lang, theme (lesson number), english, romanization, youSay, soundTip, note,
  source, checked, anchor.
- Full lesson contents — never sample or cap the number of phrases per lesson.
- `pipeline/validate_content.py` is the hard gate; every language must pass it.

Full design history in `data/shared-kb/` in the opencode-config repo.
