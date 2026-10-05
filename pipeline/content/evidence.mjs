/**
 * The evidence model.
 *
 * WHAT PROBLEM THIS SOLVES
 *
 * Kedhar will never assemble native speakers to review this content. That is settled, not a
 * problem to solve. So the question is not "how do we get a reviewer" but "what stands in for
 * one" — and the honest answer is evidence of varying strength, recorded per item rather
 * than assumed.
 *
 * WHY THE EXISTING AXIS WAS WRONG
 *
 * The original sourcing axis was human-reviewed versus machine-aggregated, with three
 * classes: `authored`, `curated`, `corpus`. Tatoeba does not fit any of them honestly.
 *
 *   - It is NOT `curated`. Nobody selected these sentences for a phrasebook. I selected them,
 *     with a concept filter that is demonstrably imprecise.
 *   - It is NOT `corpus` in the sense that was barred from Tier 0. `corpus` meant "assembled
 *     without any native-speaker involvement". A Tatoeba sentence was written, deliberately,
 *     by a named native speaker. It is machine-aggregated in COLLECTION and human-authored
 *     in CONTENT, and collapsing those two facts is precisely the error this project exists
 *     to avoid.
 *
 * So there is a fourth class, `attested`, and it is stronger than `authored` and weaker than
 * `curated`:
 *
 *   curated   — a native speaker chose this text for a phrasebook. Nobody will ever produce
 *               these for us; this class exists to name what we cannot reach.
 *   attested  — a named native speaker wrote this sentence, and it says what it is claimed
 *               to say. Verifiable back to a specific sentence id. Attestation proves the
 *               SENTENCE exists and is idiomatic; it does NOT prove the sentence is the
 *               right thing to say to a shopkeeper, which is a separate judgement.
 *   authored  — written by this project. A considered guess with reasoning behind it.
 *   corpus    — assembled with no native-speaker involvement. Barred from Tier 0.
 *
 * WHY ATTESTED IS NOT TRUSTED BLINDLY
 *
 * Attestation is evidence that a sentence exists. It is not evidence that the sentence is
 * the best way to ask for something. The harvest proves this concretely: the concept
 * `i_want` matched "Aku tidak ingin hidup selamanya" (I don't want to live forever) because
 * "don't want" contains "want". A native speaker wrote that sentence and it is still not a
 * phrasebook line.
 *
 * That is why confidence is recorded, not just class, and why a second signal
 * (`corroboration`) is separated from the first.
 */

/**
 * HOW MUCH A CONTRIBUTOR'S SHARE DEGRADES AN ENTRY
 *
 * One contributor, CK, supplies about a third of the attested pool in every harvested
 * language, and has 1000+ sentences in 20 languages including Swahili, Tamil, Thai and
 * Korean. Nobody is a native speaker of 20 languages. The Thai evidence is direct: 249 of
 * 250 CK Thai sentences carry no politeness particle, which native Thai uses almost
 * universally, so those sentences are translated word lists rather than speech.
 *
 * Tatoeba exposes no nativeness signal to correct for this -- `native=yes`, `own=yes`,
 * `is_native=1` and `username=` are all silently ignored, returning plausible data while
 * changing nothing. A filter that pretends to work is worse than one that errors.
 *
 * So the concentration is corrected here, mechanically and visibly, rather than left as an
 * aggregate somebody has to remember. A sentence from one person dominating a language's
 * pool is evidence about that one person; it is weaker than the same sentence from among a
 * dozen contributors, and the entry must say so.
 *
 * The threshold is deliberately blunt. It is a review trigger, not a probability: it marks
 * an entry as leaning on a single contributor's usage so it can be read with that in mind.
 */
export const CONCENTRATION_DEGRADES_AT = 0.1;

/**
 * Confidence, in descending order of what would have to be true.
 *
 * `note` exists because a bare label teaches the reader nothing. Each level states what the
 * label actually rests on, which is the honest form of a confidence claim.
 */
