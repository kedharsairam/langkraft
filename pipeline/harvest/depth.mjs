#!/usr/bin/env node
/**
 * Derives each language's Tier 0 depth from measured evidence yield.
 *
 * WHY TIER 0 SIZE MUST NOT BE A CONSTANT
 *
 * Every spec so far declares a Tier 0 `size`, and those numbers were chosen before any
 * evidence existed. The harvest has since measured what each language can actually attest,
 * and the spread is roughly 8x on identical machinery:
 *
 *     German  340 selected, 63 of 63 concepts covered,  0 empty
 *     Italian 325          60 of 63,                     3 empty
 *     Hindi   167          41 of 63,                    22 empty
 *     Swahili 109          32 of 63,                    31 empty
 *     Tamil    44          16 of 63,                    47 empty
 *
 * A single Tier 0 size would be wrong in both directions. 50 for Tamil asks for more than
 * 44 candidates exist; 340 for German would mean writing 290 entries no evidence supports
 * and none of which a solo author can verify. The number has to follow the yield.
 *
 * WHY NOT JUST TAKE THE YIELD
 *
 * Candidate count is an upper bound on Tier 0, not a target. More than about 40-60 entries
 * in a courtesy tier stops being a floor and becomes a chapter -- at that point the learner
 * is reading a phrasebook rather than being helped out of a conversation. And a language with
 * thin attestation has a worse problem than a short tier: what it ships is less trustworthy,
 * so a smaller tier is the honest response, not a padded one.
 *
 * So depth is capped at COVEN_MAX regardless of yield. German gets the cap because it can
 * support it; Tamil gets what it has.
 *
 * WHAT THIS DOES NOT DO
 *
 * It writes spec files. It reports the number each language should declare, with its
 * reasoning, so the change is a deliberate edit to a spec rather than a silent rewrite. Spec
 * files carry `size.verified` claims, and those are claims a human makes.
 */

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = new URL('../..', import.meta.url).pathname;

/**
 * The most a Tier 0 courtesy floor should contain.
 *
 * English is the calibration language and ships 48. Beyond roughly this many, a tier stops
 * being the floor of a conversation and becomes a course, and Kedhar's stated goal is the
 * floor: enough to not be rude, to buy something, to ask where the toilet is.
 */
export const COVEN_MAX = 60;

/**
 * The least a Tier 0 should contain, however thin the evidence.
 *
 * Below this a language cannot carry a greeting, a thank-you, a price question and a toilet
 * question, which is the minimum for the app to be worth opening. A language that cannot
 * reach this is a different problem -- see NO_EVIDENCE below.
 */
export const COVEN_MIN = 24;

/**
 * Below this many attested candidates the language is not a Tier 0 language yet.
 *
 * Thai overran its declared 50 and was deliberately not trimmed, because Thai is isolating
 * and affixes cannot carry the grammar. So a small pool is not automatically wrong. But a
 * pool this thin cannot support a courtesy floor either, and the honest response is to say
 * so rather than to ship 20 entries and call it a floor.
 */
export const NO_EVIDENCE = 30;

/**
 * Depth for one language, from its yield record.
 *
 * Kept as a pure function so the arithmetic is testable without touching the filesystem,
 * which matters because these numbers decide how much content gets written.
 */
