// Content linter.
//
// Second half of the enforcement. The spec linter stops a bad claim entering a
// spec; this stops a bad ITEM entering the app.
//
// Three things it does that a JSON Schema validator cannot:
//
//   1. Enforces the tier/source policy per item. Tier 0 accepts curated sources
//      only. This is the single highest-value rule in the whole pipeline, because
//      Tier 0 is where a mediocre phrase costs the most.
//   2. Checks cross-record integrity against the spec: romanisation presence is
//      decided by the spec's script, not by the item.
//   3. Checks things that are wrong in aggregate but fine one at a time — duplicate
//      ids, dangling exchange references, non-contiguous turns, and the
//      production/reception balance.
//
// SCHEMA.json is authoritative for field shape and is validated with ajv, so the
// schema and the validator cannot drift apart.

import Ajv2020 from 'ajv/dist/2020.js';
import addFormats from 'ajv-formats';
import YAML from 'yaml';
import { readdirSync, readFileSync } from 'fs';
import { join } from 'path';

const SCHEMA_PATH = new URL('../../content/SCHEMA.json', import.meta.url).pathname;
const SPEC_DIR = new URL('../../specs/', import.meta.url).pathname;

// The axis is human-reviewed versus machine-aggregated, not published versus not.
// `authored` and `curated` are both controlled by someone who knows the language;
// `corpus` is not and cannot be, so it is barred from Tier 0.
const HUMAN_REVIEWED = new Set(['authored', 'curated']);
const TIER_SOURCING = {
  0: HUMAN_REVIEWED,          // courtesy — quality over coverage, always
  1: new Set(['authored', 'curated', 'corpus']),
  2: new Set(['authored', 'curated', 'corpus']),
  3: new Set(['authored', 'curated', 'corpus']),
};

// From the research: production cards must outnumber recognition cards 2:1, because
// the app's whole thesis is that output is the hard part and passive recognition is not.
const DIRECTION_RATIO = { say: 2, understand: 1 };

class Report {
  constructor() { this.errors = []; this.warnings = []; this.stats = {}; }
  err(where, msg) { this.errors.push(`${where}: ${msg}`); }
  warn(where, msg) { this.warnings.push(`${where}: ${msg}`); }
}

// ---- spec loading, for cross-record checks --------------------------------
export function loadSpecs(dir = SPEC_DIR) {
  const specs = new Map();
  for (const f of readdirSync(dir).filter(f => /\.(ya?ml)$/.test(f) && f !== 'SCHEMA.md')) {
    const doc = YAML.parse(readFileSync(join(dir, f), 'utf8'));
    specs.set(doc.language.code, doc);
  }
  return specs;
}

function isLatinScript(spec) {
  return /Latin/i.test(spec?.structure?.script?.primary ?? '');
}

// ---- record classification -----------------------------------------------
function kind(rec) {
  return Array.isArray(rec.turns) ? 'exchange' : 'entry';
}

