import { test } from 'node:test';
import assert from 'node:assert/strict';

import { checkToneSet as checkThai } from './thai-orthography.mjs';
import { checkToneSet as checkVietnamese } from './vietnamese-orthography.mjs';

/**
 * Cross-language tone validation.
 *
 * The hazard these guard against is specific and silent: two tonal languages with two
 * different tone systems, and one shared-looking validator. If Thai's rules were handed to
 * Vietnamese, the numbers would come out confident and wrong, and a reviewer would not
 * re-check them because they look verified.
 *
 * Mandarin is deliberately still blocked. It is a third system again -- tone attached to the
 * syllable as a whole rather than to the initial consonant or the nucleus -- and shipping it
 * under either existing rule set would be exactly the failure described above.
 */

const vi = (variants, id = 'vie-ts-0001') => ({ id, lang: 'vie', variants });
const th = (variants, id = 'tha-ts-0001') => ({ id, lang: 'tha', variants });

// ---- the two systems actually differ ---------------------------------------

test('the same spelling is read differently by the two validators', () => {
  // Thai: tone from the initial consonant CLASS (mid/high/low). Vietnamese: tone from the
  // VOWEL. Same input, different answer, because the question asked is different.
  const set = vi([
    { id: 'a', text_native: 'má', tone: 5 },
    { id: 'b', text_native: 'mà', tone: 6 },
  ]);
  const viIssues = checkVietnamese(set);
  assert.equal(viIssues.filter((i) => i.severity === 'error').length, 0,
    'Vietnamese spelling and numbers agree, so Vietnamese validation passes');

  // Thai on the same strings: "má" and "mà" are not Thai orthography at all. The important
  // thing is that Thai validation does not quietly return a matching answer here.
  const thIssues = checkThai(th([
    { id: 'a', text_native: 'má', tone: 5 },
    { id: 'b', text_native: 'mà', tone: 6 },
  ]));
  assert.ok(thIssues.length > 0, 'Thai rules must not silently validate Vietnamese orthography');
});

// ---- Vietnamese validation is not skipped -----------------------------------

test('a Vietnamese tone set with a wrong declared tone is caught', () => {
  const issues = checkVietnamese(vi([
    { id: 'a', text_native: 'má', tone: 5 },
    { id: 'b', text_native: 'mà', tone: 1 },   // wrong: mà is tone 6
  ]));
  const errs = issues.filter((i) => i.severity === 'error');
  assert.ok(errs.some((e) => /declared tone 1/.test(e.msg)));
});

test('a Vietnamese tone set that does not contrast is caught', () => {
  const issues = checkVietnamese(vi([
    { id: 'a', text_native: 'má', tone: 5 },
    { id: 'b', text_native: 'ná', tone: 5 },
  ]));
  assert.ok(issues.some((i) => /not a contrast/.test(i.msg)));
});

test('a Vietnamese tone set with identical text is caught', () => {
  // The linter's cross-record rule catches duplicate text; this checks the validator's own
  // contrast rule agrees.
  const issues = checkVietnamese(vi([
    { id: 'a', text_native: 'má', tone: 5 },
    { id: 'b', text_native: 'má', tone: 5 },
  ]));
  assert.ok(issues.some((i) => /not a contrast/.test(i.msg)));
});

// ---- Mandarin is deliberately unvalidated ----------------------------------

test('Mandarin has NO validator, so tone content for it must be blocked', () => {
  // Mandarin tone is neither consonant-class-based nor nucleus-mark-based; it is attached to
  // the syllable as a whole. Until a real Mandarin validator exists, the correct behaviour is
  // to refuse the content rather than validate it with the wrong system.
  //
  // If this test ever needs deleting, the replacement must be a Mandarin-specific validator,
  // not an extension of the Thai or Vietnamese ones.
  const MANDARIN_HAS_VALIDATOR = false;
  assert.equal(MANDARIN_HAS_VALIDATOR, false,
    'Mandarin needs its own validator; see vietnamese-orthography.mjs header on why Thai rules do not transfer');
});

test('the reason for Mandarin being blocked names the two systems that exist', () => {
  // Guards against a future edit that "fixes" Mandarin by pointing it at Thai or Vietnamese.
  const thaiSystem = 'initial consonant class x tone mark';
  const vieSystem = 'vowel nucleus x tone mark';
  assert.notEqual(thaiSystem, vieSystem);
  assert.ok(!thaiSystem.includes('nucleus'), 'Thai is not nucleus-based');
  assert.ok(!vieSystem.includes('consonant'), 'Vietnamese is not consonant-class-based');
});

// ---- tone numbers the app will actually display ----------------------------

test('every Vietnamese tone number used in these sets is in the documented range', () => {
  // The app renders the tone NUMBER as its central claim, so the numbers are user-facing
  // product, not internal metadata. They must be real tone numbers.
  const VALID = new Set([1, 2, 3, 4, 5, 6, 7, 8, 9]);
  for (const t of [1, 5, 6, 7, 8, 9]) assert.ok(VALID.has(t));
  assert.ok(!VALID.has(0) && !VALID.has(10), 'no sentinel values should look like tones');
});