# "You say:" style guide — LOCKED 2026-11-22

## What the fields mean

| Field | Purpose |
|---|---|
| `english` | The English prompt, what the traveller wants to say. |
| `romanization` | The target phrase, Latin script only. Correct spelling with accents/diacritics where the language uses them. |
| `youSay` | The spoken guide: CAPS on the stressed beat, hyphens between syllables/morae. Latin letters only. |
| `soundTip` | One short line: closest English or Telugu sound, or the trap to watch. |
| `note` | One short factual line: polite form, region, when to use. |
| `source` / `checked` | Provenance and the date it was verified. Never blank. |
| `anchor` | The LingoHut lesson this meaning came from. Reference only — its text is never copied. |

## Rules

1. **CAPS = stress or the beat the voice lands on.** `OH-lah`, `BWEH-nos DEE-ahs`.
   For tone languages CAPS is only a beat, not a tone — the tones are described in `soundTip`
   and `note`, never in `youSay`.
2. **Hyphens split syllables** (`DEH-DOHN-deh-EHS-oos-TEHD`) or morae for Japanese
   (`ko-n-BA-n-wa`). Keep them consistent within a language.
3. **Lowercase = unstressed.** Do not capitalise whole words. Unstressed clitics in Russian,
   for example, stay lowercase.
4. **No native script in any visible field.** The validator rejects it. Telugu is allowed only
   as a sound reference in `soundTip`/`note`.
5. **One sound tip, one line.** Good: "H is silent.", "v sounds like b.", "ll = y in year."
   Bad: vague advice like "soft pronunciation".
6. **Polite form first.** usted, vous, Sie, aap, вы, ninga, nín, shomā — the informal form is
   mentioned in the note but never the default.
7. **Plain copy.** Words: note, caution, where this fails. No "danger", alarms, emojis or praise.
8. **Full lesson contents.** Do not shorten a lesson to a sample. Every meaning LingoHut covers
   in the selected 35 lessons is present.

## Numbers grid

0–29, tens, 100, 1000. Same stress and no-script rules. Source and checked date required.
The grid and the lesson rows must agree on the pronunciation of the same number.

## Validation

```bash
node pipeline/lint.mjs
python3 pipeline/validate_content.py <code>
```

The gate checks all 35 lessons, ~450–470 rows, Latin-only visible fields, CAPS stress, source,
checked, anchor, and that numbers/sound-check files contain no target-language script.
A language cannot ship until it passes.
