#!/usr/bin/env node
/**
 * Wikivoyage phrasebook harvester.
 *
 * WHY THIS EXISTS, AND WHY IT RANKED SECOND
 *
 * Tatoeba was found first and it produced numbers immediately, so the question "is there a
 * better source" stopped being asked. It should have been. Wikivoyage carries a human-written,
 * peer-reviewed phrasebook for ALL TWENTY catalogue languages, CC BY-SA 4.0 -- the same licence
 * the credits screen already discharges -- and it is the `curated` evidence class, the strongest
 * one available.
 *
 * This was missed partly through my own fault and is worth recording: when the harvest reported
 * thirteen concepts with no attestation in ANY language, I wrote that those phrases "have to be
 * authored in every language and will always carry the weaker evidence label". That was an
 * unverified claim stated as fact. It is the project's own failure mode -- the content schema
 * says a bare value without a source is a lint failure precisely so that "I could not find it"
 * stays distinguishable from "it does not exist" -- and I collapsed exactly that distinction.
 * A human had already written most of them.
 *
 * WHY IT IS NOT A REPLACEMENT FOR TATOEBA
 *
 * Coverage is uneven: the Portuguese page is 780KB and the Dari page is 91KB, so some languages
 * can nearly fill a Tier 0 from this alone and others cannot fill a third of it. And Tatoeba
 * carries per-sentence author attribution and a much larger pool. They are used together, and
 * where both contain a phrase they are INDEPENDENT, which is the only route to
 * `attested_corroborated` -- a level nothing in the catalogue currently reaches.
 *
 * THE FORMAT IS ACTUALLY GOOD
 *
 * Phrase rows are `; English : Native (''pronunciation'')`, grouped under headings that are the
 * traveller scenarios directly: Greetings, Numbers, Money, Shopping, Eating, Transportation,
 * Lodging, Problems. That is the same shape as the app's Tier 0 intent, and it is the grouping a
 * corpus cannot give you -- Tatoeba sentences arrive flat and unordered.
 *
 * Licence: Wikivoyage text is CC BY-SA 4.0. Attribution is a licence obligation, so the page is
 * recorded on every row and reaches the credits screen.
 */

import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = new URL('../..', import.meta.url).pathname;
const OUT = join(ROOT, 'catalogue', 'curated');

const API = 'https://en.wikivoyage.org/w/api.php';
const UA = { 'User-Agent': 'LangKraft research (offline phrasebook project)' };

/**
 * Wikivoyage page titles, per catalogue language.
 *
 * Not derivable from the ISO code: Mandarin and Chinese are the same page, Dari has its own, and
 * Indonesian is `Indonesian`. Measured by probing rather than assumed, after the `fas`/`pes`
 * incident taught me not to trust a code convention.
 */
/**
 * Languages whose phrasebook infobox sign lists put the NATIVE side first.
 *
 * Measured per language, by reading the markup, because the convention is not consistent and
 * assuming it produces transposed columns:
 *
 *   Indonesian  {{infobox}}  `; BUKA : Open`        NATIVE first
 *   Swahili     {{infobox}}  `; OPEN : Fungua`      English first
 *
 * Both are Latin-script, so the script check cannot arbitrate: "BUKA : Open" and "Open : BUKA"
 * are entirely Latin either way. The only remaining signal is which template the row is in,
 * combined with a per-language table of which way that template reads.
 *
 * This was measured wrong once already -- Swahili was marked reversed when it is not -- so the
 * table is data, visible, and re-checkable rather than a fact buried in the parser.
 */
const INFOBOX_REVERSED = new Set(['ind']);

/**
 * Characters that mark a side as being in a non-Latin target script.
 *
 * Used to decide which half of a phrase row is the phrase and which is the gloss, independent of
 * which template the row came from. Defined at module scope because the `{{Lang}}` override path
 * needs it before the row-splitting code runs.
 */
const NON_LATIN_SCRIPT = /[\u3040-\u30ff\u4e00-\u9fff\uac00-\ud7af\u0e00-\u0e7f\u0600-\u06ff\u0900-\u097f]/;

