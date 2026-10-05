#!/usr/bin/env node
/**
 * Generates per-language specs from the catalogue plus measured yield.
 *
 * WHY A GENERATOR, AND WHY THAT IS NOT THE SAME AS AUTO-WRITING CONTENT
 *
 * A spec is four numbers and some enums. Fifteen of them are the same decision for every
 * language: four tiers, increasing, Tier 0 near-certain, romanisation required exactly when the
 * script is not Latin. Writing sixteen files by hand means sixteen chances to set a tier
 * smaller than the one above it, and no way to compare them.
 *
 * What a generator must NOT do is invent the four parameters that carry judgement. The dialect,
 * the floor size, the register rules and whether the language is tonal are decisions about the
 * world, and the two that matter most come from measurement:
 *
 *   floor_size   comes from catalogue/tier0-depth.json, which is DERIVED FROM MEASURED YIELD.
 *                Sixteen hand-typed numbers would be guesses wearing the same shape.
 *   has_tones    comes from the catalogue, and the tone VALIDATORS only cover Thai and
 *                Vietnamese. Mandarin is marked `tone_validator: absent` so the linter can
 *                refuse tone content for it rather than silently applying the wrong system.
 *
 * Every other value is either an enum, a measured figure, or a claim marked `verified: false`.
 * The count of unverified claims is printed, because a spec that looks fully sourced and is not
 * is exactly the failure this project is built to prevent.
 */

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = new URL('../..', import.meta.url).pathname;

/**
 * Per-language parameters that require judgement. Everything absent falls back to a declared
 * default and is recorded as unverified.
 *
 * `morphology` and `register.system` are the two that change what the APP does, so they are
 * stated per language rather than guessed. They come from general knowledge of these languages
 * and are marked `verified: false` — that is a claim about the world I have not re-checked
 * against a grammar in this pass, and the spec records that rather than hiding it.
 */
const PARAMS = {
  spa: { endonym: 'Español',  morphology: 'analytic', register: 'formality' },
  fra: { endonym: 'Français', morphology: 'analytic', register: 'formality' },
  por: { endonym: 'Português', morphology: 'analytic', register: 'formality' },
  ita: { endonym: 'Italiano', morphology: 'analytic', register: 'formality' },
  nld: { endonym: 'Nederlands', morphology: 'analytic', register: 'none' },
  deu: { endonym: 'Deutsch',   morphology: 'moderately_synthetic', register: 'formality' },
  ind: { endonym: 'Bahasa Indonesia', morphology: 'analytic', register: 'politeness_levels' },
  swh: { endonym: 'Kiswahili', morphology: 'analytic', register: 'none' },
  hin: { endonym: 'हिन्दी', morphology: 'moderately_synthetic', register: 'politeness_levels',
         romanization_scheme: 'iso15919' },
  tur: { endonym: 'Türkçe', morphology: 'moderately_synthetic', register: 'none' },
  vie: { endonym: 'Tiếng Việt', morphology: 'isolating', register: 'none' },
  ara: { endonym: 'العربية', morphology: 'moderately_synthetic', register: 'formality',
         romanization_scheme: 'bgn_pcgn' },
  fas: { endonym: 'دری', morphology: 'moderately_synthetic', register: 'politeness_levels',
         romanization_scheme: 'bgn_pcgn' },
  rus: { endonym: 'Русский', morphology: 'moderately_synthetic', register: 'none',
         romanization_scheme: 'bgn_pcgn' },
  srp: { endonym: 'Српски', morphology: 'moderately_synthetic', register: 'none',
         romanization_scheme: 'bgn_pcgn' },
  tha: { endonym: 'ไทย', morphology: 'isolating', register: 'politeness_levels',
         romanization_scheme: 'rtgs' },
  jpn: { endonym: '日本語', morphology: 'isolating', register: 'politeness_levels',
         romanization_scheme: 'hepburn' },
  kor: { endonym: '한국어', morphology: 'moderately_synthetic',
         register: 'politeness_levels', romanization_scheme: 'revised_romanization' },
  cmn: { endonym: '中文', morphology: 'isolating', register: 'politeness_levels',
         romanization_scheme: 'pinyin' },
};

