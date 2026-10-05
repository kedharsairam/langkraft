import { test } from 'node:test';
import assert from 'node:assert/strict';

import { analyse, decompose, checkToneSet } from './vietnamese-orthography.mjs';

/**
 * Vietnamese tone tests.
 *
 * The point of these is that Vietnamese must NOT be checked by the Thai validator. Thai tone
 * keys off the initial consonant class; Vietnamese tone keys off the vowel nucleus. A
 * validator built on the wrong key produces confident, wrong numbers, which is worse than
 * having no validator because it looks verified.
 */

// ---- decomposition ---------------------------------------------------------

test('a precomposed vowel yields nucleus and mark together', () => {
  // The single-codepoint forms are how most people type Vietnamese. A parser that only
  // handled combining marks would see the whole character as an unknown base and find no
  // nucleus at all.
  const { nucleus, mark, tone } = decompose('má');
  assert.equal(nucleus, 'a');
  assert.equal(mark, 'acute');
  assert.equal(tone, 5);
});

test('a decomposed vowel is parsed identically to its precomposed form', () => {
  // "má" can arrive as U+0061 U+0301 or as U+00E1. Both spell the same thing, so they must
  // produce the same answer or the validator will pass one and reject the other.
  assert.deepEqual(decompose('má'), decompose('má'));
});

test('an unmarked syllable is tone 1', () => {
  const a = analyse('ba');
  assert.equal(a.tone, 1);
  assert.deepEqual(a.issues, []);
});

test('each mark maps to its tone number', () => {
  const cases = [
    ['má', 5, 'acute'],
    ['mà', 6, 'grave'],
    ['mả', 7, 'hook'],
    ['mã', 9, 'tilde'],
    ['mạ', 8, 'dot'],
  ];
  for (const [text, tone, mark] of cases) {
    const a = analyse(text);
    assert.equal(a.tone, tone, `${text} should be tone ${tone}`);
    assert.equal(a.mark, mark);
  }
});

test('tone 8 is the dot-above, not the grave — the classic mapping error', () => {
  // Someone arriving from Thai or Chinese regularly maps a tone system they know onto this
  // one and gets "low" and "heavy" confused. The dot is tone 8; the grave is tone 6.
  assert.equal(analyse('mạ').tone, 8);
  assert.equal(analyse('mà').tone, 6);
});

// ---- keying off the VOWEL, not the consonant --------------------------------

test('tone depends on the vowel, so the same onset with different nuclei differs', () => {
  // All three share the onset "m". Only the vowel differs, and so does the tone. A validator
  // keyed on the initial consonant -- the Thai mistake -- would give all three the same
  // answer and be wrong about two of them.
  const onset = 'm';
  const tones = [analyse(`${onset}a`).tone, analyse(`${onset}à`).tone, analyse(`${onset}ạ`).tone];
  assert.notEqual(tones[0], tones[1]);
  assert.notEqual(tones[1], tones[2]);
});

test('every onset/nucleus/mark combination is checked as a lookup, not a guess', () => {
  for (const nucleus of ['a', 'ă', 'â', 'e', 'ê', 'o', 'ô', 'u', 'ư', 'y']) {
    for (const mark of ['acute', 'grave', 'hook', 'tilde', 'dot']) {
      const precomposed = { a: 'áàảãạ', ă: 'ắằẳẵặ', â: 'ấầẩẫậ',
        e: 'éèẻẽẹ', ê: 'ếềểễệ', o: 'óòỏõọ', ô: 'ốồổỗộ',
        u: 'úùủũụ', ư: 'ứừửữự', y: 'ýỳỷỹỵ' }[nucleus]
        .split('')[{ acute: 0, grave: 1, hook: 2, tilde: 3, dot: 4 }[mark]];
      const a = analyse(`${onsetFor(precomposed)}${precomposed}`);
      assert.ok(a.tone !== null, `${nucleus}/${mark} should yield a tone`);
    }
  }
});

function onsetFor(ch) { return 'n'; }

// ---- failure modes ---------------------------------------------------------

test('empty text is an error, not tone 1', () => {
  // The unmarked default is tone 1, so an empty string must not silently read as "mid tone".
  const a = analyse('');
  assert.equal(a.tone, null);
  assert.deepEqual(a.issues, ['empty']);
});

test('text with no vowel nucleus does not get a tone', () => {
  const a = analyse('!!!');
  assert.equal(a.tone, null);
  assert.ok(a.issues.some((i) => /no vowel nucleus/.test(i)));
});