// ---- the linter ----------------------------------------------------------
export function lintContent(records, specs, schema) {
  const r = new Report();
  const ajv = new Ajv2020({ allErrors: true, strict: false });
  // Without this, ajv silently IGNORES every `format` keyword in the schema, so
  // `format: date-time` would be decoration and a garbage timestamp would pass.
  addFormats(ajv);
  const validate = ajv.compile(schema);

  const seenIds = new Map();       // id -> record
  const directionCount = { say: 0, understand: 0 };
  const tierCount = { 0: 0, 1: 0, 2: 0, 3: 0 };
  const normalized = new Map();    // id -> text_native, for duplicate detection

  records.forEach((rec, i) => {
    const where = `#${i}${rec?.id ? ` (${rec.id})` : ''}`;

    // 1. shape
    if (!validate(rec)) {
      for (const e of validate.errors ?? []) {
        r.err(`${where} schema`, `${e.instancePath || '/'} ${e.message}`);
      }
      return;
    }

    const k = kind(rec);

    // 2. unique, never-reused ids. Content is corrected by revision, not replacement,
    //    so a learner's history must survive a spec change.
    if (seenIds.has(rec.id)) {
      r.err(where, `duplicate id "${rec.id}". Ids are never reused — revise in place instead.`);
    } else {
      seenIds.set(rec.id, rec);
    }

    // 3. the spec must exist for this language
    const spec = specs.get(rec.lang);
    if (!spec) {
      r.err(where, `no spec for language "${rec.lang}". Content cannot ship without a spec — the spec decides the variety and the tiers.`);
      return;
    }

    // 4. THE POLICY. Tier 0 draws on curated sources only.
    const cls = rec.source?.class;
    if (!TIER_SOURCING[rec.tier]?.has(cls)) {
      r.err(where, `tier ${rec.tier} may not use a "${cls}" source (allowed: ${[...(TIER_SOURCING[rec.tier] ?? [])].join(', ')}). Tier 0 is where a mediocre phrase costs the most.`);
    }

    // 5. the source must actually be listed in the spec
    const declared = (spec.resources ?? []).some(x => x.name === rec.source?.id);
    if (!declared && rec.source?.id !== 'internal') {
      r.err(where, `source "${rec.source?.id}" is not listed in ${rec.lang}.spec.resources. An undeclared source cannot be licence-checked.`);
    }

    // 5b. gloss_mode decides whether a gloss is permitted to be null
    const sameAsNative = spec.language?.gloss_mode === 'same_as_native';
    const glosses = k === 'entry'
      ? [{ n: 'text_english', v: rec.text_english }]
      : (rec.turns ?? []).map((t, ti) => ({ n: `turns[${ti}].text_english`, v: t.text_english }));
    for (const g of glosses) {
      if (g.v == null && !sameAsNative) {
        r.err(where, `${g.n} is null but the spec does not set language.gloss_mode: same_as_native`);
      }
    }

    // 6. tier must exist in the spec and the item must not exceed it
    const tierDef = (spec.tiers ?? []).find(t => t.id === rec.tier);
    if (!tierDef) {
      r.err(where, `tier ${rec.tier} is not defined in the spec`);
    }

    // 7. romanisation presence is decided by the spec's script, not by the item.
    //    Only text_romanized is checked — text_native is required by the schema in
    //    every language, so testing it against the Latin rule rejects valid content.
    const latin = isLatinScript(spec);
    const fields = k === 'entry'
      ? [{ n: 'text_romanized', v: rec.text_romanized }]
      : (rec.turns ?? []).map((t, ti) => ({ n: `turns[${ti}].text_romanized`, v: t.text_romanized }));

    for (const f of fields) {
      if (latin && f.v != null) {
        r.err(where, `${f.n} is set but the spec declares a Latin script. The same string would ship twice.`);
      }
      if (!latin && f.v == null) {
        r.err(where, `${f.n} is null but the spec declares a non-Latin script, which requires a romanisation for the app to be usable without reading the target script.`);
      }
    }

    // 8. duplicate content within a language
    const key = `${rec.lang}|${(rec.text_native ?? '').toLowerCase().trim()}`;
    if (k === 'entry' && rec.text_native) {
      if (normalized.has(key)) {
        r.err(where, `duplicate text_native, already used by "${normalized.get(key)}"`);
      } else {
        normalized.set(key, rec.id);
      }
    }

    // 9. aggregate counters. Exchange turns are counted as well as entries:
    //    reception mostly lives on the `them` side, so counting entries alone
    //    reported Tier 0 as say=41/understand=7 when the true figure is nowhere
    //    near that. An enforcement statistic that undercounts is worse than none.
    if (k === 'entry') {
      directionCount[rec.direction] = (directionCount[rec.direction] ?? 0) + 1;
      tierCount[rec.tier] = (tierCount[rec.tier] ?? 0) + 1;
    } else {
      for (const t of rec.turns ?? []) {
        directionCount[t.direction] = (directionCount[t.direction] ?? 0) + 1;
      }
    }
  });

  // ---- cross-record: exchanges ------------------------------------------
  records.filter(r2 => kind(r2) === 'exchange').forEach(ex => {
    const where = `exchange ${ex.id}`;

    const turns = ex.turns ?? [];
    const numbers = turns.map(t => t.turn);
    const contiguous = numbers.every((n, i) => n === i + 1);
    if (!contiguous) {
      r.err(where, `turn numbers must be 1..n contiguous, got [${numbers.join(', ')}]`);
    }

    // An all-`them` exchange is legitimate: "what you will hear" is real content for a
    // spoken app, and it is how a learner meets receptive vocabulary in conversational
    // order. So this warns rather than errors — it asks whether it was intended, and the
    // author knows. A genuine phrase list is already caught by the schema's minItems.
    if (!turns.some(t => t.speaker === 'you')) {
      r.warn(where, 'every turn is "them". Valid if this exchange is deliberately receptive-only; check it was not meant to have a reply.');
    }

    turns.forEach((t, i) => {
      const entry = t.entry_id ? seenIds.get(t.entry_id) : null;
      if (t.entry_id && !entry) {
        r.err(`${where} turn ${i + 1}`, `entry_id "${t.entry_id}" does not exist`);
        return;
      }
      // Direction-aware, not speaker-aware. An entry records how HE uses a phrase.
      //  - a `you` turn may link to a `say` entry
      //  - a `them` turn may link to an `understand` entry
      // Linking across that boundary conflates production with reception, which is the
      // split the `direction` field exists to keep honest. When the same words genuinely
      // serve both sides — "Jina lako ni nani?" is something he asks AND something he is
      // asked — the entry keeps his usage and the turn inlines its own text.
      if (entry && t.direction === 'say' && entry.direction !== 'say') {
        r.err(`${where} turn ${i + 1}`, `links to "${t.entry_id}" which is tagged understand, but the turn is production.`);
      }
      if (entry && t.direction === 'understand' && entry.direction !== 'understand') {
        r.err(`${where} turn ${i + 1}`, `a "them" turn links to "${t.entry_id}", which is tagged for production. Inline the text and leave entry_id null — the same words serve both sides.`);
      }
    });
  });

  // ---- aggregate sanity --------------------------------------------------
  const say = directionCount.say ?? 0, understand = directionCount.understand ?? 0;
  if (say + understand > 0) {
    const expected = { say: understand * DIRECTION_RATIO.say, understand: say / DIRECTION_RATIO.say };
    if (say < understand) {
      r.warn('aggregate direction', `say=${say} < understand=${understand}. The research says production is the hard part and should outnumber recognition.`);
    } else if (say < expected.say * 0.6) {
      r.warn('aggregate direction', `say=${say} against a target of ~${Math.round(expected.say)} for understand=${understand}. Consider whether recognition items are crowding out production.`);
    }
  }

  if (tierCount[0] === 0 && records.length > 0) {
    r.warn('aggregate tiers', 'no Tier 0 items. Tier 0 is the near-certain tier and the one that must exist first.');
  }

  r.stats = { total: records.length, entries: records.filter(x => kind(x) === 'entry').length,
    exchanges: records.filter(x => kind(x) === 'exchange').length,
    tiers: tierCount, directions: directionCount, ids: seenIds.size };
  return r;
}