const PAGE_TITLES = {
  ind: 'Indonesian phrasebook',
  spa: 'Spanish phrasebook',
  hin: 'Hindi phrasebook',
  fra: 'French phrasebook',
  ara: 'Arabic phrasebook',
  swh: 'Swahili phrasebook',
  tam: 'Tamil phrasebook',
  por: 'Portuguese phrasebook',
  nld: 'Dutch phrasebook',
  ita: 'Italian phrasebook',
  deu: 'German phrasebook',
  srp: 'Serbian phrasebook',
  tur: 'Turkish phrasebook',
  fas: 'Dari phrasebook',
  tha: 'Thai phrasebook',
  rus: 'Russian phrasebook',
  jpn: 'Japanese phrasebook',
  kor: 'Korean phrasebook',
  vie: 'Vietnamese phrasebook',
  cmn: 'Chinese phrasebook', // Mandarin shares the Chinese page
};

/**
 * Wikivoyage section heading -> our concept ids.
 *
 * The headings are already traveller scenarios, so this mapping is mostly a rename rather than a
 * classification. `null` means the section is not a phrase list -- Pronunciation, Grammar and
 * Differences are prose or notation and must not be mined for phrases.
 */
const SECTION_CONCEPTS = {
  basics: 'basics',
  greetings: 'greeting',
  courtesy: 'courtesy',
  problems: 'problem',
  numbers: 'numbers',
  cardinal_numbers: 'numbers',
  ordinal_numbers: 'numbers',
  dates: 'time',
  time: 'what_time',
  money: 'price',
  banking: 'price',
  shopping: 'shopping',
  eating: 'food',
  drinking: 'food',
  accommodation: 'hotel',
  lodging: 'hotel',
  transportation: 'transport',
  directions: 'directions',
  phrases: null,
  useful_phrases: null,
  numbers_and_fractions: 'numbers',
  small_talk: 'basics',
  introductions: 'greeting',
  survival: 'survival',
  emergencies: 'emergency',
  health: 'doctor',
  at_the_doctor: 'doctor',
  police: 'police',
  understanding: 'understand',

  // Added after the unmapped-section report showed these were being dropped silently. Thai's
  // `greeting_and_leavetaking` alone holds the register material the Thai spec relies on, and
  // `at_the_doctors` is where the pharmacy and doctor concepts live -- two of the thirteen that
  // Tatoeba could not fill. A dropped section is worse than a wrong one: it is invisible.
  greeting_and_leavetaking: 'courtesy',
  greetings_and_leave_taking: 'courtesy',
  greeting_and_leave_taking: 'courtesy',
  leave_taking: 'courtesy',
  cultural_notes: null,
  forms_of_address: 'people',
  interrogatives: 'basics',
  writing: null,
  honorifics: 'register',
  respectful_form: 'register',
  humble_form: 'register',
  polite_form: 'register',
  respectful_language: 'register',
  typical_japanese_expressions: 'register',
  asking_about_language: 'understand',
  learning_more: 'basics',
  explaining_symptoms: 'doctor',
  allergies: 'doctor',
  medical_emergencies: 'emergency',
  extreme_weather: 'emergency',
  on_the_phone: 'basics',
  talking_about_your_own_family: 'people',
  talking_about_anothers_family: 'people',
  booking_tickets: 'ticket',
  good_afternoon: 'greeting',
  good_morning: 'greeting',
  good_evening: 'greeting',
  minutes: 'time',
  time_and_date_format: 'time',
  country_names: 'basics',
  place_names: 'basics',
  nationalities_countries: 'people',
  public_transport: 'transport',
  sinokorean_numbers: 'numbers',
  native_korean_numbers: 'numbers',
  number_of_days: 'time',
  days_of_the_week: 'time',
  days_of_the_month: 'time',
  days_gnler: 'time',
  authorities: 'police',
  dietary_requirements: 'food',
  asking_questions: 'basics',
  useful_is: 'basics',
  things: 'basics',
  days_of_the_week: 'time',
  bars_and_clubs: 'food',
  at_the_airport: 'transport',
  holidays: 'time',
  seasons: null,
  adjectives: null,
  taxi: 'transport',
  at_the_doctors: 'doctor',
  at_the_doctor: 'doctor',
  health_and_hygiene: 'doctor',
  getting_help: 'problem',
  useful_numbers: 'emergency',
  asking_for_directions: 'directions',
  navigation: 'directions',
  trains_and_buses: 'transport',
  bus_and_train: 'transport',
  taxis_and_ridesharing: 'transport',
  getting_around: 'transport',
  money_and_banks: 'price',
  prices_and_bargaining: 'price',
  buying_something: 'shopping',
  markets: 'shopping',
  at_the_market: 'shopping',
  food_and_drink: 'food',
  eating_and_drinking: 'food',
  at_a_restaurant: 'food',
  restaurants: 'food',
  accommodation_and_hotels: 'hotel',
  hotels_and_lodging: 'hotel',
  checking_in: 'hotel',
  name_and_address: 'address',
  asking_directions: 'directions',
  questions: 'basics',
  useful_words: 'basics',
  common_words: 'basics',
  courtesy_and_modesty: 'courtesy',
  communication: 'survival',
  safety: 'emergency',
  lost_and_found: 'lost',
  police_and_emergencies: 'emergency',
  ordinal: 'numbers',
  numbers_and_counting: 'numbers',
  clock_time: 'what_time',
  time_and_dates: 'time',
  dates_and_time: 'time',
  days: 'time',
  months: 'time',
  duration: 'time',
  writing_time_and_date: 'time',
  colours: 'basics',
  colors: 'basics',
  pronouns: 'basics',
  family: 'people',
  titles_and_addresses: 'people',
  people: 'people',
  country_and_territory_names: 'basics',
  other_words: 'basics',
  animals: 'basics',
  body: 'doctor',
  clothing: 'shopping',
  driving: 'transport',
  authority: 'police',
  bars: 'food',
  adult_talking: 'people',
  on_safari: 'travel',
  nature: 'travel',

  pronunciation: null,
  grammar: null,
  differences_with_malay: null,
  abbreviations: null,
  false_friends: null,
  vocabulary_differences: null,
  note: null,
};

