/**
 * The 15 phrase-row shapes this extractor has to get right, as they appear on the pages.
 *
 * Every case below is a row copied verbatim out of a Wikivoyage phrasebook, not one written to
 * suit the parser. The expected values are what a reader would say and how they would write it
 * down — not what the previous implementation happened to produce. Several of these the previous
 * implementation got wrong, and two of its answers were confidently wrong rather than merely
 * absent, which is why each case pins the expected value rather than comparing against a stored
 * snapshot of the old output.
 *
 * A snapshot would have passed every time the parser changed its mind in the same direction. A
 * human-judged expectation cannot.
 */

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { extractReading, cleanWikitext, decodeEntities } from './reading.mjs';

/** [label, row, options, expected native, expected reading] */
const CASES = [
  // Bare reading, no italics, backtick apostrophe. Arabic numbers table.
  ['ara bare backtick', '; 4 : اربعة arba`a', {}, 'اربعة', 'arba`a'],

  // Reading first, then a register note that is ALSO italic and also English.
  ['ara reading then register',
    "; Leave me alone. : اتركني /  اتركيني   ''utrukni'' ''(to a male) / utrukiini'' ''(to a female)''",
    {}, 'اتركني / اتركيني', 'utrukni'],

  // Apostrophe inside the reading.
  ['ara apostrophe',
    "; I'll call the police. : سأتصل بالشرطة ''sa'ataSal bashurTah''",
    {}, 'سأتصل بالشرطة', "sa'ataSal bashurTah"],

  // Reading then a second pronunciation aid. The reading is the first one.
  ['jpn reading then aid',
    "; Thank you. (less formal) : ありがとうございます。 ''Arigatō gozaimasu.'' (''ah-ree-GAH-toh go-ZAh-ee-mahs'')",
    {}, 'ありがとうございます。', 'Arigatō gozaimasu'],

  // Bare italic reading, Japanese.
  ['jpn bare italic', '; aspirin : アスピリン \'\'asupirin\'\'', {}, 'アスピリン', 'asupirin'],

  // Reading with terminal punctuation inside the italics.
  ['jpn trailing period', '; Leave me alone. : ほっといてくれ。 \'\'Hottoitekure.\'\'',
    {}, 'ほっといてくれ。', 'Hottoitekure'],

  // Reading then English commentary.
  ['kor reading then commentary',
    "; Hello. (''formal'') : 안녕하십니까. (''annyeonghasimnikka'') Common in North Korea.",
    {}, '안녕하십니까.', 'annyeonghasimnikka'],

  // REVERSED row: the Korean is before the colon. Direction must be decided by script.
  ['kor reversed sign', "; 열림 (''yeollim'') : Open", {}, '열림', 'yeollim'],

  // Reversed AND the last row of the infobox, so it carries the template's closing braces.
  ['kor reversed closing braces', "; 금지 (禁止) (''geumji'') : Forbidden}}", {}, '금지', 'geumji'],

  // A one-character reading. Real, and it needs the single-letter allowance.
  ['kor single letter', '; 2 : 이 (i)', {}, '이', 'i'],

  // A reading whose text is also an English word. Rejecting it as a "register note" deleted a
  // real romanisation, which is why the reject list is phrases and not words.
  ['kor reading that is an English word', '; the day after tomorrow : 모레 (\'\'more\'\')',
    {}, '모레', 'more'],

  // Two alternative spellings inside one bracket group, split by a slash across two italic runs.
  ['rus slashed alternatives', "; 0 :ноль/нуль (''nohl’''/''nool’'')", {}, 'ноль/нуль', 'nohl’/nool’'],

  // Parenthesised reading, no italics.
  ['rus plain parenthetical', "; I can't.: Я не могу. (''yah nei mah-GOO'')", {}, 'Я не могу.', 'yah nei mah-GOO'],

  // Entity-separated, with a parenthesised vowel inside the reading and an editorial "(?)".
  ['hin em dash with parenthesised vowel',
    '; Can I get insurance? : मुझे बीमा का कार सकता है? &mdash; mera insurance ho sakta (-ī) hai? (?)',
    {}, 'मुझे बीमा का कार सकता है?', 'mera insurance ho sakta (-ī) hai?'],

  // The reading is a single accented letter, and the phrase carries a traditional-character gloss.
  ['cmn single accented letter', "; goose: 鹅 (鵝) ''é''", {}, '鹅', 'é'],

  // Reading in square brackets on the ENGLISH side of a reversed row.
  ['cmn reading on english side', "; 入口 (入口) : Entrance [''rùkǒu'']", {}, '入口', 'rùkǒu'],

  // Reading BEFORE the phrase, with a second form after an arrow.
  ['jpn reading before phrase', "; to die : ''shinu'' (死ぬ) → ''nakunaru'' (亡くなる)",
    {}, '死ぬ', 'shinu'],

  // The reading sits INSIDE the phrase, so it is spliced out rather than cut at. The Thai numbers
  // table puts the digit, then the reading, then the spelled-out word; truncating at the reading
  // returned an empty phrase and lost forty-one of the most useful phrases in the language.
  // Caught by the corpus diff, not by a unit test — an earlier version of this rule passed the
  // Japanese case above and failed every one of these.
  ['tha reading between two parts of the phrase', "; 0 : ๐ (''suun'') ศูนย์", {}, '๐ ศูนย์', 'suun'],
  ['tha compound number', "; 11 : ๑๑ (''sip-et'') สิบเอ็ด", {}, '๑๑ สิบเอ็ด', 'sip-et'],
  ['tha number with a note after it',
    "; 1 : ๑ (''nueng'') หนึ่ง on its own, (''et'') เอ็ด in compounds",
    {}, '๑ หนึ่ง on its own, (et) เอ็ด in compounds', 'nueng'],
  // Truncation still applies when the reading ENDS the phrase — nothing follows it to keep.
  // `اربعة arba\`a` above is the real row for that; this one pins the same rule against the same
  // splice shape so the two branches cannot drift apart.
  ['tha splice does not apply when nothing follows', "; 4 : 4 (''si'')", {}, '4', 'si'],
  ['splice keeps script on both sides of the reading', "; 4 : 4 (''si'') สี่", {}, '4 สี่', 'si'],

  // A reading placeholder. The phrase is kept; the reading is honestly absent.
  ['tam reading placeholder', "; tea (''drink'') : teh / tea (''...'')", {}, 'teh / tea', null],

  // A Latin-script language has no reading at all. The previous parser invented one.
  ['por latin script', "; Open : buka (''...'')", { latinScript: true }, 'buka', null],

  // An English gloss in italics on the English side. It is NOT a reading, and the phrase is
  // Latin, so there is no reading. The previous parser filed "to sleep" as the pronunciation.
  ['tam gloss is not a reading', "; Good night (''to sleep'') : nalliravu", {}, 'nalliravu', null],

  // A pronunciation-guide row rather than a phrase. `ண்` is the Tamil side and the English
  // commentary is the English side, and there is no reading — which is the correct answer even
  // though shipping a bare consonant as a phrase is not useful. Deciding that a GUIDE row is not
  // a phrase is the harvester's job, by section; this extractor's job is to read the columns.
  ['tam guide row', '; ண் : like "n" in "bend" but retroflex', {}, 'ண்', null],

  // The Tamil phrasebook writes its phrases in romanisation, so there is no reading to find and
  // the Latin text is the phrase. A bare walk that reached the start of the phrase side would
  // otherwise file the whole phrase into the pronunciation column and leave nothing behind.
  ['tam romanised phrase', "; Good night : nalliravu", {}, 'nalliravu', null],

  // Not a phrase row at all.
  ['no colon', '; Just some text with no separator', {}, null, null],

  // ---- Templates that CARRY the phrase, not decoration around it ----
  //
  // Stripping `{{Lang}}` the way every other template is stripped deleted the Arabic and left the
  // romanisation, so thirteen Arabic rows and eight Japanese pronoun rows had no phrase at all.

  ['ara Lang template', "; Yes.: {{Lang|ar|نَعَمْ}}  ''na'am''", {}, 'نَعَمْ', "na'am"],
  ['ara Lang template, no space', "; No.: {{Lang|ar|لا}}  ''laa''", {}, 'لا', 'laa'],
  ['ara Lang template, no space before colon', "; Maybe.: {{Lang|ar|رُبَّمَا}}  ''(rubbamaa)''",
    {}, 'رُبَّمَا', 'rubbamaa'],
  ['ara Lang template with a full stop', "; Never mind.: {{Lang|ar|لا يهم}}.  ''laa yahummu.''",
    {}, 'لا يهم.', 'laa yahummu'],

  // An italic span that opens and never closes, because the page's closing bracket is missing.
  // The scan pairs the opening marker with the one at the end of the row and returns
  // "min faDlak) (male" — a stray bracket and a register word welded onto the reading.
  ['ara unclosed italic', "; Please. :{{Lang|ar|من فضلك}}:(''min faDlak) (male)''",
    {}, 'من فضلك', 'min faDlak'],

  // `{{Lang|ar-Latn}}` is the Arabic page's own romanisation column — a STATEMENT about the row,
  // so it wins over inference, and it must be cut out of the phrase. The final vowel mark of
  // سَمَحْتَ is the regression test: an off-by-two cut dropped it, and a missing vowel mark turns a
  // correct word into a wrong one while still rendering as plausible Arabic.
  ['ara Lang-Latn cuts the phrase',
    "; Excuse me. (''getting attention''):  {{Lang|ar|لَوْ سَمَحْتَ}} ''{{Lang|ar-Latn|law samaḥta}} (to a female)",
    {}, 'لَوْ سَمَحْتَ', 'law samaḥta'],

  ['jpn Lang pronoun', "; {{Lang|ja|私}} ''watashi'' : the most common polite form for \"I\"",
    {}, '私', 'watashi'],
  ['jpn Lang pronoun, informal', "; {{Lang|ja|僕}} ''boku'' : boyish and more informal",
    {}, '僕', 'boku'],

  // `{{pron}}` is an explicit reading sitting at the END of the ENGLISH column. It is not cut out
  // of the phrase: the `q` on the phrase side is the table's own notation and restating the same
  // letter, and cutting there left a bare consonant `क़` with no reading beside it.
  ['hin pron template on the english side',
    "; क़ q : like s'''k'''ip but further back in the throat{{pron|q}}", {}, 'क़ q', 'q'],

  // ---- A reading in the TARGET script, not a transliteration ----
  //
  // The Dari page writes the phrase in Latin and the Dari in Arabic script inside the italics.
  // Requiring Latin rejected all twenty-two of those rows, which is every phrase on the page.

  ['fas reading in the target script', "; Hello. : Salaam. (''.سلام'')",
    { readingInTargetScript: true }, 'Salaam.', 'سلام'],
  ['fas reading in the target script, multi-word',
    "; How are you? : Chi hal dari? (''چي حال داري؟'')",
    { readingInTargetScript: true }, 'Chi hal dari?', 'چي حال داري؟'],
  ['fas target script is refused without the flag', "; Hello. : Salaam. (''.سلام'')",
    {}, 'Salaam. (.سلام)', null],
];

