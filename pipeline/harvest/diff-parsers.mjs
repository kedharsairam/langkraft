/**
 * Corpus-wide regression harness for reading extraction.
 *
 * WHY THIS FILE EXISTS
 *
 * Six attempts were made to fix missing romanisations by widening a character class, and each
 * one broke a different language. Every one of them passed the row it was written against and
 * would have shipped the breakage had it not been caught by running the full corpus.
 *
 * So this compares the OLD parser and the NEW extractor over every `;` row of all twenty
 * phrasebook pages and prints every row whose output differs. The point is not that the new
 * one wins; it is that the difference is KNOWN rather than discovered on a user's phone.
 *
 * Pages are cached under pipeline/.page-cache/ so a re-run costs nothing and a diff between two
 * runs compares parsers rather than comparing Wikipedia's current state.
 *
 *     node harvest/diff-parsers.mjs            # compare
 *     node harvest/diff-parsers.mjs --show ara # show every changed Arabic row
 */

import { readFileSync, writeFileSync, existsSync, mkdirSync } from 'node:fs';
import { join } from 'node:path';
import { parsePhraseRow, parsePhraseRowLegacy, PAGE_TITLES_EXPORT } from './wikivoyage.mjs';

const ROOT = new URL('../..', import.meta.url).pathname;
const CACHE = join(ROOT, 'pipeline', '.page-cache');
const API = 'https://en.wikivoyage.org/w/api.php';
const UA = { 'User-Agent': 'LangKraft research (offline phrasebook project)' };

/** Every `;` row on the page, unfiltered. The parser's own rejections still apply. */
function phraseRows(wikitext) {
  const rows = [];
  for (const line of wikitext.split('\n')) {
    const t = line.trim();
    if (!t.startsWith(';')) continue;
    // A `;;` sub-row carries no colon and is a heading inside a list.
    if (t.startsWith(';;')) continue;
    rows.push(t);
  }
  return rows;
}

async function fetchWikitext(title) {
  const file = join(CACHE, `${title.replace(/\W+/g, '_')}.txt`);
  if (existsSync(file)) return readFileSync(file, 'utf8');
  const url =
    `${API}?action=query&prop=revisions&rvslots=*&rvprop=content` +
    `&titles=${encodeURIComponent(title)}&format=json&formatversion=2`;
  for (let attempt = 0; attempt < 4; attempt += 1) {
    const res = await fetch(url, { headers: UA });
    // A throttle must never be cached as content, and never counted as a missing page.
    if (res.status === 429 || res.status >= 500) {
      await new Promise((r) => setTimeout(r, 4000 * (attempt + 1)));
      continue;
    }
    if (!res.ok) throw new Error(`${title}: HTTP ${res.status}`);
    const page = (await res.json())?.query?.pages?.[0];
    if (!page || page.missing || !page.revisions?.length) {
      throw new Error(`${title}: page is genuinely missing`);
    }
    const text = page.revisions[0].slots.main.content;
    mkdirSync(CACHE, { recursive: true });
    writeFileSync(file, text);
    await new Promise((r) => setTimeout(r, 800));
    return text;
  }
  throw new Error(`${title}: still throttled after 4 attempts`);
}

/**
 * Languages whose PHRASES are written in Latin, so there is no reading column to find.
 *
 * Not the same as "the language's script is Latin". Tamil's script is Tamil and its spec says so,
 * but the Tamil phrasebook writes its phrases in romanisation, so a trailing Latin run on a Tamil
 * row is more phrase, not a reading. The harvester already knows this per language; the diff needs
 * the same list or it measures a parser being asked a question its caller would never ask.
 */
const LATIN_PHRASE_LANGUAGES = new Set([
  'por', 'ind', 'spa', 'fra', 'nld', 'ita', 'deu', 'srp', 'tur', 'swh', 'tam',
]);

/**
 * Languages whose READING COLUMN is in their own script rather than Latin.
 *
 * Dari writes the phrase in Latin and the Dari in Arabic script inside the italics, so a rule
 * that requires a Latin reading rejects every phrase on the page. This is a per-language fact
 * about how one page is written, not a general rule, which is why it is a set and not a heuristic.
 */
