// Mechanical verification of Vietnamese tone.
//
// WHY VIETNAMESE NEEDS ITS OWN VALIDATOR, NOT THAI'S
//
// The tone stage was built for Thai, and Thai's rules do not generalise. Thai tone is
// determined by the CLASS of the syllable's initial consonant (mid, high, low, fixed per
// codepoint) combined with which of four marks is written. Vietnamese tone is determined by
// the VOWEL NUCLEUS, and the diacritic sits on the vowel rather than the consonant. They are
// different systems with different inputs:
//
//     Thai:          initial consonant class  x  tone mark
//     Vietnamese:    vowel nucleus            x  tone mark
//
// Shipping the Thai rules to Vietnamese would produce confident, wrong numbers -- the worst
// failure mode available, because it looks verified. So each tonal language gets a validator
// keyed to its own system, and a language with no validator says so rather than inheriting
// one.
//
// THE SYSTEM, AS FAR AS IT IS MECHANICAL
//
// Vietnamese has six tones for syllables with a vowel nucleus, plus two for syllables whose
// nucleus is a glide or a schwa-type vowel, which are unmarked in orthography. The tone mark
// is written on the vowel:
//
//     acute   ´   tone 5  (ngang, high)
//     grave   `   tone 6  (trac, low)
//     hook    ̉   tone 7  (huyen, rising)
//     tilde   ~   tone 9  (ngã, dipping)
//     dot above . tone 8  (nặng, heavy)   <- sometimes written with an extra breve
//     none    -   tone 1  (ngang, mid)
//
// Combining nucleus with mark gives the number, exactly as Thai does with consonant and mark.
// Crucially it is the NUCLEUS that decides, not the initial consonant, so a validator that
// keys off the first letter produces nonsense here.
//
// THE PRE-1991/POST-1991 PROBLEM, AND WHY IT MATTERS HERE
//
// Vietnamese orthography changed in 1990 to mark tone on the nucleus. Southern speakers
// largely pronounce the old system, where tone depended on the initial consonant. The same
// spelling can therefore be read two ways by two populations. That is a real ambiguity, not
// a bug to be fixed mechanically, and this validator cannot resolve it -- it can only refuse
// to assert a single answer. Entries whose vowel nucleus is ambiguous get checked but not
// passed silently.
//
// WHAT IT CANNOT DO
//
// It cannot tell you whether a phrase is natural, whether the register fits, or whether a
// Northern and a Southern speaker would disagree about how to read it. It catches the class
// of error where the tone number contradicts the spelling, which is the error a non-native
// author makes most often.

const HANZI_START = 0x4e00;
const HANZI_END = 0x9fff;

// The Latin block plus the Latin-1 Supplement, Latin Extended-A and Latin Extended Additional.
// The last of those is essential and easy to get wrong: it runs to U+1EFF and contains most
// of the Vietnamese vowel+tone combinations (U+1EA0-U+1EF9). An earlier version of this file
// used a loose `cp > 0x024f` cutoff intending to catch Han, which rejected every one of those
// correct Vietnamese syllables as "not Vietnamese orthography" -- and the validator reported
// tone null for perfectly good content. The bound is explicit here for that reason.
const VIETNAMESE_MAX = 0x1eff;

// Vietnamese vowel nuclei, grouped by tone behaviour. The grouping is what the mark combines
// with, so it is written as an explicit table rather than derived -- there is no closed rule
// for Vietnamese vowels that would be safe to infer.
//
//   dot  - the dot above the vowel marks tone 8 (nặng) for essentially every nucleus.
//   ngang- mid tone 1, the unmarked default.
//   The e/ê and o/ô nuclei additionally attract tones 5 and 6 under the old system, which
//   is why they are listed separately: they are where Southern and Northern readers diverge.
const NUCLEI_DOT = new Set(['a', 'ă', 'â', 'e', 'ê', 'i', 'o', 'ô', 'u', 'ư', 'y']);
const NUCLEI_GLIDE = new Set(['ia', 'ua', 'ưa', 'ươ']); // semivowel-ish, tone behaviour differs
const NUCLEI_SCHWA = new Set(['iê', 'uô', 'yê']);       // diphthongs, worth naming for review

