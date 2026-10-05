#!/usr/bin/env node
/**
 * Correlates the two sources per language.
 *
 * WHAT THIS IS FOR
 *
 * Two corpora, neither sufficient alone:
 *
 *   curated   Wikivoyage. Human-written, peer-reviewed, organised by traveller scenario. Strong
 *             evidence and unevidently bounded -- Portuguese 296 phrases, Dari 55.
 *   attested  Tatoeba. Named contributors, per-sentence attribution, much larger pool, but flat
 *             and unordered, and dominated by a handful of prolific translators.
 *
 * The interesting output is not "more rows". It is what happens when an INDEPENDENT source says
 * something about the same phrase. Two people who do not know each other arriving at the same
 * native wording is real corroboration, and it is the only route to the top confidence level --
 * nothing in the catalogue reached it before.
 *
 * WHAT IT DOES NOT CLAIM
 *
 * Agreement on the ENGLISH GLOSS is weak evidence and is reported separately from agreement on
 * the native wording. Two sources describing "where is the toilet" proves both thought of the
 * toilet; it does not prove they would phrase it identically, and in practice they often do not,
 * because register and region differ. Conflating the two would let a large number of gloss-level
 * matches masquerade as native-level corroboration.
 *
 * The hard case is register, and it is where a naive matcher is most wrong. Arabic has a formal
 * and an informal form of "thank you" that are both correct and not interchangeable; a matcher
 * that treats them as one match will report agreement where there is a real choice. So
 * disagreement is surfaced rather than resolved.
 */

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = new URL('../..', import.meta.url).pathname;
const CAT = join(ROOT, 'catalogue');

/**
 * Normalisation for comparison only. Never used for display.
 *
 * Lowercase, strip punctuation and quote marks, collapse whitespace. Deliberately conservative:
 * it does not strip diacritics, because in most of these languages diacritics carry meaning and
 * "ca" versus "cá" is the difference between two words.
 */
/**
 * Arabic diacritics and tatweel, for COMPARISON ONLY.
 *
 * Arabic short vowels are written inconsistently: the same word appears as عفوًا (with
 * fathatan) and عفواً (with tanwin), and sometimes with no mark at all. Those are the same
 * word. Comparing them as bytes reported ZERO corroboration for Arabic, Hindi, Portuguese,
 * Serbian and Dari -- five languages, none of which is actually empty. That is the worst kind
 * of wrong answer: a number that looks like a finding about the corpus and is actually a
 * limitation of the comparator.
 *
 * Arabic diacritics are stripped for matching only. Never for display, and never for
 * Vietnamese or Thai, where the marks carry tone and are the whole point.
 */
const ARABIC_MARKS = /[ً-ْٰـ]/g;

/** Scripts where diacritics must be preserved when comparing. */
const MARK_SENSITIVE = new Set(['vie', 'tha', 'hin']);

