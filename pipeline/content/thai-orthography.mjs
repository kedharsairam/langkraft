// Mechanical verification of Thai orthography.
//
// WHY THIS EXISTS
//
// Every Thai phrase and every tone contrast in this project was written or checked by
// someone who is not a Thai speaker. The tone NUMBER attached to each variant is the app's
// central claim about the language, and it is currently asserted rather than verified. That
// is the same class of defect as a source field with no provenance: it looks checked, and
// nothing checks it.
//
// It IS checkable. Thai tone is fully determined by two mechanical facts:
//
//   1. the CLASS of the syllable's initial consonant -- mid, high, or low, and the class of
//      every consonant is fixed by the Unicode codepoint;
//   2. which of the four tone marks is written, if any.
//
// Combining those gives the tone number. So "ขาว is tone 5" is not a matter of opinion, it
// is a lookup, and a wrong value in the content is a bug this file can find.
//
// WHAT IT CANNOT DO
//
// It cannot tell you whether a word means what we think it means, whether a local would
// say it, or whether the register is right. Those need a Thai speaker. This catches the
// class of error where the number contradicts the spelling -- which is the error a
// non-native author makes most often, and the one a reviewer would waste time on.

// Unicode ranges for the Thai block.
const THAI = (cp) => cp >= 0x0e00 && cp <= 0x0e7f;

// Consonant classes. The classification is a property of the letter, not of the word, so
// this table is total and closed: every Thai consonant is in exactly one row.
//
//   MID  - the "stop" letters that behave predictably. Unmarked = mid tone.
//   HIGH - historically the "living" set. Unmarked = RISING tone, which is the single fact
//          most Thai-learning material gets wrong.
//   LOW  - everything else. Marks map differently here too.
const MID_CLASS = new Set([
  0x0e01, // ก
  0x0e08, // จ
  0x0e0e, // ฎ
  0x0e0f, // ฏ
  0x0e14, // ด
  0x0e15, // ต
  0x0e1a, // บ
  0x0e1b, // ป
  0x0e2d, // อ
]);

const HIGH_CLASS = new Set([
  0x0e02, 0x0e03, // ข ฃ
  0x0e05, 0x0e06, // ฅ ฆ
  0x0e09, // ฉ
  0x0e10, 0x0e11, 0x0e12, // ฐ ฑ ฒ
  0x0e16, // ถ
  0x0e1c, 0x0e1d, // ผ ฝ
  0x0e28, 0x0e29, 0x0e2a, 0x0e2b, // ศ ษ ส ห
]);

// Vowel signs that are written BEFORE their consonant (spacing vowels). The initial
// consonant of a syllable is therefore not always its first codepoint.
const PREPOSING_VOWELS = new Set([0x0e40, 0x0e41, 0x0e42, 0x0e43, 0x0e44]);

// Final consonants that keep a syllable ALIVE when it is closed: the sonorants.
const LIVE_FINALS = new Set([
  0x0e21, // ม
  0x0e13, // ณ
  0x0e19, // น
  0x0e07, // ง
  0x0e22, // ย
  0x0e23, // ร
  0x0e25, // ล
  0x0e27, // ว
  0x0e3a, // ห  (or ฯ ฤ)
]);

// Tone marks.
const TONE_MARKS = {
  0x0e48: 2, // ่ mai ek
  0x0e49: 3, // ้ mai tho
  0x0e4a: 4, // ๊ mai tri
  0x0e4b: 5, // ๋ mai chattawa
};

const TONE_NAMES = { 1: 'mid', 2: 'low', 3: 'falling', 4: 'high', 5: 'rising' };

/**
 * Tone implied by a mark, per consonant class.
 *
 * The three columns are the whole rule. None of them is the textbook rule:
 *
 *   mark  |  mid  |  high  |  low
 *   ------+-------+--------+-------
 *   none  |   1   |    5   |   1
 *   ่     |   2   |    2   |   3     <- FALLING for a low-class initial
 *   ้     |   3   |    3   |   4     <- HIGH for a low-class initial
 *   ๊     |   4   |    4   |   4
 *   ๋     |   5   |    5   |   5
 *
 * The low column is the one that gets collapsed by accident, because it looks like the mid
 * column shifted by one — and that collapse is exactly what this function did on its first
 * version, caught by ค่ำ and ค้ำ. It is written out per-class rather than derived so the
 * difference is visible at a glance.
 */
const TONE_TABLE = {
  mid: { 0: 1, 2: 2, 3: 3, 4: 4, 5: 5 },
  high: { 0: 5, 2: 2, 3: 3, 4: 4, 5: 5 },
  low: { 0: 1, 2: 3, 3: 4, 4: 4, 5: 5 },
};

function toneForMark(consonantClass, markCode) {
  const m = markCode == null ? 0 : TONE_MARKS[markCode];
  const col = TONE_TABLE[consonantClass] ?? TONE_TABLE.mid;
  return col[m];
}