// Tone number by mark on the vowel.
const TONE_BY_MARK = {
  none: 1,
  acute: 5,
  grave: 6,
  hook: 7,
  tilde: 9,
  dot: 8,
};

/**
 * Decomposes Vietnamese orthography into nucleus + mark.
 *
 * Tone marks in Vietnamese are combining characters that attach to the VOWEL, and they are
 * also frequently typed as precomposed Latin-1/Latin-Extended characters (ế, ệ, ậ and so on).
 * Both spellings occur in the wild, so both are handled: a precomposed character carries both
 * the nucleus and the mark in one codepoint, which is the case a naive base-letter-plus-
 * combining-mark parser gets wrong.
 */
export function decompose(text) {
  const lower = (text ?? '').toLowerCase();

  // Precomposed characters: [base vowel, tone]. These are the single-codepoint forms, and
  // they are how most Vietnamese is actually typed. They carry nucleus AND mark in one
  // codepoint, so a parser that only understands combining marks sees an unknown base letter
  // and finds no tone at all.
  const PRECOMPOSED = {
    'á': ['a', 'acute'], 'à': ['a', 'grave'], 'ả': ['a', 'hook'], 'ã': ['a', 'tilde'], 'ạ': ['a', 'dot'],
    'ắ': ['ă', 'acute'], 'ằ': ['ă', 'grave'], 'ẳ': ['ă', 'hook'], 'ẵ': ['ă', 'tilde'], 'ặ': ['ă', 'dot'],
    'ấ': ['â', 'acute'], 'ầ': ['â', 'grave'], 'ẩ': ['â', 'hook'], 'ẫ': ['â', 'tilde'], 'ậ': ['â', 'dot'],
    'é': ['e', 'acute'], 'è': ['e', 'grave'], 'ẻ': ['e', 'hook'], 'ẽ': ['e', 'tilde'], 'ẹ': ['e', 'dot'],
    'ế': ['ê', 'acute'], 'ề': ['ê', 'grave'], 'ể': ['ê', 'hook'], 'ễ': ['ê', 'tilde'], 'ệ': ['ê', 'dot'],
    'í': ['i', 'acute'], 'ì': ['i', 'grave'], 'ỉ': ['i', 'hook'], 'ĩ': ['i', 'tilde'], 'ị': ['i', 'dot'],
    'ó': ['o', 'acute'], 'ò': ['o', 'grave'], 'ỏ': ['o', 'hook'], 'õ': ['o', 'tilde'], 'ọ': ['o', 'dot'],
    'ố': ['ô', 'acute'], 'ồ': ['ô', 'grave'], 'ổ': ['ô', 'hook'], 'ỗ': ['ô', 'tilde'], 'ộ': ['ô', 'dot'],
    'ú': ['u', 'acute'], 'ù': ['u', 'grave'], 'ủ': ['u', 'hook'], 'ũ': ['u', 'tilde'], 'ụ': ['u', 'dot'],
    'ứ': ['ư', 'acute'], 'ừ': ['ư', 'grave'], 'ử': ['ư', 'hook'], 'ữ': ['ư', 'tilde'], 'ự': ['ư', 'dot'],
    'ý': ['y', 'acute'], 'ỳ': ['y', 'grave'], 'ỷ': ['y', 'hook'], 'ỹ': ['y', 'tilde'], 'ỵ': ['y', 'dot'],
  };

  // Combining marks for the decomposed spelling: base vowel followed by U+0301 and friends.
  const COMBINING = {
    '\u0301': 'acute', // acute
    '\u0300': 'grave', // grave
    '\u0309': 'hook',  // hook above
    '\u0303': 'tilde', // tilde
    '\u0323': 'dot',   // dot below
  };

  const SIMPLE_VOWELS = new Set(['a', 'ă', 'â', 'e', 'ê', 'i', 'o', 'ô', 'u', 'ư', 'y']);

  // Multi-character nuclei must be matched BEFORE single vowels, longest first, or "ưa" is
  // read as two syllables "ư" + "a". That split is not cosmetic: the regional-ambiguity flag
  // keys on glide and diphthong nuclei, so splitting them made the flag silently never fire
  // for exactly the words it exists to catch.
  const COMPOUND_NUCLEI = ['ươ', 'iê', 'uô', 'yê', 'ưa', 'ia', 'ua', 'oe', 'uy']
    .sort((a, b) => b.length - a.length);

  const nuclei = [];
  let pendingMark = 'none';
  let i = 0;

  while (i < lower.length) {
    const ch = lower[i];

    if (PRECOMPOSED[ch]) {
      const [base, m] = PRECOMPOSED[ch];
      nuclei.push({ nucleus: base, mark: m });
      pendingMark = 'none';
      i += 1;
      continue;
    }
    if (COMBINING[ch]) { pendingMark = COMBINING[ch]; i += 1; continue; }

    const pair = lower.slice(i, i + 2);
    if (COMPOUND_NUCLEI.includes(pair)) {
      nuclei.push({ nucleus: pair, mark: pendingMark });
      pendingMark = 'none';
      i += 2;
      continue;
    }

    if (SIMPLE_VOWELS.has(ch)) {
      nuclei.push({ nucleus: ch, mark: pendingMark });
      pendingMark = 'none';
    }
    // Onsets (consonants) are skipped: Vietnamese tone lives on the vowel, never the onset.
    // This is the opposite of Thai, where the initial consonant is what decides.
    i += 1;
  }

  // A combining mark can also follow a precomposed base in mixed input.
  if (pendingMark !== 'none' && nuclei.length > 0 && nuclei[nuclei.length - 1].mark === 'none') {
    nuclei[nuclei.length - 1].mark = pendingMark;
  }

  // The LAST nucleus is the primary one: Vietnamese is largely a final-syllable-timed
  // language for tone purposes, and it is the syllable that carries a phrase's final
  // contour. Multi-syllable words therefore tone on their last vowel.
  const primary = nuclei[nuclei.length - 1] ?? { nucleus: '', mark: 'none' };
  return {
    nucleus: primary.nucleus,
    mark: primary.mark,
    tone: TONE_BY_MARK[primary.mark] ?? null,
    nuclei,
  };
}

