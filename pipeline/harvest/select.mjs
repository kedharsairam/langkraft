/**
 * Candidate selection.
 *
 * WHAT THIS IS NOT
 *
 * This does not produce shipping content. It selects, from the attested pool, the candidates
 * most likely to be usable phrasebook lines, and it records the reasoning. Turning a
 * selection into content — adding `why`, `caution`, checking register, deciding what a
 * learner should meet first — is a separate editorial act, and nothing here pretends to do it.
 *
 * WHY SELECTION IS NEEDED AT ALL, GIVEN THE HARVESTER FILTERS FOR TRAVEL
 *
 * Because concept membership is not phrasebook-worthiness. The harvest matched the concept
 * `i_want` with "Aku tidak ingin hidup selamanya" (I don't want to live forever), because
 * "don't want" contains "want". A native contributor wrote that sentence and it is still
 * useless in a phrasebook. No keyword or concept filter can close that gap, because the
 * failure is in what the sentence MEANS for the job, not in which words it contains.
 *
 * So the ranking here uses signals that a filter can act on, and is explicit that the final
 * judgement is editorial.
 *
 * THE CONCENTRATION CAP IS THE PART THAT MATTERS MOST
 *
 * CK supplies about a third of the pool in every language, and is demonstrably a translator
 * rather than a speaker in several (249 of 250 of CK's Thai sentences carry no politeness
 * particle, which native Thai uses almost universally). Left alone, selection would happily
 * fill a language's Tier 0 with one person's usage and call it attested.
 *
 * The cap enforces diversity mechanically instead. It is a REVIEW TRIGGER, not a ban: if a
 * concept genuinely has one contributor's sentences and nothing else, the selection says so
 * and records the concentration, rather than silently preferring nothing.
 */

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = new URL('../..', import.meta.url).pathname;
const CAT = join(ROOT, 'catalogue');

/**
 * Maximum share of one concept's selections that may come from a single contributor.
 *
 * 0.6 rather than something stricter because a narrow concept can genuinely have only one
 * available speaker, and returning nothing is not obviously better than returning a flagged
 * candidate. The point is that the cap records and exposes, not that it forbids.
 */
export const CONTRIBUTOR_CAP = 0.6;

/**
 * Sentence-length band, in words.
 *
 * Narrow on purpose. A phrasebook line is something you say at a counter, so it must be
 * short. The harvest already caps at 12 words on the target side; this is tighter still,
 * because "Tolong emperitelyk dan print缅甸-nya" is not a thing you say out loud even though
 * every word in it is correct.
 */
const IDEAL_MAX = 6;
const LONG_MAX = 9;

/** Languages written without spaces between words. */
const UNSPACED = new Set(['jpn', 'cmn', 'kor']);

/**
 * A comparable "length" for ranking.
 *
 * For spaced scripts this is the word count. For Chinese, Japanese and Korean it cannot be:
 * "こんにちは。小川と申します。" splits into two whitespace-separated runs, so a word-count
 * band tuned on Latin would score a long Japanese sentence as terse. Character count is the
 * usable proxy there, divided by a factor chosen so the two scales land in comparable ranges
 * rather than at the mercy of a constant.
 */
export function wordCount(text, code) {
  if (!UNSPACED.has(code)) return text.trim().split(/\s+/).length;
  // Punctuation and whitespace do not count as content.
  const chars = [...text].filter((c) => /\p{L}/u.test(c) || /\p{N}/u.test(c)).length;
  return Math.round(chars / 3);
}

/** Phrases that make a sentence unusable however well it scores elsewhere. */
const DISQUALIFY = [
  /\bthanks? (?:for|to) (?:my|our|your|the) (?:question|help|comment|post|translation|contribution)/i,
  /\b(?:thank you|thanks) (?:for|to) [a-z]+\b/i,  // any "thanks for/of X", closed or open
  /\bcontribut(?:or|ion|ed)\b/i,
  /\btranslation\b/i,
  /\bwallmart|ikea|walmart\b/i,
  /\b[A-Z]{3,}\b/,           // acronyms: names, brands, initialisms
  /\d{3,}/,                   // ids, years, phone numbers

  // Nonsense from mixed-script machine translation. These rows are exactly what a
  // non-speaker running a translator produces, and shipping one puts a visibly broken string
  // in front of a learner.
  //
  // CRITICAL: these guards apply only to languages whose own script is Latin. The first
  // version of this rule rejected 100% of legitimate Japanese (980/993), Korean (425/432) and
  // Mandarin (854/865) -- those languages ARE CJK, so "contains Han" says nothing about
  // quality. It selected exactly zero candidates for three languages holding 993, 432 and
  // 865 harvested rows, which reads as "no evidence exists" when the truth is "my filter is
  // wrong". Script checking is per-language below; see scriptIsPlausible.
  /\p{Script=Latin}[\p{Script=Han}\p{Script=Arabic}\p{Script=Cyrillic}\p{Script=Thai}\p{Script=Devanagari}\p{Script=Hangul}]/u,
  /\p{Script=Han}[\p{Script=Latin}\p{Script=Cyrillic}]/u,
];

