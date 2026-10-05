import { test } from 'node:test';
import assert from 'node:assert/strict';

import { scoreCandidate, select, summarise, scriptIsPlausible, wordCount, CONTRIBUTOR_CAP } from './select.mjs';

const row = (native, english, author = 'someone', concept = 'price') => ({
  native,
  english,
  author,
  concept,
  group: 'money',
  licence: 'CC BY 2.0 FR',
  tatoeba_id: '1',
  tatoeba_id_english: '1',
  dir: 'ltr',
});

// ---- disqualification ------------------------------------------------------

// The real corpus is full of Tatoeboard's own meta-conversation, which is about the site
// rather than about anything a traveller says.
const JUNK = [
  ['Terima kasih atas kontribusi Anda.', 'Thank you for your contributions.'],
  ['Terima kasih atas pertanyaan Anda.', 'Thanks for your question.'],
  ['Saya di Walmart hari ini.', 'I am at Wallmart today.'],
  ['Nomor 12345 tidak valid.', 'Number 12345 is not valid.'],
  ['Saya akan,Ganti经验 Anda.', 'Hello there.'],
];

for (const [native, english] of JUNK) {
  test(`disqualified: ${english.slice(0, 34)}`, () => {
    assert.equal(scoreCandidate(row(native, english)), null);
  });
}

test('a long sentence is disqualified', () => {
  const long = 'Saya ingin bertanya tentang harga tiket kereta api ke_other tujuan';
  assert.equal(scoreCandidate(row(long, 'I want to ask about train ticket prices')), null);
});

test('an empty or missing native or english is disqualified', () => {
  assert.equal(scoreCandidate(row('', 'Hello')), null);
  assert.equal(scoreCandidate(row('Halo', '')), null);
});

// ---- ranking ---------------------------------------------------------------

test('a polite question beats a bare one', () => {
  // The real Indonesian toilet pair, which is the case that matters: both are correct, and
  // the polite one is what you can actually say to a stranger.
  const polite = scoreCandidate(row('Permisi, toiletnya di mana ya?', 'Excuse me, where is the toilet?'));
  const bare = scoreCandidate(row('Di manakah toilet?', 'Where is the toilet?'));
  assert.ok(polite.score > bare.score, 'politeness must be worth more than nothing else');
});

test('a question outranks a statement of the same length', () => {
  const q = scoreCandidate(row('Di mana.trainnya?', 'Where is the train?'));
  const s = scoreCandidate(row('Ini tempat train', 'This is the train place'));
  assert.ok(q.score > s.score);
});

test('a single-word sentence is penalised heavily', () => {
  // Usually a dictionary entry or a name; a phrasebook line with no verb rarely works alone.
  const one = scoreCandidate(row('Terima kasih', 'Thanks'));
  const sentence = scoreCandidate(row('Terima kasih banyak sekali', 'Thank you very much'));
  assert.ok(sentence.score > one.score);
});

test('an over-long native sentence paired with a fragment english is penalised', () => {
  const fragment = scoreCandidate(
    row('Permisi tolong di mana toiletnya ada',
      'Toilet'),
  );
  assert.ok(fragment.score < 40, 'fragment pairs should not score in the ideal band');
});

test('scoring is deterministic', () => {
  const a = scoreCandidate(row('Permisi, toiletnya di mana ya?', 'Excuse me, where is the toilet?'));
  const b = scoreCandidate(row('Permisi, toiletnya di mana ya?', 'Excuse me, where is the toilet?'));
  assert.equal(a.score, b.score);
});

// ---- the contributor cap: the tests that matter ----------------------------

test('a single contributor cannot take every slot in a concept', () => {
  // 20 sentences from CK, nothing from anyone else. A naive top-N would return all 20 CK
  // rows, which is exactly how a language's Tier 0 becomes one translator's vocabulary.
  const rows = Array.from({ length: 20 }, (_, i) =>
    row(`Permisi, toilet di mana ya nomor ${i}?`, `Excuse me, where is the toilet number ${i}?`, 'CK', 'toilet'),
  );
  const { selected, report } = select(rows, { perConcept: 6 });
  const cap = Math.max(1, Math.ceil(6 * CONTRIBUTOR_CAP));
  assert.ok(selected.length <= cap, `expected at most ${cap} from one contributor, got ${selected.length}`);
  assert.equal(report.toilet.capped, true, 'the cap binding must be reported, not silent');
});

