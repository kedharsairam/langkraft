#!/usr/bin/env node
/**
 * Concept-driven attestation harvest.
 *
 * WHY THIS REPLACED THE BULK SCAN
 *
 * The first version downloaded every en-XX pair and filtered with travel keywords. It found
 * declarative chat -- "Dia lapar" (He is hungry), "Air mengalir" (Water flows), "The folder
 * caught fire" -- because a keyword filter cannot distinguish a sentence a traveller UTTERS
 * from a sentence that merely mentions a travel object. Tatoeba's en-pivot bitext is mostly
 * conversational, and travel vocabulary is common inside it without any travel intent.
 *
 * Searching the other way round fixes it. Each Tier 0 concept is queried directly against
 * the English side of the bitext, so what returns are sentences that genuinely express that
 * concept. Volume collapses -- from thousands of loosely related pairs to a few dozen exactly
 * relevant ones -- and precision becomes total.
 *
 * The API shape matters and was found by measurement, not documentation:
 *
 *   query=X&from=XX&to=eng   -> 0 results. `from` and `query` appear to AND over the same
 *                              side, so requiring the target language to also match the
 *                              English words can never succeed.
 *   query=X&to=eng           -> correct. `query` matches the English pivot, `to=lang`
 *                              requires a translation into that language.
 *
 * This is a REAL limit, not a filter, and it should not be dressed up as one: the yield is
 * genuinely low for several languages, because en-XX bitext is dominated by a few very
 * prolific contributors working through simple declarative sentences. A language with 1000
 * pairs will yield far fewer than 1000 usable travel phrases. Where a concept returns
 * nothing, that is recorded as a gap rather than papered over with an authored guess.
 *
 * PROVENANCE IS PER SENTENCE
 *
 * CC BY 2.0 FR asks for the author, so `author` is recorded on every row. The English pivot
 * sentence carries `user` on the primary record, which is what we need since the query side
 * is English.
 */

import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = new URL('../..', import.meta.url).pathname;
const OUT = join(ROOT, 'catalogue', 'attested');

const DELAY_MS = Number(process.env.DELAY_MS || 200);

function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

/**
 * Queries Tatoeba for one English phrase, restricted to pairs touching `lang`.
 *
 * Returns the rows that survived the phrasebook-shape filter. An empty array is a real
 * answer meaning "this concept has no attestation in this language" -- callers record it as
 * a gap instead of inventing something.
 */
async function queryConcept(lang, form) {
  const url =
    `https://tatoeba.org/en/api_v0/search?query=${encodeURIComponent(form)}` +
    `&to=${lang}&orphans=no&unapproved=no&sort=random`;

  let data;
  try {
    const res = await fetch(url);
    if (!res.ok) return { rows: [], error: `HTTP ${res.status}` };
    data = await res.json();
  } catch (err) {
    return { rows: [], error: err.message };
  }

  const rows = [];
  for (const en of data.results ?? []) {
    if (en.lang !== 'eng') continue;

    // `translations` is [direct, indirect]; the target language may sit in either.
    const target = (en.translations ?? [])
      .flat()
      .find((t) => t.lang === lang);
    if (!target) continue;

    const nativeWords = target.text.trim().split(/\s+/).length;
    // A phrasebook line is terse. Very long sentences are conversational narrative even when
    // they happen to contain a useful concept, and they do not survive being read at a
    // market stall.
    if (nativeWords > 12) continue;
    if (/https?:\/\//.test(target.text) || /https?:\/\//.test(en.text)) continue;

    rows.push({
      code: lang,
      native: target.text,
      english: en.text,
      author: en.user?.username ?? null,
      license: en.license ?? 'CC BY 2.0 FR',
      tatoeba_id: target.id,
      tatoeba_id_english: en.id,
      dir: target.dir ?? en.dir,
      matched_form: form,
    });
  }
  return { rows, error: null };
}

async function harvestLanguage(lang, concepts) {
  const path = join(OUT, `${lang}.jsonl`);
  const out = [];
  const seen = new Set();
  const gaps = [];

  if (existsSync(path)) {
    for (const line of readFileSync(path, 'utf8').split('\n')) {
      if (!line.trim()) continue;
      try {
        const r = JSON.parse(line);
        out.push(r);
        seen.add(`${r.tatoeba_id_english}:${r.matched_form}`);
      } catch {
        /* truncated line from an interrupted run */
      }
    }
  }

  const flush = () =>
    writeFileSync(path, out.map((r) => JSON.stringify(r)).join('\n') + (out.length ? '\n' : ''));

  for (const concept of concepts) {
    let found = 0;
    for (const form of concept.en_forms) {
      const { rows, error } = await queryConcept(lang, form);
      if (error) {
        console.error(`    ${lang}/${concept.id}: ${error}`);
        continue;
      }
      for (const r of rows) {
        const key = `${r.tatoeba_id_english}:${form}`;
        if (seen.has(key)) continue;
        seen.add(key);
        out.push({ ...r, concept: concept.id, group: concept.group });
        found += 1;
      }
      await sleep(DELAY_MS);
    }
    flush();
    if (found === 0) gaps.push(concept.id);
    process.stdout.write(
      `    ${lang}/${concept.id}${found ? ` +${found}` : ' GAP'}\n`,
    );
  }

  return { total: out.length, gaps };
}

async function main() {
  const catalogue = JSON.parse(readFileSync(join(ROOT, 'catalogue', 'languages.json'), 'utf8'));
  const { concepts } = JSON.parse(readFileSync(join(ROOT, 'pipeline', 'harvest', 'concepts.json'), 'utf8'));

  if (!existsSync(OUT)) mkdirSync(OUT, { recursive: true });

  // English is the pivot, so it has no bitext to harvest FROM.
  const targets = catalogue.languages.filter((l) => l.code !== catalogue.calibration);
  const only = process.env.ONLY ? process.env.ONLY.split(',') : null;

  const report = {};

  for (const lang of targets) {
    if (only && !only.includes(lang.code)) continue;
    process.stdout.write(`  ${lang.code} (${lang.name})\n`);
    const { total, gaps } = await harvestLanguage(lang.code, concepts);
    report[lang.code] = { total, gaps };
    console.log(
      `  ${lang.code} ${lang.name}: ${total} attested candidates, ` +
        `${gaps.length}/${concepts.length} concepts with no attestation`,
    );
  }

  const gapPath = join(OUT, '_gaps.json');
  const prev = existsSync(gapPath) ? JSON.parse(readFileSync(gapPath, 'utf8')) : {};
  writeFileSync(gapPath, JSON.stringify({ ...prev, ...report }, null, 2));
  console.log(`  concept gaps -> catalogue/attested/_gaps.json`);
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});