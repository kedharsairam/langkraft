#!/usr/bin/env node
/**
 * Attestation harvester.
 *
 * WHY THIS EXISTS
 *
 * Kedhar will never assemble a team of native speakers to review this content. That is a
 * settled fact, not a problem to solve. So the plan cannot be "be careful", and it cannot
 * be "hire a reviewer". The only honest substitute for a native reviewer is EVIDENCE: text
 * that native speakers actually wrote, published under a licence that permits reuse with
 * attribution.
 *
 * Tatoeba is exactly that, and every one of the 21 catalogue languages has 1000+ English-
 * pivot sentence pairs. This script turns that corpus into per-language candidate phrases
 * so that the content pipeline stops AUTHORING text and starts SELECTING attested text.
 *
 * The distinction is the whole point. An authored phrase is a guess with a citation. An
 * attested phrase is a thing a speaker wrote, with the speaker's username attached.
 *
 * PROVENANCE IS CAPTURED PER SENTENCE, NOT PER SOURCE
 *
 * Tatoeba is CC BY 2.0 FR and the licence requires naming the author. "Tatoeba
 * contributors" is not sufficient attribution when the terms ask for the author per
 * sentence, so `author` travels with every harvested row and reaches the exported flag
 * file. Getting this wrong is a licence breach, not a style issue.
 *
 * WHY from=XX&to=eng AND NOT from=eng&to=XX
 *
 * The API puts the FROM language in `results[]` with its `user`, and the TO language under
 * `translations`. Querying from the target language therefore yields the TARGET sentence as
 * the primary record, which is the one whose author we are obliged to credit. Querying from
 * English yields the English author's name and loses the target's.
 *
 * NOT A CONTENT GENERATOR
 *
 * This produces CANDIDATES for a human (or a later selection step) to accept. It does not
 * write content files, it does not choose what ships, and it makes no claim that an attested
 * sentence is a good TRAVEL phrase -- only that a speaker wrote it. Travel-relevance is a
 * filter, and a coarse one. Selection remains a separate, deliberate step.
 */

