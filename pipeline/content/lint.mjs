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

// Four sourcing classes, and the middle one is the reason the original three were wrong.
//
// The original axis was human-reviewed versus machine-aggregated: `authored` and `curated`
// are both controlled by someone who knows the language, `corpus` is not, so it was barred
// from Tier 0. Tatoeba fits none of those honestly. Nobody selected its sentences for a
// phrasebook, so they are not `curated`. But each one was deliberately written by a named
// native speaker, so calling it `corpus` — which meant "assembled with no native-speaker
// involvement" — was wrong in the direction that mattered, and it barred from Tier 0 the one
// class of real native-speaker text this project can actually obtain without a review team.
//
// `attested` names the middle: machine-aggregated in collection, human-authored in content.
// It sits between `curated` (a speaker chose this for a phrasebook, which we cannot get) and
// `authored` (we wrote it ourselves).
const TIER_SOURCING = {
  0: new Set(['attested', 'authored', 'curated']),  // courtesy — quality over coverage, always
  1: new Set(['attested', 'authored', 'curated', 'corpus']),
  2: new Set(['attested', 'authored', 'curated', 'corpus']),
  3: new Set(['attested', 'authored', 'curated', 'corpus']),
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

import { checkToneSet as checkThaiToneSet } from './thai-orthography.mjs';
import { checkToneSet as checkVietnameseToneSet } from './vietnamese-orthography.mjs';
import { evidenceFor, confidenceIsEntitled } from './evidence.mjs';

/**
 * Which validator each tonal language gets.
 *
 * The mapping is explicit and total because the failure it prevents is silent and confident.
 * Thai tone is determined by the CLASS of the initial consonant combined with the tone mark.
 * Vietnamese tone is determined by the VOWEL NUCLEUS combined with the mark. Handing Thai's
 * rules to Vietnamese would produce specific, wrong tone numbers that look verified -- which is
 * worse than no validator, because a reviewer would not re-check them.
 *
 * So a language with no entry here is NOT validated, and that is reported rather than quietly
 * skipped. Mandarin is the known gap: it is tonal with a THIRD system again, where the tone is
 * carried by the syllable as a whole rather than by the initial consonant or the nucleus. It
 * has no validator yet, and tone content must not ship for it until one exists.
 */
const TONE_VALIDATORS = {
  tha: { fn: checkThaiToneSet, system: 'initial consonant class x tone mark' },
  vie: { fn: checkVietnameseToneSet, system: 'vowel nucleus x tone mark' },
};

// Vocabulary that describes how content was PRODUCED rather than how to use it.
//
// `why` and `caution` render verbatim into the shipping UI, so a phrase's own build notes
// become the reader's instructions. Kept as a module-level table so it is constructed once
// and so the list is reviewable in one place.
const BUILD_VOCAB = [
  [/\battested\b/i, "'attested'"],
  [/\bbitexts?\b/i, "'bitext'"],
  [/\bcurated\b/i, "'curated'"],
  [/\bauthored\b/i, "'authored'"],
  [/\bprovenance\b/i, "'provenance'"],
  [/\bcorpus\b/i, "'corpus'"],
  [/\bpipeline\b/i, "'pipeline'"],
  [/\bWikivoyage\b/i, "'Wikivoyage'"],
  [/\bWikibooks?\b/i, "'Wikibooks'"],
  [/\bTatoeba\b/i, "'Tatoeba'"],
  [/\bWiktionary\b/i, "'Wiktionary'"],
  [/\bSOURCED\b/, "'SOURCED'"],
  [/\bentr(?:y|ies)\s+\d+/i, 'a numeric entry reference a reader cannot follow'],
  [/\bthis Tier \d/i, "a tier reference"],
];

// ---- record classification -----------------------------------------------
function kind(rec) {
  if (Array.isArray(rec.variants)) return 'tone_set';
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

    // 5a. THE EVIDENCE GATE.
    //
    // This is the single rule standing in for a native-speaker review team, which this
    // project will never have. Every entry must be able to state where it came from in terms
    // strong enough to check, and a Tier 0 entry whose confidence cannot be derived is a
    // build failure.
    //
    // Note what this does and does not do. It does NOT reject `authored` — wifi passwords
    // and extra towels have no attestation anywhere in the en-XX bitext, and blocking them
    // would mean shipping no wifi phrase at all. It requires that an authored entry SAY it
    // is authored, so the weakness travels with the content instead of being invisible.
    const ev = k === 'entry' ? evidenceFor(rec) : evidenceFor(rec.source ? rec : null);
    if (k === 'entry') {
      if (ev.confidence === 'unattributed') {
        const cls = rec.source?.class;
        if (cls === 'attested') {
          r.err(where, `class "attested" without both a named author and a resolvable external_id. Attestation with nothing to check it against is not attestation — a sentence with no id and no author cannot be verified and cannot be properly attributed under CC BY.`);
        } else if (rec.tier === 0) {
          r.err(where, `no evidence derivable for a Tier 0 entry. Every Tier 0 item must resolve to attested, authored or curated provenance.`);
        } else {
          r.warn(where, `no evidence derivable; this entry cannot state its own standing.`);
        }
      } else if (!confidenceIsEntitled(rec.source.class, ev.confidence)) {
        r.err(where, `class "${rec.source.class}" claims confidence "${ev.confidence}", which that class is not entitled to.`);
      }
      if (ev.confidence === 'authored_corpus_aligned' || ev.confidence === 'authored_single') {
        r.stats.authored_tier0 = (r.stats.authored_tier0 ?? 0) + (rec.tier === 0 ? 1 : 0);
      }
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

    // 8b. `why` and `caution` are RENDERED VERBATIM on the phone, which makes them
    // product copy rather than build notes. Fifteen Thai entries breached that before this
    // rule existed: cross-references such as "the same spelling as the middle of entry 5",
    // which a reader cannot follow, and words like "attested", "bitext" and "curated",
    // which describe how the content was produced rather than how to use the phrase.
    //
    // Models.kt claimed these fields were "for the build and for review, not for a phone"
    // while Screens.kt rendered them. The comment was the thing that was wrong; both sides
    // are aligned now and this rule holds the line between them.
    if (typeof rec.why === 'string' || typeof rec.caution === 'string') {
      for (const fld of ['why', 'caution']) {
        const v = rec[fld];
        if (typeof v !== 'string') continue;
        for (const [re, label] of BUILD_VOCAB) {
          if (re.test(v)) {
            r.err(rec.id, `${fld} is shown on the phone, so it must be user-facing, but it contains ${label}: "${v.slice(0, 80)}..."`);
          }
        }
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

  // ---- cross-record: tone sets ------------------------------------------
  // The whole pedagogical point of a tone set is that the variants differ ONLY by tone.
  // Two variants with identical text_native means the learner is shown the same word
  // twice and taught nothing, which looks like content and is not.
  // Mechanical orthography. The tone NUMBER is the app's central claim about Thai and
  // until this rule it was asserted by hand. Tone is fully determined by the initial
  // consonant's class and the mark written, so a wrong number is a bug a lookup can find --
  // see thai-orthography.mjs for what this deliberately cannot check.
  records.filter(r2 => kind(r2) === 'tone_set').forEach(ts => {
    const validator = TONE_VALIDATORS[ts.lang];
    if (!validator) {
      // Refuse rather than skip. Falling through would leave a tonal language's tone numbers
      // unchecked while the build reported success, which is the "looks verified, nothing
      // verifies it" failure this whole mechanism exists to prevent.
      r.err(ts.id,
        `no tone validator for "${ts.lang}". Tone sets ship with a tone number as the central ` +
        `claim about the language, and that number must be mechanically checked or not shipped. ` +
        `Validators exist for: ${Object.entries(TONE_VALIDATORS).map(([c, v]) => `${c} (${v.system})`).join('; ')}.`);
      return;
    }
    for (const issue of validator.fn(ts)) {
      const msg = issue.msg ?? issue.message ?? String(issue);
      if (issue.severity === 'warn') r.warn(ts.id, msg);
      else r.err(ts.id, msg);
    }
  });

  records.filter(r2 => kind(r2) === 'tone_set').forEach(ts => {
    const where = `tone set ${ts.id}`;
    const seen = new Map();
    for (const v of ts.variants ?? []) {
      if (seen.has(v.text_native)) {
        r.err(where, `variant "${v.text_native}" appears twice (tones ${seen.get(v.text_native)} and ${v.tone}). A contrast set needs the text to DIFFER.`);
      } else {
        seen.set(v.text_native, v.tone);
      }
      if (!v.text_english) {
        r.err(where, `tone ${v.tone} has no meaning. Without it the set teaches a squiggle rather than a contrast.`);
      }
    }
    // A tone set in a language the spec says has no tones is a spec/content mismatch.
    const spec = specs.get(ts.lang);
    if (spec && spec.structure?.tones !== true) {
      r.err(where, `spec for "${ts.lang}" declares structure.tones: false, so a tone set cannot belong to it.`);
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