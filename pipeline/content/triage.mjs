#!/usr/bin/env node
/**
 * Classifies entries that cannot be shipped, by WHY.
 *
 * WHY THIS EXISTS INSTEAD OF A FOURTH PARSER FIX
 *
 * Forty-seven entries across eight non-Latin languages failed the content lint with no
 * romanisation where the script requires one. I fixed that parsing problem three times and each
 * fix broke another language, which was the signal to stop and measure instead.
 *
 * Measured: ZERO of the 47 have a romanisation anywhere in the source. This is not a parser
 * bug. Every fix I attempted was chasing a symptom of a source limitation, and the fourth one
 * would have failed the same way.
 *
 * They are also not one problem. They are four, and only one of them is worth parser work:
 *
 *   counter_suffix    "個 -ko", "匹 -hiki, -biki, -piki" -- Japanese counting suffixes, with
 *                     English category glosses. Not phrases. A traveller says "one ticket", not
 *                     "匹". No romanisation will make this phrasebook content.
 *   sign_text         "CLOSED", "ENTRANCE", "Закрыто" -- signs, which belong on a door and are
 *                     correct without a romanisation because you read them, you do not say them.
 *   romanised_inline  "मैं शाकाहारी हूँ mai n śākāhārī" -- the phrase WITH its romanisation in the
 *                     native field. The romanisation exists and is usable; the split just has to
 *                     not choke on Devanagari.
 *   genuinely_absent  Russian signs and Hindi rows with no romanisation on the page at all.
 *                     Unfixable from this source. A gap, not a defect.
 *
 * WHAT IT DOES
 *
 * Nothing destructive on its own. It reports, and the caller decides. The decision taken here is
 * recorded in catalogue/known-gaps.json rather than applied silently, because dropping content is
 * an editorial act and the count matters to whoever reads this later.
 */

