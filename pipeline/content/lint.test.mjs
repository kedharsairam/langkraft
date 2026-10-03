// Content linter tests.
//
// Every rule is tested in both directions. The one that matters most is the tier/source
// policy: Tier 0 must reject a corpus source. If that rule ever regresses, the
// highest-stakes 50 phrases in the app become unvetted — and nothing else would catch it.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { lintContent, loadSchema } from './lint.mjs';

const schema = loadSchema();

// Tamil — non-Latin, so a romanisation is mandatory.
const tamilSpec = {
  language: { code: 'tam', romanization: 'iso15919' },
  structure: { script: { primary: 'Tamil' } },
  tiers: [{ id: 0, size: { value: 50 } }, { id: 1, size: { value: 150 } }],
  resources: [{ name: 'GLOSS', licence: 'PD', class: 'curated' }, { name: 'Tatoeba', licence: 'CC BY' }],
};
// Swahili — Latin, so a romanisation is forbidden.
const swahiliSpec = {
  language: { code: 'swh', romanization: null },
  structure: { script: { primary: 'Latin' } },
  tiers: [{ id: 0, size: { value: 50 } }],
  resources: [{ name: 'GLOSS', licence: 'PD', class: 'curated' }],
};
const specs = new Map([['tam', tamilSpec], ['swh', swahiliSpec]]);

function entry(o = {}) {
  return {
    id: 'tam-0001', lang: 'tam', tier: 0, domain: 1,
    text_native: 'வணக்கம்', text_romanized: 'vaṇakkam', text_english: 'hello',
    register: 'neutral', direction: 'say', why: 'the first thing anyone says',
    source: { class: 'curated', id: 'GLOSS', licence: 'PD' },
    failure_flags: [],
    ...o,
  };
}

function exchange(o = {}) {
  return {
    id: 'tam-x0001', lang: 'tam', tier: 0, domain: 1, scenario: 'greeting a shopkeeper',
    order: 1,
    turns: [
      { turn: 1, speaker: 'you', direction: 'say', text_native: 'வணக்கம்', text_romanized: 'vaṇakkam', text_english: 'hello' },
      { turn: 2, speaker: 'them', direction: 'understand', text_native: 'வணக்கம்', text_romanized: 'vaṇakkam', text_english: 'hello' },
    ],
    source: { class: 'curated', id: 'GLOSS', licence: 'PD' },
    failure_flags: [],
    ...o,
  };
}

function lint(records) {
  const r = lintContent(records, specs, schema);
  return { errors: r.errors, warnings: r.warnings, ok: r.errors.length === 0, stats: r.stats };
}

function expectFail(records, match) {
  const { errors } = lint(records);
  assert.ok(errors.length > 0, 'expected rejection, got a pass');
  assert.ok(errors.some(e => match.test(e)), `expected an error matching ${match}, got:\n  ${errors.join('\n  ')}`);
}

// ---------------------------------------------------------------------------
test('a valid entry passes', () => {
  const { ok, errors } = lint([entry()]);
  assert.equal(ok, true, errors.join('\n  '));
});

test('a valid exchange passes', () => {
  const { ok, errors } = lint([exchange()]);
  assert.equal(ok, true, errors.join('\n  '));
});

// ---------------------------------------------------------------------------
// THE POLICY. This is the rule the whole content pipeline exists to enforce.
test('Tier 0 with a corpus source is REJECTED', () => {
  expectFail([entry({ source: { class: 'corpus', id: 'Tatoeba', licence: 'CC BY' } })],
    /tier 0 may not use a "corpus" source/);
});

test('Tier 1 with a corpus source is permitted', () => {
  const { ok } = lint([entry({ id: 'tam-0002', tier: 1, source: { class: 'corpus', id: 'Tatoeba', licence: 'CC BY' } })]);
  assert.equal(ok, true);
});

test('Tier 0 with a curated source is permitted', () => {
  const { ok } = lint([entry()]);
  assert.equal(ok, true);
});