/**
 * Analyses one Vietnamese syllable or word.
 *
 * Returns a record the linter can act on. `issues` is non-empty when something is wrong or
 * genuinely ambiguous; `tone` is null when it cannot be determined rather than guessed.
 */
export function analyse(text) {
  const issues = [];
  const { nucleus, mark, tone } = decompose(text);
  const lower = (text ?? '').toLowerCase();

  if (!lower) {
    return { text, tone: null, issues: ['empty'], nucleus: '', mark: 'none' };
  }

  // Script check BEFORE the early return.
  //
  // This ordering was wrong at first: the "no vowel nucleus" guard returned early, so a
  // string of Han characters never reached the script check and was reported as "no vowel
  // nucleus" rather than "this is not Vietnamese". Both are true, but the second is the one
  // that tells the author they have the wrong language's content in the file.
  const nonLatin = [];
  for (const ch of text) {
    const cp = ch.codePointAt(0);
    if ((cp >= HANZI_START && cp <= HANZI_END) || cp > VIETNAMESE_MAX) {
      nonLatin.push(`${ch} (U+${cp.toString(16).toUpperCase()})`);
    }
  }
  if (nonLatin.length) {
    issues.push(
      `contains ${nonLatin.join(', ')}: this is not Vietnamese orthography, so tone cannot ` +
      `be read from it. Either the wrong language's content reached the Vietnamese ` +
      `validator, or the text needs romanisation.`,
    );
    return { text, tone: null, issues, nucleus, mark, ambiguous: false };
  }

  // A syllable with no vowel nucleus cannot carry Vietnamese tone at all.
  if (!nucleus) {
    issues.push('no vowel nucleus found, so tone is not determinable from spelling');
    return { text, tone: null, issues, nucleus, mark, ambiguous: false };
  }

  // Tone 8 is written with a dot above the vowel. It is the tone most often mis-stated,
  // because speakers of other tonal languages map it onto their own system's "low" or
  // "grave", so it is checked explicitly.
  if (mark === 'dot' && !NUCLEI_DOT.has(nucleus)) {
    issues.push(`dot above nucleus "${nucleus}" — tone 8 is not expected on this vowel`);
  }

  // Glide and diphthong nuclei tone differently and are where Southern and Northern readers
  // disagree. Flag rather than pass silently.
  const ambiguous =
    NUCLEI_GLIDE.has(nucleus) || NUCLEI_SCHWA.has(nucleus) || nucleus === 'ê' || nucleus === 'ô';

  if (ambiguous) {
    issues.push(
      `nucleus "${nucleus}" is one of the diphthong/glide nuclei where Southern and ` +
      `Northern Vietnamese readings can diverge; the 1990 orthography marks tone on the ` +
      `vowel, but Southern speakers often apply the pre-1991 initial-consonant system. ` +
      `Record the intended reading rather than asserting one.`,
    );
  }

  return { text, tone, issues, nucleus, mark, ambiguous };
}