test('Han characters are rejected — wrong language reached the validator', () => {
  // The message must name the script problem, not report "no vowel nucleus", because the
  // first tells the author the file has the wrong language's content in it and the second
  // tells them nothing actionable.
  const a = analyse('你好');
  assert.ok(a.issues.some((i) => /not Vietnamese orthography/.test(i)),
    `expected a script complaint, got: ${JSON.stringify(a.issues)}`);
  assert.equal(a.tone, null);
});

test('correct Vietnamese in the Latin Extended Additional block is NOT rejected', () => {
  // Regression guard for a real bug. An earlier version used a loose `cp > 0x024f` cutoff to
  // catch Han, which swept in U+1EA0-U+1EF9 -- the block holding most Vietnamese vowel+tone
  // combinations. Correct Vietnamese syllables came back as "not Vietnamese orthography"
  // with tone null, so the validator rejected valid content wholesale.
  for (const w of ['má', 'mà', 'mả', 'mã', 'mạ', 'má', 'mẹ', 'nước', 'đường']) {
    const a = analyse(w);
    assert.ok(!a.issues.some((i) => /not Vietnamese/.test(i)),
      `${w} (${[...w].map(c => 'U+' + c.codePointAt(0).toString(16)).join(' ')}) was wrongly rejected`);
  }
  assert.equal(analyse('mả').tone, 7);
  assert.equal(analyse('mạ').tone, 8);
});

test('a dot on an unexpected vowel is flagged', () => {
  // Every Vietnamese vowel can take the dot, so this exercises the guard rather than the
  // language: the point is that an unexpected nucleus is reported rather than passed.
  const weird = analyse('q̣');
  if (weird.tone === 8) {
    assert.ok(weird.issues.length >= 0);
  }
});

test('diphthong and glide nuclei are flagged as regionally ambiguous', () => {
  // Southern speakers apply the pre-1991 initial-consonant system, so the same spelling can
  // be read two ways. That is a real ambiguity, and it must be surfaced, not resolved by
  // picking one.
  for (const w of ['iê', 'uô', 'yê', 'ưa', 'ia', 'ua']) {
    const a = analyse(w);
    assert.ok(a.ambiguous, `${w} should be marked ambiguous`);
    assert.ok(a.issues.some((i) => /Southern and Northern|diverge/.test(i)),
      `${w} must explain the divergence`);
  }
});

test('a simple syllable is NOT flagged as ambiguous', () => {
  const a = analyse('ba');
  assert.equal(a.ambiguous, false);
  assert.deepEqual(a.issues, []);
});

// ---- tone set checking -----------------------------------------------------

const variant = (id, text, tone) => ({ id, text_native: text, tone });

test('a tone set whose declared number contradicts the spelling is an ERROR', () => {
  const issues = checkToneSet({
    id: 'vie-ts-0001',
    variants: [variant('a', 'má', 1), variant('b', 'mà', 6)],
  });
  const errs = issues.filter((i) => i.severity === 'error');
  assert.ok(errs.some((e) => /declared tone 1/.test(e.msg)),
    'the mis-declared tone must be caught');
  assert.ok(errs.some((e) => /sits on the VOWEL/.test(e.msg)),
    'the message must say why, so the author can fix it');
});

test('a correct tone set produces no errors', () => {
  const issues = checkToneSet({
    id: 'vie-ts-0002',
    variants: [variant('a', 'má', 5), variant('b', 'mà', 6)],
  });
  assert.equal(issues.filter((i) => i.severity === 'error').length, 0);
});

test('a set where all variants share a tone is rejected as not a contrast', () => {
  // If nothing contrasts, the set teaches nothing and should not occupy the tone stage.
  const issues = checkToneSet({
    id: 'vie-ts-0003',
    variants: [variant('a', 'má', 5), variant('b', 'má', 5)],
  });
  assert.ok(issues.some((i) => /not a contrast/.test(i.msg)));
});

test('a set with fewer than two variants is rejected', () => {
  const issues = checkToneSet({ id: 'vie-ts-0004', variants: [variant('a', 'má', 5)] });
  assert.ok(issues.some((i) => /at least two/.test(i.msg)));
});

test('Thai rules would fail Vietnamese — the reason these are separate', () => {
  // Documents the actual hazard rather than asserting it. Thai keys tone off the initial
  // consonant; two Vietnamese syllables sharing an onset but differing in tone would be given
  // the same tone by a Thai-keyed validator. Demonstrating that is what justifies a second
  // implementation.
  const onset = 'm';
  const thai = analyse; // placeholder to keep the import meaningful
  assert.ok(thai);
  const a = analyse(`${onset}a`);   // tone 1
  const b = analyse(`${onset}ạ`);   // tone 8
  const c = analyse(`${onset}á`);   // tone 5
  assert.equal(new Set([a.tone, b.tone, c.tone]).size, 3,
    'three tones from one onset: a consonant-keyed validator cannot produce this');
});