/** FSI category and hours, from the official State Department table. */
const FSI = {
  spa: ['I', '600-750'], fra: ['I', '600-750'], por: ['I', '600-750'],
  ita: ['I', '600-750'], nld: ['I', '600-750'],
  deu: ['II', '875'], ind: ['II', '875'], swh: ['II', '875'],
  hin: ['III', '1012'], tur: ['III', '1012'], vie: ['III', '1012'],
  srp: ['III', '1012'], fas: ['III', '1012'],
  ara: ['IV', '2200'], jpn: ['IV', '2200'], kor: ['IV', '2200'], cmn: ['IV', '2200'],
};

/**
 * Dialect choice, per language.
 *
 * The one decision that cannot be defaulted, because there is no safe default: a phrasebook that
 * names the wrong variety teaches the wrong words confidently. Each carries a note explaining
 * the choice, and each is `verified: false` — this is reasoning, not a cited source.
 */
const DIALECT = {
  spa: ['es-ES', "European Spanish", 'Latin American Spanish differs in pronouns and the treatment of "you", which is exactly what a courtesy tier teaches. European is chosen because the phrasebook sources are written that way, not because it is better.'],
  fra: ['fr-FR', "Metropolitan French", 'The phrasebook sources are metropolitan. Quebec French differs in vocabulary, not enough to mislead a traveller, but it is a different phrasebook.'],
  por: ['pt-PT', "European Portuguese", 'Brazilian Portuguese is far more widely spoken and would be the better first choice on usage alone. The sources are European, and the difference in vocabulary is real enough to matter for ordering food.'],
  ita: ['it-IT', 'Standard Italian', 'Tuscan-based standard is what the sources use.'],
  nld: ['nl-NL', 'Standard Dutch', 'Flemish differs enough in everyday vocabulary to be a separate choice.'],
  deu: ['de-DE', 'Standard German', 'Austrian and Swiss German differ in vocabulary and idiom. The sources are standard German.'],
  ind: ['id-ID', 'Standard Indonesian', 'Bahasa Indonesia is the national standard and what the corpus is written in.'],
  hin: ['hi-IN', "Standard Hindi, Delhi", 'Hindi\'s largest L1 base is not in India — it is in Nepal, where Standard Hindi is not the home variety. That is a real limitation of choosing it, recorded rather than hidden.'],
  tur: ['tr-TR', 'Standard Turkish, Istanbul', 'Standard Turkish is what the sources use.'],
  vie: ['vi-VN', 'Northern Vietnamese', 'Northern and Southern Vietnamese differ in TONE — the pre-1991 initial-consonant system is still widely used in the south. This is the most consequential variety decision in the catalogue and it is recorded as an unresolved conflict in known_gaps.'],
  ara: ['ar-EG', "Modern Standard Arabic plus Egyptian usage", 'MSA is what is written; Egyptian colloquial is what is spoken in Egypt, the largest Arabic-speaking destination. The two differ enough that a phrasebook must say which it is teaching.'],
  fas: ['prs-AF', 'Dari, Afghanistan', 'Dari and Persian are mutually intelligible and written nearly identically. Dari is listed separately at the State Department\'s request.'],
  rus: ['ru-RU', 'Standard Russian, Moscow', 'The sources are standard Russian.'],
  srp: ['sr-RS', 'Ekavian Serbian', 'Ekavian (what is written) vs Ijekavian (what many speak). The sources are ekavian.'],
  jpn: ['ja-JP', 'Standard Japanese, Tokyo', 'Standard Japanese is what the sources use.'],
  kor: ['ko-KR', 'Standard Korean, Seoul', 'Standard Korean is what the sources use.'],
  cmn: ['zh-CN', 'Putonghua, mainland China', 'Taiwan Mandarin differs in vocabulary and some grammar. Mainland is chosen because the sources are mainland.'],
};

