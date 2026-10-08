/**
 * Generates the human review checklist: every (language, slot) pair the pipeline cannot fill.
 *
 * WHY THIS IS A GENERATOR AND NOT A DOCUMENT
 *
 * The gap list changes every time content is harvested, and a checklist that has to be edited by
 * hand is a checklist that is wrong within a week. This reads the two spine files, measures the
 * pools, and writes both a Markdown file for a person and a JSONL file for the machine. Neither is
 * edited by hand; regenerating is the only way either changes.
 *
 * WHY IT IS RANKED BY HOW MANY LANGUAGES A SLOT IS MISSING FROM
 *
 * A slot missing in one language is one sentence. A slot missing in nineteen is nineteen, and the
 * spine exists so that a traveller who learned it anywhere can find it everywhere. Ranking by
 * breadth rather than by tier order puts the highest-leverage work first: the reviewer reaches the
 * most languages per minute spent, which is the only thing that makes this tractable.
 *
 * WHY IT CARRIES NEAR-MISSES
 *
 * The nine Tier 0 slots that fill in one or two languages were shown to fill from keyword search
 * with real native sentences of entirely the wrong meaning — "After you" returning Hindi "After
 * cutting the vegetables, put them there". Those wrong answers are still the most useful thing this
 * project knows about those slots: they prove the corpus HAS content in that area, so a human
 * reviewer starts from candidates rather than from nothing.
 *
 * They are labelled UNVERIFIED CANDIDATES and are never used as fills. A candidate appearing here
 * means "a native speaker wrote something about this", not "this is the phrase".
 *
 *     node content/reviewer-checklist.mjs
 *     node content/reviewer-checklist.mjs --tier 0
 *     node content/reviewer-checklist.mjs --lang hin --top 5
 */

import { readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { assign, rowsForTier, loadSpine, LATIN_LANGUAGES } from './spine.mjs';

const ROOT = new URL('../..', import.meta.url).pathname;
const OUT_MD = join(ROOT, 'catalogue', 'REVIEWER.md');
const OUT_JSONL = join(ROOT, 'catalogue', 'reviewer-checklist.jsonl');

const arg = (name, fallback = null) => {
  const i = process.argv.indexOf(name);
  return i >= 0 ? process.argv[i + 1] : fallback;
};
const TOP = Number(arg('--top', '0')) || Infinity;

const catalogue = JSON.parse(readFileSync(join(ROOT, 'catalogue', 'languages.json'), 'utf8'));
const langs = catalogue.languages;

/**
 * Distinctive words only. "Do you have a *smaller* size" is a candidate for that slot; "do you have
 * any rooms available" is not, and the difference is whether any word actually carries the meaning.
 */
function keywords(english) {
  const stop = new Set(
    ('the a an i is it this that do you my me we to of for in on and or please can could would ' +
     'will there here what s not t don with have has are am be at as so very any some do does did ' +
     'he she they them his her its our your their if but then than about more most much many')
      .split(' '),
  );
  return String(english).toLowerCase()
    .replace(/[‘’]/g, "'")
    .replace(/[^a-z0-9' ]/g, ' ')
    .split(/\s+/)
    .filter((w) => w.length > 2 && !stop.has(w));
}

/** How well a caption is about the slot. A RANK for a reader, never a decision. */
function overlapScore(caption, words) {
  const cap = new Set(String(caption).toLowerCase().replace(/[^a-z0-9' ]/g, ' ').split(/\s+/));
  let hits = 0;
  for (const w of words) if (cap.has(w)) hits += 1;
  return hits;
}

const rows = [];
const perTier = {};

for (const tier of [0, 1]) {
  const spines = loadSpine(tier);
  if (!spines.length) continue;
  const spine = spines[0];
  const onlyLang = arg('--lang');
  const targets = onlyLang ? langs.filter((l) => l.code === onlyLang) : langs;

  // Measure every language once, then invert: which languages is each slot missing from.
  const missing = new Map(spine.slots.map((s) => [s.id, []]));
  const pools = new Map();

  for (const lang of targets) {
    const [curated, attested] = rowsForTier(tier, lang.code);
    pools.set(lang.code, [...curated, ...attested]);
    const res = assign(spine, curated, attested, LATIN_LANGUAGES.has(lang.code));
    for (const slot of spine.slots) {
      if (!res.filled.has(slot.id)) missing.get(slot.id).push(lang);
    }
  }

  let tierRows = 0;
  for (const slot of spine.slots) {
    const absent = missing.get(slot.id);
    if (!absent.length) continue;
    const words = keywords(slot.english);

    for (const lang of absent) {
      // Candidates are drawn from THIS language's own pool, so a reviewer is shown what exists in
      // the language they are being asked about, not in some other one.
      const pool = pools.get(lang.code) ?? [];
      const seen = new Set();
      const candidates = [];
      for (const r of pool) {
        if (!r.english || seen.has(r.english)) continue;
        const score = overlapScore(r.english, words);
        if (score >= 1) {
          seen.add(r.english);
          candidates.push({ score, english: r.english, native: r.native });
        }
      }
      candidates.sort((a, b) => b.score - a.score);

      rows.push({
        tier,
        lang: lang.code,
        lang_name: lang.name,
        script: lang.script,
        slot: slot.id,
        english: slot.english,
        why: slot.why ?? null,
        direction: slot.direction,
        concept: slot.concept ?? null,
        matched_words: words,
        unverified_candidates: candidates.slice(0, 5).map((c) => ({
          caption: c.english,
          in_language: c.native,
          shared_words: c.score,
        })),
      });
      tierRows += 1;
    }
  }

  perTier[tier] = { slots: spine.slots.length, gaps: tierRows, tiers: tiersOf(spine) };
}

function tiersOf(spine) {
  return spine.slots.length;
}

// Ranked by breadth first, then by tier, so the slots that unlock the most languages lead.
rows.sort((a, b) => b.unverified_candidates.length - a.unverified_candidates.length
  || a.tier - b.tier
  || a.slot.localeCompare(b.slot));

// ---------------------------------------------------------------------------
// The Markdown a person actually reads.
// ---------------------------------------------------------------------------

const bySlot = new Map();
for (const r of rows) {
  if (!bySlot.has(r.slot)) bySlot.set(r.slot, { meta: r, items: [] });
  bySlot.get(r.slot).items.push(r);
}
const ranked = [...bySlot.entries()].sort((a, b) => b[1].items.length - a[1].items.length);

const out = [];
out.push('# LangKraft reviewer checklist');
out.push('');
out.push('**Generated by `pipeline/content/reviewer-checklist.mjs`. Do not edit this file.**');
out.push('Run that script again and the numbers below are whatever is true then.');
out.push('');
out.push('## What this is');
out.push('');
out.push('Every (language, slot) pair that the harvest and the assigner cannot fill. Each line is one');
out.push('sentence someone needs to supply: the phrase as a local would say it, in that language.');
out.push('');
out.push('The candidates listed under a slot are **unverified**. They are real sentences written by');
out.push('native speakers that share words with the slot, which is why they were found — and why');
out.push('they cannot be trusted. A keyword search for "After you" returns Hindi *"After cutting the');
out.push('vegetables, put them there"*, which is a native speaker\'s real sentence and the wrong');
out.push('meaning entirely. Roughly half the candidates do mean the slot. Nothing in this repository');
out.push('can tell which half.');
out.push('');
out.push('So: **read them, or write the phrase yourself.** Do not accept a candidate because it looks');
out.push('close. Do not accept one because a machine ranked it first.');
out.push('');
out.push('## How to fill one in');
out.push('');
out.push('Add the phrase to `catalogue/reviewed/<lang>.jsonl`, one JSON object per line:');
out.push('');
out.push('```json');
out.push('{"lang":"hin","slot":"greeting.hello","text_native":"नमस्ते","text_romanized":"namaste",');
out.push(' "note":"said to anyone, any time of day"}');
out.push('```');
out.push('');
out.push('`text_romanized` is required for any language that does not write in Latin letters. If you');
out.push('are unsure how a local would greet a stranger rather than a friend, say so in `note` — an');
out.push('entry marked uncertain is fine, an entry marked confident and wrong is not.');
out.push('');
out.push('Then rebuild: `cd pipeline && node content/spine-build.mjs && node content/spine-build.mjs --tier 1`.');
out.push('');

let n = 0;
for (const [slotId, { meta, items }] of ranked) {
  n += 1;
  if (n > TOP) break;
  out.push(`## ${n}. \`${slotId}\` — ${meta.english}`);
  out.push('');
  out.push(`**Tier ${meta.tier} · missing from ${items.length} of ${items.length === 20 ? 20 : items.length} languages in this run**`);
  out.push('');
  if (meta.why) {
    out.push(`> ${meta.why}`);
    out.push('');
  }
  out.push('| Language | Unverified candidates — read, do not trust |');
  out.push('|---|---|');
  for (const it of items) {
    const cands = it.unverified_candidates.length
      ? it.unverified_candidates.map((c) => `${c.caption} → ${c.in_language}`).join('<br>')
      : '*nothing in the corpus*';
    out.push(`| ${it.lang_name} | ${cands} |`);
  }
  out.push('');
}

out.push('## The numbers');
out.push('');
for (const [tier, info] of Object.entries(perTier)) {
  out.push(`- **Tier ${tier}** — ${info.slots} slots, **${info.gaps} gaps**`);
}
out.push(`- **Total: ${rows.length} sentences**`);
out.push('');
out.push('Tier 0 first. It is the tier a traveller meets at an airport, and it is the tier whose');
out.push('slots are the same in every language — the reason the app exists.');
out.push('');

writeFileSync(OUT_MD, out.join('\n'));
writeFileSync(OUT_JSONL, rows.map((r) => JSON.stringify(r)).join('\n') + (rows.length ? '\n' : ''));

console.log(`  ${OUT_MD.replace(ROOT, '')}`);
console.log(`  ${OUT_JSONL.replace(ROOT, '')}`);
for (const [tier, info] of Object.entries(perTier)) {
  console.log(`  tier ${tier}: ${info.gaps} gaps across ${info.slots} slots`);
}
console.log(`  total: ${rows.length} sentences a person has to supply`);
console.log(`  ${ranked.length} distinct slots, ranked by how many languages each unlocks`);