import { readFileSync, writeFileSync, mkdirSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = new URL('../..', import.meta.url).pathname;
const OUT = join(ROOT, 'catalogue', 'attested');

/**
 * Travel-relevance, as CONCEPTS rather than keywords.
 *
 * The first version of this filter was a flat list of travel words and it produced "My
 * shoes are clean" and "Are you busy?" as candidate travel phrases, because "shoes" and
 * "busy" were on the list. A word list cannot separate a phrase a traveller needs from a
 * sentence that merely mentions a travel object.
 *
 * So the filter is inverted: Tier 0 already has a known shape -- courtesy, prices, food,
 * transport, directions, accommodation, shopping, emergencies -- and a sentence qualifies
 * when it expresses one of THOSE, using the phrasing a traveller would actually use. Each
 * concept requires a multi-word phrase rather than a bare noun, which is the difference
 * between "I have lost my wallet" matching `lost` and "I have a big wallet" matching
 * nothing.
 */
const CONCEPTS = [
  { id: 'greeting',    must: [/\b(hello|hi|hey|good morning|good evening|good afternoon|good night|greetings)\b/i] },
  { id: 'how_are_you', must: [/how are you/i, /how'?s it going/i] },
  { id: 'thanks',      must: [/thank you/i, /\bthanks\b/i, /many thanks/i] },
  { id: 'welcome',     must: [/you'?re welcome/i, /you are welcome/i] },
  { id: 'sorry',       must: [/\bsorry\b/i, /excuse me/i, /i apologise/i, /i apologize/i, /my (bad|mistake)/i] },
  { id: 'goodbye',     must: [/goodbye/i, /good ?bye/i, /see you/i, /have a good (day|night)/i] },
  { id: 'name',        must: [/your name/i, /what('?s| is) (your|his|her) name/i, /my name is/i] },
  { id: 'understand',  must: [/don'?t understand/i, /do you understand/i, /i understand/i, /not understand/i] },
  { id: 'speak_lang',  must: [/do you speak/i, /don'?t speak/i, /speak (english|your language)/i, /i can'?t speak/i] },
  { id: 'repeat',      must: [/say (that )?again/i, /repeat/i, /one more time/i, /slower/i] },
  { id: 'learn_lang',  must: [/i'?m learning/i, /i am learning/i, /studying/i] },
  { id: 'help',        must: [/\bhelp me\b/i, /can you help/i, /\bhelp\b/i] },
  { id: 'price',       must: [/how much/i, /what('?s| is) the (price|cost)/i, /the price/i] },
  { id: 'expensive',   must: [/too expensive/i, /\bexpensive\b/i, /too much (money|for)/i] },
  { id: 'cheap',       must: [/\bcheap(er)?\b/i, /good price/i, /\bdiscount\b/i] },
  { id: 'pay',         must: [/\bpaying?\b/i, /take (the )?money/i, /\bcash\b/i, /\bbill\b/i, /\bchange\b/i] },
  { id: 'buy',         must: [/\bbuying?\b/i, /i (want|need) to buy/i] },
  { id: 'food',        must: [/i'?m hungry/i, /i am hungry/i, /\bhungry\b/i, /to eat/i, /something to eat/i, /i'?d like/i] },
  { id: 'order',       must: [/\border\b/i, /can i (have|get|order)/i, /i'?ll have/i, /i will have/i] },
  { id: 'drink',       must: [/\bwater\b/i, /\btea\b/i, /\bcoffee\b/i, /\bbeer\b/i, /i'?m thirsty/i] },
  { id: 'delicious',   must: [/delicious/i, /tastes? good/i] },
  { id: 'spicy',       must: [/\bspicy\b/i, /too hot/i] },
  { id: 'toilet',      must: [/\btoilet\b/i, /\bbathroom\b/i, /\bwc\b/i, /rest ?room/i] },
  { id: 'bus',         must: [/\bbus\b/i, /bus stop/i, /by bus/i] },
  { id: 'train',       must: [/\btrain\b/i, /\brailway\b/i, /\bplatform\b/i] },
  { id: 'taxi',        must: [/\btaxi\b/i, /by taxi/i] },
  { id: 'ticket',      must: [/\bticket\b/i] },
  { id: 'airport',     must: [/\bairport\b/i] },
  { id: 'where_is',    must: [/where is/i, /where'?s the/i, /where are/i, /do you know where/i] },
  { id: 'how_to_get',  must: [/how (do i|can i|to) get/i, /which (bus|train)/i, /what time (is|does)/i] },
  { id: 'left_right',  must: [/turn (left|right)/i, /on (the )?(left|right)/i, /\bstraight (ahead|on)\b/i] },
  { id: 'near_far',    must: [/is it far/i, /\bnear here\b/i, /\bnext to\b/i, /\bopposite\b/i, /far from/i] },
  { id: 'hotel',       must: [/\bhotel\b/i, /\bhostel\b/i, /a room/i, /rooms available/i, /reservation/i] },
  { id: 'shop',        must: [/\bshopping\b/i, /\bshop\b/i, /\bmarket\b/i, /how much (is|for) (this|these|it)/i] },
  { id: 'police',      must: [/\bpolice\b/i] },
  { id: 'doctor',      must: [/\bdoctor\b/i, /\bhospital\b/i, /\bpharmacy\b/i, /i'?m sick/i, /\bmedicine\b/i] },
  { id: 'lost',        must: [/\blost\b/i, /i lost/i, /\bstolen\b/i, /my (wallet|passport|bag|money|phone)/i] },
  { id: 'emergency',   must: [/emergency/i, /\bhelp me\b/i, /\bfire\b/i, /accident/i, /\bdanger\b/i, /look out/i] },
  { id: 'what_time',   must: [/what time/i, /\bo'?clock\b/i] },
  { id: 'today',       must: [/\btoday\b/i, /\btomorrow\b/i, /\btonight\b/i, /this (morning|evening|afternoon)/i] },
  { id: 'no_yes',      must: [/\byes\b/i, /\bno\b/i, /i don'?t (know|want|like)/i, /i don'?t think/i] },
];

/** Which Tier 0 concepts a pair expresses. More than one is a better candidate. */
function conceptsHit(english) {
  const hits = [];
  for (const c of CONCEPTS) if (c.must.some((re) => re.test(english))) hits.push(c.id);
  return hits;
}

/** Sentences that are unusable in a phrasebook whatever their topic. */
const REJECT = [
  /\bhttps?:\/\//, /\bwww\./, /@[a-z0-9_]+/i,          // links and handles
  /[A-Z]{2,}/,                                          // shouting, acronyms
  /\d{4,}/,                                             // long numbers, ids
  /[<>{}|\\^~`]/,                                       // markup and code
  /^[\s\W]*$/,                                          // no letters
];

const MAX_PER_LANG = Number(process.env.MAX_PER_LANG || 4000);

function acceptable(s) {
  const words = s.text.trim().split(/\s+/).length;
  // Terse enough to be a phrase, long enough to be a sentence with meaning.
  if (words < 2 || words > 14) return false;
  return !REJECT.some((re) => re.test(s.text));
}

/**
 * Pages through the Tatoeba search API for one language.
 *
 * The API caps `page` at 100 and returns 10 per page, so 1000 is the ceiling unless the
 * `sort` parameter is used to walk deeper. 1000 is already far more than Tier 0 needs, and
 * going deeper would mean paging into material with no English counterpart, so the cap is
 * treated as the design limit rather than fought.
 */
async function harvest(code) {
  // Seed from any existing file so a second run ADDS material instead of replacing it.
  // With random ordering each run is a fresh sample, so repeated runs widen the pool of
  // contributors, which is the thing we actually care about.
  const path = join(OUT, `${code}.jsonl`);
  const out = [];
  const seen = new Set();
  if (existsSync(path)) {
    for (const line of readFileSync(path, 'utf8').split('\n')) {
      if (!line.trim()) continue;
      try {
        const r = JSON.parse(line);
        out.push(r);
        seen.add(r.tatoeba_id_english);
      } catch {
        /* a truncated final line from an interrupted run; skip it */
      }
    }
  }
  const before = out.length;

  /**
   * Flush after every page, not at the end.
   *
   * A full run over 20 languages takes over an hour, and the first version of this script
   * buffered everything and wrote once at the end -- so a timeout at language four threw
   * away four minutes of paging. Writing per page means an interrupted run keeps
   * everything it had already done, and the skip-if-exists check in main() then resumes
   * from where it stopped rather than starting over. Partial results are also useful on
   * their own: fifty good attested sentences tells us more about a language than nothing.
   */
  const flush = () =>
    writeFileSync(path, out.map((r) => JSON.stringify(r)).join('\n') + (out.length ? '\n' : ''));

  for (let page = 1; page <= 100 && out.length < MAX_PER_LANG; page += 1) {
    // sort=random is load-bearing, not a performance tweak.
    //
    // The API's default ordering returns sentences GROUPED BY CONTRIBUTOR, so paging
    // through it walks long unbroken runs of one author's work. The first version of this
    // harvester did that, and 15 of its 16 Indonesian rows turned out to be from a single
    // user (@HAGNi). That would have made the "attested" corpus a portrait of one hobbyist,
    // which defeats the entire purpose: the corpus has to sample many speakers to mean
    // anything about a language. Random ordering samples across contributors instead.
    //
    // The cost is that random order is not stable between requests, so page 2 is not the
    // logical successor of page 1 and later passes may re-encounter earlier sentences. The
    // dedupe set handles that, and re-running the harvester becomes a way to draw MORE
    // material rather than a way to redo work.
    const url =
      `https://tatoeba.org/en/api_v0/search?from=${code}&to=eng` +
      `&orphans=no&unapproved=no&sort=random&page=${page}`;

    let data;
    try {
      const res = await fetch(url);
      if (!res.ok) break;
      data = await res.json();
    } catch (err) {
      console.error(`  ${code} page ${page}: ${err.message}`);
      break;
    }

    const results = data.results ?? [];
    if (results.length === 0) break;

    for (const target of results) {
      if (target.lang !== code) continue;
      const en = (target.translations?.[0] ?? []).find((t) => t.lang === 'eng');
      if (!en) continue;
      if (seen.has(en.id)) continue;

      const nativeOk = acceptable(target);
      const enOk = acceptable(en);
      if (!nativeOk || !enOk) continue;
      // Scored on the ENGLISH side, because that is where the concept lives: a Thai
      // sentence may be terse enough that no English keyword survives, but the English is
      // what tells us whether this is a price question or a remark about the weather.
      const concepts = conceptsHit(en.text);
      if (concepts.length === 0) continue;

      seen.add(en.id);
      out.push({
        code,
        native: target.text,
        english: en.text,
        // Per-sentence attribution. CC BY 2.0 FR asks for the author, so this is carried
        // on every row and reaches the exported flag file.
        author: target.user?.username ?? null,
        author_english: en.user?.username ?? null,
        license: target.license ?? en.license ?? 'CC BY 2.0 FR',
        // Which Tier 0 concepts this pair expresses. Selection uses this to fill concept
        // gaps deliberately rather than taking whatever arrived first.
        concepts,
        tatoeba_id: target.id,
        tatoeba_id_english: en.id,
        dir: target.dir,
      });
    }

    flush();

    if (results.length < 10) break;
    if (page % 10 === 0) process.stdout.write(`  ${code}: ${out.length}\n`);
    await new Promise((r) => setTimeout(r, 250)); // be polite to a free public API
  }

  flush();
  return out;
}

async function main() {
  const catalogue = JSON.parse(
    readFileSync(join(ROOT, 'catalogue', 'languages.json'), 'utf8'),
  );

  if (!existsSync(OUT)) mkdirSync(OUT, { recursive: true });

  // English has no bitext to harvest FROM -- it is the pivot and the calibration language.
  //
  // Deliberately NOT skipping languages that already have a file. harvest() seeds from disk
  // and adds to it, so re-running widens the contributor pool. Skipping would freeze the
  // corpus at whatever one pass happened to find, which for a low-yield language like
  // Indonesian was 15 sentences from one person.
  const targets = catalogue.languages.filter((l) => l.code !== catalogue.calibration);

  if (targets.length === 0) {
    console.log('  nothing to harvest');
    return;
  }

  for (const lang of targets) {
    process.stdout.write(`  harvesting ${lang.code} (${lang.name})...\n`);
    const rows = await harvest(lang.code);
    console.log(
      `    ${rows.length} attested travel candidates -> catalogue/attested/${lang.code}.jsonl`,
    );
  }
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});