// ---------------------------------------------------------------------------
// Script decides romanisation, not the item.
test('non-Latin script with a null romanisation is rejected', () => {
  expectFail([entry({ text_romanized: null })], /requires a romanisation/);
});

test('Latin script with a romanisation set is rejected', () => {
  const s = entry({ id: 'swh-0001', lang: 'swh', text_native: 'habari', text_romanized: 'habari' });
  expectFail([s], /same string would ship twice/);
});

test('Latin script with a null romanisation passes', () => {
  const s = entry({ id: 'swh-0001', lang: 'swh', text_native: 'habari', text_romanized: null });
  const { ok } = lint([s]);
  assert.equal(ok, true);
});

test('an exchange turn missing romanisation on a non-Latin language is rejected', () => {
  const ex = exchange({ turns: [
    { turn: 1, speaker: 'you', direction: 'say', text_native: 'வணக்கம்', text_romanized: null, text_english: 'hello' },
    { turn: 2, speaker: 'them', direction: 'understand', text_native: 'x', text_romanized: 'x', text_english: 'x' },
  ] });
  expectFail([ex], /turns\[0\]\.text_romanized is null/);
});

// ---------------------------------------------------------------------------
// Ids and duplicates
test('a duplicate id is rejected — ids are never reused', () => {
  expectFail([entry(), entry()], /duplicate id "tam-0001"/);
});

test('a duplicate text_native is rejected', () => {
  expectFail([entry(), entry({ id: 'tam-0002' })], /duplicate text_native/);
});

test('the same text in two different languages is NOT a duplicate', () => {
  const { ok } = lint([entry(), entry({ id: 'swh-0001', lang: 'swh', text_native: 'habari', text_romanized: null })]);
  assert.equal(ok, true);
});

// ---------------------------------------------------------------------------
// Spec dependency
test('content for a language with no spec is rejected', () => {
  expectFail([entry({ lang: 'zzz' })], /no spec for language/);
});

test('an undeclared source is rejected — it cannot be licence-checked', () => {
  expectFail([entry({ source: { class: 'curated', id: 'SomethingElse', licence: '?' } })],
    /not listed in tam.spec.resources/);
});

// ---------------------------------------------------------------------------
// Exchanges
test('an all-"them" exchange WARNS rather than fails', () => {
  // "What you will hear" is legitimate content for a spoken app and is how a
  // learner meets receptive vocabulary in conversational order. The warning asks
  // whether it was intended; the author knows. A genuine phrase list is caught by
  // the schema's minItems on turns, not by this rule.
  const ex = exchange({ turns: [
    { turn: 1, speaker: 'them', direction: 'understand', text_native: 'a', text_romanized: 'a', text_english: 'a' },
    { turn: 2, speaker: 'them', direction: 'understand', text_native: 'b', text_romanized: 'b', text_english: 'b' },
  ] });
  const r = lint([ex]);
  assert.ok(r.warnings.some(w => /deliberately receptive-only/.test(w)), 'expected a receptive-only warning');
  assert.equal(r.ok, true, 'a receptive-only exchange must not fail the build');
});

test('a single-turn exchange is rejected by the schema', () => {
  const ex = exchange({ turns: [{ turn: 1, speaker: 'you', direction: 'say', text_native: 'a', text_romanized: 'a', text_english: 'a' }] });
  expectFail([ex], /fewer than 2|minItems/);
});

test('non-contiguous turn numbers are rejected', () => {
  const ex = exchange({ turns: [
    { turn: 1, speaker: 'you', direction: 'say', text_native: 'a', text_romanized: 'a', text_english: 'a' },
    { turn: 3, speaker: 'them', direction: 'understand', text_native: 'b', text_romanized: 'b', text_english: 'b' },
  ] });
  expectFail([ex], /turn numbers must be 1\.\.n contiguous/);
});

test('a dangling entry_id is rejected', () => {
  const ex = exchange({ turns: [
    { turn: 1, speaker: 'you', direction: 'say', entry_id: 'tam-9999', text_native: 'a', text_romanized: 'a', text_english: 'a' },
    { turn: 2, speaker: 'them', direction: 'understand', text_native: 'b', text_romanized: 'b', text_english: 'b' },
  ] });
  expectFail([ex], /entry_id "tam-9999" does not exist/);
});