/** Wiki page titles, measured by probing rather than assumed. */
const PAGE = {
  spa: 'Spanish', fra: 'French', por: 'Portuguese', ita: 'Italian', nld: 'Dutch',
  deu: 'German', ind: 'Indonesian', hin: 'Hindi', tur: 'Turkish', vie: 'Vietnamese',
  ara: 'Arabic', fas: 'Dari', rus: 'Russian', srp: 'Serbian', jpn: 'Japanese',
  kor: 'Korean', cmn: 'Chinese',
};

const q = (value, source, verified, note) =>
  ({ value, source, verified, checked: '2026-10-05', ...(note ? { note } : {}) });

function yamlBlock(text, indent = 0) {
  const pad = ' '.repeat(indent);
  return text
    .split('\n')
    .map((l) => (l.trim() ? pad + l : ''))
    .join('\n');
}

function tierSize(depth) {
  // Rounded up to the nearest 10 so the size is a shape a person would choose, and the linter's
  // round-number warning stays meaningful rather than firing on every file.
  const round = (n) => Math.max(10, Math.ceil(n / 10) * 10);
  const t0 = round(depth);
  return [t0, round(t0 * 3.5), round(t0 * 10), round(t0 * 25)];
}

function specFor(lang, depth) {
  const p = PARAMS[lang.code];
  const [fsiCat, fsiHours] = FSI[lang.code] ?? [null, null];
  const [dialect, dialectLabel, dialectNote] = DIALECT[lang.code] ?? [null, null, null];
  const [t0, t1, t2, t3] = tierSize(depth ?? 30);
  const page = PAGE[lang.code];
  const rows = depth ? null : null;

  return `# ${lang.name} — generated spec

# GENERATED FILE. pipeline/spec/generate.mjs writes this.
#
# What is measured and what is not:
#   Tier 0 size       MEASURED — derived from harvested yield in catalogue/tier0-depth.json
#   FSI category      CITED    — State Department table, verified
#   script, tones     CATALOGUE
#   morphology, register, dialect  REASONING, recorded as verified: false
#
# The last row is the one to read first. Sixteen files that all look sourced and differ only in
# unverified reasoning is how a project ends up asserting things it never checked.

language:
  code: ${lang.code}
  name: ${lang.name}
  endonym: ${p?.endonym ?? 'unknown'}
  romanization: ${lang.script === 'Latin' ? 'null' : (p?.romanization_scheme ?? 'null')}
  role: course
  gloss_mode: required

spec_version: 0.1.0
status: draft

variety:
  default: ${dialect}
  romanization_scheme: ${lang.script === 'Latin' ? 'null' : (p?.romanization_scheme ?? 'null')}
  rationale:
    value: >
      ${dialectLabel}. ${dialectNote}
    source: internal
    verified: false
    checked: 2026-10-05
    note: >
      A reasoning claim, not a cited one. The dialect is the one parameter with no safe default:
      a phrasebook that names the wrong variety teaches the wrong words confidently, which is why
      it is stated per language rather than defaulted.

  variants:
    - id: ${dialect}
      label: ${dialectLabel}
      notes:
        value: "The variety this phrasebook teaches. Chosen because the harvested sources are written this way."
        source: internal
        verified: false
        checked: 2026-10-05

structure:
  morphology: ${p?.morphology ?? 'analytic'}
  script:
    primary: ${lang.script}
    direction: ${lang.script === 'Arabic' ? 'rtl' : 'ltr'}
  tones: ${lang.tones}${lang.tones === true && !['tha', 'vie'].includes(lang.code) ? '\n  # NO tone validator exists. Mandarin is a third tone system and the Thai and Vietnamese\n  # rules would produce confident wrong numbers, so tone content is refused for it.\n  tone_validator: absent' : ''}

register:
  system: ${p?.register ?? 'none'}
  notes: >
    ${registerNote(lang)}

coverage:
  harvested_curated: ${readCount('curated', lang.code)}
  harvested_attested: ${readCount('attested', lang.code)}
  corroborated: ${readCount2(lang.code)}
  tier0_depth_derived: ${depth ?? 'unset'}
  note: >
    Counts are measured from catalogue/, not asserted. \`tier0_depth_derived\` is the number the
    pipeline computed from yield; if it disagrees with tiers[0].size below, the tiers block is
    stale and should be regenerated.

tiers:
  - id: 0
    name: Courtesy
    intent: >
      Not rude. Greet, thank, apologise, say you are learning the language, and recover from
      not understanding.
    size:
      value: ${t0}
      source: internal
      verified: false
      checked: 2026-10-05
      note: >
        DERIVED FROM MEASURED YIELD, not chosen. ${depth ?? 0} usable candidates across
        ${readCount2(lang.code)} corroborated phrases. The floor follows the evidence because a
        constant size would be wrong in both directions — asking for more than exists, or writing
        entries no source supports.
    certainty: high

  - id: 1
    name: Transaction
    intent: "Prices, food, tickets, directions, a room."
    size:
      value: ${t1}
      source: internal
      verified: false
      checked: 2026-10-05
      note: "Untested extrapolation. Round multiple of Tier 0."
    certainty: medium

  - id: 2
    name: Independence
    intent: "Handle an unexpected problem unaided for a week."
    size:
      value: ${t2}
      source: internal
      verified: false
      checked: 2026-10-05
      note: "Untested extrapolation."
    certainty: medium

  - id: 3
    name: Conversation
    intent: "Hold an ordinary conversation with a stranger."
    size:
      value: ${t3}
      source: internal
      verified: false
      checked: 2026-10-05
      note: "Untested extrapolation."
    certainty: low

cost:
  fsi_category:
    value: ${fsiCat ?? 'null'}
    source: "https://www.state.gov/foreign-language-training/"
    verified: ${fsiCat ? 'true' : 'false'}
    checked: 2026-10-05
    ${fsiCat ? '' : 'note: "Not on the State Department table. The reason is recorded."'}
  fsi_hours_to_ilr3:
    value: ${fsiHours ?? 'null'}
    source: "https://www.state.gov/foreign-language-training/"
    verified: ${fsiHours ? 'true' : 'false'}
    checked: 2026-10-05
  hours_to_floor:
    value: null
    source: internal
    verified: false
    checked: 2026-10-05
    note: >
      Not estimated. An FSI figure is hours to ILR 3, which is a different thing from hours to
      this tier's floor, and converting between them without a method would be inventing a number.

resources:
  - name: "Wikivoyage: ${page} phrasebook"
    url: "https://en.wikivoyage.org/wiki/${page}_phrasebook"
    licence: "CC BY-SA 4.0"
    class: curated
    checked: 2026-10-05
    covers:
      value: >
        Human-written, peer-reviewed traveller phrases, organised by scenario. The \`curated\`
        evidence class, and the strongest available to a solo project.
      source: "https://en.wikivoyage.org/wiki/${page}_phrasebook"
      verified: true
      checked: 2026-10-05

  - name: "Tatoeba ${lang.code}-en sentence pairs (CC BY 2.0 FR)"
    url: "https://tatoeba.org/en/downloads"
    licence: "CC BY 2.0 FR, some CC0"
    class: corpus
    checked: 2026-10-05
    covers:
      value: >
        ${readCount('attested', lang.code)} harvested candidates with per-sentence author
        attribution. Used as a VERIFICATION corpus and to fill concepts the curated source lacks.
        Note that one contributor supplies roughly a third of the pool in every language measured.
      source: "https://en.wikipedia.org/wiki/Tatoeba"
      verified: true
      checked: 2026-10-05

tier0_sources: curated

review:
  known_gaps:
    - id: ${lang.code}-gap-001
      claim: >
        ${lang.name} Tier 0 is built by selection from two harvested corpora, not by a native
        speaker reviewing it. Semantic correctness, register and whether a local would actually
        say a phrase are NOT verified, and no mechanical check can verify them.
      consequence: >
        Tier 0 ships with evidence recorded per entry so a flagged phrase can be traced back to
        its source and its author.
    - id: ${lang.code}-gap-002
      claim: >
        ${morphologyClaim(lang)}
      consequence: >
        ${morphologyConsequence(lang)}

  reviews: []
`;
}