/**
 * Analyses one Thai syllable and returns what its spelling implies.
 *
 * `tone` is what the ORTHOGRAPHY says, which is not a stylistic question. `impliedTone` is
 * the number a learner would read off the mark if they knew the class of the initial.
 */
export function analyse(syllable) {
  const cps = [...syllable].map((c) => c.codePointAt(0));
  const consonants = cps.filter((c) => THAI(c) && c >= 0x0e01 && c <= 0x0e2e);

  // The initial is the first consonant AFTER any preposing vowel.
  let initialIndex = 0;
  while (
    initialIndex < cps.length &&
    (PREPOSING_VOWELS.has(cps[initialIndex]) || cps[initialIndex] < 0x0e01)
  ) {
    initialIndex += 1;
  }
  const initial = cps[initialIndex];
  const consonantClass = MID_CLASS.has(initial)
    ? 'mid'
    : HIGH_CLASS.has(initial)
      ? 'high'
      : 'low';

  const markCode = cps.find((c) => TONE_MARKS[c] !== undefined) ?? null;

  // Live if it ends in a long vowel / open syllable, or closes on a sonorant.
  const lastConsonant = consonants[consonants.length - 1];
  const endsWithVowel = cps[cps.length - 1] > 0x0e30;
  const live = endsWithVowel || LIVE_FINALS.has(lastConsonant);

  return {
    initial,
    initialChar: initial != null ? String.fromCodePoint(initial) : '?',
    consonantClass,
    markChar: markCode != null ? String.fromCodePoint(markCode) : '',
    impliedTone: toneForMark(consonantClass, markCode),
    live,
    codepoints: cps.map((c) => 'U+' + c.toString(16).toUpperCase().padStart(4, '0')),
  };
}

/**
 * Checks one tone_set record's variants against the orthography.
 *
 * @returns {Array<{level, message}>} problems, empty when the record is consistent.
 */
export function checkToneSet(set) {
  const problems = [];
  const variants = set.variants ?? [];
  if (variants.length < 2) return problems;

  // 1. Every variant's tone number must match what its own spelling implies.
  for (const v of variants) {
    const a = analyse(v.text_native);
    if (a.impliedTone !== v.tone) {
      problems.push({
        level: 'error',
        message:
          `tone ${v.tone} (${v.tone_name ?? TONE_NAMES[v.tone]}) contradicts the spelling of ` +
          `"${v.text_native}" (${a.codepoints.join(' ')}). Its initial is ${a.initialChar}, ` +
          `a ${a.consonantClass}-class consonant, and the mark is ` +
          `${a.markChar ? `"${a.markChar}"` : 'absent'}, which implies tone ${a.impliedTone}.`,
      });
    }
    if (!a.live && v.tone === 5) {
      problems.push({
        level: 'error',
        message:
          `"${v.text_native}" is given tone 5 (rising), but it is a DEAD syllable and dead ` +
          `syllables cannot carry a rising tone. Check the final consonant.`,
      });
    }
    // 2. The tone name must match the number, where one is declared.
    if (v.tone_name && TONE_NAMES[v.tone] && v.tone_name !== TONE_NAMES[v.tone]) {
      problems.push({
        level: 'error',
        message:
          `"${v.text_native}" is tone ${v.tone} (${TONE_NAMES[v.tone]}) but is named ` +
          `"${v.tone_name}".`,
      });
    }
  }

  // 3. A set whose POINT is that only the tone differs must actually differ only in tone.
  //    If two variants share spelling, it teaches nothing; if they differ in more than the
  //    mark, it is not a tone set at all.
  const initials = new Set(variants.map((v) => analyse(v.text_native).initial));
  const bodies = new Set(
    variants.map((v) =>
      [...v.text_native]
        .filter((c) => TONE_MARKS[c.codePointAt(0)] === undefined)
        .join(''),
    ),
  );
  if (bodies.size > 1) {
    problems.push({
      level: 'error',
      message:
        `tone set ${set.id} variants differ by more than the tone mark. Stripping the marks ` +
        `leaves ${bodies.size} different spellings, so this is not a minimal pair.`,
    });
  }
  if (initials.size > 1) {
    problems.push({
      level: 'error',
      message:
        `tone set ${set.id} variants have different initial consonants ` +
        `(${[...initials].map((c) => String.fromCodePoint(c)).join(', ')}), so they are not a ` +
        `minimal set.`,
    });
  }

  // 4. The set should actually contain a contrast.
  const tones = new Set(variants.map((v) => v.tone));
  if (tones.size < 2) {
    problems.push({
      level: 'error',
      message: `tone set ${set.id} declares only ${tones.size} distinct tone; there is no contrast.`,
    });
  }

  return problems;
}

export const TONE_NAMES_EXPORT = TONE_NAMES;