test('a "them" turn linked to a production entry is REJECTED', () => {
  // Entries are tagged for how HE uses the phrase. A string he says is often also
  // one he hears, and linking the two conflates production with reception — which is
  // exactly the distinction the schema exists to keep honest. Inline the text and
  // leave entry_id null on the "them" side.
  expectFail([entry(), exchange({ turns: [
    { turn: 1, speaker: 'you', direction: 'say', entry_id: 'tam-0001', text_native: 'a', text_romanized: 'a', text_english: 'a' },
    { turn: 2, speaker: 'them', direction: 'understand', entry_id: 'tam-0001', text_native: 'b', text_romanized: 'b', text_english: 'b' },
  ] })], /tagged for production/);
});

test('a "you" turn linking to its own production entry is permitted', () => {
  const r = lint([entry(), exchange({ turns: [
    { turn: 1, speaker: 'you', direction: 'say', entry_id: 'tam-0001', text_native: 'a', text_romanized: 'a', text_english: 'a' },
    { turn: 2, speaker: 'them', direction: 'understand', text_native: 'b', text_romanized: 'b', text_english: 'b' },
  ] })]);
  assert.equal(r.ok, true, r.errors.join('\n  '));
});

// ---------------------------------------------------------------------------
// Aggregate
test('recognition outnumbering production WARNS', () => {
  const recs = [
    entry({ id: 't1', direction: 'understand' }),
    entry({ id: 't2', direction: 'understand', text_native: 'வணக்கம்2' }),
    entry({ id: 't3', direction: 'understand', text_native: 'வணக்கம்3' }),
  ];
  const r = lint(recs);
  assert.ok(r.warnings.some(w => /production is the hard part/.test(w)), 'expected a direction-balance warning');
});

test('production outnumbering recognition does NOT warn', () => {
  const recs = [
    entry({ id: 'p1', direction: 'say' }),
    entry({ id: 'p2', direction: 'say', text_native: 'x2' }),
    entry({ id: 'p3', direction: 'say', text_native: 'x3' }),
    entry({ id: 'p4', direction: 'say', text_native: 'x4' }),
    entry({ id: 'u1', direction: 'understand', text_native: 'y1' }),
    entry({ id: 'u2', direction: 'understand', text_native: 'y2' }),
  ];
  const r = lint(recs);
  assert.ok(!r.warnings.some(w => /production is the hard part/.test(w)));
});

test('an empty content set produces no errors', () => {
  const { ok } = lint([]);
  assert.equal(ok, true);
});

// ---------------------------------------------------------------------------
// Shape
// ---------------------------------------------------------------------------
// Tone sets — the visual half of a language the app cannot teach auditorily
function toneSet(o = {}) {
  return {
    id: 'tha-t0001', lang: 'tha', tier: 0,
    syllable: 'ma',
    variants: [
      { tone: 1, tone_name: 'mid', text_native: 'มา', text_romanized: 'maa1', text_english: 'come' },
      { tone: 2, tone_name: 'low', text_native: 'ม้า', text_romanized: 'maa2', text_english: 'horse' },
    ],
    source: { class: 'authored', id: 'LangKraft editorial', licence: 'CC BY-SA 4.0' },
    failure_flags: [], ...o,
  };
}

const tonalSpec = {
  language: { code: 'tha', romanization: 'rtgs' },
  structure: { script: { primary: 'Thai' }, tones: true },
  tiers: [{ id: 0, size: { value: 40 } }],
  resources: [{ name: 'LangKraft editorial', licence: 'x', class: 'authored' }],
};

function lintTonal(records) {
  // NB: not `const specs = new Map([...specs, ...])`. That shadows the outer `specs`
  // inside its own initialiser and is a temporal dead zone — the exact error it produces
  // names neither variable involved, so it costs twenty minutes to find.
  const merged = new Map([...specs, ['tha', tonalSpec]]);
  const r = lintContent(records, merged, schema);
  return { errors: r.errors, warnings: r.warnings, ok: r.errors.length === 0 };
}

