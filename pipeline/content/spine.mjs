/**
 * Assigns harvested phrases to spine slots, and reports what is missing.
 *
 * WHY THIS EXISTS
 *
 * Twenty languages were each selected independently from their own Wikivoyage page, in page order,
 * so no two languages shared a Tier 0 phrase at all — 376 distinct English captions across 1,050
 * entries, and none present in all twenty. Nothing tied "thank you" in Japanese to "thank you" in
 * Thai. This file is the thing that ties them.
 *
 * IT MEASURES BEFORE IT WRITES
 *
 * The first run of this reports coverage and writes nothing. That order is deliberate: a generator
 * that fills slots silently and reports afterwards cannot tell you whether a missing slot is a
 * real gap in the sources or a matching pattern that was written too narrowly. Measuring first
 * makes that a question with an answer.
 *
 * MATCHING IS EXPLICIT, NOT FUZZY
 *
 * Each slot lists the page captions that identify a row as filling it. No similarity scores, no
 * nearest-neighbour, no embedding. A slot filled by a phrase an editor did not put there cannot be
 * seen, corrected, or argued with, and this project's entire content history is a sequence of
 * confident wrong answers that looked fine.
 *
 * The other consequence is deliberate: an unmatched slot is a GAP, reported by name. Filling it
 * with the closest available phrase is how a phrasebook ends up asserting that "bye" means
 * "thank you".
 *
 * ORDERING AND CLAIMS
 *
 * Slots are filled in spine order, and each harvested row is claimed at most once. The first slot
 * to match a caption wins, so a caption listed under two slots is assigned to the one that appears
 * earlier in the spine — which is the more fundamental meaning of the two.
 *
 *     node content/spine.mjs              # coverage for every tier and language
 *     node content/spine.mjs --tier 0     # one tier
 *     node content/spine.mjs --lang jpn   # one language, and what is missing from it
 *     node content/spine.mjs --unused     # harvested rows no slot claimed
 */

import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = new URL('../..', import.meta.url).pathname;
const SPINE_DIR = join(ROOT, 'content', 'spine');
const CURATED = join(ROOT, 'catalogue', 'curated');

/**
 * Languages whose phrases are written in Latin, from the catalogue.
 *
 * Read from `catalogue/languages.json` rather than listed here, because a hardcoded list is a
 * language that behaves differently the moment it is added — which is exactly the class of bug
 * that made ten Latin languages look starved of content in the first place.
 */
export const LATIN_LANGUAGES = new Set(
  JSON.parse(readFileSync(join(ROOT, 'catalogue', 'languages.json'), 'utf8'))
    .languages.filter((l) => l.script === 'Latin').map((l) => l.code),
);

const arg = (name, fallback = null) => {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : fallback;
};

/**
 * The key two captions are compared by.
 *
 * Aggressive on punctuation and whitespace, exact on words. `Thank you very much!` and
 * `thank  you.` are the same caption; `Thank you` and `Thanks` are not, and a file that merged
 * them would be hiding the distinction rather than normalising it.
 */