test('the cap admits different contributors first', () => {
  // Interleaved so that a naive score-sorted fill would still be CK-heavy if scores tie.
  const rows = [
    row('Permisi, di mana toiletnya?', 'Excuse me, where is the toilet?', 'CK', 'toilet'),
    row('Toiletnya di mana ya?', 'Where is the toilet please?', 'slyfin', 'toilet'),
    row('Kamar mandi di mana?', 'Where is the bathroom?', 'brauliobezerra', 'toilet'),
    row('Permisi toilet di mana ya?', 'Excuse me, toilet where?', 'shekitten', 'toilet'),
    row('Di manakah toiletnya?', 'Where is the toilet exactly?', 'CK', 'toilet'),
    row('Toilet ada di mana?', 'Where is the toilet located?', 'mervert1', 'toilet'),
  ];
  const { selected } = select(rows, { perConcept: 4 });
  const authors = new Set(selected.map((s) => s.author));
  assert.ok(authors.size >= 3, `expected a spread of authors, got ${[...authors]}`);
});

test('a concept with one author is reported as concentrated rather than hidden', () => {
  // The cap must not silently return nothing. One contributor is still evidence; it is just
  // flagged so the entry can be read with that in mind.
  const rows = [
    row('Merci beaucoup', 'Thank you very much', 'CK', 'thanks'),
    row('Merci beaucoup encore', 'Thank you very much again', 'CK', 'thanks'),
  ];
  const { selected, report } = select(rows, { perConcept: 6 });
  assert.equal(selected.length, 2, 'evidence is returned, flagged');
  assert.equal(report.thanks.dominant_share, 1);
  assert.equal(selected[0].capped_concept, false);
});

test('capped_concept is set when the cap actually bound', () => {
  const rows = Array.from({ length: 20 }, (_, i) =>
    row(`Permisi toilet di mana nomor ${i} ya?`, `Excuse me toilet where number ${i}?`, 'CK', 'toilet'),
  );
  const { selected } = select(rows, { perConcept: 6 });
  assert.ok(selected.every((s) => s.capped_concept === true));
});

test('concepts are selected independently', () => {
  const rows = [
    ...Array.from({ length: 20 }, (_, i) =>
      row(`Permisi harga nomor ${i}?`, `Excuse me price number ${i}?`, 'CK', 'price')),
    row('Terima kasih banyak', 'Thank you very much', 'slyfin', 'thanks'),
  ];
  const { selected } = select(rows, { perConcept: 6 });
  assert.ok(selected.some((s) => s.concept === 'thanks'),
    'one contributor dominating price must not starve the thanks concept');
});

// ---- script handling: the bug that zeroed out three languages --------------

// The first version of the mixed-script guard disqualified any text containing CJK. That is
// correct for a Latin-script language and catastrophically wrong for Japanese, Korean and
// Mandarin, which ARE CJK. It rejected 980/993 Japanese, 425/432 Korean and 854/865 Mandarin
// candidates, and the language with the largest pools in the catalogue appeared to have no
// evidence at all. These tests exist so that cannot happen again silently.

test('legitimate CJK survives scoring', () => {
  const cases = [
    { code: 'cmn', native: '你好，怎么样？', english: 'Hello, how are you?' },
    { code: 'jpn', native: 'こんにちは。', english: 'Hello.' },
    { code: 'jpn', native: 'やあ、みんな！', english: 'Hello everybody!' },
    { code: 'kor', native: '안녕하세요.', english: 'Hello.' },
    { code: 'tha', native: 'สวัสดีครับ', english: 'Hello.' },
    { code: 'tam', native: 'வணக்கம்!', english: 'Hello.' },
    { code: 'ara', native: 'مرحبا', english: 'Hello.' },
    { code: 'rus', native: 'Привет', english: 'Hello.' },
  ];
  for (const c of cases) {
    const r = row(c.native, c.english, 'a', 'greeting');
    // row() does not set `code`, and scoreCandidate reads it. Without this the language is
    // undefined, defaults to Latin, and the CJK text is rejected for being CJK -- which is the
    // very bug these tests exist to catch. Worth being explicit about.
    r.code = c.code;
    assert.ok(scriptIsPlausible(c.native, c.code), `${c.code} native script must be plausible`);
    assert.ok(scoreCandidate(r) !== null,
      `${c.code} "${c.native}" was rejected as invalid script`);
  }
});

