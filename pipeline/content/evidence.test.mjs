import { test } from 'node:test';
import assert from 'node:assert/strict';

import { deriveConfidence, evidenceFor, confidenceIsEntitled, tier0Allows } from './evidence.mjs';

/**
 * Tests for the evidence model.
 *
 * The behaviour worth protecting here is not the happy path. It is that content which cannot
 * account for itself FAILS, because this rule is the only thing standing in for a
 * native-speaker review team this project will never have. A permissive bug here would be
 * invisible: the app would ship plausible text of unknown standing and nothing would look
 * wrong.
 */

const base = {
  id: 'x-0001',
  lang: 'ind',
  tier: 0,
  text_native: 'Permisi, toiletnya di mana ya?',
  text_english: 'Excuse me, where is the toilet?',
  direction: 'say',
};

// ---- the four classes ------------------------------------------------------

test('attested with author and external_id derives `attested`', () => {
  const rec = {
    ...base,
    source: {
      class: 'attested',
      id: 'Tatoeba',
      licence: 'CC BY 2.0 FR',
      author: 'slyfin',
      external_id: '499081',
    },
  };
  assert.equal(deriveConfidence(rec), 'attested');
});

test('authored derives authored_single', () => {
  const rec = {
    ...base,
    source: { class: 'authored', id: 'internal', licence: 'CC BY-SA 4.0' },
  };
  assert.equal(deriveConfidence(rec), 'authored_single');
});

test('corpus derives nothing — it is barred from Tier 0 and cannot claim standing', () => {
  const rec = {
    ...base,
    source: { class: 'corpus', id: 'whatever', licence: 'unknown' },
  };
  assert.equal(deriveConfidence(rec), null);
  assert.equal(evidenceFor(rec).confidence, 'unattributed');
});

test('a missing source derives nothing', () => {
  assert.equal(deriveConfidence({ ...base }), null);
  assert.equal(deriveConfidence(null), null);
});

// ---- attestation requires provenance ---------------------------------------

// These are the tests that matter most. Attestation with nothing to check it against is a
// claim, not evidence, and each of these would otherwise let unbacked text into Tier 0.

test('attested WITHOUT an author is rejected', () => {
  const rec = {
    ...base,
    source: {
      class: 'attested',
      id: 'Tatoeba',
      licence: 'CC BY 2.0 FR',
      external_id: '499081',
    },
  };
  assert.equal(deriveConfidence(rec), null, 'a sentence with no author cannot be attributed');
});

test('attested WITHOUT an external_id is rejected', () => {
  const rec = {
    ...base,
    source: {
      class: 'attested',
      id: 'Tatoeba',
      licence: 'CC BY 2.0 FR',
      author: 'slyfin',
      external_id: null,
    },
  };
  assert.equal(deriveConfidence(rec), null, 'an id is what makes the claim checkable');
});

test('attested with an EMPTY external_id is rejected, not treated as present', () => {
  // An empty string is truthy-adjacent in sloppy code and would pass a `!= null` check on
  // the wrong side. Provenance must not be satisfiable by an empty value.
  const rec = {
    ...base,
    source: {
      class: 'attested',
      id: 'Tatoeba',
      licence: 'CC BY 2.0 FR',
      author: 'slyfin',
      external_id: '',
    },
  };
  assert.equal(deriveConfidence(rec), null);
});

test('attested with an empty author is rejected', () => {
  const rec = {
    ...base,
    source: {
      class: 'attested',
      id: 'Tatoeba',
      licence: 'CC BY 2.0 FR',
      author: '',
      external_id: '499081',
    },
  };
  assert.equal(deriveConfidence(rec), null);
});

// ---- corroboration ---------------------------------------------------------

test('an independent second sentence raises attested to corroborated', () => {
  const rec = {
    ...base,
    source: {
      class: 'attested',
      id: 'Tatoeba',
      licence: 'CC BY 2.0 FR',
      author: 'slyfin',
      external_id: '499081',
      corroborating_sentences: ['123456'],
    },
  };
  assert.equal(deriveConfidence(rec), 'attested_corroborated');
});

test('corroboration by the SAME sentence id does not count as corroboration', () => {
  // The harvest can report the same pair under several search forms, so a naive
  // implementation would let one sentence vouch for itself and manufacture the top
  // confidence level out of nothing.
  const rec = {
    ...base,
    source: {
      class: 'attested',
      id: 'Tatoeba',
      licence: 'CC BY 2.0 FR',
      author: 'slyfin',
      external_id: '499081',
      corroborating_sentences: ['499081', '499081'],
    },
  };
  assert.equal(deriveConfidence(rec), 'attested');
});