/**
 * Checks a tone_set record.
 *
 * The shape mirrors the Thai checkToneSet so the linter treats all three tonal languages
 * uniformly, but the arithmetic inside is Vietnamese's, not Thai's. A shared interface with
 * different rules is deliberate: the app's tone stage is one feature, and it must not
 * silently apply one language's rules to another.
 */
export function checkToneSet(set) {
  const issues = [];
  const variants = set.variants ?? [];

  if (variants.length < 2) {
    issues.push({ severity: 'error', where: set.id, msg: 'a tone set needs at least two variants' });
    return issues;
  }

  const analysed = variants.map((v) => ({ v, a: analyse(v.text_native ?? '') }));

  for (const { v, a } of analysed) {
    for (const msg of a.issues) {
      issues.push({
        severity: a.tone === null ? 'error' : 'warn',
        where: `${set.id}/${v.id ?? v.text_native}`,
        msg,
      });
    }

    // The tone number the content CLAIMS must match what the spelling says. This is the
    // whole point: the number is the app's central claim about the language and must be
    // checked, not asserted.
    if (a.tone !== null && v.tone !== undefined && v.tone !== null) {
      if (Number(v.tone) !== a.tone) {
        issues.push({
          severity: 'error',
          where: `${set.id}/${v.id ?? v.text_native}`,
          msg: `declared tone ${v.tone} but the spelling "${v.text_native}" (nucleus "${a.nucleus}", ` +
            `mark "${a.mark}") gives tone ${a.tone}. In Vietnamese the mark sits on the VOWEL, ` +
            `so this is a lookup, not a matter of opinion.`,
        });
      }
    }
  }

  // A minimal pair must actually contrast. If every variant tones the same, the set teaches
  // nothing and should not occupy space in the tone stage.
  const tones = [...new Set(analysed.map(({ a }) => a.tone).filter((t) => t !== null))];
  if (tones.length === 1 && analysed.length > 1) {
    issues.push({
      severity: 'error',
      where: set.id,
      msg: `all variants share tone ${tones[0]}, so this is not a contrast`,
    });
  }

  return issues;
}

/** Exposed so the linter can report which tonal languages actually have a validator. */
export const TONAL_LANGUAGES_WITH_VALIDATORS = new Set(['tha', 'vie']);