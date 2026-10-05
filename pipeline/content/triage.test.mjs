import { test } from 'node:test';
import assert from 'node:assert/strict';

import { classify } from './triage.mjs';

/**
 * Triage tests.
 *
 * These exist because the first version of this file GUESSED and corrupted content. Applying it
 * turned "はい、元気です。 Hai, genki desu" into two disagreeing fragments and split Hindi
 * mid-word. The tests below pin the classifications, and the absence of a splitter is itself
 * the thing being asserted: there is no `repairable` verdict any more, because that verdict is
 * what enabled the corruption.
 */

const entry = (lang, native, english, extra = {}) => ({
  lang, text_native: native, text_english: english, text_romanized: null, ...extra,
});

// ---- the three real causes, kept distinct -----------------------------------

test('a counting suffix is not a phrase', () => {
  // "個 -ko" is a Japanese counter suffix glossed as a CATEGORY of objects. No romanisation
  // makes it phrasebook content.
  const c = classify(entry('jpn', '個 -ko', 'small roundish objects (apples)'));
  assert.equal(c.verdict, 'not_a_phrase');
});

test('a counting suffix with a comma-list of readings is still not a phrase', () => {
  assert.equal(classify(entry('jpn', '匹 -hiki, -biki, -piki', 'small animals')).verdict, 'not_a_phrase');
});

test('a genuine Japanese phrase is NOT classified as a counter suffix', () => {
  // The discriminator is the English side naming a category, not the shape. Getting this wrong
  // would delete real content, which is the opposite of conservative.
  const c = classify(entry('jpn', 'お願いします', 'Please (asking for something)'));
  assert.notEqual(c.verdict, 'not_a_phrase');
});

test('sign text is identified as read-not-spoken', () => {
  const c = classify(entry('rus', 'Закрыто', 'CLOSED'));
  assert.equal(c.verdict, 'sign_text');
});

test('a Russian phrase is NOT classified as a sign', () => {
  assert.notEqual(classify(entry('rus', 'Я не понимаю', 'I do not understand')).verdict, 'sign_text');
});

test('an inline romanisation is reported as pending, never as repairable', () => {
  // The verdict that caused the corruption is gone. This must not come back as a fix.
  const c = classify(entry('jpn', 'はい、元気です。 Hai, genki desu', 'Yes, I am fine'));
  assert.equal(c.verdict, 'split_pending');
  assert.equal(c.fix, undefined, 'a fix must never be offered from stored text');
});

test('text with no romanisation anywhere is unfixable from this source', () => {
  const c = classify(entry('tam', 'வணக்கம்', 'Hello'));
  assert.equal(c.verdict, 'unfixable_here');
});

// ---- Latin-script languages are never triaged ------------------------------

test('a Latin-script entry is always fine', () => {
  // `text_romanized: null` is CORRECT for these. An earlier count included them and reported
  // 536 broken entries, when the real number was 47 across eight languages.
  for (const lang of ['spa', 'deu', 'fra', 'ind', 'por', 'ita', 'nld', 'tur']) {
    assert.equal(classify(entry(lang, 'Hola', 'Hello')).verdict, 'fine', lang);
  }
});

test('an entry that already has a romanisation is fine', () => {
  const e = entry('tha', 'สวัสดี', 'Hello');
  e.text_romanized = 'sawatdee';
  assert.equal(classify(e).verdict, 'fine');
});

test('English as the calibration language is exempt', () => {
  assert.equal(classify(entry('eng', 'Hello', 'Hello')).verdict, 'fine');
});

// ---- the corruptor must stay gone ------------------------------------------

test('no verdict carries a fix payload', () => {
  // The structural guarantee, not just a behavioural one: if `fix` ever appears on a verdict
  // again, the corruption path is back.
  const cases = [
    entry('jpn', 'はい、元気です。 Hai, genki desu', 'Yes, I am fine'),
    entry('hin', 'मैं शाकाहारी हूँ mai n śākāhārī', "I'm a vegetarian"),
    entry('jpn', '個 -ko', 'small objects'),
    entry('rus', 'Вход', 'ENTRANCE'),
    entry('tam', 'வணக்கம்', 'Hello'),
    entry('spa', 'Hola', 'Hello'),
  ];
  for (const c of cases) {
    const v = classify(c);
    assert.equal(v.fix, undefined, `${v.verdict} must not carry a fix`);
    assert.equal(typeof v.verdict, 'string');
    assert.ok(['fine', 'not_a_phrase', 'sign_text', 'split_pending', 'unfixable_here'].includes(v.verdict),
      `unexpected verdict "${v.verdict}" — a new cause needs recording, not guessing`);
  }
});