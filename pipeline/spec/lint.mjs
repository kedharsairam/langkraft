// Spec linter.
//
// Purpose: make it structurally impossible to ship an unsourced number in a spec.
// A previous research pass produced three hour figures it could not source, presented
// them as fact, and had to retract them later. That is the failure this prevents.
//
// Exit code 1 on any error. Warnings do not fail the build.

import YAML from 'yaml';
import { readdirSync, readFileSync, statSync } from 'fs';
import { join } from 'path';

const MAX_AGE_DAYS = 400; // resource `checked` dates older than this warn

const ENUMS = {
  'spec_version_shape': /^\d+\.\d+\.\d+$/,
  'status': ['draft', 'reviewed', 'shipped', 'deprecated'],
  'structure.morphology': ['analytic', 'moderately_synthetic', 'heavily_synthetic', 'isolating'],
  'structure.script.direction': ['ltr', 'rtl'],
  'structure.tones': [true, false],
  'register.system': ['none', 'formality', 'politeness_levels', 'gender'],
  'tier.certainty': ['high', 'medium', 'low'],
  'variety.romanization_scheme': ['iso15919', 'rtgs', 'hepburn', 'revised_romanization', 'pinyin', 'gajs_latin', 'bgn_pcgn', 'elid_iso843', 'chat_alphabet', 'practical', null],
  'language.role': ['calibration', 'course'],
  'language.gloss_mode': ['required', 'same_as_native'],
};

// Paths that must resolve to a claim object rather than a bare value.
const REQUIRED_SOURCED = [
  'variety.rationale',
  'tiers[].size',
  'cost.fsi_category',
  'cost.fsi_hours_to_ilr3',
  'cost.hours_to_floor',
];

class Report {
  constructor(file) { this.file = file; this.errors = []; this.warnings = []; }
  err(where, msg) { this.errors.push(`${where}: ${msg}`); }
  warn(where, msg) { this.warnings.push(`${where}: ${msg}`); }
}

const get = (o, p) => p.split('.').reduce((a, k) => (a == null ? undefined : a[k]), o);

function isClaim(v) {
  return v != null && typeof v === 'object' && !Array.isArray(v)
    && 'value' in v && 'source' in v && 'verified' in v && 'checked' in v;
}

function checkClaim(r, where, v, { sourced }) {
  if (!sourced) return;
  if (v == null) return;                       // explicit null is a decision, record it in a note
  if (typeof v !== 'object' || Array.isArray(v)) {
    r.err(where, 'bare value where a claim is required. Wrap it: {value, source, verified, checked}');
    return;
  }
  for (const k of ['value', 'source', 'verified', 'checked']) {
    if (!(k in v)) r.err(where, `claim is missing required key "${k}"`);
  }
  if ('verified' in v && typeof v.verified !== 'boolean') {
    r.err(where, 'claim.verified must be true or false');
  }
  if ('checked' in v && !/^\d{4}-\d{2}-\d{2}$/.test(String(v.checked))) {
    r.err(where, 'claim.checked must be YYYY-MM-DD');
  }
  if (v.source === '' || v.source == null) {
    r.err(where, 'claim.source must not be empty. "I could not find a figure" is honest; an empty source is not.');
  }
}