test('corroboration mixing a duplicate id with one distinct id still corroborates', () => {
  const rec = {
    ...base,
    source: {
      class: 'attested',
      id: 'Tatoeba',
      licence: 'CC BY 2.0 FR',
      author: 'slyfin',
      external_id: '499081',
      corroborating_sentences: ['499081', '777'],
    },
  };
  assert.equal(deriveConfidence(rec), 'attested_corroborated');
});

// ---- corpus alignment ranks BELOW unaided authoring ------------------------

test('corpus_aligned ranks BELOW authored_single, not above it', () => {
  // Counterintuitive but deliberate. Alignment with a corpus says a phrasing is plausible;
  // it is not a speaker confirming anything, so it must not outrank unaided authoring.
  const plain = {
    ...base,
    source: { class: 'authored', id: 'internal', licence: 'CC BY-SA 4.0' },
  };
  const aligned = {
    ...base,
    source: {
      class: 'authored',
      id: 'internal',
      licence: 'CC BY-SA 4.0',
      corpus_aligned: true,
    },
  };
  const a = evidenceFor(plain);
  const b = evidenceFor(aligned);
  assert.equal(a.confidence, 'authored_single');
  assert.equal(b.confidence, 'authored_corpus_aligned');
  assert.ok(
    b.rank > a.rank,
    'corpus alignment must rank as WEAKER evidence, i.e. a higher rank number',
  );
});

// ---- entitled confidence ---------------------------------------------------

test('a class cannot claim confidence above what it is entitled to', () => {
  assert.ok(confidenceIsEntitled('attested', 'attested'));
  assert.ok(confidenceIsEntitled('attested', 'attested_corroborated'));
  assert.ok(!confidenceIsEntitled('attested', 'authored_single'));
  assert.ok(!confidenceIsEntitled('authored', 'attested'));
  assert.ok(!confidenceIsEntitled('corpus', 'attested'));
});

test('corpus is not entitled to any confidence at all', () => {
  assert.ok(!confidenceIsEntitled('corpus', 'attested'));
  assert.ok(!confidenceIsEntitled('corpus', 'attested_corroborated'));
  assert.ok(!confidenceIsEntitled('corpus', 'authored_single'));
});

// ---- tier 0 policy ---------------------------------------------------------

test('tier 0 permits attested but never corpus', () => {
  // This is the substantive change: Tatoeba is admitted, and the class that genuinely has
  // no native-speaker involvement stays out.
  assert.ok(tier0Allows('attested'));
  assert.ok(tier0Allows('authored'));
  assert.ok(tier0Allows('curated'));
  assert.ok(!tier0Allows('corpus'));
});

// ---- the emitted block -----------------------------------------------------

test('evidence block carries author and licence to the app', () => {
  // CC BY 2.0 FR asks for the author of a reused sentence. This is a licence obligation
  // travelling as data, not optional metadata, so it must survive into the asset.
  const rec = {
    ...base,
    source: {
      class: 'attested',
      id: 'Tatoeba',
      licence: 'CC BY 2.0 FR',
      author: 'slyfin',
      external_id: '499081',
    },
  };
  const ev = evidenceFor(rec);
  assert.equal(ev.author, 'slyfin');
  assert.equal(ev.licence, 'CC BY 2.0 FR');
  assert.equal(ev.external_id, '499081');
  assert.equal(ev.class, 'attested');
});

test('every confidence level carries a note explaining what it rests on', () => {
  // A bare label like "attested" teaches the reader nothing. The note is what makes the
  // label an honest claim rather than a reassuring word.
  for (const cls of ['attested', 'authored']) {
    const rec = {
      ...base,
      source:
        cls === 'attested'
          ? { class: 'attested', id: 'T', licence: 'CC BY 2.0 FR', author: 'a', external_id: '1' }
          : { class: 'authored', id: 'internal', licence: 'CC BY-SA 4.0' },
    };
    const ev = evidenceFor(rec);
    assert.ok(ev.note && ev.note.length > 10, `${cls} must explain what its confidence rests on`);
  }
});

test('unattributed evidence has the worst rank', () => {
  const ev = evidenceFor({ ...base });
  assert.equal(ev.confidence, 'unattributed');
  assert.ok(
    ev.rank >= 5,
    'content that cannot account for itself must rank worst, so it sorts to the top of any review queue',
  );
});