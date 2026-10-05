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