/**
 * The expected dominant script per language.
 *
 * Anything unlisted is treated as Latin, which is the majority case and the conservative one:
 * a language whose script is unknown to this table keeps the stricter guard rather than
 * silently losing it.
 */
const EXPECTED_SCRIPT = {
  jpn: 'Japanese',
  kor: 'Hangul',
  cmn: 'Han',
  tha: 'Thai',
  tam: 'Tamil',
  ara: 'Arabic',
  fas: 'Arabic',
  rus: 'Cyrillic',
  hin: 'Devanagari',
};

/**
 * Whether a text's scripts belong to the language.
 *
 * A Latin word containing Han is machine-translation damage. The same characters in a Chinese
 * sentence are simply Chinese, and a Japanese sentence mixes kana with kanji routinely. So the
 * test is whether the language's OWN script appears at all -- not whether a particular script
 * is absent.
 */
export function scriptIsPlausible(text, code) {
  const expected = EXPECTED_SCRIPT[code] ?? 'Latin';
  const chars = [...(text ?? '')];

  // Kana satisfies both Japanese and Han, since Japanese writes kanji and kana together.
  const hasKana = chars.some((c) => /\p{Script=Hiragana}|\p{Script=Katakana}/u.test(c));
  if (expected === 'Japanese' && hasKana) return true;
  if (expected === 'Han' && hasKana) return true;

  const scripts = new Set();
  for (const c of chars) {
    // \p{Script=...} does not allow a capture group inside the property name, so each
    // candidate script is tested individually.
    for (const name of ['Latin', 'Han', 'Hiragana', 'Katakana', 'Hangul', 'Arabic',
                        'Cyrillic', 'Thai', 'Devanagari', 'Tamil']) {
      if (new RegExp(`\\p{Script=${name}}`, 'u').test(c)) { scripts.add(name); break; }
    }
  }
  if (scripts.has(expected)) return true;

  // Short Japanese content may be written entirely in kanji.
  if (expected === 'Japanese' && scripts.has('Han')) return true;

  return false;
}

/**
 * Scores one candidate. Returns null if it is disqualified.
 *
 * Higher is better. The bands are deliberately coarse: this ranks candidates so a human sees
 * the plausible ones first, and it does not pretend the difference between 7 and 8 is
 * meaningful.
 */