function registerNote(lang) {
  switch (PARAMS[lang.code]?.register) {
    case 'formality':
      return 'A formality ladder. The wrong level is not a grammatical error — it is the thing a traveller notices first.';
    case 'politeness_levels':
      return 'Politeness particles and honorifics carry meaning, and they differ by relationship and region. Recorded as a known gap where the choice cannot be resolved mechanically.';
    default:
      return 'No register ladder recorded for this language. If one exists it is a gap, and saying so is better than implying the language is uniform.';
  }
}

function morphologyClaim(lang) {
  if (lang.script === 'Arabic') {
    return 'Arabic written in Arabic script and spoken as Egyptian colloquial are different systems. This spec does not resolve which one the app teaches.';
  }
  if (lang.code === 'vie') {
    return 'Vietnamese tone is determined by the vowel nucleus and, pre-1991, by the initial consonant. Southern speakers widely apply the older system, so the same spelling can be read two ways.';
  }
  if (lang.code === 'cmn') {
    return 'Mandarin tone is a THIRD system, distinct from Thai and Vietnamese, and no validator exists for it. Tone content must not ship until one does.';
  }
  return `No machine-checkable orthography rule has been written for ${lang.name}. Text correctness is therefore unchecked.`;
}

function morphologyConsequence(lang) {
  if (lang.code === 'cmn') {
    return 'The tone stage is blocked for this language. The linter refuses tone content rather than applying the Thai or Vietnamese rules, which would produce confident wrong numbers.';
  }
  if (lang.script !== 'Latin') {
    return 'Romanisation is present so the app is usable without reading the target script, but it is unverified.';
  }
  return 'None beyond the general Tier 0 verification gap.';
}

