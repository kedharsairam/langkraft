#!/usr/bin/env node
/**
 * Builds Tier 0 content records from the harvested corpora.
 *
 * WHAT THIS IS
 *
 * The harvester produces candidates. The selector ranks them. Neither produces content, because
 * content needs two things a filter cannot supply: a judgement about which of several correct
 * phrasings is the one you would actually use, and prose that tells the reader why it is there.
 *
 * This builds records. It does not decide whether a phrase is idiomatic -- nothing here can --
 * and it does not pretend otherwise. What it does do is make every editorial decision explicit
 * and recorded, so the ones that are wrong are findable and correctable rather than invisible.
 *
 * WHY CURATED LEADS AND ATTESTED FILLS
 *
 * Curated (Wikivoyage) is human-written, peer-reviewed and organised by traveller scenario.
 * Attested (Tatoeba) is larger, flatter, and dominated by a few prolific translators. So the
 * curated phrase is preferred for a concept, and attested is used only where curated has nothing
 * -- which is a real gap: 13 concepts have no attestation anywhere, and curated covers most of
 * them while Tatoeba covers none.
 *
 * WHERE BOTH EXIST, BOTH ARE RECORDED
 *
 * That is the corroborated case, and it is the only route to the strongest confidence level:
 * two people who do not know each other wrote the same target-language string. The second source
 * is kept on the record rather than discarded, because it is the evidence.
 *
 * THE `why` LINE IS THE PRODUCT
 *
 * It renders verbatim on the phone, on the entry the reader is looking at. A template that says
 * "Concept: price. Group: money" is not prose a learner can use, and the linter rejects pipeline
 * vocabulary in these fields precisely because that failure shipped once before. So the prose is
 * written per scenario, in second person, saying when you would need the phrase -- which is
 * knowledge the phrasebook exists to transfer, not metadata about the build.
 *
 * DIRECTION SPLIT
 *
 * A learner is spoken to faster than he speaks, so the floor is weighted toward `understand` --
 * except courtesy, which is the one area where being able to SPEAK is what keeps an interaction
 * from collapsing. The research behind the 2:1 production bias is noted in lint.mjs; this applies
 * it per scenario rather than globally.
 */

import { readFileSync, writeFileSync, existsSync } from 'node:fs';
import { createRequire } from 'node:module';
import { join } from 'node:path';

const ROOT = new URL('../..', import.meta.url).pathname;
const CAT = join(ROOT, 'catalogue');
const CONTENT = join(ROOT, 'content');

/** Our 12 domains, from content/SCHEMA.json. */
const DOMAIN = {
  greeting: 1, courtesy: 1, register: 1, people: 1, basics: 1, travel: 1,
  numbers: 2, time: 2, what_time: 2,
  price: 3, money: 3,
  food: 4,
  hotel: 5,
  transport: 6, ticket: 6, address: 7, directions: 7,
  shopping: 8,
  doctor: 9, police: 10, emergency: 10, problem: 10, lost: 10,
  understand: 12, survival: 12,
};

/**
 * Whether a romanisation is required for this language, from the spec's own script declaration.
 *
 * The content linter makes the same decision from the same field, and the two have to agree: the
 * generator drops what the linter would reject so a build is never stopped by a row the generator
 * chose. Two implementations that merely look alike will disagree somewhere, and the disagreement
 * surfaces as a red build rather than as an explanation.
 */
function needsRomanisation(spec) {
  return spec?.structure?.script?.primary !== 'Latin';
}

/**
 * True when an English gloss is itself the phrase, so a romanisation would add nothing.
 *
 * A sign the traveller READS — OPEN, TOILET — is spelled out in the caption already. Mirrors
 * `isReadableAsIs` in content/lint.mjs.
 */