export const CONFIDENCE = {
  attested_corroborated: {
    rank: 1,
    note: 'Written by a named speaker, and the same wording appears independently.',
  },
  attested: {
    rank: 2,
    note: 'Written by a named contributor. Provenance verified; nativeness unknown, fitness for the job is editorial.',
  },
  attested_concentrated: {
    // Ranks below plain `attested` on purpose. This is the CK case: a real sentence written
    // by a real person, but one person who supplies most of the pool and is demonstrably a
    // translator rather than a speaker in several of these languages.
    rank: 3,
    note: 'One contributor supplies most of this language’s pool. Their usage is not a broad sample.',
  },
  authored_single: {
    rank: 4,
    note: 'Written for this app. Reasoning recorded; no native-speaker confirmation.',
  },
  authored_corpus_aligned: {
    // Weaker than authored_single, not stronger, despite the name. Alignment with a corpus
    // is a weak signal that a phrasing is plausible, not a substitute for a speaker
    // confirming it, and it is ranked below unaided authoring on purpose.
    rank: 5,
    note: 'Written for this app, and similar wording occurs in the corpus. Not confirmed.',
  },
  unattributed: {
    // Not shippable. Present so that an entry with no evidence FAILS loudly rather than
    // being quietly scored.
    rank: 6,
    note: 'No evidence recorded.',
  },
};

/** Classes permitted in Tier 0, and the confidence each one is entitled to claim. */
const CLASS_CONFIDENCE = {
  curated: ['attested_corroborated'],
  attested: ['attested', 'attested_corroborated', 'attested_concentrated'],
  authored: ['authored_single', 'authored_corpus_aligned'],
  corpus: [], // barred from Tier 0 by policy
};

/** True when `cls` may appear in Tier 0 at all. */
export function tier0Allows(cls) {
  return cls === 'attested' || cls === 'authored' || cls === 'curated';
}

/**
 * Derives confidence from an entry's provenance.
 *
 * Returns null when there is not enough information to derive one — which is the case the
 * build must reject, because an entry that cannot state its own evidence has no evidence.
 *
 * The derivation is deliberately mechanical and lives in code rather than being written into
 * each content file. A confidence field an author sets by hand is a confidence field that
 * will eventually be wrong, and it will be wrong upward.
 */
export function deriveConfidence(record) {
  const src = record?.source;
  if (!src) return null;

  const cls = src.class;

  if (cls === 'attested') {
    // Attestation without provenance is not attestation. Both the author and a resolvable
    // external id are required, or there is nothing to check the claim against.
    const hasAuthor = typeof src.author === 'string' && src.author.length > 0;
    const hasId =
      src.external_id !== null && src.external_id !== undefined && src.external_id !== '';
    if (!hasAuthor || !hasId) return null;

    // Corroboration is an INDEPENDENT second sentence agreeing on the wording. Distinct
    // sentences by the same author are not independent, so corroborating_sentences must
    // name different ids.
    const others = (src.corroborating_sentences ?? []).filter(
      (id) => String(id) !== String(src.external_id),
    );
    if (others.length > 0) return 'attested_corroborated';

    // Concentration downgrade. Checked after corroboration because independent agreement
    // outweighs a single contributor's share: two different speakers is a real sample even
    // if one of them writes a lot.
    const share = src.contributor_share;
    if (typeof share === 'number' && share >= CONCENTRATION_DEGRADES_AT) {
      return 'attested_concentrated';
    }
    return 'attested';
  }

  if (cls === 'authored') {
    const aligned = src.corpus_aligned === true;
    return aligned ? 'authored_corpus_aligned' : 'authored_single';
  }

  if (cls === 'curated') {
    // Reachable only for languages whose spec declares a curated phrasebook.
    return 'attested_corroborated';
  }

  return null;
}

/**
 * The full evidence block for an entry, as the app should receive it.
 *
 * This is what gets emitted into the shipped asset, so that an entry's standing is visible
 * in the data rather than being a property only the pipeline knows about.
 */
export function evidenceFor(record) {
  const confidence = deriveConfidence(record);
  if (!confidence) return { confidence: 'unattributed', rank: 5, note: CONFIDENCE.unattributed.note };

  const src = record.source;
  return {
    confidence,
    rank: CONFIDENCE[confidence].rank,
    note: CONFIDENCE[confidence].note,
    class: src.class,
    // Attribution travels to the app because CC BY 2.0 FR requires naming the author of
    // any reused sentence. This is a licence obligation, not metadata.
    author: src.author ?? null,
    licence: src.licence ?? null,
    external_id: src.external_id ?? null,
    // Carried through so concentration is inspectable in the shipped data rather than only
    // in an aggregate that a later reader has to go looking for.
    contributor_share: typeof src.contributor_share === 'number' ? src.contributor_share : null,
  };
}

/** Whether a claimed confidence is one the source class is entitled to. */
export function confidenceIsEntitled(cls, confidence) {
  const allowed = CLASS_CONFIDENCE[cls];
  return allowed ? allowed.includes(confidence) : false;
}