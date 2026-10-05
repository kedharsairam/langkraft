/**
 * Every phrase-list sub-heading on all twenty pages that SECTION_CONCEPTS does not cover.
 *
 * WHY A SEPARATE TOOL
 *
 * An unmapped heading makes the harvester set `section = null`, and every row under it is then
 * skipped. Nothing is printed per heading, nothing fails, and the language still reports a healthy
 * phrase count — the phrases are simply absent from the corpus. That is the worst possible failure
 * mode for a gap: invisible, and it makes the surrounding numbers look better than they are.
 *
 * `wikivoyage.mjs` already collects these per language and prints them in brackets, but only for
 * the languages in a given run. This lists them all at once, with row counts, so the mapping can be
 * completed in one pass instead of being discovered language by language.
 *
 *     node harvest/report-sections.mjs          # unmapped headings, with how many rows each holds
 *     node harvest/report-sections.mjs --all    # every heading, mapped or not
 */

import { readFileSync, existsSync, mkdirSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { PAGE_TITLES_EXPORT } from './wikivoyage.mjs';
import { cleanWikitext } from './wikivoyage.mjs';

const ROOT = new URL('../..', import.meta.url).pathname;
const CACHE = join(ROOT, 'pipeline', '.page-cache');
const API = 'https://en.wikivoyage.org/w/api.php';
const UA = { 'User-Agent': 'LangKraft research (offline phrasebook project)' };

/** Mirrors the key derivation in wikivoyage.mjs. Kept in step by the assertion below. */
function sectionKey(heading) {
  return cleanWikitext(heading)
    .toLowerCase()
    .replace(/[^a-z ]/g, '')
    .trim()
    .replace(/\s+/g, '_');
}

async function fetchWikitext(title) {
  const file = join(CACHE, `${title.replace(/\W+/g, '_')}.txt`);
  if (existsSync(file)) return readFileSync(file, 'utf8');
  const url =
    `${API}?action=query&prop=revisions&rvslots=*&rvprop=content` +
    `&titles=${encodeURIComponent(title)}&format=json&formatversion=2`;
  for (let attempt = 0; attempt < 4; attempt += 1) {
    const res = await fetch(url, { headers: UA });
    if (res.status === 429 || res.status >= 500) {
      await new Promise((r) => setTimeout(r, 4000 * (attempt + 1)));
      continue;
    }
    if (!res.ok) throw new Error(`${title}: HTTP ${res.status}`);
    const page = (await res.json())?.query?.pages?.[0];
    if (!page?.revisions?.length) throw new Error(`${title}: missing`);
    const text = page.revisions[0].slots.main.content;
    mkdirSync(CACHE, { recursive: true });
    writeFileSync(file, text);
    await new Promise((r) => setTimeout(r, 800));
    return text;
  }
  throw new Error(`${title}: throttled`);
}

/** Section keys declared in wikivoyage.mjs, read from the source rather than re-imported. */
function declaredSections() {
  const src = readFileSync(new URL('./wikivoyage.mjs', import.meta.url), 'utf8');
  const body = src.slice(
    src.indexOf('const SECTION_CONCEPTS = {'),
    src.indexOf('};', src.indexOf('const SECTION_CONCEPTS = {')),
  );
  const keys = new Set();
  for (const m of body.matchAll(/^\s{2}([a-z0-9_]+)\s*:/gm)) keys.add(m[1]);
  return keys;
}

const declared = declaredSections();
const showAll = process.argv.includes('--all');

const unmapped = new Map(); // key -> { rows, languages:Set, headings:Set }
const mapped = new Map();

for (const [code, title] of Object.entries(PAGE_TITLES_EXPORT)) {
  let wikitext;
  try {
    wikitext = await fetchWikitext(title);
  } catch (e) {
    console.log(`  ${code}: ${e.message}`);
    continue;
  }

  let inPhraseList = false;
  const allLines = wikitext.split('\n');
  for (let lineNo = 0; lineNo < allLines.length; lineNo += 1) {
    const line = allLines[lineNo].trim();

    // EXACTLY the harvester's gate: `/^==\s*[^=].*==\s*$/`.
    //
    // The obvious-looking `/^=+[^=]/` is wrong, and being wrong here made this tool report "every
    // heading is mapped" while the harvester was simultaneously reporting five unmapped Mandarin
    // sections. `^=+` is greedy, so on `=== Going to the doctor ===` it consumes the three `=`,
    // `[^=]` then has to match the space — which it does — and the line is treated as a TOP-LEVEL
    // heading, resetting `inPhraseList`. Every sub-heading was therefore read as a section
    // boundary, no sub-heading was ever tested against the map, and the tool reported a clean
    // sweep over a corpus it had not looked at.
    //
    // A gap report that reports no gaps is worse than no gap report, because it is believed.
    if (/^==\s*[^=].*==\s*$/.test(line)) {
      inPhraseList = /phrases?/i.test(cleanWikitext(line.replace(/^=+|=+$/g, '')));
      continue;
    }
    const sub = /^={3,}\s*([^=]+?)\s*={3,}$/.exec(line);
    if (!sub) continue;
    if (!inPhraseList) continue;

    const heading = cleanWikitext(sub[1]);
    const key = sectionKey(sub[1]);
    if (!key) continue;

    // Count the rows under this heading, by walking forward from this line's own index.
    // `indexOf(raw)` was wrong — it returns the FIRST line with that text, which undercounts
    // every heading whose name repeats, and undercounting a dropped section makes the loss look
    // smaller than it is.
    let rows = 0;
    for (let i = lineNo + 1; i < allLines.length; i += 1) {
      const l = allLines[i].trim();
      if (/^=+[^=]/.test(l) || /^={3,}/.test(l)) break;
      if (l.startsWith(';')) rows += 1;
    }

    const target = declared.has(key) ? mapped : unmapped;
    if (!target.has(key)) target.set(key, { rows: 0, languages: new Set(), headings: new Set() });
    const entry = target.get(key);
    entry.rows += rows;
    entry.languages.add(code);
    entry.headings.add(heading);
  }
}

console.log(`\n  ${declared.size} section keys declared in SECTION_CONCEPTS\n`);

if (showAll) {
  console.log('  MAPPED');
  for (const [key, e] of [...mapped].sort()) {
    console.log(`    ${key.padEnd(42)} ${String(e.rows).padStart(5)} rows  ${[...e.languages].join(',')}`);
  }
}

console.log(`\n  UNMAPPED — every row under these is silently dropped\n`);
const list = [...unmapped].sort((a, b) => b[1].rows - a[1].rows);
if (!list.length) {
  console.log('    none. Every phrase-list heading on every page is mapped.\n');
} else {
  let total = 0;
  for (const [key, e] of list) {
    total += e.rows;
    console.log(`    ${key.padEnd(42)} ${String(e.rows).padStart(5)} rows  ${[...e.languages].join(',')}`);
    if (e.headings.size > 1) {
      for (const h of e.headings) console.log(`        as written: "${h}"`);
    }
  }
  console.log(`\n    ${list.length} unmapped headings, ${total} rows dropped in total\n`);
}

export { sectionKey, declaredSections };