for (const [label, row, options, wantNative, wantReading] of CASES) {
  test(`reading: ${label}`, () => {
    const got = extractReading(row, options);
    assert.equal(got.native, wantNative, 'native');
    assert.equal(got.pronunciation, wantReading, 'pronunciation');
  });
}

test('reading: no output ever carries wikitext markup', () => {
  for (const [label, row, options] of CASES) {
    const got = extractReading(row, options);
    for (const [field, value] of Object.entries(got)) {
      if (typeof value !== 'string') continue;
      assert.ok(
        !/''|\{\{|\}\}|&[a-z]+;|<[a-z]/i.test(value),
        `${label}.${field} still carries markup: ${JSON.stringify(value)}`,
      );
    }
  }
});

test('reading: entity decoding is deterministic across repeated calls', () => {
  // The table it replaces was an array of `/g` regexes consulted with `.test()`, which advances
  // `lastIndex`. The same entity therefore matched on one call and not the next, and the parser
  // threw on the first entity a page used that the table had not already matched.
  const text = 'a &mdash; b &mdash; c &mdash; d &mdash; e';
  for (let i = 0; i < 5; i += 1) {
    assert.equal(decodeEntities(text), 'a — b — c — d — e', `call ${i + 1}`);
  }
});

test('reading: cleanWikitext never removes letters', () => {
  // The one invariant every markup rule must respect. A cleaning rule that ate a vowel would
  // produce a phrase that renders correctly and reads wrongly.
  const rows = [
    '; 4 : اربعة arba`a',
    '; aspirin : アスピリン \'\'asupirin\'\'',
    "; 0 :ноль/нуль (''nohl’''/''nool’'')",
    '; 2 : 이 (i)',
  ];
  for (const row of rows) {
    const { native } = extractReading(row);
    assert.ok(native && native.length > 0, `lost the phrase in ${row}`);
  }
});

test('reading: a row with no reading yields null rather than a guess', () => {
  const { native, pronunciation } = extractReading('; Hello : 안녕하세요');
  assert.equal(native, '안녕하세요');
  assert.equal(pronunciation, null);
});