export function depthFor(yieldRecord) {
  const { selected, concepts_covered, concepts_missing } = yieldRecord;

  if (selected < NO_EVIDENCE) {
    return {
      depth: 0,
      verdict: 'not-yet',
      reason:
        `Only ${selected} selected candidates covering ${concepts_covered} of 63 concepts. ` +
        `Below ${NO_EVIDENCE}, this cannot support a courtesy floor. Ship it as ` +
        `role: calibration pending, or author deliberately and label every entry.`,
    };
  }

  // Thin coverage is the honest limiter. Even with plenty of candidates, a language missing
  // most concepts cannot offer a floor -- the learner would meet four greetings and no
  // toilet question. So coverage caps depth as well as volume does.
  const coverageCap = Math.round((concepts_covered / 63) * COVEN_MAX);

  const depth = Math.max(COVEN_MIN, Math.min(selected, COVEN_MAX, coverageCap));

  // Name the binding constraint.
  //
  // This took two attempts because the obvious formulation -- pick whichever limit is
  // smallest -- is wrong in the presence of a FLOOR. `floor: 24` is the smallest value in
  // the list for most languages, so sorting blindly reported "coverage caps depth at 57,
  // below the 24 minimum" for Spanish, which is nonsense: 57 is not below 24.
  //
  // The floor is not a constraint competing to be the binding one. It is the fallback when
  // every other limit would fall below it. So it is excluded from the comparison and
  // reported only when it actually overrides something.
  let reason;
  const limits = [
    { key: 'ceiling', value: COVEN_MAX },
    { key: 'coverage', value: coverageCap },
    { key: 'volume', value: selected },
  ].sort((a, b) => a.value - b.value);
  const binding = limits[0];
  const overridden = binding.value < COVEN_MIN;

  if (binding.key === 'ceiling') {
    reason =
      `${selected} selected candidates covering ${concepts_covered} of 63 concepts. ` +
      `Yield is not the constraint; the courtesy floor is.`;
  } else if (binding.key === 'coverage') {
    reason = overridden
      ? `Coverage caps depth at ${coverageCap}, below the ${COVEN_MIN} minimum, so the ` +
        `minimum applies. ${concepts_missing} concepts have no attestation in this language.`
      : `${selected} selected, ${concepts_covered} of 63 concepts covered, so coverage caps ` +
        `depth at ${coverageCap}.`;
  } else if (overridden) {
    reason =
      `Only ${selected} usable candidates, below the ${COVEN_MIN} minimum, so the minimum ` +
      `applies.`;
  } else {
    reason = `Yield is the constraint: only ${selected} usable candidates after selection.`;
  }

  return { depth, verdict: depth > 0 ? 'set' : 'not-yet', reason };
}

function main() {
  const yieldPath = join(ROOT, 'catalogue', 'yield.json');
  if (!existsSync(yieldPath)) {
    console.error('  catalogue/yield.json missing — run `node harvest/select.mjs` first.');
    process.exit(1);
  }
  const y = JSON.parse(readFileSync(yieldPath, 'utf8'));
  const catalogue = JSON.parse(readFileSync(join(ROOT, 'catalogue', 'languages.json'), 'utf8'));

  const measured = [];
  const pending = [];

  for (const lang of catalogue.languages) {
    if (lang.code === catalogue.calibration) continue;
    const rec = y[lang.code];
    if (!rec) { pending.push(lang); continue; }
    const { depth, verdict, reason } = depthFor(rec);
    measured.push({ code: lang.code, name: lang.name, ...rec, depth, verdict, reason });
  }

  console.log('\n  Tier 0 depth, derived from measured yield:\n');
  for (const m of measured.sort((a, b) => b.depth - a.depth)) {
    console.log(`  ${String(m.depth).padStart(3)}  ${m.code} ${m.name.padEnd(12)} ${m.reason}`);
  }

  if (pending.length) {
    console.log(`\n  ${pending.length} language(s) not yet harvested: ` +
      pending.map(p => p.code).join(', '));
  }

  const notYet = measured.filter(m => m.verdict === 'not-yet');
  if (notYet.length) {
    console.log(`\n  ${notYet.length} language(s) below the evidence floor: ` +
      notYet.map(m => m.code).join(', ') + '\n  These cannot ship a Tier 0 yet.');
  }

  writeFileSync(
    join(ROOT, 'catalogue', 'tier0-depth.json'),
    JSON.stringify({ generated_from: 'catalogue/yield.json', languages: measured,
      pending: pending.map(p => p.code) }, null, 2) + '\n',
  );
  console.log('\n  -> catalogue/tier0-depth.json\n');
}

if (import.meta.url === `file://${process.argv[1]}`) main();