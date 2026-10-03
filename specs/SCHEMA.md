# LangKraft spec schema, v1

> The contract every `specs/<lang>.yaml` must satisfy. `pipeline/spec/lint.py` enforces it.
> A spec that does not lint does not ship.
>
> **Design rule:** the app's engine is invariant; the spec is the only thing that varies per
> language. Adding a language must never require a code change. If a field cannot be expressed
> here, it does not belong in the engine.

## Provenance — the gate that matters

Every factual claim in a spec is a `claim`:

```yaml
value: 88.2            # the number
source: "https://…"    # where it came from
verified: true         # did a critic fetch that URL and confirm it says this?
checked: 2026-10-03    # when
note: "…"              # optional caveat
```

**Rules the linter enforces:**

1. Any field listed in `REQUIRED_SOURCED` must be a `claim`. A bare number is a failure.
2. `verified: false` is allowed. **A bare number with no source is not.** That combination was
   how a previous research pass produced three unsourced hour figures and then had to admit
   they were invented — so it is now structurally impossible.
3. `checked` older than `max_age_days` on a resource URL is a warning, not an error. Sources rot.

---

## The schema

```yaml
language:
  code: en                              # ISO 639-3
  name: English
  endonym: English
  romanization: null                    # null for Latin-script languages
  role: calibration                     # calibration | course
  gloss_mode: same_as_native            # required | same_as_native — see below

spec_version: 1.0.0                     # semver. Bump minor when content changes.
status: draft                           # draft | reviewed | shipped | deprecated

# ---------------------------------------------------------------------------
# Variety — the decision that determines whether the language is useful at all
# ---------------------------------------------------------------------------
variety:
  default: en-US
  romanization_scheme: null             # see table in docs/METHODOLOGY.md
  rationale: claim
  # Variants ship WITH the spec, so switching is possible later. Locking the
  # choice at build time was treated as irreversible; storing variants removes
  # that risk.
  variants:
    - id: en-GB
      label: British
      notes: claim
    - id: en-IN
      label: Indian
      notes: claim
  # Countries where this choice does not work. Surfaced in country packs.
  fails_in:
    - territory: Hong Kong
      reason: claim

# ---------------------------------------------------------------------------
# Tiers — the floor, made granular so uncertainty is bounded
# ---------------------------------------------------------------------------
# The tier *names* are invariant across every language. The *sizes* are not.
# An isolating language needs more items to reach the same capability.
tiers:
  - id: 0
    name: Courtesy
    intent: Not rude. Greet, thank, apologise, announce you are learning.
    size: claim                        # REQUIRED_SOURCED
    certainty: high                    # high | medium | low
  - id: 1
    name: Transaction
    intent: Prices, food, tickets, directions, rooms work.
    size: claim
    certainty: medium
  - id: 2
    name: Independence
    intent: Handle an unexpected problem unaided for a week.
    size: claim
    certainty: low
  - id: 3
    name: Conversation
    intent: Real small talk about your life.
    size: claim
    certainty: low

# ---------------------------------------------------------------------------
# Structure — what makes this language this language
# ---------------------------------------------------------------------------
structure:
  morphology: moderately_synthetic      # analytic | moderately_synthetic | heavily_synthetic | isolating
  tones: false
  word_order: SVO
  # Sounds absent from Telugu that carry meaning. Kedhar's L1 is the baseline
  # he learns from, so these are the ones worth flagging.
  sounds_absent_from_l1: []
  script:
    primary: Latin
    direction: ltr                     # ltr | rtl

register:
  system: formality                     # none | formality | politeness_levels | gender
  # Thai, Japanese, Korean, Georgian and others: the wrong level is worse than
  # bad grammar. This is a first-class content dimension where present.
  notes: claim

# ---------------------------------------------------------------------------
# Coverage — the metric that actually drives selection
# ---------------------------------------------------------------------------
coverage:
  territories: claim                    # list of territories where a beginner gets a real conversation
  adjacent:                             # mutual intelligibility -> "you already know this"
    - language: code
      overlap: claim
      note: claim
  # Where it fails even though the language is "official" there.
  traps: []

# ---------------------------------------------------------------------------
# Cost — never a bare number
# ---------------------------------------------------------------------------
cost:
  fsi_category: claim                   # I | II | III | IV | null (null = the FSI scale's own L1)
  fsi_hours_to_ilr3: claim              # State Dept figures: 552-690 / 828 / 1012 / 2200
  hours_to_floor: claim                 # per tier, at Kedhar's 2h/day = ~60h/month

# ---------------------------------------------------------------------------
# Resources — each checked, dated, licensed. All optional: a language with no
# free resource is still valid, it just gets a lower content score.
# ---------------------------------------------------------------------------
resources:
  - name: string
    url: string
    licence: string
    checked: YYYY-MM-DD
    covers: claim                       # what it actually provides

# ---------------------------------------------------------------------------
# Content attribution — a licence obligation, not an external link
# ---------------------------------------------------------------------------
attribution:
  - source: string
    licence: string
    author_credit: string
    # No URL is required in the app. The in-app credits screen is a static file.

# ---------------------------------------------------------------------------
# Correction channel — fed by the app's "this failed me" flag
# ---------------------------------------------------------------------------
# Specs are revised FROM these. Lived experience outranks research on every
# point where they disagree.
review:
  flags_received: 0                    # written by the pipeline, not by hand
  last_reviewed: null
  known_gaps: []                        # e.g. "no register guidance for X"
```

---

## Content sources — three classes, and the tier policy is enforced

Every source is one of three classes, and the class decides where its items may be used.

