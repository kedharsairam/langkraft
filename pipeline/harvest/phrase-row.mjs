/**
 * The phrase-row parser the harvester actually calls.
 *
 * WHAT THIS IS
 *
 * A thin shell around `extractReading` (`reading.mjs`), plus the four rejections that are about
 * whether a row is a PHRASE rather than about how a phrase is written.
 *
 * That split is the whole point. The previous parser was one 400-line function that decided both
 * things, and so the two concerns got patched against each other six times: widening a character
 * class to capture a reading also captured a phrase, and rejecting a template row also discarded
 * the `{{Lang}}` payload that held the Arabic. Reading lives in `reading.mjs` and is tested there
 * against 39 verbatim rows from the pages. This file decides only what is a phrase.
 *
 * WHAT STAYS HERE, AND WHY EACH ONE
 *
 *  - A row with no separator is not a phrase row.        (structural)
 *  - A row that is really a fill-in-the-blank TEMPLATE.   (not something anyone says)
 *  - A row whose phrase is a wall of text.                (a scraped fragment, not an utterance)
 *  - A row whose "phrase" has no letters in it.           (a number, a symbol)
 *
 * None of these are judgements about language. That is what keeps them here rather than in the
 * extractor, where they would be re-litigated every time a new language appeared.
 *
 * WHAT IS NOT HERE ANY MORE
 *
 * `{{Lang}}` resolution, the romanisation bracket, the inline two-script split, the bare trailing
 * Latin run, the register-note filter, the three-part Mandarin row, the `<sup>` and `&mdash;`
 * handling, and the eight separate "and then this language broke it" repairs. All of that is in
 * `extractReading`, where it is covered by named tests naming the language each rule came from.
 */

import { extractReading } from './reading.mjs';

/**
 * Longest phrase accepted, in characters.
 *
 * A phrase a traveller utters is short. A run of several hundred characters is a paragraph that
 * some other template leaked into a phrase row, and it is worse to ship it than to drop it —
 * nothing in the app can present it, and it displaces a real phrase in the tier.
 *
 * Kept deliberately blunt rather than language-aware. A per-language length would be one more
 * per-language fact to forget, and the failure it guards against is not subtle.
 */
const MAX_PHRASE_CHARS = 100;

/**
 * Parses one phrase row.
 *
 * @param {string} row    raw wikitext of a `;` phrase row, with or without the leading `;`
 * @param {boolean} reversed true when the page's layout puts the target language FIRST — the
 *        sign tables in several infoboxes do. Threaded from the harvester, which tracks whether
 *        the row sits inside a reversed infobox; the extractor will also work it out from script
 *        presence when the caller does not say.
 * @param {{latinScript?: boolean, readingInTargetScript?: boolean}} [options]
 *        Per-language facts the harvester knows and this file cannot infer. `latinScript` means the
 *        phrases are written in Latin so there is no reading column to find; `readingInTargetScript`
 *        means the reading is in the language's own script rather than transliterated (Dari).
 * @returns {{english: string, native: string, pronunciation: string|null}|null}
 */
export function parsePhraseRow(row, reversed = false, options = {}) {
  if (!row) return null;

  /**
   * A row that begins with an ellipsis, or whose PHRASE begins with one, is a CONTINUATION.
   *
   * `; ...bedsheets? : ...pOrvai` completes the row above it, and
   * `; Does the room come with... : ... roomOda varumaa?` inherits from it. Shipped as phrases
   * these are an ellipsis, a noun and a verb with nothing to say what completes them.
   *
   * Tested on the phrase side after the separator, not on the row. An earlier version tested the
   * English side and passed every row, because English text always contains letters.
   */
  const colonAt = row.indexOf(':');
  if (colonAt > 0 && /^\s*(?:\{\{[^{}]*\}\}\s*)?\.{2,}/.test(row.slice(colonAt + 1))) return null;
  if (/^\s*\.{2,}/.test(row)) return null;

  /**
   * A blanked word slot makes the row a template whatever else it looks like.
   *
   * `; How do I get to _____ ? : _____ ku eppadi pOvathu?` starts with the blank rather than an
   * ellipsis, so the test above misses it. Underscore runs are the tell — five or more, since
   * single underscores occur inside words.
   */
  if (/_{4,}/.test(row)) return null;

  // No separator means no columns, so there is nothing to read.
  if (colonAt <= 0) return null;

  const got = extractReading(row, { reversed, ...options });
  if (!got.native) return null;
  if (!/\p{L}/u.test(got.native)) return null;
  if (got.native.length > MAX_PHRASE_CHARS) return null;

  return {
    english: got.englishSide ?? '',
    native: got.native,
    pronunciation: got.pronunciation,
  };
}