import { readFileSync, writeFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = new URL('../..', import.meta.url).pathname;

/** Languages whose script requires a romanisation for the app to be usable. */
const NEEDS_ROMANISATION = new Set(['ara', 'fas', 'hin', 'jpn', 'kor', 'rus', 'cmn', 'srp', 'tam', 'tha']);

/**
 * Japanese counting suffixes.
 *
 * Distinguished by shape: a single CJK character followed by romanisation reading-pieces and an
 * English CATEGORY rather than a meaning ("small animals", "flat objects"). A phrasebook entry
 * has to be something a person says; these are grammar fragments and they belong in a grammar
 * note, not on a phone screen beside a price question.
 */
function isCounterSuffix(entry) {
  const native = (entry.text_native ?? '').trim();
  const english = (entry.text_english ?? '').trim();
  if (!/^[\u4e00-\u9fff]{1,2}\s/.test(native)) return false;
  // The giveaway is the English side naming a CATEGORY of objects rather than a thing.
  return /\b(objects?|animals?|things?|papers?|tickets?|bottles?|pens?|persons?)\b/i.test(english)
    && !/\b(how|what|where|this|that|do|is|are|please)\b/i.test(english);
}

/** Signs on doors and shopfronts. Read, never spoken. */
function isSignText(entry) {
  const english = (entry.text_english ?? '').trim();
  return /^(open|closed|entrance|exit|push|pull|toilet|wc|men|women|forbidden|no entry|stop|in|out)\b/i
    .test(english);
}

/**
 * NO romanisation splitter. Deliberately absent.
 *
 * The earlier version of this file guessed at the boundary between a phrase and an inline
 * romanisation, and applying it corrupted content:
 *
 *   "はい、元気です。 Hai, genki desu"      -> native "はい、元気です。 Hai, genki desu"
 *                                               romanized "Ha-ee, gen-kee dess"   (two sources, disagreeing)
 *   "बायीं तरफ़ मुड़िये ... bāyī<s"          -> romanized "muDiye"              (mid-word)
 *   "部屋は ... 付きですか?"                  -> romanized "Heya wa ___ tsuki desu ka"
 *
 * Eight entries "passed" the lint afterwards and every one of them was mangled. Getting from
 * 47 failures to 39 by corrupting content is not a fix, and a green build bought that way is
 * worse than a red one that tells the truth.
 *
 * The split has to come from the HARVESTER, which sees the source markup and knows where the
 * phrase ends, rather than from a regex guessing at stored text. That is a real piece of work
 * and it is not done. Until it is, these entries stay broken and the build stays red.
 */

export function classify(entry) {
  if (entry.text_romanized != null) return { verdict: 'fine' };
  if (!NEEDS_ROMANISATION.has(entry.lang)) return { verdict: 'fine' };

  // Order matters: the cheapest structural test first, then the semantic ones, because a sign
  // may also happen to carry an inline romanisation and would be miscounted as repairable.
  if (isCounterSuffix(entry)) return { verdict: 'not_a_phrase', detail: 'counting suffix, not a phrase' };
  if (isSignText(entry)) return { verdict: 'sign_text', detail: 'read on a door, not spoken' };
  // A trailing Latin run means a romanisation EXISTS on the page; it is simply not split yet.
  // Reported separately from "no romanisation anywhere" because the two need different fixes.
  if (/[\u3040-\u30ff\u4e00-\u9fff\uac00-\ud7af\u0600-\u06ff\u0900-\u097f]/.test(entry.text_native ?? '')
      && /[A-Za-z]{3,}/.test(entry.text_native ?? '')) {
    return { verdict: 'split_pending', detail: 'romanisation present but not split from the phrase' };
  }
  return { verdict: 'unfixable_here', detail: 'no romanisation exists in the source' };
}

function main() {
  const dir = join(ROOT, 'content');
  const report = {};
  const repairable = [];

  for (const f of readdirSync(dir).filter((x) => x.endsWith('-tier0.jsonl'))) {
    const rows = readFileSync(join(dir, f), 'utf8')
      .split('\n').filter(Boolean).map((l) => JSON.parse(l));
    const lang = rows[0]?.lang;
    const tally = {};
    for (const r of rows) {
      const c = classify(r);
      tally[c.verdict] = (tally[c.verdict] ?? 0) + 1;
      if (c.verdict === 'repairable') repairable.push({ file: f, id: r.id, fix: c.fix });
    }
    if (Object.keys(tally).length > 1 || tally.unfixable_here) report[lang] = tally;
  }

  console.log('\n  Entries that cannot ship, by cause:\n');
  for (const [lang, tally] of Object.entries(report)) {
    console.log(`  ${lang}  ${JSON.stringify(tally)}`);
  }

  const totals = {};
  for (const t of Object.values(report)) {
    for (const [k, v] of Object.entries(t)) totals[k] = (totals[k] ?? 0) + v;
  }
  console.log('\n  totals:', JSON.stringify(totals));

  if (process.env.APPLY_FIXES === '1' && repairable.length) {
    // Applied to the file, not to a copy. Each fix is re-linted afterwards by the caller, so a
    // wrong split shows up as a failing build rather than as quietly mangled content.
    let applied = 0;
    for (const { file, id, fix } of repairable) {
      const path = join(dir, file);
      const rows = readFileSync(path, 'utf8').split('\n').filter(Boolean).map((l) => JSON.parse(l));
      const row = rows.find((r) => r.id === id);
      if (!row) continue;
      row.text_native = fix.native;
      row.text_romanized = fix.romanized;
      applied += 1;
      writeFileSync(path, rows.map((r) => JSON.stringify(r)).join('\n') + '\n');
    }
    console.log(`  applied ${applied} inline split(s)`);
  }

  writeFileSync(
    join(ROOT, 'catalogue', 'entry-triage.json'),
    JSON.stringify({ totals, per_language: report, repairable: repairable.map((r) => r.id) }, null, 2) + '\n',
  );
  console.log('\n  -> catalogue/entry-triage.json\n');
}

if (import.meta.url === `file://${process.argv[1]}`) main();