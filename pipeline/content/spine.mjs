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

function loadSpine(tier) {
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
function loadCurated(code) {
  const p = join(CURATED, `${code}.jsonl`);
  if (existsSync(p)) {
    return readFileSync(p, 'utf8').split('\n').filter(Boolean).map((l) => JSON.parse(l));
  }
  const authored = join(ROOT, 'content', `${code}-tier0.jsonl`);
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
function loadAttested(code) {
  const p = join(CURATED, '..', 'attested', `${code}.jsonl`);
  if (!existsSync(p)) return [];
  return readFileSync(p, 'utf8').split('\n').filter(Boolean).map((l) => {
    const r = JSON.parse(l);
    return { native: r.native, english: r.english, pronunciation: null, _source: 'attested' };
  });
}

export function assign(spine, curated, attested = []) {
  // Rows that carry no text in the target script are still usable here — the spine assigns by
  // MEANING, and the romanisation is the phrase for these pages — but they are recorded so a
  // language whose whole page is a romanisation is visible rather than silently thin.
  const usable = [
    ...curated.filter((r) => r.native && r.english).map((r) => ({ ...r, _source: r._source ?? 'curated' })),
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
    const res = assign(one, loadCurated(code), loadAttested(code));
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
    const res = assign(one, loadCurated(code), loadAttested(code));
    console.log(`\n  ${code}: ${res.unused.length} harvested rows no slot claims (of ${res.usableCount})`);
    for (const r of res.unused.slice(0, 25)) {
      console.log(`    ${JSON.stringify(r.english)}  →  ${JSON.stringify(r.native)}`);
    }
  }
  console.log('');
}