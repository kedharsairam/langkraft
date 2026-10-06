# Product contract

What LangKraft is, what it will never do, and why. Anything not in this document is not a
planned feature.

## The goal it serves

Reach a deliberately-low, real-world-usable floor in a fixed portfolio of ~19 languages, so
that the maximum number of countries become places where a short, genuine interaction with
an ordinary local is possible. Speaking and listening only — reading and writing are never
the goal, and the interface language is always English.

## Permanent constraints

These are product constraints, not preferences. They will not be relaxed as scope
suggestions, and proposing them as improvements is out of scope.

| Constraint | Reason |
|---|---|
| **No audio, in or out.** No bundled clips, no generated audio, not even on-device system TTS | No text-to-speech is ever as close to a native speaker as a native speaker. Listening is supplied externally, deliberately outside the app |
| **No microphone** | The app never captures audio |
| **No typing to the app** | No free text, no answer input, no typed translation |
| **No exercises** | The app never asks the learner to retrieve anything. This is the defining trade |
| **No calendar, streaks, points, badges** | The learner works at their own pace; nothing counts how often |
| **Read-only** | The app presents; it does not test |
| **No external links or runtime sources** | Every byte ships in the APK. No network permission. Licence attribution appears as an in-app static file |
| **English interface** | Regardless of target language |
| **Progress is position only** | `unit · item · of`, per language per tier, plus a bookmark. Not accuracy, not comprehension, not time |

### The one permitted input

A **"this failed me" flag** on any entry, with an optional second tap naming the country.
One tap, no free text, no score, nothing counted on the home screen.

It exists because the floor is an empirical property of real speech that no amount of research
can settle. Lived experience outranks research on every point where they disagree, and a flag
tagged with a country points directly at the dialect or register call that was wrong.

## What this app therefore is

**A research-ordered, country-organised, minimum-sufficient phrasebook for spoken travel.**

Its four differentiators — country packs, mutual-intelligibility clusters, the
say-it/understand-it split, and failure warnings — all trace to research already done. None
is available from any competitor, because none organises content around *countries* rather
than around languages.

## What it is honest about

- **It makes you prepared, not fluent.** Because it never asks you to retrieve anything,
  practice happens outside it. This is stated in the app, not buried here.
- **It has no retention mechanism.** No streak, no calendar, no reward. It works if
  discipline holds and only then. No design decision fixes that.
- **The floor is a hypothesis.** `certainty` on each tier says which ones are near-certain
  and which are guesses. Tier 0 is built to be safe; Tier 3 is a bet.
- **Some things cannot be known in advance.** The dialect choice for a language not yet
  started, the floor for a language not yet reached, and register judgment in a culture not
  yet visited. All three resolve in the user's favour once they travel.

## Architecture

```
specs/*.yaml        per-language parameters — the only thing that varies
content/*.jsonl     the items, ordered
app/                the engine — invariant, ~3,500 LOC, written once
pipeline/           build-time: research → spec → critique → lint → content → build
```

**Adding a language must never require a code change.** If a field cannot be expressed in
`specs/SCHEMA.md`, it does not belong in the engine.

### The pipeline's accuracy mechanism

More agents does not mean more accuracy. Unsupervised parallel agents multiply the same
mistake with more confidence. Accuracy comes from three gates:

1. **Provenance gate.** No number enters a spec without a source. `verified: false` is
   allowed; a bare number fails the linter.
2. **Adversarial critique.** A critic agent attempts to *refute* each spec, fetching every
   cited URL to confirm it says what is claimed. Unrefuted claims become `verified: true`;
   unsupported ones fail.
3. **Cross-language consistency.** If one spec calls a language analytic and another calls
   a closely-related language synthetic, one is wrong. Machine-checkable across all files.

### Script rendering is the real engineering risk

A read-only app is still hard at exactly one thing: drawing 20 writing systems correctly.
Thai tone marks collide. Tamil, Telugu and Devanagari conjuncts need the right font and fail
*silently* without one. Arabic is RTL and break naive layout assumptions. CJK
breaks differently again.

Fonts are bundled, not inherited from the phone. PDF and HTML export are verified on the
real device on Tamil and Thai **before** anything else is built — because a missing glyph
produces empty boxes, not an error, and only a device reveals it.

## Open questions

| | |
|---|---|
| Tier sizes | Untested. English is the calibration and has not yet been tested against a real trip |
| Exchanges | Tier 0 is currently a flat phrase list, which is the wrong shape — real interaction is two-sided |
| Register mapping | No register guidance authored for any language |
| Country packs | None built. Generated from spec data, so fixing a spec fixes every country at once |
| Licence | Not chosen for the repository |