function captionKey(s) {
  return String(s ?? '')
    .toLowerCase()
    .replace(/[‘’‛]/g, "'")
    .replace(/[“”]/g, '"')
    .replace(/[!?.,;:()]/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

export function loadSpine(tier) {
  const files = existsSync(SPINE_DIR)
    ? readdirSync(SPINE_DIR).filter((f) => f === `tier${tier}.json`).sort()
    : [];
  return files.map((f) => JSON.parse(readFileSync(join(SPINE_DIR, f), 'utf8')));
}

/**
 * The rows a language's spine is filled from.
 *
 * For nineteen of the twenty that is its harvested corpus, `catalogue/curated/<code>.jsonl`.
 *
 * ENGLISH IS DIFFERENT, and that is not an inconsistency. English is the calibration language —
 * it measures whether the tier sizes are right before they are applied to the rest — and
 * phrasebooks exist for languages you travel TO, not for one you already speak. It therefore has
 * no harvested corpus at all, and its spine is filled from its own authored Tier 0, where
 * `text_native` IS the English and `text_english` is null because there is nothing to gloss.
 *
 * Loading English as if it had a curated file produced "0 of 32 slots filled" with no indication
 * that the file was absent rather than empty, which is a number that reads like a broken source.
 */
export function loadCurated(code) {
  const p = join(CURATED, `${code}.jsonl`);
  if (existsSync(p)) {
    return readFileSync(p, 'utf8').split('\n').filter(Boolean).map((l) => JSON.parse(l));
  }
  // `catalogue/authored/`, NOT `content/`. The spine builder writes `content/<code>-tier0.jsonl`,
  // so reading English's source from there made the builder consume its own previous output:
  // English was filled from the file it had just written, and the two authored entries were gone
  // by the second run. A generator that reads its own output is not a generator.
  const authored = join(ROOT, 'catalogue', 'authored', `${code}-tier0.jsonl`);
  if (existsSync(authored)) {
    return readFileSync(authored, 'utf8')
      .split('\n')
      .filter(Boolean)
      .map((l) => JSON.parse(l))
      // An authored record's phrase is `text_native`; give the matcher the caption it expects.
      .filter((r) => r.text_native)
      .map((r) => ({
        ...r,
        native: r.text_native,
        english: r.text_english ?? r.text_native,
        pronunciation: r.text_romanized ?? null,
        // An authored row is `authored`, not `curated`. Defaulting it to curated made English
        // claim its own editorial work was a phrasebook, and then fail to find a curated resource
        // to source it from — because it has none.
        _slot_source: r.source?.class === 'attested' ? 'attested' : 'authored',
      }));
  }
  return [];
}

/**
 * Fills one language's spine.
 *
 * Returns the assignment plus the reasons for every gap, because "3 slots missing" is a number
 * and "greeting.after-you is missing" is something that can be acted on.
 */
/**
 * The attested corpus — native-authored Tatoeba sentences, already concept-tagged.
 *
 * This is where a slot goes when no phrasebook has it, and that is not a rare case. The Hindi
 * Wikivoyage page was measured and contains NO greeting, thank you or goodbye row anywhere on the
 * page: its twelve phrase-list sections are Basics, Bus and Train, Directions, Taxi, Lodging,
 * Money, Eating, Bars, Shopping, Driving, Problems and Authority. Hindi filled 0 of 32 slots from
 * the phrasebook alone, which is a real hole in the source rather than a defect in the selector.
 *
 * Tatoeba then has `नमस्ते।` for "Hello!" — a native speaker's own sentence, which is stronger
 * evidence than a volunteer phrasebook line in any case.
 *
 * Curated is tried FIRST and attested only fills what is left, because a phrasebook row carries
 * register and context that a single sentence does not. `नमस्ते` is a greeting; it does not tell
 * you whether it is what you say to a shopkeeper or to an elder.
 */
export function loadAttested(code) {
  const p = join(CURATED, '..', 'attested', `${code}.jsonl`);
  if (!existsSync(p)) return [];
  return readFileSync(p, 'utf8').split('\n').filter(Boolean).map((l) => {
    const r = JSON.parse(l);
    // `tatoeba_id` and `author` are carried through, and dropping them is what the content linter
    // exists to catch: an `attested` entry with neither a named author nor a resolvable id cannot
    // be verified by whoever reviews it and cannot be properly attributed under CC BY, so the
    // provenance has to survive being read by a parser. The first version of this mapping kept
    // only the three display fields and every attested entry failed on that rule.
    return {
      native: r.native,
      english: r.english,
      pronunciation: null,
      author: r.author ?? null,
      tatoeba_id: r.tatoeba_id ?? null,
      _source: 'attested',
    };
  });
}

/**
 * Both of a language's row sources, in the order they must be offered: phrasebook first, Tatoeba
 * second. Exported as ONE call so no caller can pass them the other way round, which would silently
 * fill every slot from Tatoeba while reporting it as curated.
 */
export function loadRowsFor(code) {
  return [loadCurated(code), loadAttested(code)];
}

/**
 * The Tier 1 pool: harvested rows the Tier 0 spine did not claim.
 *
 * Read from `catalogue/tier1-material/`, which the Tier 0 build writes, so Tier 1 draws from
 * exactly the rows Tier 0 left over and cannot re-claim a phrase Tier 0 already ships — the same
 * phrase in two tiers is a duplication the reader would see twice for no reason.
 *
 * `source_class`, `author` and `tatoeba_id` are carried through for the same reason the attested
 * loader carries them: an entry with no resolvable provenance cannot be licence-checked, and the
 * content linter refuses it. A surplus file that lost those fields would be Tier 1 material that
 * cannot become Tier 1 entries.
 */
/**
 * The rows a given tier is allowed to draw from.
 *
 * Tier 0 draws from the phrasebook and the attested corpus. Tier 1 draws from what Tier 0 left
 * over, so a phrase cannot appear in both tiers — the reader would meet it twice, in two places,
 * for no reason. Routing both through one function means a later tier cannot quietly start reading
 * Tier 0's pool by accident.
 */
export function rowsForTier(tier, code) {
  return tier === 0
    ? [loadCurated(code), loadAttested(code)]
    : [loadTier1Material(code), []];
}

export function loadTier1Material(code) {
  const p = join(ROOT, 'catalogue', 'tier1-material', `${code}.jsonl`);
  if (!existsSync(p)) return [];
  return readFileSync(p, 'utf8')
    .split('\n')
    .filter(Boolean)
    .map((l) => JSON.parse(l))
    .filter((r) => r.native && r.english)
    .map((r) => ({
      native: r.native,
      english: r.english,
      pronunciation: r.pronunciation ?? null,
      concept: r.concept ?? null,
      romanized_only: r.romanized_only ?? false,
      author: r.author ?? null,
      tatoeba_id: r.tatoeba_id ?? null,
      url: r.source_url ?? null,
      _source: r.source_class ?? 'curated',
    }));
}

export function assign(spine, curated, attested = [], latinNative = false) {
  // Rows that carry no text in the target script are still usable here — the spine assigns by
  // MEANING, and the romanisation is the phrase for these pages — but they are recorded so a
  // language whose whole page is a romanisation is visible rather than silently thin.
  // A romanised-only row cannot fill a slot in a language that has its own script.
  //
  // Measured across the corpus: 357 of 6,656 harvested rows carry `romanized_only`, and 264 rows
  // in thirteen languages have the English caption as their "native" text. For a LATIN-script
  // language the second group is normal — a German phrasebook writes "Where is the toilet?" as
  // both the caption and the German phrase — but for a language with its own script it is either
  // a page placeholder or a phrase the harvester failed to read, and either way it is not content
  // in that language.
  //
  // Two slots were being filled this way, both Tamil, both with a romanisation standing in for a
  // phrase that does not exist in the corpus. The entry would then fail the content linter,
  // because a Tamil entry with Latin text and no romanisation column is not a shippable record —
  // so the slot would have looked filled and produced nothing.
  //
  // Excluded here rather than in the generator so the measurement and the generation cannot
  // disagree about what is available.
  const NON_LATIN = latinNative => !latinNative;
  const usable = [
    ...curated
      .filter((r) => r.native && r.english)
      .filter((r) => !(r.romanized_only && NON_LATIN(r._latin)))
      .map((r) => ({ ...r, _source: r._source ?? 'curated' })),
    ...attested.filter((r) => r.native && r.english),
  ];

  const claimed = new Set();
  const filled = new Map();
  const unmatchedRows = [];

  for (const slot of spine.slots) {
    // A pattern ending in `*` is a prefix match. Needed because some page captions embed the
    // language's own name — "How do you say this in Thai?" — and an exact list can never match
    // more than the one page that happens to phrase it that way.
    const exact = new Set();
    const prefixes = [];
    for (const pattern of slot.match) {
      const k = captionKey(pattern);
      if (k.endsWith('*')) prefixes.push(k.slice(0, -1).trim());
      else exact.add(k);
    }
    const hit = usable.find((r) => {
      if (claimed.has(r)) return false;
      const k = captionKey(r.english);
      return exact.has(k) || prefixes.some((pre) => pre && k.startsWith(pre));
    });
    if (hit) {
      claimed.add(hit);
      // The source is recorded per slot, because "attested" and "curated" are different claims
      // about how well known a phrase is, and the whole point of the evidence model is that the
      // difference survives into the app rather than being flattened into a single list.
      filled.set(slot.id, { ...hit, _slot: slot.id, _slot_source: hit._source ?? 'curated' });
    }
  }

  for (const r of usable) {
    if (!claimed.has(r)) unmatchedRows.push(r);
  }

  return {
    filled,
    slots: spine.slots,
    missing: spine.slots.filter((s) => !filled.has(s.id)).map((s) => s.id),
    unused: unmatchedRows,
    usableCount: usable.length,
  };
}

// ---------------------------------------------------------------------------
// Report
// ---------------------------------------------------------------------------

function main() {
const onlyTier = arg('--tier');
const onlyLang = arg('--lang');
const showUnused = process.argv.includes('--unused');

const catalogue = JSON.parse(readFileSync(join(ROOT, 'catalogue', 'languages.json'), 'utf8'));
const languages = catalogue.languages.map((l) => l.code);
const tiers = (onlyTier ? [Number(onlyTier)] : [0, 1, 2, 3])
  .map((t) => ({ t, spine: loadSpine(t) }))
  .filter(({ spine }) => spine.length);

if (!tiers.length) {
  console.log('  no spine files found under content/spine/');
  process.exit(0);
}

const totalSlots = tiers.reduce((n, { spine }) => n + spine[0].slots.length, 0);
console.log(`\n  spine: ${totalSlots} slots across ${tiers.length} tier(s)\n`);

for (const { t, spine } of tiers) {
  const one = spine[0];
  console.log(`  TIER ${t} · ${one.name} — ${one.slots.length} slots`);
  console.log(`  lang`.padEnd(9) + `filled`.padStart(7) + `missing`.padStart(9) + `usable`.padStart(9) + `   coverage`);

  const langs = onlyLang ? [onlyLang] : languages;
  const perSlot = new Map(one.slots.map((s) => [s.id, 0]));
  const perSource = new Map();

  for (const code of langs) {
    const [a, b] = rowsForTier(t, code);
    const res = assign(one, a, b, LATIN_LANGUAGES.has(code));
    for (const id of res.filled.keys()) perSlot.set(id, perSlot.get(id) + 1);
    for (const row of res.filled.values()) {
      const k = row._slot_source ?? 'curated';
      perSource.set(k, (perSource.get(k) ?? 0) + 1);
    }
    const pct = Math.round((res.filled.size / one.slots.length) * 100);
    const src = [...res.filled.values()].reduce((m, r) => {
      const k = r._slot_source ?? 'curated'; m[k] = (m[k] ?? 0) + 1; return m;
    }, {});
    console.log(
      `  ${code.padEnd(6)}${String(res.filled.size).padStart(8)}${String(res.missing.length).padStart(9)}` +
        `   ${String(res.usableCount).padStart(7)}   ${String(pct).padStart(3)}%` +
        `   ${Object.entries(src).map(([k, v]) => `${k} ${v}`).join('  ')}` +
        (onlyLang && res.missing.length ? `\n         missing: ${res.missing.join(', ')}` : ''),
    );
  }
  console.log(`\n  filled by source: ${[...perSource].map(([k, v]) => `${k} ${v}`).join('  ')}`);

  // Per-slot coverage, weakest first. THIS is the table that decides what to do next, and it
  // separates the two very different reasons a slot is thin:
  //
  //   - filled in most languages but not all  -> usually a match list written too narrowly
  //   - filled in almost none                 -> usually a real hole in the sources
  //
  // Those need opposite fixes, and reporting one number per language hides which is which.
  console.log(`\n  slot coverage across ${langs.length} languages, weakest first:`);
  const ranked = one.slots
    .map((sl) => ({ sl, k: perSlot.get(sl.id) }))
    .sort((a, b) => a.k - b.k);
  for (const { sl, k } of ranked) {
    const bar = '#'.repeat(k) + '.'.repeat(langs.length - k);
    console.log(`    ${String(k).padStart(2)}/${langs.length}  ${bar}  ${sl.id.padEnd(26)} "${sl.english}"`);
  }

  const n = langs.length;
  const full = one.slots.filter((s) => perSlot.get(s.id) === n);
  const partial = one.slots.filter((s) => {
    const k = perSlot.get(s.id);
    return k > 0 && k < n;
  });
  const none = one.slots.filter((s) => perSlot.get(s.id) === 0);

  console.log(`\n  slots filled in all ${n} languages : ${full.length}`);
  console.log(`  slots filled in some            : ${partial.length}`);
  console.log(`  slots filled in none             : ${none.length}`);
  if (none.length) {
    console.log(`\n  UNFILLED EVERYWHERE — these have no matching caption in any page:`);
    for (const s of none) console.log(`    ${s.id.padEnd(30)} "${s.english}"`);
    console.log(`\n  Either the slot is not on any phrasebook, or its match list is written too`);
    console.log(`  narrowly. Both are worth fixing, and they are fixed differently: a slot that is`);
    console.log(`  genuinely unavailable stays, labelled; a match list that is too narrow gets more`);
    console.log(`  patterns. Neither is fixed by filling it with the nearest phrase.`);
  }
  if (partial.length && partial.length <= 12) {
    console.log(`\n  partially filled:`);
    for (const s of partial) console.log(`    ${s.id.padEnd(30)} ${perSlot.get(s.id)}/${n}`);
  }

  if (showUnused) {
    const code = onlyLang || langs[0];
    const [a, b] = rowsForTier(t, code);
    const res = assign(one, a, b, LATIN_LANGUAGES.has(code));
    console.log(`\n  ${code}: ${res.unused.length} harvested rows no slot claims (of ${res.usableCount})`);
    for (const r of res.unused.slice(0, 25)) {
      console.log(`    ${JSON.stringify(r.english)}  →  ${JSON.stringify(r.native)}`);
    }
  }
  console.log('');
}
}

// This file exports the row loaders and `assign`, and `spine-build.mjs` imports them. Running
// the report on import would print a full coverage table as a side effect of being imported — the
// same shape as the unguarded `main()` in the harvester that made every test fetch twenty pages.
if (process.argv[1] && process.argv[1].endsWith('spine.mjs')) main();
