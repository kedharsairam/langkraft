import { test } from 'node:test';
import assert from 'node:assert/strict';

import { parsePhraseRow } from './wikivoyage.mjs';

/**
 * Template rows must never become content.
 *
 * Every test here corresponds to something that actually shipped into a content file and was
 * only caught because the content linter complained about a missing romanization and led back
 * here. The symptom in each case was a plausible-looking row containing punctuation or
 * underscores that are in no language.
 */

// ---- fill-in-the-blank rows -------------------------------------------------

test('a blanked word slot makes the row a template', () => {
  // `; How do I get to _____ ? : _____ ku eppadi pOvathu?` shipped as an entry whose native text
  // was five underscores followed by a verb.
  assert.equal(parsePhraseRow("How do I get to _____ ? : _____ ku eppadi pOvathu? (''...'')"), null);
});

test('a leading-ellipsis row is a template', () => {
  // `; ...bedsheets? : ...pOrvai (''...'')` completes the previous row and stands alone as
  // nothing.
  assert.equal(parsePhraseRow("...bedsheets? : ...pOrvai (''...'')"), null);
});

test('an inherited phrase is a template even when the English side has words', () => {
  // The bug this catches: an earlier guard tested the ENGLISH side for letters, and
  // "Does the room come with" always has letters, so the row passed and shipped as
  // "... roomOda varumaa?" -- an ellipsis, a noun and a verb with nothing to complete it.
  assert.equal(parsePhraseRow("Does the room come with... : ... roomOda varumaa? (''...'')"), null);
});

// ---- placeholder pronunciations ---------------------------------------------

test("a (''...'') placeholder is dropped, not recorded as a pronunciation", () => {
  // Taken literally this became the pronunciation "...", and the entry rendered as
  // "...pugai vandi nilayam (...)".
  const r = parsePhraseRow("Is there a house specialty? : Ethavathu speciala irruka (''...'')");
  assert.ok(r, 'a real phrase with a placeholder pronunciation should still be kept');
  assert.equal(r.pronunciation, null, 'the placeholder must not become a pronunciation');
  assert.ok(!/\.\.\./.test(r.native), `native must be clean: ${r.native}`);
});

test('a placeholder pronunciation does not leave dots on the phrase', () => {
  const r = parsePhraseRow("...the train station? : pugai vandi nilayam (''...'')");
  // Rejected as a continuation row, which is the correct outcome for a different reason.
  assert.equal(r, null);
});

test('a real pronunciation is kept', () => {
  const r = parsePhraseRow("Hello. : Halo. (''HAH-loh'')");
  assert.equal(r.pronunciation, 'HAH-loh');
});

// ---- the language-with-a-script rule ---------------------------------------

test('a romanization-only phrase is kept by the harvester and flagged downstream', () => {
  // The harvester cannot reject these -- the source genuinely provides no native form -- so it
  // records them and lets the content builder refuse. Rejecting here would hide the gap.
  const r = parsePhraseRow("Is there a house specialty? : Ethavathu speciala irruka");
  assert.ok(r);
  assert.equal(r.native, 'Ethavathu speciala irruka');
});
// ---- Arabic's three script shapes ------------------------------------------
//
// The Arabic page writes the target script three different ways, and each one shipped wrong
// before it was handled: as an inline pair, as a script-tagged template, and with an English
// register note sitting in the pronunciation slot.

test('inline two-script cells are split into native and romanisation', () => {
  // `; 0 : صفر Sifr` -- the numbers table puts both scripts in one cell separated by a space.
  // Unsplit, the entry's native text was "صفر Sifr" and its romanisation was null.
  const r = parsePhraseRow('0 : صفر Sifr');
  assert.equal(r.native, 'صفر');
  assert.equal(r.pronunciation, 'Sifr');
});

test('a script-tagged romanisation template is used', () => {
  const r = parsePhraseRow(
    "Excuse me. (''getting attention''):  {{Lang|ar|لَوْ سَمَحْتَ}} ''{{Lang|ar-Latn|law samaḥta}} (to male)",
  );
  assert.equal(r.native, 'لَوْ سَمَحْتَ');
  assert.equal(r.pronunciation, 'law samaḥta');
});

test('an English register note is not recorded as a pronunciation', () => {
  // `''(getting attention)''` shares the italics slot with a real pronunciation on that page.
  // Stored as one, the app displays "getting attention" under the phrase.
  const r = parsePhraseRow("Maybe: {{Lang|ar|\u0631\u064f\u0628\u064e\u0645\u064e\u0627}}  ''(rubbamaa)''");
  assert.equal(r.pronunciation, 'rubbamaa');
});

test('a later script guess must not overrule a resolved template', () => {
  // The non-Latin side test re-derived from the raw halves put "صفر Sifr" back after the
  // inline split had resolved it, because both halves match the Arabic range. A resolved
  // template is authoritative and a subsequent guess must not undo it.
  const r = parsePhraseRow('7 : سبعة sab\'a');
  assert.equal(r.native, 'سبعة');
  assert.equal(r.pronunciation, "sab'a");
});

// ---- script-scoped matchers -------------------------------------------------
//
// The Arabic inline split and the Mandarin traditional-variant split are each written for one
// page's shape. Applied globally they claim OTHER languages' phrases, because the pattern alone
// cannot tell "this page writes the romanisation inline" from "this page has parentheses".

test('the Mandarin three-part matcher does not claim a Thai phrase', () => {
  // `Open : เปิด (pèrt)` matched the Mandarin shape and produced "เปิด (pèrt" as the pronunciation
  // while taking the phrase itself as the simplified form.
  const r = parsePhraseRow('Open : เปิด (pèrt)');
  assert.equal(r.native, 'เปิด');
  assert.equal(r.pronunciation, 'pèrt');
});

test('the Arabic inline matcher does not claim a Thai phrase', () => {
  // The Thai page puts romanisation in parentheses, so this row is the INLINE shape -- exactly
  // what the Arabic matcher was built for, on the wrong script.
  const r = parsePhraseRow('Hello : สวัสดี (sawatdee)');
  assert.equal(r.native, 'สวัสดี');
  assert.equal(r.pronunciation, 'sawatdee');
});

test('the Mandarin split still works on Mandarin', () => {
  const r = parsePhraseRow("Thank you. :  谢谢。 (謝謝。) ''Xièxie.''");
  assert.equal(r.native, '谢谢。');
  assert.equal(r.pronunciation, 'Xièxie');
});

test('a template row is not claimed by the inline matcher', () => {
  // The widened `[^:]*` would otherwise swallow `{{Lang|ar-Latn|...}}` as a plain phrase.
  // KNOWN LIMIT: a row whose phrase lives ONLY inside templates is currently rejected outright
  // rather than parsed, so this asserts the non-regression (no template text in the output)
  // rather than a successful parse. Tracked as a real gap, not written as if it worked.
  const r = parsePhraseRow("Excuse me.: {{Lang|ar|لَوْ سَمَحْتَ}} ''{{Lang|ar-Latn|law samaḥta}}");
  if (r !== null) {
    assert.ok(!r.native.includes('{{'), `no template markup may survive: ${r.native}`);
    assert.ok(!/Lang\|/.test(r.pronunciation ?? ''), 'romanisation must not be raw template text');
  }
});