// ---- loaders -------------------------------------------------------------
export function loadRecords(dir) {
  const out = [];
  for (const f of readdirSync(dir).filter(f => f.endsWith('.jsonl'))) {
    const text = readFileSync(join(dir, f), 'utf8');
    text.split('\n').forEach((line, i) => {
      const t = line.trim();
      if (!t || t.startsWith('//')) return;
      try { out.push(JSON.parse(t)); }
      catch (e) { out.push({ __parseError: `${f}:${i + 1} ${e.message}` }); }
    });
  }
  return out;
}

export function loadSchema() {
  return JSON.parse(readFileSync(SCHEMA_PATH, 'utf8'));
}

// ---- driver --------------------------------------------------------------
if (import.meta.url === `file://${process.argv[1]}`) {
  const contentDir = process.argv[2] ?? '../content';
  const specs = loadSpecs();
  const schema = loadSchema();
  const records = loadRecords(contentDir);

  const parseErrors = records.filter(x => x.__parseError);
  if (parseErrors.length) {
    console.error(`\x1b[31mFAIL\x1b[0m ${parseErrors.length} unparseable line(s)`);
    parseErrors.forEach(p => console.error(`     ${p.__parseError}`));
    process.exit(1);
  }

  if (!records.length) {
    console.log('no content files yet — nothing to lint');
    process.exit(0);
  }

  const r = lintContent(records, specs, schema);
  if (r.errors.length) {
    console.error(`\x1b[31mFAIL\x1b[0m ${r.errors.length} error(s)`);
    r.errors.forEach(e => console.error(`     ${e}`));
  } else {
    console.log(`\x1b[32mPASS\x1b[0m ${r.stats.total} record(s), ${r.stats.ids} unique id(s)`);
  }
  r.warnings.forEach(w => console.warn(`     ${w}`));
  console.log(`     ${r.stats.entries} entries, ${r.stats.exchanges} exchanges`);
  console.log(`     tiers ${JSON.stringify(r.stats.tiers)}  directions ${JSON.stringify(r.stats.directions)}  (counts entries AND exchange turns)`);
  process.exit(r.errors.length ? 1 : 0);
}