/**
 * Cleans wikitext down to plain text.
 *
 * Strips templates, links, markup. `nowiki` wrappers exist specifically so that a quoted
 * apostrophe survives rendering, and they must go before any length or quote test, or phrases
 * like "Terima kasih." carry an invisible wrapper that breaks nothing visible and everything
 * comparable.
 */
export function cleanWikitext(s) {
  return (s ?? '')
    .replace(/<nowiki>/g, '')
    .replace(/<\/nowiki>/g, '')
    .replace(/\{\{[^{}]*\}\}/g, '')      // innermost templates first
    .replace(/\{\{[^{}]*\}\}/g, '')
    // [[target|anything|anything|label]] -> label. Wikivoyage file links carry an extra
    // dimension: [[File:x.jpg|thumb|350px|caption]], so matching only two segments leaves
    // "thumb|caption" as the phrase text.
    .replace(/\[\[[^\]|]*\|(?:[^\]|]*\|)*([^\]|]*)\]\]/g, '$1')
    .replace(/\[https?:\/\/[^\s\]]+\s+([^\]]*)\]/g, '$1')
    .replace(/''{1,3}/g, '')
    .replace(/&nbsp;/g, ' ')
    .replace(/&rarr;/g, '→')
    .replace(/&amp;/g, '&')
    .replace(/&[a-z]+;/g, ' ')
    .replace(/<ref[^>]*>[\s\S]*?<\/ref>/g, '')
    .replace(/<[^>]+>/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

/**
 * Parses one phrase row.
 *
 * THE SAME FILE USES BOTH ORDERS, which cost a round of debugging and is worth stating plainly:
 *
 *   ; Hello. : Halo. (''HAH-loh'')        English first -- the normal case
 *   ; BUKA : Open                        Native first -- inside {{infobox}} sign lists
 *
 * Both appear on the Indonesian page, minutes apart. Splitting on the first colon and assuming
 * English-then-native therefore transposes every sign in the infoboxes: "BUKA" became the
 * English gloss and "Open" the thing you say. So `reversed` is threaded through from the caller,
 * which knows whether the row is inside an infobox, and a script check catches the rest.
 *
 * The pronunciation in `(''...'')` is extracted rather than discarded, because for a language
 * the learner cannot read in its own script it is the only usable form, and dropping it would
 * throw away the most valuable column on the page.
 *
 * Returns null when the row cannot be read as a phrase pair -- which happens, and silently
 * accepting those is how a harvester fills with prose fragments.
 */
export function parsePhraseRow(row, reversed = false) {
  if (!row) return null;

  /**
   * Fill-in-the-blank rows are TEMPLATES, not phrases.
   *
   * `; How do I get to _____ ? : _____ ku eppadi pOvathu?` and `; ...the train station? : ...
   * pugai vandi nilayam` are patterns for building a sentence, not things anyone says. Shipping
   * them puts a row of underscores in front of a learner, and the underscores are not in any
   * language. Rejected rather than harvested: the concept is real, but this row is not its
   * realisation.
   *
   * The ellipsis prefix is the tell -- a real phrase does not begin with "...".
   */
  // A continuation row. `; ...bedsheets? : ...pOrvai (''...'')` completes the previous phrase,
// and `; Does the room come with... : ... roomOda varumaa? (''...'')` inherits from it.
  //
  // The test is on the NATIVE side after the colon, not on the row. An earlier version tested the
  // English side and concluded the row was fine, because "Does the room come with" does contain
  // letters -- it always does. What marks a continuation is the phrase itself beginning with an
  // ellipsis, and that ships as "... roomOda varumaa?", which is an ellipsis, a noun and a
  // verb with nothing to say what completes it.
  const colonAt = row.indexOf(':');
  if (colonAt > 0) {
    const nativeSide = row.slice(colonAt + 1);
    if (/^\s*(?:\{\{[^}]*\}\}\s*)?\.{2,}/.test(nativeSide)) return null;
  }
  if (/^\s*\.{2,}/.test(row)) return null;

  // A blanked word slot makes the row a template whatever it starts with. The Tamil page also
  // writes `; How do I get to _____ ? : _____ ku eppadi pOvathu?`, where the phrase begins with
  // the blank rather than an ellipsis, so the ellipsis test above does not catch it. Underscore
  // runs are the giveaway: five or more, since single underscores appear inside words.
  if (/_{4,}/.test(row)) return null;

  // Pronunciation FIRST, from the raw wikitext.
  //
  // cleanWikitext strips '' italic markup, turning "(''HAH-loh'')" into "(HAH-loh)" -- so
  // looking for the '' markers in cleaned text can never match. Reading the pronunciation after
  // cleaning silently returned null for every row and discarded the most valuable column on the
  // page: for Thai, Japanese, Mandarin and Tamil it is the only usable form of the phrase, since
  // the learner cannot read the native script yet.
  const pronRaw = /\(''\s*([^']+?)\s*''\)/.exec(row)?.[1] ?? null;
  const pronTpl = /\{\{\s*pron\s*\|\s*([^}|]+)/i.exec(row)?.[1]?.trim() ?? null;

  // A PLACEHOLDER, not a pronunciation.
  //
  // The Tamil page writes its fill-in-the-blank rows as `(''...'')`:
  //   `; ...the train station? : ...pugai vandi nilayam (''...'')`
  // Taken literally that became the pronunciation "...", and the phrase then rendered as
  // "...pugai vandi nilayam (...)" -- which is what shipped into a content file until the
  // linter complained about a missing romanization and led here. Placeholders are dropped
  // rather than carried, so the phrase is kept and the missing column is honestly missing.
  const isPlaceholder = (v) => !v || /^\.{2,}$/.test(v.trim()) || /^[\s.…_-]+$/.test(v.trim());
  const pron = isPlaceholder(pronRaw) ? null : pronRaw;
  let pronunciation = pron ?? (isPlaceholder(pronTpl) ? null : pronTpl);

  // The Arabic page nests the register note INSIDE the pronunciation parentheses:
  // `; Excuse me.: {{Lang|ar|إسمحلي}}  ''(min faDlak)''` is polite, while
  // `''(rubbamaa)''` is bare. The leading token of the parenthetical is the register when it is
  // one of a small known set, and dropping it loses exactly the information the app exists to
  // show. It is recorded on the row rather than discarded.
  const pronRegister = /\(\s*(informal|formal|polite|male|female|masculine|feminine|honorific|humile)\b/i
    .exec(pronunciation ?? '')?.[1] ?? null;

  /**
   * Resolve `{{Lang|<script>|<text>}}`.
   *
   * This template exists on the Arabic and Persian pages to mark which part of the row is in
   * the target script: `; Yes.: {{Lang|ar|نَعَمْ}}  ''na'am''`. Template-stripping removed it,
   * so the native column came out as `na'am` -- the romanisation, in Latin letters, filed as
   * though it were the Arabic. Every Arabic and Dari phrase was wrong in the same way, and the
   // symptom was an apparently plausible file with zero correct script.
   */
  const langTpl = /\{\{\s*Lang\s*\|[^|]*\|\s*([^{}|]+)\}\}/i.exec(row)?.[1]?.trim() ?? null;
  let nativeOverride = langTpl;

  // Arabic sometimes writes the pronunciation bare rather than in italics:
  // `; Maybe.: {{Lang|ar|رُبَّمَا}}  ''(rubbamaa)''` still has the italics, but
  // `; Excuse me.: {{Lang|ar|إسمحلي}}  ''(min faDlak)''` puts the register note inside the
  // parentheses alongside the pronunciation, so the tail is kept verbatim and left for a reader.
  // Bare-paren pronunciation, restricted to rows whose PHRASE is in a non-Latin script.
  //
  // The restriction is the whole point. In Thai, Japanese, Mandarin, Arabic, Hindi and Tamil the
  // native script cannot be read by a learner who has not learned it, so the romanisation is the
  // only usable form of the phrase. In Indonesian or Swahili, a trailing "(after a big meal)" is
  // part of the phrase itself, and treating it as a pronunciation amputates content the app would
  // otherwise render. The two cases look identical in the markup and are told apart only by the
  // script of the text before the parenthesis.
  if (!pronunciation && !nativeOverride && NON_LATIN_SCRIPT.test(row)) {
    const bare = /\(\s*([^()]+?)\s*\)\s*$/.exec(row);
    // Checked for placeholder-ness too, because the same pages use a bare "(...)" where the
    // italic form would hold real text, and taking it literally rendered "...pugai vandi
    // nilayam (...)" into a content file.
    if (bare && !isPlaceholder(bare[1]) && /\p{Script=Latin}/u.test(bare[1])) {
      pronunciation = bare[1].trim();
    }
  }

  let text = cleanWikitext(row);
  if (!text) return null;

  // Remove the pronunciation tail, but only the specific shapes we recognise, so a genuine
  // parenthetical inside the phrase is not amputated. A blanket `/\([^)]*\)$/` strip was tried
  // and destroyed real content; it also ran when the pronunciation had not been found, which is
  // when the parentheses are most likely to belong to the phrase.
  if (pronunciation || pronRaw || pronTpl) {
    // Strip whenever a pronunciation slot was PRESENT, even when its content was rejected as a
    // placeholder. Keying the strip on `pronunciation` being non-null left the discarded
    // "(...)" attached to the phrase, so the entry rendered as "Ethavathu speciala irruka (...)"
    // -- which is the exact artefact the placeholder check exists to remove.
    text = text
      // Strip the register note first, so a trailing "(male)" cannot block the pronunciation
      // parenthesis from being recognised as one shape.
      .replace(/\s*\(?(male|female|masculine|feminine|plural|singular)\)?\s*$/i, '')
      .replace(/\s*\(''[^']*''\)\s*$/, '')     // still italic at this point
      .replace(/\s*\(\s*(?:\.{2,}|[.…_-]{2,})\s*\)\s*$/, '') // discarded placeholder
      .replace(/\s*\([A-Za-z][^()]*\)\s*$/, '') // bare parenthetical, e.g. (pèrt)
      .trim();
  }

  const sep = text.indexOf(':');
  if (sep <= 0) return null;

  let left = text.slice(0, sep).trim();
  let right = text.slice(sep + 1).trim();

  // Trailing annotation in parentheses is commentary, not part of the phrase.
  right = right.replace(/\s*\((?:informal|formal|Muslim|polite)[^)]*\)\s*$/i, '').trim();
  left = left.replace(/\s*\((?:informal|formal|Muslim|polite)[^)]*\)\s*$/i, '').trim();

  if (!left || !right) return null;
  if (right.length > 90) return null;
  if (!/\p{L}/u.test(right)) return null;

  let english = reversed ? right : left;
  let native = reversed ? left : right;

  // `{{Lang|<script>|<text>}}` is AUTHORITATIVE: its payload is the target-script text, full
  // stop. The rest of the row is a gloss or a romanisation.
  //
  // Without this, template-stripping left `; Yes.: {{Lang|ar|نَعَمْ}} ''na'am''` with no Arabic
  // anywhere in the row, so the script check found nothing on either side and the parser filed
  // "Yes." as BOTH the English and the native text. Every Arabic and Dari row was wrong in the
  // same way, and the file looked perfectly plausible.
  if (nativeOverride) {
    native = nativeOverride;
    // English is the side that is NOT the template payload. In the normal `English : Native`
    // layout that is `left`; reversed layouts put it on the right.
    const candidate = reversed ? right : left;
    if (candidate && candidate !== native) {
      english = candidate.replace(/\s*\([^)]*\)\s*$/, '').trim();
    }
    // Anything left after the payload is the romanisation.
    if (!pronunciation) {
      const tail = reversed ? left : right;
      const tailClean = cleanWikitext(tail).replace(/^\(|\)$/g, '').trim();
      // The Arabic page writes `; Please. :{{Lang|ar|من فضلك}}:(''min faDlak) (male)''` --
      // no space before the colon, and the register note trails the pronunciation inside the
      // same parentheses. Both artifacts have to come off or the pronunciation column reads
      // ":(min faDlak) (male", which is what it did.
      const tidy = tailClean
        .replace(/^[:\s]+/, '')
        .replace(/['"]+$/, '')
        .replace(/\s*\(?(male|female|masculine|feminine|plural|singular)\)?\s*$/i, '')
        .replace(/['"]+$/, '')
        .trim();
      if (tidy && tidy !== native && /\p{Script=Latin}/u.test(tidy)) {
        pronunciation = tidy;
      }
    }
  }

  // Independent check that does not depend on template context: for a non-Latin language, the
  // side carrying the target language's script is the native one. If that contradicts the
  // template's ordering, the script wins -- a row that says "Hello : 你好" in an English-first
  // context is still an English-first row.
  const NON_LATIN = NON_LATIN_SCRIPT;
  if (NON_LATIN.test(left) !== NON_LATIN.test(right)) {
    const leftIsNative = NON_LATIN.test(left);
    english = leftIsNative ? right : left;
    native = leftIsNative ? left : right;
  }

  if (!/\p{L}/u.test(native)) return null;

  return {
    english: english.trim(),
    native: native.trim(),
    pronunciation,
    register: pronRegister,
  };
}

/** Fetches a page's wikitext. */
async function fetchWikitext(title) {
  const url =
    `${API}?action=query&prop=revisions&rvslots=*&rvprop=content` +
    `&titles=${encodeURIComponent(title)}&format=json&formatversion=2`;
  const res = await fetch(url, { headers: UA });

  // Rate limiting and 404 look identical from here: both are "no wikitext". A missing page and
  // a throttled request must not be reported as the same thing, because one is a real gap and
  // the other is a retry, and treating a retry as a gap loses a language. The first full run
  // reported 11 of 20 languages as "not found" purely because they were fetched too fast.
  if (res.status === 429) return { retry: true };
  if (!res.ok) return { missing: res.status === 404 };
  const d = await res.json();
  const page = d?.query?.pages?.[0];
  if (!page || page.missing || !page.revisions?.length) return { missing: true };
  return page.revisions[0].slots.main.content;
}

/**
 * Harvests one phrasebook.
 *
 * Walks the wikitext tracking the current `==Section==` so each phrase is filed under the
 * traveller scenario it was written for, then parses only the phrase rows inside sections that
 * are actually phrase lists.
 */
async function harvest(code) {
  const title = PAGE_TITLES[code];
  if (!title) return { rows: [], note: `no page title mapped for ${code}` };

  const fetched = await fetchWikitext(title);

  // Retry with backoff before concluding anything. Three attempts, because a throttled run
  // that reports "not found" is indistinguishable from a genuinely absent page.
  let wikitext = typeof fetched === 'string' ? fetched : null;
  let lastStatus = typeof fetched === 'object' ? (fetched.retry ? 'throttled' : 'missing') : null;
  for (let attempt = 0; attempt < 3 && !wikitext; attempt += 1) {
    await new Promise((r) => setTimeout(r, 3000 * (attempt + 1)));
    const again = await fetchWikitext(title);
    if (typeof again === 'string') wikitext = again;
    else lastStatus = again?.retry ? 'throttled' : 'missing';
  }
  if (!wikitext) return { rows: [], note: `page "${title}" ${lastStatus} after retries` };

  const rows = [];
  const seen = new Set();
  let section = null;
  let inPhraseList = false;
  let inInfobox = false;
  let braceDepth = 0;
  let skippedSections = [];

  for (const raw of wikitext.split('\n')) {
    const line = raw.trim();

    // Track template nesting so rows inside an {{infobox}} can be recognised. Infobox sign
    // lists reverse the column order, which is the single easiest thing to get wrong here.
    const opens = (line.match(/\{\{/g) ?? []).length;
    const closes = (line.match(/\}\}/g) ?? []).length;
    if (braceDepth === 0 && /^\{\{\s*infobox\b/i.test(line)) inInfobox = true;
    braceDepth += opens - closes;
    if (braceDepth <= 0) { braceDepth = 0; inInfobox = false; }

    // Top-level section: decide whether we are inside the phrase list.
    //
    // Matched on "phrase" appearing anywhere in the heading, not on the exact string
    // "Phrase list". Eleven pages call it that; the Hindi page calls its equivalent
    // "Hindi Phrases" and returned zero phrases while being reported as a successful harvest of
    // an empty page. An exact-match gate silently drops whole languages, and the harvester
    // reports nothing wrong -- the same failure shape as the CJK selector bug.
    if (/^==\s*[^=].*==\s*$/.test(line)) {
      const heading = cleanWikitext(line.replace(/^=+|=+$/g, ''));
      inPhraseList = /phrases?/i.test(heading);
      section = null;
      continue;
    }
    // Sub-headings inside the phrase list name the scenario.
    const sub = /^={3,}\s*([^=]+?)\s*={3,}$/.exec(line);
    if (sub) {
      if (!inPhraseList) continue;
      const key = cleanWikitext(sub[1])
        .toLowerCase()
        .replace(/[^a-z ]/g, '')
        .trim()
        .replace(/\s+/g, '_');
      const mapped = Object.prototype.hasOwnProperty.call(SECTION_CONCEPTS, key)
        ? SECTION_CONCEPTS[key]
        : undefined;
      if (mapped === undefined) {
        // An unmapped heading is a real gap in our mapping, and hiding it would let a whole
        // section of phrases vanish without trace.
        skippedSections.push(key);
        section = null;
      } else {
        section = mapped;
      }
      continue;
    }

    if (!inPhraseList || !section) continue;
    if (!line.startsWith(';')) continue;

    const parsed = parsePhraseRow(line.slice(1), inInfobox && INFOBOX_REVERSED.has(code));
    if (!parsed) continue;

    // Deduplicate on the native text: many phrasebooks list a form in Basics and again under
    // Greetings, and shipping it twice teaches the learner the phrase has two meanings.
    const key = `${parsed.native}|${parsed.english}`;
    if (seen.has(key)) continue;
    seen.add(key);

    // A row whose phrase came out in Latin script for a language that has its own script is a
    // romanization-only row: the Tamil page writes `; Is there a house specialty? : ''Ethavathu
    // speciala irruka''` with no Tamil characters at all. The text is real and usable, but it is
    // a romanisation, and recording it as `native` would leave the entry with no target-script
    // text and no separate romanization column -- which is exactly what the content linter
    // rejects. Flagged here so the caller can decide, rather than silently mislabelled.
    const nativeIsLatin = /^\p{Script=Latin}[\p{Script=Latin}\p{M}\p{N}\s\p{P}]*$/u.test(parsed.native);

    rows.push({
      code,
      romanized_only: nativeIsLatin,
      native: parsed.native,
      english: parsed.english,
      pronunciation: parsed.pronunciation,
      concept: section,
      source_page: title,
      licence: 'CC BY-SA 4.0',
      // Recorded so the credits screen can name the page, which the licence requires.
      source_url: `https://en.wikivoyage.org/wiki/${encodeURIComponent(title)}`,
    });
  }

  return { rows, note: skippedSections.length ? `unmapped sections: ${[...new Set(skippedSections)].join(', ')}` : null };
}

async function main() {
  if (!existsSync(OUT)) mkdirSync(OUT, { recursive: true });
  const catalogue = JSON.parse(readFileSync(join(ROOT, 'catalogue', 'languages.json'), 'utf8'));

  const only = process.env.ONLY ? process.env.ONLY.split(',') : null;
  const report = {};

  for (const lang of catalogue.languages) {
    if (lang.code === catalogue.calibration) continue;
    if (only && !only.includes(lang.code)) continue;

    // Be a good citizen of a volunteer-run site. 1.5s between pages is far under what the
    // harvester could take and far above what risks another throttle.
    await new Promise((r) => setTimeout(r, 1500));
    const { rows, note } = await harvest(lang.code);
    const path = join(OUT, `${lang.code}.jsonl`);

    // NEVER overwrite a good harvest with an empty one.
    //
    // A throttled or partially-failed run returned zero rows for Spanish and Turkish and
    // overwrote 466 and 299 phrases with nothing. The command printed "0 phrases" and exited
    // zero, so nothing downstream could tell the difference between "this page has no phrases"
    // and "this run failed". Two independent sources of that mistake already: a null-result
    // language reported as a successful empty harvest, and 11 languages reported missing.
    const existing = existsSync(path)
      ? readFileSync(path, 'utf8').split('\n').filter(Boolean)
      : [];
    if (!rows.length && existing.length) {
      console.log(
        `  ${lang.code} ${lang.name}: KEPT existing ${existing.length} phrases; this run ` +
          `produced none (${note ?? 'no reason recorded'})`,
      );
      report[lang.code] = { rows: existing.length, kept: true, note };
      continue;
    }
    writeFileSync(path, rows.map((r) => JSON.stringify(r)).join('\n') + (rows.length ? '\n' : ''));

    const concepts = new Set(rows.map((r) => r.concept));
    report[lang.code] = { rows: rows.length, concepts: concepts.size, note };
    console.log(
      `  ${lang.code} ${lang.name.padEnd(12)} ${String(rows.length).padStart(4)} phrases, ` +
        `${concepts.size} scenarios${note ? `  [${note}]` : ''}`,
    );
  }

  writeFileSync(join(OUT, '_report.json'), JSON.stringify(report, null, 2) + '\n');
  console.log('  -> catalogue/curated/*.jsonl');
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});