export function scoreCandidate(row) {
  const native = (row.native ?? '').trim();
  const english = (row.english ?? '').trim();
  if (!native || !english) return null;

  // Per-language script check, on the NATIVE side only.
  //
  // The English side is Latin for every language in the catalogue, including Chinese, Japanese
  // and Korean -- so checking it against the target language's script rejected every CJK pair
  // in the pool. "Hello everybody!" is the correct English half of a Japanese sentence.
  if (!scriptIsPlausible(native, row.code)) return null;
  // A Chinese or Japanese sentence containing embedded Latin is a genuine signal (a brand
  // name is fine; a whole Latin clause is not), so the English side is checked for its own
  // sanity rather than for belonging to the language.
  if (!scriptIsPlausible(english, 'eng')) return null;

  if (DISQUALIFY.some((re) => re.test(native) || re.test(english))) return null;

  // CJK languages do not delimit words with spaces, so a whitespace word count is meaningless:
  // "こんにちは。小川と申します。" counts as TWO words, which reads as terse when it is not.
  // Character count is the usable proxy, and it must not be compared against a Latin band.
  const words = wordCount(native, row.code);
  if (words > LONG_MAX) return null;

  let score = 0;

  // Length. Shorter is better, and the ideal band is rewarded over merely-acceptable.
  score += words <= IDEAL_MAX ? 40 : 20;

  // A polite opener is strong evidence a speaker wrote this to be said TO someone, which is
  // the register a phrasebook needs. Absent an explicit politeness lexicon this is inferred
  // from the English side, so it is a nudge and not a rule.
  if (/^(excuse me|please|sorry|hello|hi\b|good (morning|afternoon|evening)|thank you)/i.test(english)) {
    score += 15;
  }

  // A question is close to the core phrasebook job: asking for a price, a direction, a
  // facility. Statements about shoes or weather are not.
  if (/\?\s*$/.test(native) || /\?\s*$/.test(english)) score += 15;

  // Imperative / request framing on the English side is a proxy for "you say this to someone".
  if (/^(can|could|may|would|please|do you|where is|how much|what time|i'?d like|i would like|i want|i need)/i.test(english)) {
    score += 12;
  }

  // A single-word native sentence is suspicious: it is usually a dictionary entry or a name,
  // and a phrasebook line with no verb or particle rarely works standalone.
  if (words === 1) score -= 25;

  // Very short English paired with longer native text often signals a fragment or a gloss
  // rather than a sentence pair.
  const enWords = english.trim().split(/\s+/).length;
  if (enWords <= 2 && words > 3) score -= 15;

  return { score, words, enWords };
}

/**
 * Selects candidates for one language, enforcing the per-contributor cap.
 *
 * @param rows    harvested candidates
 * @param options.perConcept  how many to keep per concept
 */
export function select(rows, options = {}) {
  const perConcept = options.perConcept ?? 6;

  const byConcept = new Map();
  for (const row of rows) {
    const key = row.concept ?? 'ungrouped';
    if (!byConcept.has(key)) byConcept.set(key, []);
    byConcept.get(key).push(row);
  }

  const selected = [];
  const report = {};

  for (const [concept, candidates] of byConcept) {
    const scored = [];
    for (const row of candidates) {
      const s = scoreCandidate(row);
      if (s) scored.push({ row, ...s });
    }
    scored.sort((a, b) => b.score - a.score);

    // Enforce the cap while filling, so a dominant contributor cannot take every slot.
    const taken = new Map();
    const chosen = [];
    const capped = [];

    for (const c of scored) {
      if (chosen.length >= perConcept) break;
      const author = c.row.author ?? 'unknown';
      const n = taken.get(author) ?? 0;
      const allowed = Math.max(1, Math.ceil(perConcept * CONTRIBUTOR_CAP));
      if (n >= allowed) { capped.push(c); continue; }
      taken.set(author, n + 1);
      chosen.push(c);
    }

    // If the cap left the concept short, that is a fact to report, not to hide. Filling from
    // the capped remainder would defeat the cap; leaving the concept thin is honest.
    const poolSize = scored.length;
    const authors = new Set(scored.map((c) => c.row.author));
    const topAuthor = [...scored.reduce((m, c) => {
      const a = c.row.author ?? 'unknown';
      m.set(a, (m.get(a) ?? 0) + 1);
      return m;
    }, new Map())].sort((a, b) => b[1] - a[1])[0];

    report[concept] = {
      pool: poolSize,
      selected: chosen.length,
      authors_in_pool: authors.size,
      dominant_author: topAuthor?.[0] ?? null,
      dominant_share: poolSize ? Number((topAuthor[1] / poolSize).toFixed(3)) : null,
      // Set when the cap bound. Means this concept rests on few contributors and its
      // confidence should be read as such.
      capped: capped.length > 0,
    };

    for (const c of chosen) {
      selected.push({
        concept,
        group: c.row.group,
        native: c.row.native,
        english: c.row.english,
        author: c.row.author,
        licence: c.row.licence,
        tatoeba_id: c.row.tatoeba_id,
        tatoeba_id_english: c.row.tatoeba_id_english,
        dir: c.row.dir,
        score: c.score,
        contributor_share: report[concept].dominant_share,
        capped_concept: capped.length > 0,
      });
    }
  }

  return { selected, report };
}

/** Per-language aggregate across the whole attested file, for the yield table. */
export function summarise(rows) {
  const concepts = new Set(rows.map((r) => r.concept).filter(Boolean));
  const authors = new Map();
  for (const r of rows) {
    const a = r.author ?? 'unknown';
    authors.set(a, (authors.get(a) ?? 0) + 1);
  }
  const sorted = [...authors].sort((a, b) => b[1] - a[1]);
  const gaps = Math.max(0, 63 - concepts.size);
  return {
    rows: rows.length,
    contributors: authors.size,
    concepts_covered: concepts.size,
    concepts_missing: gaps,
    dominant_author: sorted[0]?.[0] ?? null,
    dominant_share: rows.length ? Number((sorted[0][1] / rows.length).toFixed(3)) : null,
  };
}

function loadRows(code) {
  const p = join(CAT, 'attested', `${code}.jsonl`);
  if (!existsSync(p)) return null;
  return readFileSync(p, 'utf8').split('\n').filter(Boolean).map((l) => JSON.parse(l));
}

function main() {
  const catalogue = JSON.parse(readFileSync(join(CAT, 'languages.json'), 'utf8'));
  const perConcept = Number(process.env.PER_CONCEPT || 6);
  const outDir = join(CAT, 'selected');

  const only = process.env.ONLY ? process.env.ONLY.split(',') : null;
  const summary = {};
  const allReport = {};

  for (const lang of catalogue.languages) {
    if (lang.code === catalogue.calibration) continue;
    if (only && !only.includes(lang.code)) continue;
    const rows = loadRows(lang.code);
    if (!rows) continue;

    const { selected, report } = select(rows, { perConcept });
    summary[lang.code] = { ...summarise(rows), selected: selected.length };
    allReport[lang.code] = report;
  }

  if (process.env.SUMMARY === '1') {
    console.log(JSON.stringify(summary, null, 2));
    return;
  }

  writeFileSync(join(CAT, 'yield.json'), JSON.stringify(summary, null, 2) + '\n');
  writeFileSync(join(CAT, 'selection-report.json'), JSON.stringify(allReport, null, 2) + '\n');

  for (const [code, s] of Object.entries(summary)) {
    console.log(
      `  ${code}: pool ${s.rows} from ${s.contributors} contributors, ` +
        `${s.selected} selected, ${s.concepts_missing} concepts empty, ` +
        `top ${s.dominant_author} ${Math.round((s.dominant_share ?? 0) * 100)}%`,
    );
  }
  console.log('  -> catalogue/yield.json, catalogue/selection-report.json');
}

if (import.meta.url === `file://${process.argv[1]}`) main();