test('the English half of a CJK pair is not judged against the target script', () => {
  // "Hello everybody!" is Latin, and it is the CORRECT English half of a Japanese sentence.
  // Checking it against 'jpn' rejects every CJK pair in the pool.
  const c = row('やあ、みんな！', 'Hello everybody!', 'a', 'greeting');
  c.code = 'jpn';
  assert.ok(scriptIsPlausible('やあ、みんな！', 'jpn'));
  assert.ok(!scriptIsPlausible('Hello everybody!', 'jpn'), 'Latin text is not Japanese');
  assert.ok(scoreCandidate(c) !== null, 'but the pair must still pass');
});

test('Han inside a Latin-script language is still rejected', () => {
  // The guard exists for this case, and must survive being corrected for CJK.
  assert.equal(scoreCandidate(row('Saya akan,Ganti经验 Anda.', 'Hello there.', 'a', 'x')), null);
});

test('scriptIsPlausible accepts the language it is told about', () => {
  assert.ok(scriptIsPlausible('你好', 'cmn'));
  assert.ok(scriptIsPlausible('こんにちは', 'jpn'));
  assert.ok(scriptIsPlausible('日本語の文', 'jpn'), 'kanji-only Japanese is still Japanese');
  assert.ok(scriptIsPlausible('สวัสดี', 'tha'));
  assert.ok(scriptIsPlausible('Привет', 'rus'));
  assert.ok(!scriptIsPlausible('你好', 'ind'), 'Chinese is not Indonesian');
});

test('wordCount does not treat a CJK sentence as two words', () => {
  // No spaces between words, so a whitespace count says a long Japanese sentence is terse.
  // That is how a 20-character sentence passes as "short".
  const jpn = wordCount('こんにちは。小川と申します。', 'jpn');
  assert.ok(jpn > 2, `expected a multi-unit count for an unspaced script, got ${jpn}`);
  // And the spaced languages are unaffected.
  assert.equal(wordCount('Permisi, toiletnya di mana ya?', 'ind'), 5);
});

test('a real CJK pool is not empty after selection', () => {
  // End-to-end guard on the failure mode itself: a big pool producing nothing means the
  // filter is wrong, not that the evidence is absent.
  const rows = Array.from({ length: 12 }, (_, i) => ({
    ...row(`你好，请问多少钱？${i}`, 'Excuse me, how much is this?', 'a', 'price'),
    code: 'cmn',
  }));
  const { selected } = select(rows, { perConcept: 6 });
  assert.ok(selected.length > 0, 'a 12-row Mandarin pool must select something');
});

test('empty input selects nothing without throwing', () => {
  const { selected, report } = select([], { perConcept: 6 });
  assert.deepEqual(selected, []);
  assert.deepEqual(report, {});
});

// ---- contributor_share reaches the output ---------------------------------

test('selected rows carry the pool-level dominant share', () => {
  const rows = [
    row('Permisi, di mana toiletnya?', 'Excuse me, where is the toilet?', 'CK', 'toilet'),
    row('Toiletnya di mana ya?', 'Where is the toilet please?', 'slyfin', 'toilet'),
  ];
  const { selected } = select(rows, { perConcept: 6 });
  assert.equal(selected[0].contributor_share, 0.5);
});

// ---- summary ---------------------------------------------------------------

test('summary reports yield, contributors and gaps', () => {
  const rows = [
    row('Permisi, di mana toiletnya?', 'Where is the toilet?', 'CK', 'toilet'),
    row('Harganya berapa?', 'How much is it?', 'CK', 'price'),
    row('Terima kasih', 'Thanks', 'slyfin', 'thanks'),
  ];
  const s = summarise(rows);
  assert.equal(s.rows, 3);
  assert.equal(s.contributors, 2);
  assert.equal(s.concepts_covered, 3);
  assert.equal(s.dominant_author, 'CK');
  assert.ok(Math.abs(s.dominant_share - 0.667) < 0.01);
});

test('summary of an empty pool does not divide by zero', () => {
  const s = summarise([]);
  assert.equal(s.rows, 0);
  assert.equal(s.dominant_share, null);
});