export function lintSpec(doc, file) {
  const r = new Report(file);

  // ---- structure -------------------------------------------------------
  for (const k of ['language', 'spec_version', 'status', 'variety', 'structure',
    'register', 'coverage', 'tiers', 'cost', 'review']) {
    if (!(k in doc)) r.err(k, 'required top-level key is missing');
  }
  if (Object.keys(r.errors).length) return r;

  if (!ENUMS.status.includes(doc.status)) r.err('status', `must be one of ${ENUMS.status}`);
  if (!ENUMS.spec_version_shape.test(doc.spec_version)) {
    r.err('spec_version', 'must be semver, e.g. 1.0.0');
  }

  // ---- language --------------------------------------------------------
  const L = doc.language;
  if (!L || !/^[a-z]{3}$/.test(L.code ?? '')) r.err('language.code', 'must be ISO 639-3, three lowercase letters');
  if (!ENUMS['language.role'].includes(L?.role)) r.err('language.role', `must be one of ${ENUMS['language.role']}`);
  if (!ENUMS['language.gloss_mode'].includes(L?.gloss_mode)) r.err('language.gloss_mode', `must be one of ${ENUMS['language.gloss_mode']}`);
  if (L?.romanization !== null && typeof L?.romanization !== 'string') {
    r.err('language.romanization', 'must be a scheme id or null for Latin-script languages');
  }

  // ---- variety ---------------------------------------------------------
  const V = doc.variety || {};
  checkClaim(r, 'variety.rationale', V.rationale, { sourced: true });
  const variantIds = (V.variants ?? []).map(v => v.id);
  if (new Set(variantIds).size !== variantIds.length) r.err('variety.variants', 'duplicate variant id');
  if (V.default && !variantIds.includes(V.default)) {
    r.err('variety.default', `"${V.default}" is not in variants [${variantIds.join(', ')}]. The default must be shippable, not aspirational.`);
  }
  if (V.romanization_scheme !== undefined && !ENUMS['variety.romanization_scheme'].includes(V.romanization_scheme)) {
    r.err('variety.romanization_scheme', 'unknown scheme');
  }
  (V.variants ?? []).forEach((v, i) => {
    if (!isClaim(v.notes)) r.err(`variety.variants[${i}].notes`, 'must be a claim — why this variety exists is a decision, not a preference');
  });

  // ---- structure -------------------------------------------------------
  const S = doc.structure || {};
  if (!ENUMS['structure.morphology'].includes(S.morphology)) r.err('structure.morphology', `must be one of ${ENUMS['structure.morphology']}`);
  if (!ENUMS['structure.script.direction'].includes(S?.script?.direction)) r.err('structure.script.direction', `must be one of ${ENUMS['structure.script.direction']}`);
  if (typeof S.tones !== 'boolean') r.err('structure.tones', 'must be boolean — it decides whether a tone stage exists');
  if (!ENUMS['register.system'].includes(doc.register?.system)) r.err('register.system', `must be one of ${ENUMS['register.system']}`);

  // Latin script implies no romanisation, and vice versa. A romanised field on a
  // Latin-script language means the content will ship the same string twice.
  const isLatin = /Latin/i.test(S?.script?.primary ?? '');
  if (isLatin) {
    if (L?.romanization) {
      r.err('language.romanization', 'script is Latin but a romanization scheme is set. One of them is wrong.');
    }
    if (V.romanization_scheme) {
      r.err('variety.romanization_scheme', 'script is Latin but a romanization scheme is set. One of them is wrong.');
    }
  } else if (!L?.romanization) {
    // A non-Latin language with no romanisation leaves the app with one script and
    // no way to read it without learning to read it — which is not the goal.
    r.err('language.romanization', 'a non-Latin script requires a romanization scheme, so the app is usable without reading the target script');
  }

  // ---- tiers -----------------------------------------------------------
  const T = doc.tiers ?? [];
  if (T.length !== 4) r.err('tiers', `expected exactly 4 tiers, found ${T.length}. The tier count is part of the invariant spine.`);
  const ids = T.map(t => t.id);
  if (ids.join(',') !== '0,1,2,3') r.err('tiers', `ids must be 0,1,2,3 in order — found [${ids.join(', ')}]`);
  let prev = 0;
  T.forEach((t, i) => {
    checkClaim(r, `tiers[${i}].size`, t.size, { sourced: true });
    const n = t?.size?.value;
    if (typeof n !== 'number' || !Number.isInteger(n) || n <= 0) {
      r.err(`tiers[${i}].size.value`, 'must be a positive integer');
    } else {
      if (n <= prev) r.err(`tiers[${i}].size.value`, `must increase — tier ${i} is ${n}, not more than tier ${i - 1}'s ${prev}`);
      if (n % 50 !== 0) r.warn(`tiers[${i}].size.value`, `${n} is not a round number. Round sizes are easier to reason about when the estimate is wrong.`);
      prev = n;
    }
    if (!ENUMS['tier.certainty'].includes(t.certainty)) r.err(`tiers[${i}].certainty`, `must be one of ${ENUMS['tier.certainty']}`);
    if (!t.intent) r.err(`tiers[${i}].intent`, 'required — a tier without a stated intent cannot be tested');
  });
  // Tier 0 is the one tier built to be safe. A low-confidence Tier 0 means the
  // whole bounded-loss argument is void, so it is an error rather than a warning.
  if (T[0]?.certainty !== 'high') {
    r.err('tiers[0].certainty', `must be "high". The entire bounded-loss design depends on Tier 0 being near-certain; found "${T[0]?.certainty}".`);
  }

  // ---- cost ------------------------------------------------------------
  checkClaim(r, 'cost.fsi_category', doc.cost?.fsi_category, { sourced: true });
  checkClaim(r, 'cost.fsi_hours_to_ilr3', doc.cost?.fsi_hours_to_ilr3, { sourced: true });
  checkClaim(r, 'cost.hours_to_floor', doc.cost?.hours_to_floor, { sourced: true });
  if (doc.cost?.fsi_category === null && !doc.cost?.fsi_category_note) {
    r.err('cost.fsi_category_note', 'fsi_category is null, so the reason must be recorded');
  }

  // ---- resources -------------------------------------------------------
  const now = Date.now();
  (doc.resources ?? []).forEach((res, i) => {
    if (!res.name) r.err(`resources[${i}].name`, 'required');
    if (!res.licence) r.err(`resources[${i}].licence`, 'required — a licence is what makes an item shippable');
    if (!/^\d{4}-\d{2}-\d{2}$/.test(res.checked ?? '')) {
      r.err(`resources[${i}].checked`, 'must be YYYY-MM-DD. An unchecked resource is an assumption.');
    } else {
      const age = (now - Date.parse(res.checked)) / 86400000;
      if (age > MAX_AGE_DAYS) {
        r.warn(`resources[${i}].checked`, `${Math.floor(age)} days old. Sources rot — Coursera killed audit mode mid-project and a plan still assumed it.`);
      }
    }
    checkClaim(r, `resources[${i}].covers`, res.covers, { sourced: true });
  });

  // ---- Tier 0 sourcing policy ------------------------------------------
  // Tier 0 may only draw on curated sources. If none exists for this language the
  // spec has to say so out loud, so the weakness is visible rather than inferred.
  const hasHumanReviewed = (doc.resources ?? []).some(x => x.class === 'curated' || x.class === 'authored');
  const declared = doc.tier0_sources;
  if (declared === 'unavailable') {
    if (hasHumanReviewed) r.err('tier0_sources', 'declared "unavailable" but a human-reviewed resource is listed');
    const gapMentioned = (doc.review?.known_gaps ?? []).some(g => /tier\s*0/i.test(g));
    if (!gapMentioned) {
      r.err('review.known_gaps', 'tier0_sources is "unavailable" but no known_gap records the consequence. A weaker Tier 0 must be declared, not inferred.');
    }
  } else if (!hasHumanReviewed && declared !== 'unavailable') {
    r.err('tier0_sources', 'no human-reviewed (authored or curated) resource is listed. Declare "unavailable" and record the gap, or add one. Tier 0 cannot be corpus-only without saying so.');
  }

  return r;
}

