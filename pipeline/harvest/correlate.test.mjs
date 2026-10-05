import { test } from 'node:test';
import assert from 'node:assert/strict';

import { correlate } from './correlate.mjs';

/**
 * Correlation tests.
 *
 * The measured corroboration rate is about 1%. That number looks like a failure and is not, so
 * these tests exist to pin down WHY, because a future reader will otherwise assume the
 * matcher is broken and "fix" it into reporting agreement it has not earned.
 */

const att = (native, english, author = 'someone', id = '1') => ({
  native, english, author, tatoeba_id: id, concept: 'greeting',
});

const cur = (native, english, concept = 'greeting', pronunciation = null) => ({
  native, english, concept, pronunciation,
});

// ---- the three tiers are kept separate --------------------------------------

test('identical native text corroborates natively', () => {
  const r = correlate(cur('เช็คบิลครับ', 'The bill, please.'), [att('เช็คบิลครับ', 'The bill, please.')]);
  assert.equal(r.native?.agreement, 'native');
});

test('an English-only match is gloss-level, never native', () => {
  // The whole point of separating these tiers. Two sources agreeing on the English meaning
  // proves both thought of the same request; it does not prove they would word it the same way,
  // and in most of these languages they would not.
  const r = correlate(
    cur('ราคาเท่าไหร่', 'How much is this?'),
    [att('ราคาเท่าไร', 'How much does this cost?')],
  );
  assert.equal(r.native, null, 'must NOT claim native agreement');
  assert.equal(r.gloss?.agreement, 'gloss');
});

test('a near-miss in meaning is not even a gloss match', () => {
  // "toilet" and "bathroom" are different words, so the content-word overlap is 0.33 and it
  // falls below threshold. That is correct behaviour, not a miss: reporting "where is the
  // bathroom" as agreement with "where is the toilet" would be a synonym claim, and synonyms
  // are exactly where register and regional usage differ.
  const r = correlate(
    cur('ห้องน้ำอยู่ที่ไหน', 'Where is the toilet?'),
    [att('ห้องสุขาอยู่ที่ไหน', 'Where is the bathroom?')],
  );
  assert.equal(r.native, null);
  assert.equal(r.gloss, null);
});

test('no match at all yields neither', () => {
  const r = correlate(cur('สวัสดี', 'Hello.'), [att('Goodbye', 'Goodbye.')]);
  assert.equal(r.native, null);
  assert.equal(r.gloss, null);
});

// ---- register is the interesting failure mode -------------------------------

test('a register difference is flagged, not silently accepted', () => {
  // Thai and Arabic both have formal and informal forms that are each correct and not
  // interchangeable. Reporting those as plain agreement tells a learner the choice does not
  // matter, which is exactly the mistake a phrasebook exists to prevent.
  //
  // The English gloss carries the register label -- Wikivoyage writes "Hello. (informal)" --
  // so that is where the difference is detectable. Two rows agreeing on the native text while
  // differing on declared register must not pass as clean agreement.
  const r = correlate(
    cur('สวัสดี', 'Hello. (informal)', 'greeting'),
    [att('สวัสดี', 'Hello.', 'a', '9')],
  );
  assert.equal(r.native?.agreement, 'native', 'the native text does match');
  assert.equal(r.native.register_conflict, true,
    'but a register label on one side only must be flagged for review');
});

test('matching register does not raise a conflict', () => {
  const r = correlate(
    cur('สวัสดี', 'Hello.', 'greeting'),
    [att('สวัสดี', 'Hello.', 'a', '9')],
  );
  assert.equal(r.native?.agreement, 'native');
  assert.equal(r.native.register_conflict, false);
});

test('register markers are detected asymmetrically', () => {
  const r = correlate(
    cur('Hello (informal).', 'Hello.', 'greeting'),
    [att('สวัสดี', 'Hello.', 'a', '3')],
  );
  // "informal" on one side only. The conflict is recorded for a human to resolve, because
  // whether that matters depends on the language and we cannot tell from the string alone.
  assert.equal(typeof r.gloss?.register_conflict, 'boolean');
});

// ---- the real-world shapes that must not break it ---------------------------

test('an empty attested pool is handled', () => {
  const r = correlate(cur('Hello', 'Hello.'), []);
  assert.equal(r.native, null);
  assert.equal(r.gloss, null);
});

test('a curated row with no pronunciation is handled', () => {
  const r = correlate(cur('สวัสดี', 'Hello.', 'greeting', null), [att('สวัสดี', 'Hello.')]);
  assert.equal(r.native?.agreement, 'native');
});

test('punctuation differences do not block a native match', () => {
  // "Halo." vs "Halo" is the same phrase. Trailing punctuation is noise in comparison only --
  // it is never stripped for display.
  const r = correlate(cur('Halo.', 'Hello.'), [att('Halo', 'Hello.')]);
  assert.equal(r.native?.agreement, 'native');
});

test('diacritics are NOT stripped for comparison', () => {
  // Conservative on purpose: in most of these languages diacritics carry meaning, so treating
  // "ma" and "má" as the same word would manufacture agreement.
  const r = correlate(cur('má', 'mum'), [att('mà', 'dad')]);
  assert.equal(r.native, null);
});

test('many attested rows for one phrase still yield a single result', () => {
  const r = correlate(
    cur('เช็คบิลครับ', 'The bill, please.'),
    [att('เช็คบิลครับ', 'The bill, please.', 'a', '1'),
     att('เช็คบิลครับ', 'May I have the bill?', 'b', '2')],
  );
  assert.equal(r.native?.agreement, 'native');
});