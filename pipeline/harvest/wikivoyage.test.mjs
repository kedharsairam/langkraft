import { test } from 'node:test';
import assert from 'node:assert/strict';

import { parsePhraseRow, cleanWikitext } from './wikivoyage.mjs';

/**
 * Wikivoyage phrasebook parsing tests.
 *
 * Every test here corresponds to a real thing that was wrong. The harvester reported
 * successful harvests of zero phrases, and transposed columns, on more than one occasion, and
 * both look identical from outside: a plausible number and no error.
 */

// ---- wikitext cleaning -----------------------------------------------------

test('cleanWikitext strips templates, links and italics', () => {
  assert.equal(cleanWikitext("'''Hello'''"), 'Hello');
  assert.equal(cleanWikitext('[[File:x.jpg|thumb|caption]]'), 'caption');
  assert.equal(cleanWikitext("''bold''"), 'bold');
});

test('cleanWikitext removes nowiki wrappers', () => {
  // Wrappers exist so a quoted apostrophe survives rendering. They must go before any length or
  // quote test, or a phrase carries invisible markup that breaks nothing visible and everything
  // comparable.
  assert.equal(cleanWikitext("Terima kasih<nowiki>'</nowiki>"), "Terima kasih'");
});

// ---- the column order, which is inconsistent --------------------------------

test('a normal phrase row parses as English then Native', () => {
  const r = parsePhraseRow("Hello. : Halo. (''HAH-loh'')");
  assert.equal(r.english, 'Hello.');
  assert.equal(r.native, 'Halo.');
});

test('an infobox row parses as Native then English when flagged reversed', () => {
  // Indonesian signs are `; BUKA : Open` inside {{infobox}}. Without the flag, "BUKA" becomes
  // the English gloss and "Open" the thing you say.
  const r = parsePhraseRow('BUKA : Open', true);
  assert.equal(r.english, 'Open');
  assert.equal(r.native, 'BUKA');
});

test('a non-Latin row is detected from its script regardless of context', () => {
  // Thai signs sit in an infobox and are English-first. The script check is what saves it,
  // because the template tells you nothing on its own.
  const r = parsePhraseRow('Open : เปิด');
  assert.equal(r.english, 'Open');
  assert.equal(r.native, 'เปิด');
});

test('a bare parenthetical after a non-Latin phrase is taken as pronunciation', () => {
  // Wikivoyage writes Thai pronunciation as a bare "(pèrt)" with no italic markers. For Thai,
  // Japanese, Mandarin and Tamil this column is the only usable form of the phrase, so
  // discarding it would lose the entry's practical value.
  const r = parsePhraseRow('Open : เปิด (pèrt)');
  assert.equal(r.native, 'เปิด');
  assert.equal(r.pronunciation, 'pèrt');
});

test('a genuine parenthetical in a Latin-script phrase is NOT taken as pronunciation', () => {
  // The opposite case, and the reason the capture above is restricted to non-Latin scripts.
  // For Indonesian or Swahili, "(to a waiter)" belongs to the phrase and removing it would be a
  // content bug -- amputating part of what the learner is shown.
  const r = parsePhraseRow('I am full (after a big meal) : Saya kenyang (setelah makan banyak)');
  assert.ok(r.native.includes('(setelah makan banyak)'),
    `parenthetical must survive for a Latin-script phrase: ${r.native}`);
  assert.equal(r.pronunciation, null);
});

test('a row with no colon is not a phrase row at all', () => {
  // Prose in the phrase-list section is rejected rather than half-parsed. Documented because it
  // is what makes the parenthetical test above need a colon on both sides.
  assert.equal(parsePhraseRow('I am full (after a big meal)'), null);
});

test('Swahili signs are English-first and must NOT be flipped', () => {
  // Swahili was marked reversed once, on the assumption that infoboxes are always
  // native-first. They are not: `; OPEN : Fungua`. Both sides are Latin, so no character-level
  // check can catch this -- only reading the markup.
  const r = parsePhraseRow('CLOSED : Imefungwa');
  assert.equal(r.english, 'CLOSED');
  assert.equal(r.native, 'Imefungwa');
});

test('a colon inside the native text does not split the row', () => {
  const r = parsePhraseRow('WC / KAMAR KECIL: Toilet', true);
  assert.equal(r.native, 'WC / KAMAR KECIL');
  assert.equal(r.english, 'Toilet');
});

// ---- pronunciation, the column that was silently lost ----------------------

test('pronunciation is captured from italic parentheses', () => {
  const r = parsePhraseRow("Hello. : Halo. (''HAH-loh'')");
  assert.equal(r.pronunciation, 'HAH-loh');
});

test('pronunciation is captured from a pron template', () => {
  const r = parsePhraseRow('क़ q : like skip{{pron|q}}');
  assert.equal(r.pronunciation, 'q');
});

test('the italic pronunciation is stripped from the phrase itself', () => {
  // Only the italic form is recognised and removed. It is metadata about the phrase, not part
  // of what you say, and leaving it attached would render "Halo. (HAH-loh)" as the phrase.
  const r = parsePhraseRow("Hello. : Halo. (''HAH-loh'')");
  assert.equal(r.native, 'Halo.');
  assert.equal(r.pronunciation, 'HAH-loh');
});

test('a row without pronunciation yields null, not an empty string', () => {
  const r = parsePhraseRow('Yes. : Ya');
  assert.equal(r.pronunciation, null);
});

// ---- rejections ------------------------------------------------------------

test('a row with no colon is rejected', () => {
  assert.equal(parsePhraseRow('Just some prose without a separator'), null);
});

test('a row whose native side is a paragraph is rejected', () => {
  const long = 'x '.repeat(60);
  assert.equal(parsePhraseRow(`English : ${long}`), null);
});

test('a row whose native side has no letters is rejected', () => {
  assert.equal(parsePhraseRow('Number : 12345'), null);
});

test('an empty row is rejected', () => {
  assert.equal(parsePhraseRow(''), null);
  assert.equal(parsePhraseRow(null), null);
});

test('a trailing register annotation is not part of the phrase', () => {
  const r = parsePhraseRow("Hello. (''informal'') : Hai. (''high'')");
  assert.equal(r.english, 'Hello.');
  assert.equal(r.native, 'Hai.');
});