// ---- driver ------------------------------------------------------------
export function lintFile(path) {
  let doc;
  try {
    doc = YAML.parse(readFileSync(path, 'utf8'));
  } catch (e) {
    const r = new Report(path); r.err('parse', e.message); return r;
  }
  return lintSpec(doc, path);
}

export function lintDir(dir) {
  const files = readdirSync(dir)
    .filter(f => /\.(ya?ml)$/.test(f) && f !== 'SCHEMA.md')
    .map(f => join(dir, f))
    .filter(p => statSync(p).isFile());
  const reports = files.map(lintFile);
  return { files, reports };
}

if (import.meta.url === `file://${process.argv[1]}`) {
  const target = process.argv[2] ?? 'specs';
  const { files, reports } = lintDir(target);

  if (!files.length) {
    console.error(`no spec files found in ${target}/`);
    process.exit(1);
  }

  let bad = 0;
  for (const rep of reports) {
    const name = rep.file.split('/').pop();
    if (rep.errors.length) {
      bad++;
      console.error(`\x1b[31mFAIL\x1b[0m ${name}  ${rep.errors.length} error(s)`);
      for (const e of rep.errors) console.error(`     ${e}`);
    } else if (rep.warnings.length) {
      console.log(`\x1b[33mWARN\x1b[0m ${name}`);
    } else {
      console.log(`\x1b[32mPASS\x1b[0m ${name}`);
    }
    for (const w of rep.warnings) console.warn(`     ${w}`);
  }

  const errs = reports.reduce((a, r) => a + r.errors.length, 0);
  const warns = reports.reduce((a, r) => a + r.warnings.length, 0);
  console.log(`\n${files.length} spec(s), ${errs} error(s), ${warns} warning(s)`);
  process.exit(errs ? 1 : 0);
}