function readableAsIs(english) {
  const s = english.trim();
  if (!s || s.length > 40) return false;
  if (s.includes('?') || /\b(?:the|is|are|do|you|this|that|it|not)\b/i.test(s)) return false;
  return /^[A-Z][A-Z\s/&.'-]*$/.test(s);
}

/**
 * Why this phrase is in the floor, and how it is used.
 *
 * Written as advice to a traveller, not as a description of the build. `why` renders verbatim
 * in the app, so this text is product copy and is held to that standard: second person, present
 * tense, no source names, no pipeline vocabulary, no entry counts.
 */
const SCENARIO = {
  greeting: {
    direction: 'say',
    why: 'How an encounter opens. Greeting before anything else is what makes the rest work.',
    caution: 'Greet before asking. Skipping straight to a question reads as rude even when your language is perfect.',
  },
  courtesy: {
    direction: 'say',
    why: 'The politeness that keeps an interaction friendly. Cheap to learn, and it buys a lot.',
    caution: 'Match the formality to who you are speaking to. Being too casual with someone older or in charge costs you.',
  },
  register: {
    direction: 'say',
    why: 'The difference between a phrase that fits the situation and one that marks you out as a foreigner.',
    caution: 'This is where a translation stops being usable. The same words are correct and inappropriate depending on who is speaking.',
  },
  understand: {
    direction: 'understand',
    why: 'What people say back to you. Recognising it is often more useful than producing it.',
  },
  survival: {
    direction: 'say',
    why: 'For when the conversation has stopped working and you need it to start again.',
  },
  price: {
    direction: 'say',
    why: 'Asking what something costs, and what you think of the price. Every transaction needs both.',
    caution: 'Prices are often negotiable in a market and fixed in a shop. Asking what you think of it reads differently in each.',
  },
  numbers: {
    // Split deliberately. Producing a count is easy once you have the words; UNDERSTANDING a
    // count is what stops you agreeing to the wrong price. The research behind the 2:1
    // production bias is real, but it does not mean every scenario should be production-heavy,
    // and a tier of pure production would never tell the reader what they just heard.
    direction: 'understand',
    why: 'Numbers, and what is being counted. Most of this is recognising a figure that has just been said to you.',
  },
  time: {
    direction: 'say',
    why: 'Telling the time and making a plan with someone. Both directions matter; neither is hard once you have it.',
  },
  what_time: {
    direction: 'understand',
    why: 'Answering when someone asks you something, which arrives sooner than you expect.',
  },
  food: {
    direction: 'say',
    why: 'Ordering, saying what you cannot eat, and understanding what arrives.',
    caution: 'Saying you cannot eat something is more reliably understood than describing what you can eat. Use the negative form.',
  },
  hotel: {
    direction: 'say',
    why: 'Arriving, asking for what you need, and the small problems that come with staying somewhere.',
  },
  transport: {
    direction: 'say',
    why: 'Getting somewhere you have not been before, which is most of what travel is.',
    caution: 'Directions assume you can hear the name of a place afterwards. Ask for it to be repeated, or written down.',
  },
  ticket: {
    direction: 'say',
    why: 'Buying passage and asking about departures.',
  },
  directions: {
    direction: 'say',
    why: 'Asking where something is and being understood when you are the one asked.',
  },
  address: {
    direction: 'say',
    why: 'Reading an address aloud or writing it down when you have no paper.',
  },
  shopping: {
    direction: 'say',
    why: 'Prices, sizes, and declining politely when you are not buying.',
    caution: 'Deciding not to buy is a normal part of shopping and saying so early is easier than walking away.',
  },
  doctor: {
    direction: 'say',
    why: 'Saying what is wrong when you cannot explain it in the local language.',
    caution: 'Pointing at where it hurts works when words do not. Combine the two.',
  },
  police: {
    direction: 'understand',
    why: 'Knowing what is being said to you when something has gone wrong.',
  },
  emergency: {
    direction: 'say',
    why: 'For the situation where politeness is not the first priority.',
  },
  problem: {
    direction: 'say',
    why: 'Saying that something is wrong, and understanding when it is wrong with you.',
  },
  lost: {
    direction: 'say',
    why: 'Losing something and explaining what it was.',
  },
  people: {
    direction: 'understand',
    why: 'How people refer to each other. Wrong here reads as either rude or comic.',
  },
  basics: {
    direction: 'say',
    why: 'The words that carry no meaning on their own but hold an exchange together.',
  },
  travel: {
    direction: 'say',
    why: 'Describing where you are and what you are doing, which is often the whole conversation.',
  },
};

/**
 * Signs, on doors and shopfronts.
 *
 * These are not phrases. Nobody says "PUSH" to a door -- they read it, and they need to
 * understand it rather than produce it. The Wikivoyage sign lists were harvested into the same
 * `basics` bucket as greetings, so without this they came out as `say` items with greeting
 * advice attached, and a tier that opens with "say CLOSED to a shopkeeper" is teaching the
 * wrong thing.
 */
const SIGNS = {
  direction: 'understand',
  why: 'Signs on doors and shopfronts. You read these rather than say them, and misreading one puts you on the wrong side of a door.',
  caution: null,
};

/** Raised, echoed and printed English. Reads correctly; almost never something you would say. */
const SIGN_WORDS = new RegExp(
  '^\\s*(' + [
    'open', 'closed', 'entrance', 'exit', 'push', 'pull', 'toilet', 'wc',
    'men', 'women', 'forbidden', 'no entry', 'entry', 'in', 'out', 'stop',
    'cash only', 'visa', 'mastercard', 'free wi-?fi', 'no smoking',
    'check', 'check ?in', 'check ?out', 'stairs', 'lift', 'elevator',
    'taxi', 'bar', 'restaurant', 'hotel', 'information', 'emergency',
    'exit only', 'way out', 'way in', 'no entry',
  ].join('|') + ')\\b',
  'i',
);

const FALLBACK = {
  direction: 'understand',
  why: 'Part of the everyday vocabulary you will hear whether or not you are looking for it.',
};

function load(dir, code) {
  const p = join(CAT, dir, `${code}.jsonl`);
  if (!existsSync(p)) return [];
  return readFileSync(p, 'utf8').split('\n').filter(Boolean).map((l) => JSON.parse(l));
}

/** Removes trailing full stops so entries read as phrases, not sentences. */
function tidy(s) {
  return (s ?? '').replace(/\s*[.。]+\s*$/, '').trim();
}

function loadCorroboration() {
  const p = join(CAT, 'corroboration.json');
  return existsSync(p) ? JSON.parse(readFileSync(p, 'utf8')) : {};
}

/**
 * The key a phrase is compared by, for matching corroboration against a harvested row.
 *
 * `corroboration.json` stores the phrase exactly as the corroborating source wrote it — `Guten
 * Tag.` — while `tidy()` has already stripped the sentence-ending full stop from the phrase that
 * goes into the entry. Exact string equality therefore failed on 59 shipped phrases, every one of
 * them German, and the evidence was silently dropped.
 *
 * This is not bookkeeping. Confidence is DERIVED from the corroborating-sentences field, so a
 * phrase that loses its evidence is reported to the reader as less well attested than it is —
 * 5.6% of the shipped tier 0, understated, with nothing in the output to say so.
 *
 * Normalising is only safe because it is lossy in the direction that cannot invent a match:
 * whitespace collapses to one space and trailing sentence punctuation is removed, so
 * `Guten Tag.` and `Guten Tag` agree while two genuinely different phrases still do not.
 */
function corroborationKey(phrase) {
  return String(phrase ?? '')
    .replace(/\s+/g, ' ')
    .trim()
    .replace(/[.。．!?！？,،、;；:：]+$/u, '');
}

/**
 * Picks a romanisation for an entry.
 *
 * The pronunciation column is preferred over the native script ONLY where the learner cannot
 * read that script -- which is every language except the Latin-script ones. Null for Latin
 * scripts, matching the spec's romanization: null, and the schema's rule that a romanisation
 * must never duplicate the native text.
 */
function romanised(native, pronunciation, spec, romanizedOnly = false) {
  const latin = /Latin/i.test(spec?.structure?.script?.primary ?? '');

  // The phrase arrived in Latin script for a language that has its own script. That is a
  // ROMANISATION, not a native text -- 240 of 336 Tamil rows and 124 of 397 Arabic rows are
  // like this.
  //
  // The entry cannot be written from such a row. `text_native` is required by the schema, and
  // `text_romanized` may never duplicate it, so a phrase available only in romanisation has no
  // valid home. It is skipped and counted as a gap rather than filed under whichever column
  // happened to validate -- putting a romanisation in `text_native` would show a learner Latin
  // letters where they expect their own script, and it could never be checked by an
  // orthography validator.
  if (romanizedOnly && !latin) {
    return { skip: true, native: null, romanized: tidy(native) };
  }

  if (latin) return { skip: false, native, romanized: null };
  const pron = tidy(pronunciation);
  return {
    skip: false,
    native,
    romanized: pron && pron !== tidy(native) ? pron : null,
  };
}

/**
 * Chooses the scenario prose for a row.
 *
 * The page-level concept is usually right but not always specific enough. `Habari za asubuhi?` is
 * filed on the Indonesian/Swahili pages under `basics` because it sits in a general vocabulary
 * section, yet it is plainly a greeting, and the greeting advice -- greet before asking anything
 * -- is what a learner needs there. Reading it as `basics` gave it "words that hold an exchange
 * together", which is true of everything and therefore useless.
 *
 * So the domain decides when the concept is too coarse, and the English text is checked for the
 * small number of cases where that matters.
 */
/**
 * Whether a row is a counting suffix rather than a phrase.
 *
 * The discriminator is the ENGLISH side naming a category of objects, not the shape of the
 * native text. Shape alone would misjudge real phrases; the category gloss is what makes
 * `匹 -hiki` a counter and `お願いします` a phrase.
 */
function isCounterSuffixRow(row) {
  const native = tidy(row.native ?? '');
  const english = tidy(row.english ?? '');
  if (!/^[\u4e00-\u9fff]{1,2}\s/.test(native)) return false;
  if (!/\b(objects?|animals?|things?|papers?|tickets?|bottles?|pens?|persons?|pieces?)\b/i.test(english)) {
    return false;
  }
  // A real phrase is a question or a request; a counter is a noun with readings.
  return !/\b(how|what|where|this|that|do|is|are|please|thank)\b/i.test(english);
}

function scenarioFor(row, domain) {
  const sc = SCENARIO[row.concept];
  const en = `${row.english ?? ''} ${row.native ?? ''}`.toLowerCase();

  // A sign is never produced. Checked before the domain rules because the sign lists landed in
  // the same bucket as greetings and would otherwise inherit greeting advice.
  const native = tidy(row.native ?? '');
  const looksLikeSign =
    SIGN_WORDS.test(tidy(row.english ?? '')) ||
    /^\s*(buka|tutup|masuk|keluar|dorong|tarik|dila[ra]rang|pria|wanita)\b/i.test(native) ||
    // ALL CAPS with no lowercase letters is how sign text is written on these pages.
    (native.length > 1 && native === native.toUpperCase() && /^\p{Lu}/u.test(native));
  // Any domain, not just 1. Restricting it to domain 1 was wrong: the Mandarin page files its
  // signs under directions (domain 7), so `入口 [rùkǒu]` came out as a `say` entry with
  // navigation advice and no romanisation. It is a sign wherever the page put it.
  if (looksLikeSign) return SIGNS;

  if (domain === 1) {
    if (/\b(hello|hi\b|good (morning|afternoon|evening)|how are you|welcome|nice to meet)\b/.test(en)) {
      return SCENARIO.greeting;
    }
    if (/\b(thank|thanks|please|sorry|excuse me)\b/.test(en)) return SCENARIO.courtesy;
  }
  if (domain === 2 && /\b(what time|o'?clock)\b/.test(en)) return SCENARIO.what_time;
  if (domain === 3 && /\b(cash|card|receipt|change|expensive|cheap|bargain)\b/.test(en)) {
    return SCENARIO.price;
  }
  if (domain === 4 && /\b(spicy|vegetarian|allerg|meat|hungry|thirsty|water|beer)\b/.test(en)) {
    return SCENARIO.food;
  }
  if (domain === 5 && /\b(towel|wifi|hot water|check ?out)\b/.test(en)) return SCENARIO.hotel;

  return sc ?? FALLBACK;
}

/**
 * The resource id to record as an entry's source.
 *
 * Read from the spec's own `resources` block rather than constructed here. The Thai spec
 * declares `"Wikivoyage: Thai phrasebook"` and the generator was emitting `"Wikivoyage Thai
 * phrasebook"` -- one character apart, so it looked right and failed the licence check on all
 * 37 Thai entries. A source id is a licence obligation, not a label: if it is not declared in
 * the spec, nobody can check what was actually reused.
 *
 * Matching is by URL where the spec records one, since that is what identifies the resource
 * rather than how somebody punctuated its name.
 */
function sourceIdFor(spec, code) {
  const resources = spec?.resources ?? [];
  const byUrl = resources.find((r) => /wikivoyage/i.test(r.url ?? '') || /wikivoyage/i.test(r.name ?? ''));
  if (byUrl) return byUrl.name;
  const byLang = resources.find((r) => /wikivoyage/i.test(r.name ?? '') && new RegExp(code, 'i').test(r.name ?? ''));
  return byLang?.name ?? null;
}

export function buildLanguage(lang, spec, corroboration) {
  const curated = load('curated', lang.code);
  const attested = load('attested', lang.code);
  if (!curated.length && !attested.length) return { entries: [], stats: {} };

  const depth = Number(process.env[`DEPTH_${lang.code.toUpperCase()}`] || 0);
  const corr = corroboration[lang.code]?.pairs ?? [];

  // Resolved once, and REFUSED if the spec does not declare it. Building entries against a
  // source the spec has not declared produces content that cannot be licence-checked, which is
  // the one thing the provenance block exists to prevent.
  const sourceId = sourceIdFor(spec, lang.code);
  if (!sourceId) {
    return {
      entries: [],
      stats: {},
      error:
        `${lang.code}: no Wikivoyage resource is declared in the spec's resources block. ` +
        `Add it with its url, licence and checked date before writing content from it.`,
    };
  }

  // Curated leads, by scenario. Within a scenario, corroborated phrases come first: they are
  // the ones two independent sources agree on, which is the strongest evidence available here.
  const ordered = [...curated].sort((a, b) => {
    const ca = corr.some((p) => corroborationKey(p.native) === corroborationKey(a.native)) ? 1 : 0;
    const cb = corr.some((p) => corroborationKey(p.native) === corroborationKey(b.native)) ? 1 : 0;
    if (ca !== cb) return cb - ca;
    return 0;
  });

  // Fill each domain to its share of the tier. Grouping by domain is what makes a tier a FLOOR
  // rather than a list: a learner can reach the toilet, buy lunch, and get into a taxi, rather
  // than reading 40 ways to ask the price of one thing.
  // Cap per domain, but not evenly. Eight numbers is more than a courtesy floor needs and eight
  // greetings is about right, so the cap scales with how much of a floor that domain actually
  // is. Numbers were taking a third of the tier while being the domain a learner most often
  // only needs to RECOGNISE.
  const DOMAIN_CAP = { 1: 8, 2: 6, 3: 6, 4: 7, 5: 6, 6: 7, 7: 6, 8: 6, 9: 4, 10: 5, 11: 3, 12: 4 };

  const byDomain = new Map();
  // Global across domains, because one page puts the same phrase in two scenarios -- "excuse me"
  // appears under courtesy AND under problems on several. Filling per-domain independently let
  // the second copy through, and the linter caught seven duplicates as a result.
  const claimed = new Set();
  let droppedCounters = 0;
  for (const row of ordered) {
    const d = DOMAIN[row.concept];
    if (!d) continue;
    const key = tidy(row.native);
    if (!key || claimed.has(key)) continue;

    // Counting suffixes are grammar, not speech. Japanese `個 -ko` and `匹 -hiki, -biki, -piki`
    // are glossed by the page as CATEGORIES of object -- "small roundish objects", "small
    // animals" -- which is the signal: a phrasebook entry is something a person says, and a
    // traveller says "one ticket", not the counter suffix for small animals.
    //
    // Dropped rather than repaired. No romanisation makes this phrasebook content, and it was
    // five blocked entries because the page carries no reading for them at all.
    if (isCounterSuffixRow(row)) { droppedCounters += 1; continue; }

    if (!byDomain.has(d)) byDomain.set(d, []);
    const bucket = byDomain.get(d);
    if (bucket.length >= (DOMAIN_CAP[d] ?? 6)) continue;
    claimed.add(key);
    bucket.push(row);
  }

  const entries = [];
  // Duplicate native text is a real failure the linter catches, and it happens here because a
  // phrase can sit in two scenarios on the same page -- ขอโทษ "excuse me" appears under both
  // courtesy and problems. Shipping it twice teaches the reader one phrase has two meanings.
  const seenNative = new Set();
  let skippedRomanizedOnly = 0;
  let skippedNoReading = 0;
  let n = 0;
  for (const [domain, rows] of [...byDomain.entries()].sort((a, b) => a[0] - b[0])) {
    for (const row of rows) {
      if (depth && entries.length >= depth) break;
      n += 1;
      const sc = scenarioFor(row, domain);
      const native = tidy(row.native);
      const english = tidy(row.english);
      if (!native || !english) continue;

      const match = corr.find((p) => corroborationKey(p.native) === corroborationKey(native));
      const rom = romanised(native, row.pronunciation, spec, row.romanized_only === true);
      // Skipped rather than filed under the wrong column. See romanised().
      if (rom.skip) { skippedRomanizedOnly += 1; continue; }

      /**
       * A phrase the reader cannot read and cannot sound out is not Tier 0 content.
       *
       * The generator used to emit it and let the content linter refuse the build. That works, but
       * it means every build stops on content the generator itself chose, so one unusable Arabic
       * row ("How are you?", no reading anywhere on the page) blocks all twenty languages — and
       * the entry is not fixable by editing the file, because the next run regenerates it.
       *
       * Dropping it here puts the failure where it belongs. The gate MIRRORS the content linter's
       * exactly, including its narrow exemption, because a second rule that merely resembles the
       * first is a third thing: it would disagree with the linter on some row and the disagreement
       * would be discovered as a red build rather than understood here.
       */
      if (needsRomanisation(spec) && !rom.romanized) {
        const english = (row.english || '').trim();
        if (!(sc.direction === 'understand' && readableAsIs(english))) {
          skippedNoReading += 1;
          continue;
        }
      }

      seenNative.add(native);
      entries.push({
        id: `${lang.code}-t0-${String(n).padStart(4, '0')}`,
        lang: lang.code,
        tier: 0,
        domain,
        text_native: native,
        text_romanized: rom.romanized,
        text_english: english,
        register: row.register ?? 'neutral',
        direction: sc.direction,
        why: sc.why,
        caution: sc.caution ?? null,
        source: {
          // Curated is the strongest class available to a solo project. Where an independent
          // attested sentence agrees on the target text, that fact is recorded rather than
          // dropped -- it is the evidence, and losing it would lose the top confidence level.
          class: 'curated',
          id: sourceId,
          url: row.source_url,
          licence: row.licence,
          // A STRING, not the number Tatoeba returns. The schema declares
          // ["string","null"] because the same field carries non-numeric ids for other
          // sources, and emitting a number made every corroborated entry fail validation --
          // 179 errors from one type mismatch, reported as "must match exactly one schema"
          // because a `oneOf` failure hides which branch and why.
          external_id: match ? String(match.attested_id) : null,
          author: match ? match.attested_author : null,
          corroborating_sentences: match ? [match.attested_id] : [],
          contributor_share: match ? match.tatoeba_author_share : null,
        },
        failure_flags: [],
      });
    }
  }

  return {
    entries,
    stats: {
      skipped_romanized_only: skippedRomanizedOnly,
      skipped_no_reading: skippedNoReading,
      dropped_counter_suffixes: droppedCounters,
      curated: curated.length,
      attested: attested.length,
      corroborated: new Set(entries.filter((e) => e.source.external_id).map((e) => e.source.external_id)).size,
      written: entries.length,
      domains: byDomain.size,
    },
  };
}

function main() {
  const catalogue = JSON.parse(readFileSync(join(CAT, 'languages.json'), 'utf8'));
  const corroboration = loadCorroboration();
  const only = process.env.ONLY ? process.env.ONLY.split(',') : null;
  const depths = JSON.parse(readFileSync(join(CAT, 'tier0-depth.json'), 'utf8'));
  const require = createRequire(import.meta.url);
  const YAML = require('yaml');

  const report = {};
  for (const lang of catalogue.languages) {
    if (lang.code === catalogue.calibration) continue;
    if (only && !only.includes(lang.code)) continue;

    const depthRec = depths.languages.find((l) => l.code === lang.code);
    const depth = depthRec?.depth ?? 0;
    if (!depth) { report[lang.code] = { skipped: 'no depth assigned' }; continue; }

    // Content cannot ship without a spec: the spec decides the variety, the script and the
    // tier policy, so a language with no spec is not a language that can be written yet.
    //
    // Spec files are named after the LANGUAGE, not the ISO code -- swahili.yaml, thai.yaml --
    // because they are read by humans far more often than by code. Guessing `<code>.yaml` made
    // every language report "no spec yet" including the four that have one, which reads as a
    // catalogue problem and is a path bug. Looked up by trying both.
    const specPath = [join(ROOT, 'specs', `${lang.code}.yaml`), join(ROOT, 'specs', `${lang.name.toLowerCase()}.yaml`)]
      .find((p) => existsSync(p));
    if (!specPath) {
      console.log(`  ${lang.code} ${lang.name.padEnd(12)} no spec yet — content needs one to pass lint`);
      report[lang.code] = { skipped: 'no spec' };
      continue;
    }

    const spec = YAML.parse(readFileSync(specPath, 'utf8'));
    process.env[`DEPTH_${lang.code.toUpperCase()}`] = String(depth);

    const { entries, stats, error } = buildLanguage(lang, spec, corroboration);
    if (error) {
      // Not a crash and not a pass. The spec has to declare the resource before content can be
      // written from it, because a source that is not declared cannot be licence-checked, and
      // that is the entire purpose of the provenance block.
      console.log(`  ${lang.code} ${lang.name.padEnd(12)} blocked: ${error.replace(`${lang.code}: `, '')}`);
      report[lang.code] = { blocked: error };
      continue;
    }
    if (!entries.length) {
      console.log(`  ${lang.code} ${lang.name.padEnd(12)} nothing to write`);
      report[lang.code] = { skipped: 'no usable rows', ...stats };
      continue;
    }

    const path = join(CONTENT, `${lang.code}-tier0.jsonl`);

    /**
     * Existing content is a SEED, not a casualty. The harvest fills the gaps around it.
     *
     * This is the third attempt to regenerate this content and the first that survives contact
     * with the numbers. `FORCE=1` replaces the file outright, and because the selection takes the
     * first N rows per domain in page order, that whole selection changes whenever the harvested
     * pool changes — which a parser fix guarantees. Spanish kept 0 of its 57 entries. French kept
     * 4 of 56. Thai, which is the case this project has already been burned by twice, would have
     * gone 77 to 37.
     *
     * None of that is wrongness in the harvest. It is a replacement where an addition was wanted.
     * The entries already in the file were chosen deliberately and some were reviewed by hand;
     * the honest operation on them is to keep them and take the rest of the tier from the harvest.
     *
     * So the shipped phrases come first, in the order they are already in, and the generator adds
     * only phrases it has not already got — never removing, never reordering. Regeneration becomes
     * monotonic: it can raise a language's depth, and it cannot lower it.
     *
     * `DIFF=1` reports what WOULD be added without writing, and `FORCE=1` still replaces outright
     * for the rare case where a clean regeneration is genuinely wanted.
     */
    const existing = existsSync(path)
      ? readFileSync(path, 'utf8').split('\n').filter(Boolean).map((l) => JSON.parse(l))
      : null;
    const existingPhrases = new Set((existing ?? []).map((e) => (e.text_native || '').trim()));
    const additions = entries.filter((e) => !existingPhrases.has((e.text_native || '').trim()));

    if (existing && process.env.DIFF === '1') {
      const wouldAdd = additions.length;
      console.log(
        `  ${lang.code} ${lang.name.padEnd(12)} ${String(existing.length).padStart(3)} kept` +
          `${wouldAdd ? `  +${wouldAdd} available from the harvest` : '  (harvest adds nothing new)'}` +
          `${depth ? `   depth ${depth}` : ''}`,
      );
      for (const e of additions.slice(0, 5)) {
        console.log(`      + ${JSON.stringify(e.text_native)}  (${JSON.stringify(e.text_english)})`);
      }
      if (wouldAdd > 5) console.log(`      ... and ${wouldAdd - 5} more`);
      report[lang.code] = { diff: true, kept: existing.length, addable: wouldAdd, depth };
      continue;
    }

    if (existing && process.env.FORCE !== '1') {
      /**
       * Existing entries are kept, but their EVIDENCE is refreshed.
       *
       * Keeping the phrase and freezing everything attached to it turns out to be its own bug. The
       * corroboration lookup used exact string equality, so German entries whose stored phrase read
       * `Guten Tag.` against an entry reading `Guten Tag` lost their corroborating sentence and
       * kept it lost — 59 phrases, 5.6% of the tier, reported to the reader as less well attested
       * than they are. Confidence is derived from that field, so this was not bookkeeping.
       *
       * A fix to the lookup cannot reach them, because the merge that protects them is the same
       * thing that preserves the wrong value. Preserving an entry's TEXT and refreshing its
       * EVIDENCE are separate decisions, and only the first one was being made.
       *
       * So the phrase is preserved byte for byte and the evidence block is recomputed from
       * `corroboration.json`, which is the authority on what corroborates what. Only the field this
       * function derives is touched; nothing else about the entry moves.
       */
      const languagePairs = corroboration[lang.code]?.pairs ?? [];
      const refreshed = existing.map((e) => {
        const match = languagePairs.find(
          (p) => corroborationKey(p.native) === corroborationKey(e.text_native),
        );
        const had = (e.source?.corroborating_sentences ?? []).length;
        const now = match ? [match.attested_id] : [];
        if (had === now.length && (had === 0 || e.source.corroborating_sentences[0] === now[0])) {
          return e;
        }
        return {
          ...e,
          source: { ...(e.source ?? {}), corroborating_sentences: now },
        };
      });
      const evidenceFixed = refreshed.filter((e, i) => e !== existing[i]).length;

      // Additive. Existing entries are written back untouched, byte for byte, and the harvest
      // appends until the tier reaches its depth.
      const room = depth ? Math.max(0, depth - existing.length) : additions.length;
      const toAdd = additions.slice(0, room);

      /**
       * Appended entries are RENUMBERED to continue from the highest id already in the file, and
       * they keep that file's OWN id shape.
       *
       * The generator numbers its output from 1 for every language, so appending its output to a
       * file that already exists puts `por-t0-0001` into a file whose `por-t0-0001` is a different
       * phrase — and the content linter caught exactly that, reporting five duplicate ids for
       * Portuguese and four more across Hindi, Russian and Turkish.
       *
       * Ids are never reused, which is what makes them usable as a stable reference, so appended
       * ones have to start where the file ends. Nothing else in a record refers to an id —
       * verified by walking every string in every field of every record — so renumbering cannot
       * dangle a reference.
       *
       * The shape is read from the file rather than assumed. Four content files were hand-authored
       * before the `-t0-` marker existed and use `tha-0001`; writing `tha-t0-0058` into one of them
       * would put two id schemes in a single file, and every consumer that parses the number back
       * out would have to know which half of the file it was looking at.
       */
      // A plain number accumulator. The first version started from `{ n: 0 }` and returned
      // `acc.n` or `Math.max(...)` — a number in one branch and an object read in the other — so
      // the second iteration read `.n` off a number, got `undefined`, and every appended id came
      // out as `ara-t0-0NaN`. Thirteen duplicate ids, which is what the linter reported.
      let nextId = existing.reduce((max, e) => {
        const m = /(\d+)$/.exec(e.id || '');
        return m ? Math.max(max, Number(m[1])) : max;
      }, 0);
      const shape = existing.find((e) => (e.id || '').includes('-t0-')) ? '-t0-' : '-';
      const renumbered = toAdd.map((e) => {
        nextId += 1;
        const prefix = e.id ? e.id.replace(/\d+$/, '') : `${lang.code}${shape}`;
        return { ...e, id: `${prefix}${String(nextId).padStart(4, '0')}` };
      });

      const merged = [...refreshed, ...renumbered];
      if (!renumbered.length) {
        // TWO DIFFERENT REASONS, and the first version of this printed only the second.
        //
        // "the harvest has nothing this file does not already hold" was reported for Indonesian,
        // Japanese, German and Mandarin — four languages the diff had just measured as having 15,
        // 17, 12 and 12 phrases to offer. The harvest had plenty. The language was already AT its
        // measured depth and the courtesy floor refuses to take more.
        //
        // That is the intended behaviour and an entirely different fact from having nothing to
        // add, and reporting it as the latter sends whoever reads it looking for a harvesting bug
        // that does not exist — or, worse, raising the depth to "fix" it.
        const held = room === 0 && additions.length
          ? ` at depth ${depth}; ${additions.length} held back by the courtesy floor`
          : ' the harvest has nothing this file does not already hold';
        // Written even though nothing was ADDED. The first version returned without writing, so
        // the evidence refresh was silently discarded for every language that added nothing —
        // which is fifteen of twenty, and German among them, which is where the bug was largest.
        // A refresh that only persists when something else changed is not a refresh.
        if (evidenceFixed) {
          writeFileSync(path, refreshed.map((e) => JSON.stringify(e)).join('\n') + '\n');
        }
        console.log(
          `  ${lang.code} ${lang.name.padEnd(12)} keeping ${existing.length} entries —${held}` +
            (evidenceFixed ? `, ${evidenceFixed} evidence refreshed` : ''),
        );
        report[lang.code] = { kept: existing.length, added: 0, evidence_refreshed: evidenceFixed, held_back: room === 0 ? additions.length : 0, depth };
        continue;
      }
      writeFileSync(path, merged.map((e) => JSON.stringify(e)).join('\n') + '\n');
      const say = merged.filter((e) => e.direction === 'say').length;
      console.log(
        `  ${lang.code} ${lang.name.padEnd(12)} ${existing.length} kept + ${renumbered.length} added ` +
          `= ${merged.length} entries${evidenceFixed ? `, ${evidenceFixed} evidence refreshed` : ''}` +
          `${depth ? ` (depth ${depth})` : ''}` +
          `${room < additions.length ? `, ${additions.length - room} still available` : ''}`,
      );
      report[lang.code] = {
        kept: existing.length, added: renumbered.length, total: merged.length, depth,
        evidence_refreshed: evidenceFixed,
        ratio: `${say}:${merged.length - say}`,
      };
      continue;
    }

    // Never clobber an existing hand-authored file. Tier 0 for four languages is written by
    // hand and reviewed against a native speaker's judgement; a generated file must not
    // silently replace it, which is how real editorial work gets lost.
    if (existsSync(path) && process.env.FORCE !== '1') {
      const count = existing.length;
      console.log(`  ${lang.code} ${lang.name.padEnd(12)} keeping existing ${count} entries (use FORCE=1 to replace)`);
      report[lang.code] = { kept: count, generated: entries.length };
      continue;
    }

    writeFileSync(path, entries.map((e) => JSON.stringify(e)).join('\n') + '\n');
    report[lang.code] = { ...stats, depth, ratio: null };
    const say = entries.filter((e) => e.direction === 'say').length;
    report[lang.code].ratio = `${say}:${entries.length - say}`;
    console.log(
      `  ${lang.code} ${lang.name.padEnd(12)} ${String(entries.length).padStart(3)} entries, ` +
        `${stats.domains} domains, ${stats.corroborated} corroborated, ${say}:${entries.length - say}`,
    );
  }

  writeFileSync(join(CAT, 'tier0-build.json'), JSON.stringify(report, null, 2) + '\n');
  console.log('  -> content/<code>-tier0.jsonl, catalogue/tier0-build.json');
}

// main() is invoked by the caller; kept separate so the YAML import stays lazy.
if (import.meta.url === `file://${process.argv[1]}`) main();

export { main as buildAll, SCENARIO, DOMAIN, romanised, tidy, scenarioFor, SIGNS };