function normalise(s, code = null) {
  let out = (s ?? '')
    .toLowerCase()
    .replace(/[.!?,;:'"()‘’“”\[\]]/g, '')
    .replace(/\s+/g, ' ')
    .trim();

  // Arabic and Persian only: strip short-vowel marks, which are optional in writing.
  if (code === 'ara' || code === 'fas') out = out.replace(ARABIC_MARKS, '');

  return out;
}

/** Content words, for a looser comparison that ignores framing like "please" or "could you". */
function contentWords(s) {
  const STOP = new Set([
    'the', 'a', 'an', 'is', 'are', 'am', 'please', 'could', 'would', 'you', 'i', 'to',
    'do', 'does', 'can', 'me', 'my', 'it', 'that', 'this', 'of', 'in', 'on', 'at', 'for',
  ]);
  return new Set(
    normalise(s).split(' ').filter((w) => w.length > 1 && !STOP.has(w)),
  );
}

/** Jaccard overlap of content words. Cheap, and enough to rank candidates for review. */
function overlap(a, b) {
  const A = contentWords(a);
  const B = contentWords(b);
  if (A.size === 0 || B.size === 0) return 0;
  let shared = 0;
  for (const w of A) if (B.has(w)) shared += 1;
  return shared / (A.size + B.size - shared);
}

/**
 * Register markers that make two forms genuinely different rather than merely different.
 *
 * These are the pairs where a native speaker would notice, and where reporting "agreement"
 * would be actively misleading.
 */
const REGISTER_MARKERS = [
  /\b(informal|informally|casual|slang)\b/i,
  /\b(formal|polite|respectfully|humbly)\b/i,
  /\b(male|female|masculine|feminine)\b/i,
];

/** Whether a pair of rows differ in a way a native speaker would hear. */
function registerDiffers(a, b) {
  for (const re of REGISTER_MARKERS) {
    if (re.test(a) !== re.test(b)) return true;
  }
  return false;
}

/** Loads one source for a language. */
function load(dir, code) {
  const p = join(CAT, dir, `${code}.jsonl`);
  if (!existsSync(p)) return [];
  return readFileSync(p, 'utf8').split('\n').filter(Boolean).map((l) => JSON.parse(l));
}

/**
 * Finds attested rows corresponding to one curated row.
 *
 * Three tiers of agreement, kept separate because they are worth very different amounts:
 *
 *   native   the target-language text is IDENTICAL. Two sources produced the same string.
 *   gloss    only the English meaning lines up. Weak, and reported as such.
 *   none     nothing lines up.
 */
export function correlate(curatedRow, attestedRows, code = null) {
  const cNative = normalise(curatedRow.native, code);
  const cPron = normalise(curatedRow.pronunciation, code);
  let native = null;
  let gloss = null;
  let registerConflict = false;

  for (const row of attestedRows) {
    const aNative = normalise(row.native, code);

    if (aNative && aNative === cNative) {
      // Identical native text. Strongest thing available here -- but still check register, since
      // two sources can produce the same string with different intended formality.
      if (registerDiffers(curatedRow.english, row.english)) registerConflict = true;
      native = { ...row, agreement: 'native', register_conflict: registerConflict };
      break;
    }

    // Pronunciation match: for a learner who cannot read the script, the romanisation is the
    // usable form, so agreement there is not worthless. Weaker than native text, so it does not
    // claim the native tier on its own.
    if (cPron && normalise(row.native, code) === cPron) {
      native = { ...row, agreement: 'pronunciation', register_conflict: false };
      continue;
    }

    if (gloss === null && overlap(curatedRow.english, row.english) >= 0.5) {
      gloss = { ...row, agreement: 'gloss', register_conflict: registerDiffers(curatedRow.english, row.english) };
    }
  }

  return { native, gloss };
}

function main() {
  const catalogue = JSON.parse(readFileSync(join(CAT, 'languages.json'), 'utf8'));
  const only = process.env.ONLY ? process.env.ONLY.split(',') : null;

  const out = {};
  console.log('\n  Native-level corroboration (identical target-language text in both sources):\n');

  for (const lang of catalogue.languages) {
    if (lang.code === catalogue.calibration) continue;
    if (only && !only.includes(lang.code)) continue;

    const curated = load('curated', lang.code);
    const attested = load('attested', lang.code);
    if (!curated.length && !attested.length) continue;

    // Index attested by native text so exact matches are a lookup, not a scan. This matters:
    // some pools are over a thousand rows and a linear scan per curated row is quadratic.
    const byNative = new Map();
    for (const row of attested) {
      const k = normalise(row.native, lang.code);
      if (!byNative.has(k)) byNative.set(k, []);
      byNative.get(k).push(row);
    }

    const matched = [];
    let glossOnly = 0;
    let registerConflicts = 0;

    for (const c of curated) {
      const bucket = byNative.get(normalise(c.native, lang.code)) ?? [];
      const res = correlate(c, bucket, lang.code);
      if (res.native) {
        if (res.native.agreement === 'native') {
          if (res.native.register_conflict) registerConflicts += 1;
          matched.push({
            concept: c.concept,
            native: c.native,
            pronunciation: c.pronunciation ?? null,
            english_curated: c.english,
            english_attested: res.native.english,
            // Both authors are recorded. CC BY requires the Tatoeba author by name; the
            // Wikivoyage page is the curated source and is named on the credits screen.
            attested_author: res.native.author,
            attested_id: res.native.tatoeba_id,
            tatoeba_author_share: res.native.contributor_share ?? null,
            register_conflict: res.native.register_conflict === true,
          });
        }
      } else if (res.gloss) {
        glossOnly += 1;
      }
    }

    // De-duplicate: one curated row can match several attested rows for the same native text.
    const seen = new Set();
    const unique = matched.filter((m) => {
      const k = `${m.native}|${m.attested_id}`;
      if (seen.has(k)) return false;
      seen.add(k);
      return true;
    });

    // Count DISTINCT contributors behind the corroborations, because the CK finding means a
    // corroboration from one prolific translator is worth much less than three independent ones.
    const authors = new Set(unique.map((m) => m.attested_author).filter(Boolean));
    const distinctNative = new Set(unique.map((m) => m.native));

    out[lang.code] = {
      curated: curated.length,
      attested: attested.length,
      native_corroborations: distinctNative.size,
      corroborating_authors: authors.size,
      gloss_only: glossOnly,
      register_conflicts: registerConflicts,
      pairs: unique,
    };

    console.log(
      `  ${lang.code} ${lang.name.padEnd(12)} ` +
        `curated ${String(curated.length).padStart(4)}  attested ${String(attested.length).padStart(4)}  ` +
        `-> ${String(distinctNative.size).padStart(3)} corroborated by ` +
        `${String(authors.size).padStart(3)} distinct contributors` +
        (registerConflicts ? `  [${registerConflicts} register conflicts]` : ''),
    );
  }

  writeFileSync(join(CAT, 'corroboration.json'), JSON.stringify(out, null, 2) + '\n');

  const totalCurated = Object.values(out).reduce((s, v) => s + v.curated, 0);
  const totalCorr = Object.values(out).reduce((s, v) => s + v.native_corroborations, 0);
  console.log(
    `\n  ${totalCorr} of ${totalCurated} curated phrases corroborated natively ` +
      `(${(100 * totalCorr / Math.max(1, totalCurated)).toFixed(1)}%)\n`,
  );
}

if (import.meta.url === `file://${process.argv[1]}`) main();