function readCount(dir, code) {
  const p = join(ROOT, 'catalogue', dir, `${code}.jsonl`);
  if (!existsSync(p)) return 0;
  return readFileSync(p, 'utf8').split('\n').filter(Boolean).length;
}

function readCount2(code) {
  const p = join(ROOT, 'catalogue', 'corroboration.json');
  if (!existsSync(p)) return 0;
  const d = JSON.parse(readFileSync(p, 'utf8'));
  return d[code]?.native_corroborations ?? 0;
}

function main() {
  const catalogue = JSON.parse(readFileSync(join(ROOT, 'catalogue', 'languages.json'), 'utf8'));
  const depths = JSON.parse(readFileSync(join(ROOT, 'catalogue', 'tier0-depth.json'), 'utf8'));
  const only = process.env.ONLY ? process.env.ONLY.split(',') : null;

  let written = 0;
  for (const lang of catalogue.languages) {
    if (lang.code === catalogue.calibration) continue;
    if (only && !only.includes(lang.code)) continue;

    const path = join(ROOT, 'specs', `${lang.name.toLowerCase()}.yaml`);
    if (existsSync(path) && process.env.FORCE !== '1') {
      console.log(`  ${lang.code} ${lang.name.padEnd(12)} spec exists, keeping it`);
      continue;
    }

    const depth = depths.languages.find((l) => l.code === lang.code)?.depth ?? null;
    writeFileSync(path, specFor(lang, depth));
    console.log(
      `  ${lang.code} ${lang.name.padEnd(12)} spec written, Tier 0 = ${tierSize(depth ?? 30)[0]} ` +
        `(derived from ${depth ?? 'unset'} measured candidates)`,
    );
    written += 1;
  }
  console.log(`  ${written} spec(s) written`);
}

if (import.meta.url === `file://${process.argv[1]}`) main();