| Class | What it means | Examples |
|---|---|---|
| **authored** | Written for this app and reviewed by a competent speaker of the language. Per-language, unrepeatable, and the only class available for some languages | LangKraft editorial, at zero cost |
| **curated** | Professionally written and reviewed, travel-oriented. Bounded item count, high trust | US State Dept phrasebooks, Peace Corps, FSI/British Council published material |
| **corpus** | Machine-aggregated, user-contributed, unbounded, **no quality floor**, general-purpose rather than travel-domain | Tatoeba |

**The axis that matters is human-reviewed versus machine-aggregated.** `authored` and
`curated` are both controlled by a person who knows the language; `corpus` is not, and
cannot be.

**Tatoeba is `corpus`.** It is the best *available* licensed machine-readable source — 13.4M
sentences, 429 languages, CC BY, weekly exports — and it is **not** the best *existing* one.
It carries inauthentic entries, no domain guarantee, and a dated Tanaka Corpus slice in
Japanese-English.

### The policy — same for every language

| Tier | Policy | Why |
|---|---|---|
| **0 · Courtesy** | **`authored` or `curated`. Never `corpus`.** | These ~50 items decide whether a local reads you as worth helping. Highest stakes per item in the whole app |
| **1 · Transaction** | Human-reviewed preferred; `corpus` permitted to fill gaps | Same stakes, and coverage matters more as items multiply |
| **2 · Independence** | `corpus` permitted | You are looking for words for your life, not polish. Coverage beats quality |
| **3 · Conversation** | `corpus` permitted | Same |

**The linter enforces this per item, not per spec.** A Tier 0 item citing a `corpus` source is
a build failure. That is the entire mechanism, and it exists because Tier 0 is where a
mediocre phrase costs the most.

### Why there are three classes, not two

An earlier draft had only `curated` and `corpus`, and it produced an immediate contradiction:
**English declares `tier0_sources: unavailable`** — no professionally published phrasebook
exists for a language you already speak — **yet the policy barred corpus at Tier 0, so
English's Tier 0 could not be built at all.**

That was the linter working, on a policy drawn along the wrong axis. The fix is not an
exception for English. It is recognising that for the calibration language the curation *is*
judgement by a competent speaker, which is precisely what `authored` means. English Tier 0 is
therefore `authored`, and it is honestly labelled as such rather than quietly upgraded.

### When a language has no published phrasebook

Declare it rather than substituting:

```yaml
tier0_sources: authored            # or a list of source ids, or 'unavailable'
known_gaps:
  - "No published phrasebook exists for this language. Tier 0 is authored in-app and has
     had no second reader."
```

An `authored` source with one reviewer is weaker than a `curated` one with a review board, and
the gap entry says so. That is the difference being visible rather than inferred.

---

## Content item schema

Defined in `content/SCHEMA.json`. Two record types: an **entry** and an **exchange**.

### The entry

Every item carries both scripts, because the romanisation is what you actually read while
speaking and the native script is what verifies it. Latin-script languages set
`text_romanized` to `null` rather than duplicating the string.

| Field | Purpose |
|---|---|
| `id` | Stable. Never reused, so history survives spec revision |
| `lang` / `tier` / `domain` | Position. Tier drives the source policy above |
| `text_native` | The target script |
| `text_romanized` | Practical romanisation, per the language's scheme. `null` if Latin |
| `text_english` | The gloss. `null` when `language.gloss_mode` is `same_as_native` |
| `register` | `neutral` by default; language-specific levels where `register.system` demands it |
| `direction` | **`say` or `understand`** — the receptive/productive split, and the single most defensible field in the schema. You understand far more than you produce |
| `why` | One line: why this is in the floor. Lets him choose three items on a tired day |
| `exchange_id` / `exchange_turn` | Links into an exchange. Real interaction is two-sided; a flat phrase list is the wrong shape |
| `caution` | Optional. What not to say, or where it fails |
| `source` | Provenance. `{ class: authored\|curated\|corpus, id, url, licence }` — `class` is linted against the tier policy |
| `failure_flags` | Fed back from the app. Written by the pipeline from `progress.db`, never by hand |

### `language.gloss_mode`

The calibration language's gloss *is* its native text, so rendering both would print
"Hello — Hello". The spec declares which:

```yaml
language:
  gloss_mode: same_as_native      # or `required`, the default
```

`same_as_native` permits `text_english: null`. The renderer shows one line instead of two.
This is a rendering concern recorded once in the spec rather than special-cased in code.

### The exchange

An exchange is a sequence of turns with a speaker on each side. It carries a `scenario` — a
named situation, not a topic — because what he needs is *"checking into a room"*, not
*"accommodation"*.

Half of every real conversation is what the other person says. A phrase list does not
prepare you for that; an exchange does. This is the difference between knowing vocabulary and
being able to take part.

| Field | Why it exists |
|---|---|
| `role: calibration` | English is not a course to take — it is the measurement of whether the **tier sizes** are right. A different job, so it is declared |
| `tiers[].certainty` | Tier 0 is built to be near-certain; Tier 3 is a hypothesis. The app must not present a hypothesis as a fact |
| `variants[]` | Removes the irreversible week-0 dialect bet |
| `coverage.adjacent` | The mutual-intelligibility research made into a shipped feature |
| `coverage.traps` | Where a language is official but will not work. The reason the metric is "ordinary local who does not work in tourism" |
| `review.flags_received` | Makes the correction loop visible in the spec itself, not just in a log |

## Fields deliberately absent

- **`audio`** — no audio exists in this app. There is nothing to configure.
- **`exercises`** — the app never asks the learner to retrieve anything.
- **`schedule`** — no calendar. Progress is position, and position is learner state, not spec state.