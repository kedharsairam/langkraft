/**
 * Reading extraction, by page structure rather than by character matching.
 *
 * WHY THIS FILE EXISTS
 *
 * Six previous attempts fixed the "no romanisation" problem by widening a character class in
 * `parsePhraseRow`, and each fix broke a different language. Adding `'` to the word body
 * excluded the Thai reading; adding `p{L}` made Arabic letters match and the parser claimed the
 * PHRASE as the reading; anchoring the bracket to the native side broke the Arabic `{{Lang}}`
 * rows and un-anchoring it broke Korean again. Every one of those was the same mistake: treating
 * a question about page structure as a question about characters.
 *
 * So: read the structure. Every romanisation on these pages is inside `''...''` on the native
 * side of the phrase row, or is a bare Latin run at the end of it. That is a fact about
 * Wikivoyage's editing convention, not a guess about punctuation, and it holds across all twenty
 * languages.
 *
 * The real rows this handles, taken verbatim from the pages rather than from memory:
 *
 *   ; 4 : اربعة arba`a                                    bare, no italics, backtick
 *   ; aspirin : アスピリン ''asupirin''                      bare italic
 *   ; 0 :ноль/нуль (''nohl’''/''nool’'')                    parenthesised, split into two
 *   ; Leave me alone. : اتركني ''utrukni'' ''(to a male)''   reading, then a register note
 *   ; Hello. (''formal'') : 안녕. (''annyeong'') to your…   reading, then English commentary
 *   ; Can I get insurance? : … &mdash; mera insurance … (?) em dash, then reading, then "(?)"
 *
 * Six shapes, one rule: the romanisation is the FIRST Latin-bearing italic span on the native
 * side, or failing that the trailing run of Latin-bearing words.
 *
 * THE CENTRAL MOVE: CUT, DON'T STRIP
 *
 * The first version of this file extracted the reading and then tried to remove its punctuation
 * from the phrase with five more regexes. It left `アスピリン ()` behind — an empty bracket pair
 * where the parenthesised reading used to be — because the italics were deleted before the
 * brackets that wrapped them, so the bracket-stripping rule no longer matched anything.
 *
 * Removing a substring from the middle of a string and expecting the punctuation around it to
 * disappear is the wrong operation. The phrase ends where the reading begins. So the position of
 * the reading is found first and the phrase is truncated there. Every trailing artefact —
 * the empty `()`, the `(/)` left by a slash-joined reading, `(?​)`, an English gloss run —
 * falls outside the cut without any rule having to name it.
 *
 * `&mdash;` is decoded before anything else, because the Hindi row separates phrase from reading
 * with a template-escaped em dash, and an entity is invisible to every test below.
 */

/**
 * Wikitext entities the phrasebook pages use inside phrase rows.
 *
 * A plain object, not an array of regexes. The array form needed `.find(([re]) => re.test(e))`,
 * and every regex in it carried the `g` flag — which makes `test()` stateful through `lastIndex`,
 * so the same entity matched on one call and failed on the next. That is a nondeterministic parser
 * depending on how many entities it had already seen, and it threw on the first entity a page
 * used that the table had not matched yet.
 */
const ENTITIES = {
  '&mdash;': '—',
  '&ndash;': '–',
  '&nbsp;': ' ',
  '&amp;': '&',
  '&lt;': '<',
  '&gt;': '>',
  '&quot;': '"',
  '&apos;': "'",
};

/** Decode entities in one pass. Deterministic, and impossible to half-match. */
export function decodeEntities(text) {
  return text.replace(
    /&(?:mdash|ndash|nbsp|amp|lt|gt|quot|apos);/g,
    (e) => ENTITIES[e],
  );
}

