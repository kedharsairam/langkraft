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
test('an exchange with no "you" turn is rejected', () => {
  const ex = exchange({ turns: [{ turn: 1, speaker: 'them', direction: 'understand', text_native: 'a', text_romanized: 'a', text_english: 'a' }] });
  expectFail([ex], /no "you" turn is a phrase list/);
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

test('a "them" turn linked to a standalone entry WARNS', () => {
  const r = lint([entry(), exchange({ turns: [
    { turn: 1, speaker: 'you', direction: 'say', entry_id: 'tam-0001', text_native: 'a', text_romanized: 'a', text_english: 'a' },
    { turn: 2, speaker: 'them', direction: 'understand', entry_id: 'tam-0001', text_native: 'b', text_romanized: 'b', text_english: 'b' },
  ] })]);
  assert.ok(r.warnings.some(w => /wrong side of the conversation/.test(w)));
  assert.equal(r.ok, true, 'a warning must not fail the build');
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