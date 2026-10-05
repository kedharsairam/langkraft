// Content linter tests.
//
// Every rule is tested in both directions. The one that matters most is the tier/source
// policy: Tier 0 must reject a corpus source. If that rule ever regresses, the
// highest-stakes 50 phrases in the app become unvetted — and nothing else would catch it.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { lintContent, loadSchema } from './lint.mjs';
import { readFileSync } from 'node:fs';

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
// Mechanical Thai orthography
test('a tone number that contradicts the spelling is rejected', () => {
  // The whole reason thai-orthography.mjs exists. ค is a LOW-class consonant, so mai tho
  // over it is HIGH (tone 4). Declaring it tone 3 looks like a reasonable mistake and is
  // exactly the error a non-native author makes.
  const r = lintTonal([toneSet({ variants: [
    { tone: 1, text_native: 'คำ', text_romanized: 'kham', text_english: 'word' },
    { tone: 3, text_native: 'ค้ำ', text_romanized: 'kham', text_english: 'to prop up' },
  ] })]);
  assert.ok(
    r.errors.some(e => /contradicts the spelling/.test(e)),
    `expected an orthography error, got: ${r.errors.join(' ')}`
  );
});

test('an unmarked HIGH-class consonant is tone 5, not tone 1', () => {
  // The single most-repeated error in Thai-learning material. ขาว is tone 5.
  const r = lintTonal([toneSet({ variants: [
    { tone: 5, text_native: 'ขาว', text_romanized: 'khao', text_english: 'white' },
    { tone: 3, text_native: 'ข้าว', text_romanized: 'khao', text_english: 'rice' },
  ] })]);
  assert.ok(
    !r.errors.some(e => /contradicts the spelling/.test(e)),
    `these are both correct and must pass: ${r.errors.join(' ')}`
  );
  const wrong = lintTonal([toneSet({ variants: [
    { tone: 1, text_native: 'ขาว', text_romanized: 'khao', text_english: 'white' },
    { tone: 3, text_native: 'ข้าว', text_romanized: 'khao', text_english: 'rice' },
  ] })]);
  assert.ok(wrong.errors.some(e => /contradicts the spelling/.test(e)));
});

test('a rising tone on a dead syllable is rejected', () => {
  // Dead syllables cannot carry a rising tone at all.
  const r = lintTonal([toneSet({ variants: [
    { tone: 1, text_native: 'คาด', text_romanized: 'khat', text_english: 'to think' },
    { tone: 5, text_native: 'คาด', text_romanized: 'khat', text_english: 'imagine' },
  ] })]);
  assert.ok(
    r.errors.some(e => /DEAD syllable/.test(e)),
    `expected a dead-syllable error, got: ${r.errors.join(' ')}`
  );
});

test('a tone set whose variants differ by more than the mark is rejected', () => {
  // It is not a minimal set, whatever the tones say.
  const r = lintTonal([toneSet({ variants: [
    { tone: 1, text_native: 'คำ', text_romanized: 'kham', text_english: 'word' },
    { tone: 3, text_native: 'ม้า', text_romanized: 'ma', text_english: 'horse' },
  ] })]);
  assert.ok(
    r.errors.some(e => /differ by more than the tone mark|different initial consonants/.test(e)),
    r.errors.join(' ')
  );
});

// ---------------------------------------------------------------------------
// User-facing prose. `why` and `caution` render verbatim on the phone.
function withProse(f, over = {}) {
  return entry({ why: f, ...over });
}

test('a why that describes the authoring pipeline is rejected', () => {
  // This is not a style preference. Models.kt used to claim these fields were "for the
  // build and for review, not for a phone" while Screens.kt rendered them, and twenty-one
  // entries consequently shipped build notes as product copy — including one whose text
  // began "SOURCED honestly:".
  for (const word of ['attested', 'bitext', 'curated', 'provenance', 'Wikivoyage', 'Tatoeba']) {
    const r = lint([withProse(`The ${word} for this is unclear.`)]);
    assert.ok(
      r.errors.some(e => /must be user-facing/.test(e)),
      `"${word}" should be rejected: ${r.errors.join(' ')}`
    );
  }
});