/** Strip wikitext markup and decode entities. Never removes letters. */
export function cleanWikitext(text) {
  let s = decodeEntities(text);
  s = s
    .replace(/\{\{[^{}]*\}\}/g, ' ') // one level of template; nested ones fall to the loop below
    .replace(/\{\{[^{}]*\}\}/g, ' ')
    .replace(/<br\s*\/?>/gi, ' ')
    .replace(/<[^>]+>/g, ' ') // any leftover HTML tag, including <sup>
    .replace(/\[\[([^\]|]*\|)?([^\]]*)\]\]/g, '$2') // internal links: keep the label
    .replace(/\[https?:\/\/\S+\s+([^\]]*)\]/g, '$1')
    .replace(/'''?/g, '') // bold before italic; both are gone by the time a reading is looked for
    .replace(/\[\/?[a-z]+\]/gi, ' ')
    .replace(/[ \t]+/g, ' ');
  return s;
}

/**
 * Every `''...''` span in a string, in order, with markup stripped.
 * Each result carries the index of its opening `''` so the caller can truncate there.
 */
export function italicSpans(text) {
  const out = [];
  const re = /''([\s\S]*?)''/g;
  let m;
  while ((m = re.exec(text)) !== null) {
    const inner = m[1].replace(/''/g, '').trim();
    if (inner) out.push({ text: inner, index: m.index });
  }
  return out;
}

/**
 * True when a span is plausibly a reading: Latin letters, and not one of the register notes
 * these pages put in italics.
 *
 * The register notes are the trap. `''(to a male)''` and `''(informal)''` are italicised, sit
 * after the reading, and contain English letters — an earlier version took the LAST italic span
 * on the row and produced "to a male" as a pronunciation.
 *
 * THE REJECT LIST IS PHRASES, NOT WORDS, AND THAT IS THE WHOLE RULE.
 *
 * A previous version of this list also contained `less`, `more` and `very`, added for the
 * Japanese row `; Thank you. (less formal) : …`. That note is on the ENGLISH side of the colon
 * and never reaches this function — and meanwhile `more` is the romanisation of the Korean word
 * for "tomorrow". Adding `more` to a list of things that are not readings silently deleted a real
 * reading, and the row then rendered as the phrase with the reading welded on: "모레 (more)".
 *
 * So every entry must be something that is ONLY ever a register note in this position. "to a",
 * "to your", "informal", "formal" qualify. "more" does not, however often it appeared in an
 * English gloss somewhere on these pages.
 *
 * @param {string} span
 * @param {number} minLetters 1 for a span in a known reading position, 2 for the bare word-walk
 */
function looksLikeReading(span, minLetters = 1, allowTargetScript = false) {
  if (/\p{Script=Latin}/u.test(span)) {
    if (
      /^\s*[\(\[]?\s*(?:to a|to your|male|female|masculine|feminine|informal|formal|polite|plural|singular|honorific|humile|high|low|rude|blunt|fast|slow|casual|intimate|respectful)\b/i.test(span)
    ) {
      return false;
    }
    return (span.match(/\p{L}/gu) ?? []).length >= minLetters;
  }
  // A reading in the TARGET script, which is not a transliteration but is still the reading
  // column. The Dari phrasebook writes the phrase in Latin and puts the Dari in Arabic script
  // inside the italics: `; Hello. : Salaam. (''.سلام'')`. Requiring Latin rejected all twenty-two
  // of those rows, which is every phrase on the page.
  if (allowTargetScript && NON_LATIN_SCRIPT.test(span)) {
    return (span.match(/\p{L}/gu) ?? []).length >= minLetters;
  }
  return false;
}

/**
 * Resolves the row templates that CARRY CONTENT, keeping their payload.
 *
 * This is the one thing markup-stripping cannot do, and getting it wrong is worse than getting a
 * reading wrong. `{{Lang|ar|نَعَمْ}}` is not decoration around the phrase — it IS the phrase.
 * Stripping it the way every other template is stripped deleted the Arabic and left the
 * romanisation, so thirteen Arabic rows and eight Japanese pronoun rows had no phrase at all, and
 * the extractor — finding no script on either side of the colon — could not even tell which way
 * round the row ran.
 *
 * Three shapes, unwrapped rather than removed:
 *
 *   {{Lang|ar|نَعَمْ}}             the phrase, in the target script
 *   {{Lang|ar-Latn|law samaḥta}}  the phrase, in romanisation
 *   {{pron|q}}                    an explicit reading
 *
 * A `{{pron}}` becomes an italic span rather than inline text, because every rule below finds
 * readings by looking for italics. That routes three notations through one mechanism on purpose.
 *
 * The `-Latn` form must be matched FIRST, or `{{Lang|ar-Latn|x}}` matches as `{{Lang|ar` and
 * leaves `-Latn|x}}` in the phrase — a Latin script fragment welded onto an Arabic word.
 */
/**
 * The readings a row STATES rather than implies.
 *
 * `{{pron|q}}` and `{{Lang|ar-Latn|law samaḥta}}` are declarations: an editor wrote down which
 * part of the row is the pronunciation. Every other rule in this file infers that from the shape
 * of the text, and an inference should never override a statement — so these are read from the
 * raw row, before templates are resolved, and consulted first.
 *
 * They are kept in a separate channel rather than rewritten into italics because rewriting them
 * was tried and it corrupts the row. `''{{Lang|ar-Latn|law samaḥta}}''` is already italic, and
 * substituting markers inside it produces four markers around one reading; the scan then pairs
 * them as (empty, reading) and (register note, unmatched), so the Arabic phrase was cut two
 * characters short and lost its final vowel mark. Collapsing the empty pairs made it worse — it
 * removed the very markers that made the reading a reading. Leaving the page's own markup alone
 * is both simpler and correct.
 */
export function statedReadings(row) {
  const found = [];
  const pron = /\{\{\s*pron\s*\|\s*([^}|]+?)\s*\}\}/i.exec(row)?.[1]?.trim();
  if (pron) found.push({ source: 'pron', text: pron });
  // The -Latn form must be matched before the plain one, or `{{Lang|ar-Latn|x}}` matches as
  // `{{Lang|ar` and leaves `-Latn|x}}` behind as a Latin fragment welded onto an Arabic word.
  const latn = /\{\{\s*Lang\s*\|\s*[a-z]+-Latn\s*\|\s*([^{}|]+?)\s*\}\}/i.exec(row)?.[1]?.trim();
  if (latn) found.push({ source: 'lang-latn', text: latn });
  return found;
}

/**
 * Resolves the row templates that CARRY CONTENT, keeping their payload.
 *
 * This is the one thing markup-stripping cannot do, and getting it wrong is worse than getting a
 * reading wrong. `{{Lang|ar|نَعَمْ}}` is not decoration around the phrase — it IS the phrase.
 * Stripping it the way every other template is stripped deleted the Arabic and left the
 * romanisation, so thirteen Arabic rows and eight Japanese pronoun rows had no phrase at all, and
 * the extractor — finding no script on either side of the colon — could not even tell which way
 * round the row ran.
 *
 * The payload is unwrapped and NOTHING is added: no italics, no brackets. The page's own markup
 * around the template is preserved exactly as written, which is what lets the italic rules see a
 * well-formed row afterwards. See `statedReadings` for why the `-Latn` payload is not re-marked.
 */
export function resolveTemplates(row) {
  let s = row;
  s = s.replace(/\{\{\s*Lang\s*\|\s*[a-z]+-Latn\s*\|\s*([^{}|]+?)\s*\}\}/gi, ' $1 ');
  s = s.replace(/\{\{\s*Lang\s*\|\s*[a-z]+\s*\|\s*([^{}|]+?)\s*\}\}/gi, ' $1 ');
  s = s.replace(/\{\{\s*pron\s*\|\s*([^}|]+?)\s*\}\}/gi, ' $1 ');
  s = s.replace(/\{\{\s*pron\s*\|[^{}]*\}\}/gi, ' ');
  // `<sup>` is a MODIFIED LETTER, not decoration: `mai<sup>n</sup>` is "main", one word with a
  // superscript consonant. Reducing the tag to a space first — which is what the generic tag rule
  // below does — split it into "mai n", and the row shipped a romanisation with a space in the
  // middle of a word. Dropping the tags and keeping their content keeps the letters adjacent.
  // This must happen before the split, because it changes the length of the string.
  s = s.replace(/<sup>(.*?)<\/sup>/gis, '$1');

  /**
   * Close up the gap a template substitution leaves against adjacent punctuation.
   *
   * `{{Lang|ar|لا يهم}}.` unwraps to ` لا يهم . ` and the phrase shipped as "لا يهم ." — a space
   * before a full stop, which is not a spelling of anything. A space immediately before sentence
   * punctuation is never intentional on these pages, and closing it is the difference between
   * "looks scraped" and "looks written".
   */
  s = s.replace(/\s+([.,;:!?])(?=[\s)\]]|$)/g, '$1');
  return s;
}

/**
 * Balances italic markers.
 *
 * `; Please. : {{Lang|ar|من فضلك}}:(''min faDlak) (male)''` opens an italic span and never closes
 * it before the row ends. The scan pairs that opening `''` with the `''` at the very end and
 * returns the reading as "min faDlak) (male" — a reading carrying a stray bracket and a register
 * word it should not have had.
 *
 * An ODD count of markers means exactly one unmatched opening, so closing it at the end is the
 * only reading of the text that is consistent. An even count is left alone: adding a marker to a
 * balanced row would silently italicise half a phrase.
 */
export function balanceItalics(text) {
  const count = (text.match(/''/g) ?? []).length;
  return count % 2 === 1 ? `${text}''` : text;
}

/**
 * Script presence, used only to decide which side of the colon holds the target language.
 *
 * The sign tables in several phrasebooks are reversed: `; 열림 (''yeollim'') : Open` puts the
 * Korean first. That is not a parsing error to be tolerated, it is a different column order that
 * the extractor has to notice, and the only reliable signal is which side carries the script the
 * phrasebook is written in.
 *
 * WRITTEN AS "anything that is a letter and is not Latin", NOT AS A LIST OF RANGES.
 *
 * The range-list version enumerated Arabic, Devanagari, Thai, Han, Hangul and Kana and silently
 * omitted Tamil, so `; ண் : like "n" in "bend" but retroflex` was read as English-first, the
 * whole English commentary was taken as a romanisation, and a pronunciation-guide row was filed
 * as a phrase. A language added to the catalogue would have been added to the app and not to this
 * regex, and would have failed in exactly the same way — silently, and only for that language.
 *
 * `\p{Script=Latin}` with the `u` flag is exact and needs no maintenance. The `u` flag is not
 * optional: without it the property escape is not recognised as a property at all and the class
 * silently matches nothing, which is a check that passes while testing nothing.
 */
const NON_LATIN_SCRIPT = /[^\p{Script=Latin}\p{M}\p{P}\p{Z}\p{N}\p{S}\p{C}]/u;


/**
 * Splits a phrase row on the first colon that is not inside a template or an italic span.
 *
 * Wikivoyage puts register notes on the English side too (`; Hello. (''formal'') : …`), and
 * splitting inside one of those would put half a row on each side.
 */
function splitRow(row) {
  const text = row.replace(/^\s*;/, '');
  let inTemplate = false;
  let inItalic = false;
  for (let i = 0; i < text.length; i += 1) {
    const two = text.slice(i, i + 2);
    if (two === '{{') { inTemplate = true; i += 1; continue; }
    if (two === '}}') { inTemplate = false; i += 1; continue; }
    if (two === "''") { inItalic = !inItalic; i += 1; continue; }
    if (text[i] === ':' && !inTemplate && !inItalic) {
      return { englishSide: text.slice(0, i), nativeSide: text.slice(i + 1) };
    }
  }
  return null;
}

/**
 * Trims the PHRASE side.
 *
 * Removed: whitespace, a stray colon, and an em or en dash. Kept: the full stop, the slash, the
 * question mark and every script's own sentence mark.
 *
 * The full stop is content — `Hai. (''high'')` is the phrase the page recorded, and trimming it to
 * `Hai` is an edit nobody asked for. So is the `/` in `ноль/нуль` and the `。` in
 * `ほっといてくれ。`.
 *
 * The colon and the dash are different: on these pages they are what stands BETWEEN a phrase and
 * its reading, or what a template was adjacent to. `&mdash; mera insurance ho sakta` is a row
 * separator, and `{{Lang|ar|لا يهم}}.` put a template flush against a full stop. Neither is
 * something the traveller says, and both end up welded onto the phrase otherwise.
 *
 * The empty shell a lifted reading leaves behind — the `()` — is also removed, because that is
 * markup this code created rather than markup the page wrote.
 */
function trimBoundary(s) {
  let out = s
    .replace(/^[\s\u2014\u2013\-:]+/, '')
    .replace(/[\s\u2014\u2013\-:]+$/, '')
    .replace(/\s{2,}/g, ' ')
    .trim();
  const wrapped = /^\(([^()]*)\)$/.exec(out);
  if (wrapped && wrapped[1].trim()) out = wrapped[1].trim();
  return out;
}

/**
 * Move a cut point left over whitespace and any opening bracket before it.
 *
 * `(''nohl’'')` puts the italic span's index one character past the `(`, so cutting at the
 * span leaves a bare `(` on the end of the phrase. The bracket is part of the reading's
 * delimiter, not part of the phrase, so the cut moves over it rather than the phrase carrying a
 * bracket that opens nothing.
 */
function extendOverBrackets(s, index) {
  let i = index;
  while (i > 0) {
    const ch = s[i - 1];
    if (/\s/.test(ch)) { i -= 1; continue; }
    if (ch === '(') { i -= 1; continue; }
    break;
  }
  return i;
}

/**
 * Drops the variant gloss a phrase is followed by, before its reading.
 *
 * `谢谢。 (謝謝。) ''Xièxie.''` — the page gives the simplified form, then the traditional in
 * brackets, then the pinyin. Shipping all three shows the traveller two spellings of one word and
 * no signal about which to use.
 *
 * The rule is SCRIPT, not position: a bracket group that contains no Latin letters and whose
 * script is the phrase's own is a variant of the phrase, so it goes. `(高)` after a Han phrase is
 * a variant; `(pèrt)` after a Thai phrase is a reading, and readings are cut before this runs.
 *
 * Applied repeatedly because the Mandarin toilet row lists three variants in a row, and one pass
 * would leave the second and third welded onto the phrase.
 */
function dropVariantGloss(phrase, cutAt) {
  let start = cutAt;
  let text = phrase;
  for (let pass = 0; pass < 4; pass += 1) {
    const at = extendOverBrackets(text, start);
    const before = text.slice(0, at);
    const close = before.lastIndexOf(')');
    if (close < 0) break;
    const open = before.lastIndexOf('(', close);
    if (open < 0) break;
    const inner = before.slice(open + 1, close).trim();
    if (!inner) break;
    const hasLatin = /\p{Script=Latin}/u.test(inner);
    // Script is judged on the phrase's HEAD — everything before its first bracket — and not on
    // the whole string. Judged whole, `Salaam. (''.سلام'')` counts as a non-Latin phrase because
    // the READING inside the brackets is non-Latin, so the bracket holding the reading was
    // classified as a variant of the phrase and deleted. `head` is what the phrase actually is.
    const head = before.slice(0, before.search(/[[(]/));
    const sameScript = NON_LATIN_SCRIPT.test(inner) && NON_LATIN_SCRIPT.test(head);
    if (hasLatin || !sameScript) break;
    text = `${before.slice(0, open)} ${text.slice(at)}`;
    start = open;
  }
  return { text, cutAt: start };
}

/**
 * Keeps only the FIRST of several slash-separated variants.
 *
 * `鸡蛋 (雞蛋) ''jīdàn'' / 蛋 (蛋) ''dàn'' (the former specifically refers to chicken eggs…)` — the
 * Mandarin page lists two words for one concept, each with its own reading, then a note
 * distinguishing them. That is one entry with a nuance, not an utterance.
 *
 * Without this the row exceeded the length guard and was declined outright, which threw away
 * `鸡蛋` — a real word for eggs that a traveller needs. Declining is honest; discarding a correct
 * phrase because the page appended editorial commentary is not. With the first variant kept the
 * row is short, correct, and the nuance is a documentation matter rather than a phrasebook one.
 *
 * Only applied when both sides carry the phrase's own script. A slash INSIDE a phrase — `ноль/нуль`,
 * `WC / KAMAR KECIL` — is part of the phrase and is left alone.
 */
function firstVariant(phrase) {
  const at = phrase.indexOf(' / ');
  if (at <= 0) return phrase;
  const left = phrase.slice(0, at);
  const right = phrase.slice(at + 3);
  if (!NON_LATIN_SCRIPT.test(left) || !NON_LATIN_SCRIPT.test(right)) return phrase;
  return left;
}

/**
 * A parenthesised REGISTER NOTE, in the vocabulary these pages actually use.
 * `(''informal'')`, `(''to a male'')`, `(''high'')`, `(''getting attention'')`. These annotate a
 * phrase; they are never the phrase and never its reading. They are removed from both columns,
 * because an app that shows "Hello. (informal)" as the English gloss is showing an editor's note
 * as though it were the meaning.
 */
const REGISTER_NOTE =
  /\s*[([]\s*(?:to a|to your|male|female|masculine|feminine|informal|formal|polite|rude|blunt|high|low|fast|slow|casual|intimate|respectful|honorific|humile|plural|singular|less formal|more formal|very|quite|instead|only)\b[^)\]]*[)\]]/gi;

/**
 * A `(''...'')` PLACEHOLDER is not a reading, and it is not part of the phrase either.
 *
 * `; tea (''drink'') : teh / tea (''...'')` — the editor wrote dots where a reading would go.
 * Shipped literally, the phrase rendered as "teh / tea (...)" with three dots the traveller would
 * never say. The phrase is kept and the reading is honestly absent.
 *
 * The pattern has no `''` in it because by the time the text reaches here it has been through
 * `cleanWikitext`, which strips italics on the way to removing the markup. A pattern expecting
 * `(''...'')` therefore never matches anything, and the placeholder shipped in every row.
 *
 * At module scope rather than inside `extractReading`, because the Latin-script path needs it and
 * that path returns before the original declaration was reached — a `const` in the temporal dead
 * zone, so reading a Latin-script row threw before it could return anything at all.
 */
const PLACEHOLDER = /\s*\(\s*(?:\.{2,}|…+)\s*\)\s*$/;

/**
 * Extracts the phrase and its romanisation from one phrase row.
 *
 * Returns `{ englishSide, native, pronunciation }`; either of `native`/`pronunciation` may be
 * null. Never guesses: a row with no Latin-bearing italic span and no trailing Latin run yields
 * a null pronunciation, and the caller records a gap rather than inventing one.
 *
 * @param {string} row raw wikitext of a `;` phrase row, including the leading `;`
 */
export function extractReading(
  row,
  { reversed = false, latinScript = false, readingInTargetScript = false } = {},
) {  // Resolved BEFORE the split, because the payload can be either column and the split cannot see
  // inside a template. Balanced after that, for the same reason: an unbalanced `''` changes which
  // column the italics appear in.
  const split = splitRow(balanceItalics(resolveTemplates(row)));
  if (!split) return { englishSide: null, native: null, pronunciation: null, reversed };

  // Entities are decoded HERE, once, so that every index computed below refers to this string
  // and not to a differently-sized copy of it. The bare-reading walk (rule 4) needs cleaned text
  // to find words; it used to clean a copy and then slice the raw one, and because `&mdash;`
  // shrinks from seven characters to one the cut landed mid-entity and left `&m` on the end of
  // the phrase. Decoding is length-changing, so it cannot happen after an index is taken.
  // It is safe here rather than inside `cleanWikitext` because entity decoding does not touch
  // `''`, `{{` or `}}`, which are what `splitRow` and the italic scan rely on.
  let left = decodeEntities(split.englishSide);
  let right = decodeEntities(split.nativeSide);

  /**
   * Direction, decided by evidence rather than by which side looks tidier.
   *
   * Sign tables reverse the column order — the Korean page writes `; 열림 (''yeollim'') : Open`
   * with the Korean FIRST. Parsing that as English-first put "Open" in the native column and the
   * reading "yeollim" in the English column, which for eight Korean signs produced eight entries
   * whose native text was the English word.
   *
   * The caller can assert `reversed` for rows it knows sit inside a reversed infobox. When it
   * does not, the script decides: exactly one side carries the target language's script, and that
   * side is the phrase. When BOTH sides carry it — the Japanese row
   * `;中国 ''Chūgoku'' : China … 「中」` has Han on both — neither side wins on script alone, so
   * the caller's answer stands and no guess is made. Guessing here is what would have moved a
   * Chinese gloss into the phrase column.
   */
  const leftHasScript = NON_LATIN_SCRIPT.test(cleanWikitext(left));
  const rightHasScript = NON_LATIN_SCRIPT.test(cleanWikitext(right));
  if (leftHasScript !== rightHasScript) reversed = rightHasScript === false;

  const nativeSide = reversed ? left : right;
  const otherSide = reversed ? right : left;
  /**
   * The English side is trimmed of WHITESPACE only, never punctuation.
   *
   * `Hello.` is the gloss the page wrote and the app shows it as written; trimming it to `Hello`
   * was an unrequested edit to content. The PHRASE side does get punctuation trimmed, because
   * there the trailing mark is usually the sentence's rather than the utterance's — but that rule
   * is about what the traveller would SAY, and it has no business being applied to a caption.
   */
  const englishSide = cleanWikitext(otherSide)
    .replace(/\}\}\s*$/, '')
    .replace(REGISTER_NOTE, '')
    .replace(/\s{2,}/g, ' ')
    .replace(/\s+([.,;:!?])(?=[\s)\]]|$)/g, '$1')
    .trim();

  /**
   * A LATIN-SCRIPT language has no reading COLUMN to fill, and looking for one manufactures one.
   *
   * The diff against the old parser counted 105 "lost readings" in Portuguese, Indonesian,
   * Swahili, German, French and Turkish. Every one was fabricated: for a Latin language the phrase
   * is already Latin, so a trailing Latin run is just more phrase, and the old parser split
   * "buka" into native `""` plus pronunciation `buka`, or filed an English gloss as a reading.
   * Refusing outright is not a regression — it is the absence of one.
   *
   * BUT THE READING STILL HAS TO BE CUT OUT OF THE PHRASE. These pages write it in italics beside
   * the word exactly like the others — `; CLOSED : Cerrado (''sehr-RAH-doh'')`, `; PUSH :
   * Empuje/Empujar (''ehm-POO-heh/ehm-poo-HAHR'')` — so refusing to look for it left the
   * pronunciation welded to the phrase and the learner was shown a phonetic spelling of a word as
   * though it were a second thing to say.
   *
   * ONLY AN ITALIC ONE. A bare parenthetical in these languages is a semantic gloss, not a
   * pronunciation: `Saya kenyang (setelah makan banyak)` is the phrase "I am full" plus "after
   * eating a lot", and cutting the bracket would delete the traveller's only clue about when the
   * sentence applies. Italics are how every page on Wikivoyage marks a pronunciation, and reading
   * that mark rather than guessing from the shape of the words is the same rule this file applies
   * everywhere else.
   */
  if (latinScript) {
    let cut = null;
    for (const span of italicSpans(nativeSide)) {
      if (looksLikeReading(span.text)) { cut = span.index; break; }
    }
    const body = cut !== null
      ? nativeSide.slice(0, extendOverBrackets(nativeSide, cut))
      : nativeSide;
    const native = trimBoundary(
      firstVariant(
        cleanWikitext(body)
          .replace(REGISTER_NOTE, '')
          .replace(/\s*\(\s*(?:\.{2,}|…+)\s*\)\s*$/, '')
          .replace(/\s*\(\?\)\s*$/, '')
          .replace(/\}\}\s*$/, '')
          .replace(PLACEHOLDER, ''),
      ),
    );
    return {
      englishSide: englishSide || null,
      native: native || null,
      pronunciation: null,
      reversed,
    };
  }

  // An infobox row is the last row before the template closes, so it carries the closing braces
  // onto its own line. `; 금지 (禁止) (''geumji'') : Forbidden}}` — the `}}` is markup that leaked
  // in from the enclosing template, not part of the English, and it shipped as a gloss.

  let cutAt = null;
  let pronunciation = null;
  /**
   * Whether `cutAt` came from a DECLARED reading rather than an inferred one.
   *
   * The two need different treatment further down. A reading found by looking at the phrase's
   * shape may turn out to sit in the MIDDLE of the phrase, and rule 5 splices it out. A reading the
   * page declared with `{{Lang|ar-Latn|…}}` is already at a known offset, and splicing it moved the
   * cut to the wrong place: `{{Lang|ar|سَلاَم}} ''({{Lang|ar-Latn|salām}})'', {{Lang|ar|مَرْحَبًا}} ''…''`
   * became `سَلاَم , مَرْحَبًا ( marḥaban )` — the first phrase, then the second phrase, then the
   * second phrase's own reading, with the first reading nowhere. It also put a Latin transliteration
   * back inside an Arabic phrase, which the font coverage check caught as a missing glyph.
   */
  let cutFromStated = false;

  /**
   * Rule 0: a reading in square brackets on the ENGLISH side.
   *
   * `; 入口 (入口) : Entrance [''rùkǒu'']` — the Mandarin sign table puts the reading after the
   * English, in brackets, because the table is `<phrase> : <English> [<reading>]`. This is the
   * only shape where the reading is not on the phrase side at all, so it is checked first and
   * from the other column. The phrase is unaffected; only the reading comes from over here.
   */
  const bracketed = /\[''\s*([^']+?)\s*''\]/.exec(otherSide);
  if (bracketed && !/^\.{2,}$/.test(bracketed[1].trim())) {
    pronunciation = bracketed[1].trim();
  }

  /**
   * A reading in SQUARE BRACKETS at the end of the phrase, with or without italics.
   *
   * `; Left : 左 [zuǒ]` and `; Entrance : 入口 (入口) [rùkǒu]`. The Mandarin sign table uses square
   * brackets where every other page uses parentheses, and a rule that only knew parentheses
   * returned no reading for the whole table.
   *
   * Required to be TRAILING, for the same reason parentheses are: `[...]` also appears mid-phrase
   * as an editorial aside, and an unanchored bracket picks those up.
   */
  if (!pronunciation) {
    // The inner class spells its brackets escaped on BOTH sides: `[^\][]` closes at the first
    // `]`, becomes "not a bracket, or the empty alternative", and matches nothing — which is how
    // the Mandarin readings came back with their square brackets still attached, "[zuǒ]".
    //
    // `'{0,2}` and NOT `''?`. The latter means "one apostrophe, optionally a second", so it
    // REQUIRES at least one — and a plain `[zuǒ]` with no italics has none, so the whole rule
    // silently never matched the rows it was written for. Read as "zero to two", it matches both
    // `[zuǒ]` and `[''zuǒ'']`.
    const tail = /\[\s*'{0,2}\s*([^\[\]]+?)\s*'{0,2}\s*\]\s*$/.exec(nativeSide);
    if (tail && looksLikeReading(tail[1], 1, readingInTargetScript)) {
      pronunciation = tail[1].trim();
      cutAt = tail.index;
    }
  }

  /**
   * An EXPLICIT reading from the raw row, which outranks every inference on this page.
   *
   * Read before the templates are resolved, because after resolution the two are
   * indistinguishable from ordinary text. `{{pron|q}}` on the Hindi consonant table sits at the
   * END of the ENGLISH column, so looking only at the phrase column missed it entirely; and
   * `{{Lang|ar-Latn|...}}` is the Arabic page's own romanisation column, which is a declaration
   * rather than a shape to be inferred.
   */
  if (!pronunciation) {
    let source = null;
    for (const stated of statedReadings(row)) {
      if (looksLikeReading(stated.text, 1, true)) {
        pronunciation = stated.text;
        source = stated.source;
        break;
      }
    }
    // Cut the phrase at the reading ONLY for `{{Lang|xx-Latn}}`, which sits on the phrase side by
    // construction. `{{pron}}` does not: the Hindi consonant table writes
    // `; क़ q : like s'''k'''ip but further back in the throat{{pron|q}}`, where `q` on the phrase
    // side is the table's OWN notation and the template restates it from the English side.
    // Cutting there produced `क़` — a consonant with no reading beside it — because the search
    // found the table's letter rather than the template's.
    //
    // If the search fails, no cut happens: an over-long phrase is recoverable and a truncated one
    // is not.
    if (pronunciation && source === 'lang-latn') {
      const at = nativeSide.indexOf(pronunciation);
      if (at > 0) { cutAt = at; cutFromStated = true; }
    }
  }

  // 1. The first Latin-bearing italic span. On the Arabic row that is `''utrukni''`, and the
  //    `''(to a male)''` after it is a register note the filter rejects.
  if (!pronunciation) {
    for (const span of italicSpans(nativeSide)) {
      if (looksLikeReading(span.text, 1, readingInTargetScript)) {
        pronunciation = span.text;
        cutAt = span.index;
        break;
      }
    }
  }

  // 2. A parenthesised reading whose italics arrive in pieces: `(''nohl’''/''nool’'')`. Rule 1
  //    already matched the first piece and stopped, which produced the reading "nohl’" for the
  //    Russian word for zero — a transliteration of half a word, which is worse than none. If the
  //    winning span sits inside a bracket group, take the whole group: a slash between two Latin
  //    readings is an alternative spelling of one word, not two words of a phrase.
  if (pronunciation) {
    const start = extendOverBrackets(nativeSide, cutAt);
    if (nativeSide.slice(start).match(/^\s*\(/)) {
      // The bracket is BEHIND the span — `(''nohl’''…` — so this searches backwards. Searching
      // forward found no bracket at all, the branch never ran, and the Russian word for zero
      // kept the transliteration of only its first alternative.
      const open = nativeSide.lastIndexOf('(', cutAt);
      const close = nativeSide.indexOf(')', cutAt);
      if (open >= 0 && close > open) {
        const inner = nativeSide.slice(open + 1, close).replace(/''/g, '').trim();
        const parts = inner.split(/\s*\/\s*/).filter(Boolean);
        if (parts.length > 1 && parts.every(looksLikeReading)) {
          pronunciation = parts.join('/');
          cutAt = open;
        }
      }
    }
  }

  // 3. A parenthesised reading with no italics at all: `; 2 : 이 (i)`. One letter is a real
  //    reading here, so this rule accepts a single Latin character.
  //
  //    ONLY a TRAILING bracket qualifies, and that restriction is load-bearing. On the Hindi row
  //    `… &mdash; mera insurance ho sakta (-ī) hai? (?)` the bracket `(-ī)` was taken as the
  //    reading, because `ī` is a Latin letter — U+012B, LATIN SMALL LETTER I WITH MACRON — and a
  //    one-letter test accepts it. The row then shipped as the phrase plus half the reading
  //    ("… mera insurance ho sakta") with the reading "-ī".
  //
  //    What distinguishes `(-ī)` from `(i)` is not the letters inside but that a reading follows
  //    the bracket: after `(i)` the row ends, and after `(-ī)` the words "hai? (?)" continue.
  //    So the rule is positional — the LAST bracket group, with only punctuation or the `(?​)`
  //    marker after it — and the one-letter allowance stays, because `이 (i)` needs it.
  if (!pronunciation) {
    const groups = [...nativeSide.matchAll(/\(([^()]*)\)/g)];
    const last = groups[groups.length - 1];
    if (last) {
      const tail = nativeSide.slice(last.index + last[0].length);
      const isTrailing = !/\p{L}/u.test(tail.replace(/\(\?\)/g, ''));
      const inner = last[1].replace(/''/g, '').trim();
      if (isTrailing && looksLikeReading(inner, 1)) {
        pronunciation = inner;
        cutAt = last.index;
      }
    }
  }

  // 4. A bare reading with no italics at all. Found by walking the tail of the row and taking the
  //    maximal run of words that contain Latin letters, stopping at the first word that does not:
  //    `اربعة arba`a` gives "arba`a"; the Hindi row gives "mera insurance ho sakta (-ī) hai?"
  //    and stops at the em dash. Walking words rather than matching a pattern is what makes the
  //    backtick and the parenthesised vowel survive.
  //
  //    `(?​)` is removed first. These pages append it to a reading the editor is unsure of, and
  //    it contains no Latin, so leaving it in place stopped the walk dead on the last word and
  //    the Hindi row produced no reading at all — a marker for editorial doubt silently became a
  //    claim that the content was unusable.
  if (!pronunciation) {
    const flat = cleanWikitext(nativeSide).replace(/\s*\(\?\)\s*$/, '');
    const words = flat.split(/\s+/).filter(Boolean);
    let start = words.length;
    for (let i = words.length - 1; i >= 0; i -= 1) {
      if (!/\p{Script=Latin}/u.test(words[i])) break;
      start = i;
    }
    if (start < words.length) {
      const joined = words.slice(start).join(' ').trim();
      // A walk that reaches the START consumed the whole phrase, which means the phrase was
      // already Latin and there was no reading beside it — the Tamil phrasebook writes its
      // phrases in romanisation, so `; Good night : nalliravu` has no reading and the bare
      // "nalliravu" IS the phrase. Without this test the row shipped as native `null` with
      // pronunciation `nalliravu`, which is the phrase filed into the wrong column.
      const consumedEverything = start === 0;
      if (!consumedEverything && looksLikeReading(joined, 2)) {
        pronunciation = joined;
        cutAt = flat.indexOf(words[start]);
      }
    }
  }

  /**
   * Rule 5: the reading sits INSIDE the phrase, so it is spliced out rather than cut at.
   *
   * Two shapes, one rule.
   *
   * `; to die : ''shinu'' (死ぬ) → ''nakunaru'' (亡くなる)` — the Japanese verb table puts the
   * reading first, then the kanji, then an arrow and a second form. Cutting at the reading left
   * nothing behind.
   *
   * `; 0 : ๐ (''suun'') ศูนย์` — the Thai numbers table puts the DIGIT, then the reading, then the
   * spelled-out word. Cutting at the reading returned an empty phrase, so forty-one of the most
   * useful phrases in the language — every number from zero to a billion — came out null. This is
   * the regression the corpus diff caught; an earlier version of this rule required the reading to
   * be at the very START of the phrase side, which held for Japanese and not for Thai.
   *
   * The tell is that script-bearing characters FOLLOW the reading. Whatever precedes it is part of
   * the phrase too, so the reading is removed from the middle and the rest is kept:
   * `๐ ศูนย์` with the reading `suun`. When a reading is followed by nothing, the phrase ends
   * before it and the truncation path is correct.
   *
   * A `→` still ends the phrase: everything from it on is a second form, not more of the same one.
   */
  if (pronunciation && cutAt !== null && !cutFromStated) {
    const closing = nativeSide.indexOf("''", cutAt + 2);
    const after = closing > 0 ? nativeSide.slice(closing + 2) : '';
    if (closing > 0 && NON_LATIN_SCRIPT.test(cleanWikitext(after))) {
      const start = extendOverBrackets(nativeSide, cutAt);
      // The matching CLOSE of the bracket the reading sat in is removed too. Splicing only up to
      // the closing `''` left it behind, and `๐ (''suun'') ศูนย์` came out as "๐) ศูนย์" — a phrase
      // opening no bracket. Skipped over whitespace first, since the page may not have put the
      // bracket flush against the italics.
      let resume = closing + 2;
      while (resume < nativeSide.length && /\s/.test(nativeSide[resume])) resume += 1;
      if (nativeSide[resume] === ')' || nativeSide[resume] === ']') resume += 1;
      // A space is re-inserted between the two halves. `extendOverBrackets` consumed the one that
      // separated them, and gluing the splice back together produced `鸡蛋 (雞蛋)/ 蛋` — the slash
      // welded to the bracket, which then stopped `firstVariant` from recognising a variant
      // separator at all.
      const spliced = `${nativeSide.slice(0, start)} ${nativeSide.slice(resume).trimStart()}`;
      const arrow = spliced.indexOf('→');
      const phrase = trimBoundary(
        firstVariant(
          cleanWikitext(arrow >= 0 ? spliced.slice(0, arrow) : spliced).replace(/\}\}\s*$/, ''),
        ),
      );
      return {
        englishSide: englishSide || null,
        native: phrase || null,
        pronunciation,
        reversed,
      };
    }
  }

  let native = null;
  if (cutAt !== null && cutAt >= 0) {
    const moved = dropVariantGloss(nativeSide, extendOverBrackets(nativeSide, cutAt));
    native = trimBoundary(
      cleanWikitext(moved.text.slice(0, moved.cutAt))
        .replace(REGISTER_NOTE, '')
        .replace(/\}\}\s*$/, '')
        .replace(PLACEHOLDER, ''),
    );
  } else {
    // No cut point: either no reading was found, or the reading came from the OTHER column — the
    // Mandarin sign table's `Entrance [''rùkǒu'']` — in which case the phrase side is whole.
    // The variant gloss still has to go, because `入口 (入口)` is the same word written twice and
    // showing both tells the traveller nothing about which to use. So the rule runs here too,
    // with the end of the phrase side as the cut.
    //
    // `firstVariant` is deliberately NOT applied on either of these two paths, only on the splice
    // path below. Applied here it split `اتركني / اتركيني` — a deliberate "to a male / to a
    // female" pair, both halves of which the traveller may need — down to `اتركني`. A slash is
    // only a variant separator where the row has already proved it carries several readings.
    const whole = dropVariantGloss(nativeSide, nativeSide.length);
    native = trimBoundary(
      cleanWikitext(whole.text)
        .replace(REGISTER_NOTE, '')
        .replace(/\s*\(\?\)\s*$/, '')
        .replace(/\}\}\s*$/, '')
        .replace(PLACEHOLDER, ''),
    );
  }

  if (pronunciation) {
    pronunciation = pronunciation
      // A reading with punctuation welded to its front, because the page put it inside the
      // bracket that also held a full stop: `(''.سلام'')` reads as ".سلام". The full stop belongs
      // to the sentence, not to the reading.
      .replace(/^[\s.,;:!?()]+/, '')
      .replace(/\(\s*\?\s*\)/g, '')
      .replace(/\}\}\s*$/, '')
      // A register word left stranded by an unclosed bracket: "min faDlak) (male" keeps its
      // opening paren from the row and never got a closing one, so the pair cannot be matched as
      // a group and has to be peeled from the tail. Removing the word first, then the leftover
      // bracket, is the only order that works — the reverse leaves the bracket behind.
      .replace(/[\s)]*[([]?\s*(?:male|female|masculine|feminine|plural|singular|polite|informal|formal|honorific|humile)\s*[\)\]]?\s*$/i, '')
      .replace(/[\s)]+$/, '')
      .replace(/^\s*\(\s*|\s*\)\s*$/g, '')
      .replace(/\s*[\(\[]\s*(?:male|female|masculine|feminine|plural|singular|polite|informal|formal)\s*[\)\]]\s*$/i, '')
      .replace(/[.,;:!]+$/, '')
      .replace(/\s{2,}/g, ' ')
      .trim();
  }

  return {
    englishSide: englishSide || null,
    native: native || null,
    pronunciation: pronunciation || null,
    reversed,
  };
}