const TARGET_SCRIPT_READING_LANGUAGES = new Set(['fas']);

const show = process.argv.includes('--show')
  ? process.argv[process.argv.indexOf('--show') + 1]
  : null;

const titles = PAGE_TITLES_EXPORT;
const summary = [];
let totalRows = 0;
let changed = 0;
const regressions = [];
const gains = [];

for (const [code, title] of Object.entries(titles)) {
  let wikitext;
  try {
    wikitext = await fetchWikitext(title);
  } catch (e) {
    console.log(`  ${code}  FETCH FAILED: ${e.message}`);
    summary.push({ code, rows: 0, changed: 0, lost: 0, gained: 0 });
    continue;
  }

  const rows = phraseRows(wikitext);
  let nChanged = 0;
  let lost = 0;
  let gained = 0;

  for (const row of rows) {
    // The comparison is between the ACTIVE parser and the pre-2026-10-06 one, with the same
    // per-language options both would receive. Comparing the extractor directly was the earlier
    // draft and it hid the point: what ships is the call, options included.
    const oldRes = parsePhraseRowLegacy(row);
    const opts = {
      latinScript: LATIN_PHRASE_LANGUAGES.has(code),
      readingInTargetScript: TARGET_SCRIPT_READING_LANGUAGES.has(code),
    };
    const newRes = parsePhraseRow(row, false, opts);

    const oldNat = oldRes?.native?.trim() ?? null;
    const oldRom = oldRes?.pronunciation?.trim() ?? null;
    const newNat = newRes?.native?.trim() ?? null;
    const newRom = newRes?.pronunciation?.trim() ?? null;

    if (oldNat === newNat && oldRom === newRom) continue;
    nChanged += 1;
    totalRows += 1;

    const oldHadBoth = oldNat && oldRom;
    const newHasBoth = newNat && newRom;
    const kind = oldHadBoth && !newHasBoth ? 'lost' : !oldHadBoth && newHasBoth ? 'gained' : 'changed';

    if (kind === 'lost') regressions.push({ code, row, oldNat, oldRom, newNat, newRom });
    if (kind === 'gained') gains.push({ code, row, oldNat, oldRom, newNat, newRom });

    if (show === code) {
      console.log(`\n  [${kind}] ${row.slice(0, 90)}`);
      console.log(`     old  native=${JSON.stringify(oldNat)}`);
      console.log(`     old  roman=${JSON.stringify(oldRom)}`);
      console.log(`     new  native=${JSON.stringify(newNat)}`);
      console.log(`     new  roman=${JSON.stringify(newRom)}`);
    }
  }

  changed += nChanged;
  summary.push({ code, rows: rows.length, changed: nChanged, lost, gained });
  lost = regressions.filter((r) => r.code === code).length;
  gained = gains.filter((g) => g.code === code).length;
}

console.log('\n  code   rows  changed   lost a reading   gained a reading');
for (const s of summary) {
  const flag = s.lost ? '  <-- REGRESSION' : '';
  console.log(
    `  ${String(s.code).padEnd(6)} ${String(s.rows).padStart(5)} ${String(s.changed).padStart(8)}` +
    `${String(s.lost).padStart(18)}${String(s.gained).padStart(18)}${flag}`,
  );
}
console.log(
  `\n  ${changed} of ${summary.reduce((n, s) => n + s.rows, 0)} rows parse differently.` +
  `\n  ${regressions.length} LOST a complete reading, ${gains.length} gained one.`,
);

if (regressions.length) {
  console.log('\n  Every lost reading, in full — these are the ones that must not ship:');
  for (const r of regressions) {
    console.log(`\n  [${r.code}] ${r.row.slice(0, 100)}`);
    console.log(`     was  ${JSON.stringify(r.oldNat)} / ${JSON.stringify(r.oldRom)}`);
    console.log(`     now  ${JSON.stringify(r.newNat)} / ${JSON.stringify(r.newRom)}`);
  }
}