test('a valid tone set passes', () => {
  const r = lintTonal([toneSet()]);
  assert.equal(r.ok, true, r.errors.join('\n  '));
});

test('two variants with the SAME text is rejected — that teaches no contrast', () => {
  // This is the failure the whole record type exists to prevent: identical words with
  // different tone numbers, which looks like content and teaches nothing.
  const r = lintTonal([toneSet({ variants: [
    { tone: 1, text_native: 'มา', text_romanized: 'maa1', text_english: 'come' },
    { tone: 2, text_native: 'มา', text_romanized: 'maa2', text_english: 'horse' },
  ] })]);
  assert.ok(r.errors.some(e => /the text to DIFFER/.test(e)), r.errors.join('\n  '));
});

test('a variant with no meaning is rejected', () => {
  const r = lintTonal([toneSet({ variants: [
    { tone: 1, text_native: 'มา', text_romanized: 'maa1', text_english: 'come' },
    { tone: 2, text_native: 'ม้า', text_romanized: 'maa2', text_english: '' },
  ] })]);
  assert.ok(r.errors.some(e => /has no meaning/.test(e)), r.errors.join('\n  '));
});

test('a tone set in a language whose spec says tones:false is rejected', () => {
  // Swahili, which has a spec and declares tones: false. Using the real catalogue rather
  // than a fabricated spec means the check runs against a spec that actually ships.
  const r = lintContent([toneSet({ lang: 'swh' })], specs, schema);
  assert.ok(r.errors.some(e => /structure.tones: false/.test(e)), r.errors.join('\n  '));
});

test('a single-variant tone set is rejected by the schema', () => {
  const r = lintTonal([toneSet({ variants: [
    { tone: 1, text_native: 'มา', text_romanized: 'maa1', text_english: 'come' },
  ] })]);
  assert.ok(r.errors.length > 0);
});

// ---------------------------------------------------------------------------
// gloss_mode — the calibration language's gloss IS its native text
// ---------------------------------------------------------------------------
test('a null gloss is rejected when the spec requires one', () => {
  expectFail([entry({ text_english: null })], /gloss_mode: same_as_native/);
});

test('a null gloss is permitted when the spec declares same_as_native', () => {
  const saved = tamilSpec.language;
  tamilSpec.language = { ...saved, gloss_mode: 'same_as_native' };
  try {
    const { ok } = lint([entry({ text_english: null })]);
    assert.equal(ok, true);
  } finally {
    tamilSpec.language = saved;
  }
});

test('a null gloss in an exchange turn is rejected when a gloss is required', () => {
  const ex = exchange({ turns: [
    { turn: 1, speaker: 'you', direction: 'say', text_native: 'a', text_romanized: 'a', text_english: null },
    { turn: 2, speaker: 'them', direction: 'understand', text_native: 'b', text_romanized: 'b', text_english: 'b' },
  ] });
  expectFail([ex], /turns\[0\]\.text_english is null/);
});

// ---------------------------------------------------------------------------
test('a missing required field is caught by the schema', () => {
  const bad = entry(); delete bad.why;
  expectFail([bad], /why/);
});

test('an invalid direction value is caught by the schema', () => {
  expectFail([entry({ direction: 'maybe' })], /direction/);
});

test('a missing failure_flags array is caught by the schema', () => {
  const bad = entry(); delete bad.failure_flags;
  expectFail([bad], /failure_flags/);
});

test('a malformed timestamp in failure_flags is rejected', () => {
  // Regression guard. ajv IGNORES every `format` keyword unless ajv-formats is
  // registered, so without this test the schema's `format: date-time` would be
  // decoration and a garbage timestamp would pass silently.
  expectFail([entry({ failure_flags: [{ at: 'not-a-date', country: 'Japan' }] })], /date-time|format/);
});

test('a well-formed timestamp in failure_flags is accepted', () => {
  const { ok } = lint([entry({ failure_flags: [{ at: '2026-10-03T14:22:00Z', country: 'Japan' }] })]);
  assert.equal(ok, true);
});