test('a numeric entry reference is rejected because the reader cannot follow it', () => {
  const r = lint([withProse('Same spelling as the middle of entry 5, different tone.')]);
  assert.ok(
    r.errors.some(e => /numeric entry reference/.test(e)),
    r.errors.join(' ')
  );
});

test('a why written for the reader passes', () => {
  const r = lint([withProse('Point at the thing as you say it; นี่ means "this, here".')]);
  assert.equal(r.ok, true, r.errors.join(' '));
});

test('the real corpus has no pipeline vocabulary in user-facing prose', () => {
  // Guards the whole shipped catalogue, not a fixture. Read the emitted asset rather than
  // the sources so this covers every language at once.
  const shipped = readFileSync(
    new URL('../../app/src/main/assets/content.jsonl', import.meta.url).pathname, 'utf8'
  ).split('\n').filter(l => l.trim()).map(l => JSON.parse(l));
  const offenders = [];
  for (const rec of shipped) {
    for (const fld of ['why', 'caution']) {
      const v = rec[fld];
      if (typeof v !== 'string') continue;
      if (/\b(attested|bitext|curated|provenance|Wikivoyage|Tatoeba)\b|\bentr(?:y|ies)\s+\d+/i.test(v)) {
        offenders.push(`${rec.id}.${fld}`);
      }
    }
  }
  assert.deepEqual(offenders, [], `pipeline vocabulary shipped to the phone: ${offenders.join(', ')}`);
});

// ---------------------------------------------------------------------------
// Tone sets — the visual half of a language the app cannot teach auditorily
// Thai is the only TONES:true language in the catalogue, so tone-set rules need a spec
// that declares it. The other two declare tones: false and must reject tone sets.
const tonalSpec = {
  language: { code: 'tha', romanization: 'rtgs' },
  structure: { script: { primary: 'Thai' }, tones: true },
  tiers: [{ id: 0, size: { value: 40 } }],
  resources: [{ name: 'Wiktionary (Thai entries)', licence: 'CC BY-SA 4.0', class: 'curated' }],
};

function lintTonal(records) {
  // NOT `const specs = new Map([...specs, ...])`. That shadows the outer `specs` inside its
  // own initialiser, which is a temporal dead zone -- the error it produces names neither
  // variable involved, so it costs twenty minutes to find.
  const merged = new Map([...specs, ['tha', tonalSpec]]);
  const r = lintContent(records, merged, schema);
  return { errors: r.errors, warnings: r.warnings, ok: r.errors.length === 0 };
}

function toneSet(o = {}) {
  // The native example set from Thai Wikipedia: คา ข่า ข้า ค้า ขา. Chosen over a
  // hand-written fixture because the orthography validator checks these numbers against
  // the consonant class, and the first version of this fixture used มา/ม้า with ม้า
  // marked tone 2 -- which is wrong, because ม is a LOW-class consonant and mai tho
  // there is HIGH (tone 4). The validator caught the test data, not the test.
  return {
    id: 'tha-t0001', lang: 'tha', tier: 0,
    syllable: 'kha',
    variants: [
      { tone: 1, tone_name: 'mid', text_native: 'คา', text_romanized: 'kha', text_english: 'to stay' },
      { tone: 2, tone_name: 'low', text_native: 'ข่า', text_romanized: 'kha', text_english: 'to trade' },
      { tone: 3, tone_name: 'falling', text_native: 'ข้า', text_romanized: 'kha', text_english: 'a type of curry' },
      { tone: 4, tone_name: 'high', text_native: 'ค้ำ', text_romanized: 'kham', text_english: 'to prop up' },
      { tone: 5, tone_name: 'rising', text_native: 'ขา', text_romanized: 'kha', text_english: 'to be drunk' },
    ],
    source: { class: 'curated', id: 'Wiktionary (Thai entries)', licence: 'CC BY-SA 4.0' },
    failure_flags: [], ...o,
  };
}

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
    { tone: 1, text_native: 'ขา', text_romanized: 'kha', text_english: 'to be drunk' },
    { tone: 2, text_native: 'ข่า', text_romanized: 'kha', text_english: '' },
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
    { tone: 1, text_native: 'ขา', text_romanized: 'kha', text_english: 'to be drunk' },
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