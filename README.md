# LangKraft

A phrasebook that knows what order to read it in, which variety to give you, and what not
to say. Built for one person learning to hold a small, real conversation in about twenty
languages, at zero cost, entirely offline.

**Read-only. No audio. No exercises. No streaks. No accounts. No network permission.**

## What it is

Every language has a researched floor: the smallest set of real-world phrases that actually
lets you transact, navigate and ask for help, in the variety you will actually hear. The
floor is delivered as **four tiers**, each independently useful, so stopping early still
leaves you able to do something real.

- **Tier 0 · Courtesy** (~50) — not rude. Greet, thank, apologise, say you're learning.
- **Tier 1 · Transaction** (~150) — prices, food, tickets, directions, a room.
- **Tier 2 · Independence** (~500) — handle an unexpected problem unaided.
- **Tier 3 · Conversation** (~1,200) — fifteen minutes with a stranger.

Tier sizes are **per language**, because an isolating language needs more items to reach the
same capability than a synthetic one. They are not interchangeable.

## What it is not

It never asks you to retrieve anything. No quizzes, no typing, no multiple choice, no
self-grading. It shows you content; practice happens outside it, with real people.

That is a deliberate trade and it has a name: **this app makes you prepared.** It will not
make you fluent, and it says so rather than implying otherwise.

## What makes it different

| | |
|---|---|
| **Country packs** | Content re-cut by destination, not by language. *"I'm going to Morocco"* → which of your languages work there, which dialect, and which will fail |
| **"You already know this"** | Mutual-intelligibility research as a feature. Learn Swahili → Congolese Swahili marked as partial credit |
| **Say-it / understand-it** | Every item is tagged for production or reception. You need to understand far more than you produce — you're the visitor, you get spoken to faster than you speak |
| **Failure warnings** | Where a language is official but will not work. Mandarin in Hong Kong. Levantine Arabic in Morocco. Indonesian outside the cities |
| **Dialect switching** | Levantine / Egyptian / Darija side by side. The week-0 choice is a bet, not a brick wall |
| **Both scripts** | Native script plus a practical romanization, per-language scheme — so the app is usable without reading the target script at all |

## Export

Per-country, on demand, as **HTML or PDF**, from current content. Self-contained, embedded
fonts, no network. "Here's the Morocco book" — not "here's the Arabic book."

## Status

Nothing is built yet. The design is fixed and the first spec is written.

- [`specs/SCHEMA.md`](specs/SCHEMA.md) — the per-language contract, and the linter that enforces it
- [`specs/english.yaml`](specs/english.yaml) — the first spec, and the calibration of the tier sizes themselves
- [`docs/PRODUCT.md`](docs/PRODUCT.md) — what this app is, what it will never do, and why

<details>
<summary>Why it is built this way</summary>

**The engine is small; the content is the product.** No inputs, no adaptive scheduling, no
audio — that removes every subsystem that makes apps complex. Twenty languages becomes a
folder of data files rather than twenty code paths, which is why adding one is an update and
not a rewrite.

**The floor is uncertain and stays that way.** Nobody can measure how many phrases a
traveller needs for Swahili without going to Swahili. So the tiers are granular — bounded
loss instead of one large bet — the floor is calibrated against English (the one language the
user can judge from the inside), and a single "this failed me" flag feeds corrections from
lived experience back into the specs.

**Specs are versioned and critiqued adversarially.** Every factual claim carries a source and
a verification flag; a bare number fails the linter. A critic agent attempts to refute each
spec before it ships. Adding agents without adversarial checking only multiplies the same
mistake with more confidence.

**Every spec is a hypothesis until travelled.** `status` and `certainty` say so in the file
rather than in a readme nobody opens.

</details>

## Licence

Not yet chosen. Content sources are individually licensed and attributed in-app.