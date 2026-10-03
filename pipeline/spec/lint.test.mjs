// Linter tests.
//
// A check that cannot fail is worse than no check. Every rule below is tested in BOTH
// directions: the broken input must be rejected, and the valid input must be accepted.
// If a rule is ever removed from lint.mjs, these tests fail — which is the point.
//
// Derived from the BaroKraft lesson: a round-trip drift check returned the reference
// exactly at every possible value, so it read as reassurance and never complained.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { lintSpec } from './lint.mjs';
import { validSpec } from './fixture.mjs';

const FILE = 'fixture.yaml';

// The minimum spec that must pass. Every test mutates one thing off this.
function lint(mutate) {
  const spec = validSpec();
  mutate(spec);
  const r = lintSpec(spec, FILE);
  return { errors: r.errors, warnings: r.warnings, ok: r.errors.length === 0 };
}

function expectFail(mutate, match) {
  const { errors } = lint(mutate);
  assert.ok(errors.length > 0, 'expected the linter to reject this input, but it passed');
  assert.ok(
    errors.some(e => match.test(e)),
    `expected an error matching ${match}, got:\n  ${errors.join('\n  ')}`,
  );
}

// ---------------------------------------------------------------------------
test('a valid spec passes', () => {
  const { errors, ok } = lint(() => {});
  assert.equal(ok, true, `expected clean, got:\n  ${errors.join('\n  ')}`);
});

test('sweep: mutating any single field must not silently pass', () => {
  // If a future edit removes a rule, this sweep is what notices.
  const mutations = [
    ['status', s => { s.status = 'final'; }],
    ['code', s => { s.language.code = 'SWH'; }],
    ['role', s => { s.language.role = 'decoration'; }],
    ['morphology', s => { s.structure.morphology = 'fusional'; }],
    ['direction', s => { s.structure.script.direction = 'ttb'; }],
    ['tones', s => { delete s.structure.tones; }],
    ['register', s => { s.register.system = 'vibes'; }],
    ['certainty', s => { s.tiers[1].certainty = 'pretty sure'; }],
  ];
  for (const [name, m] of mutations) {
    const { errors } = lint(m);
    assert.ok(errors.length > 0, `"${name}" should have been rejected but passed`);
  }
});

// ---------------------------------------------------------------------------
// The provenance gate — the reason this linter exists.
test('a bare number where a claim is required is rejected', () => {
  expectFail(s => { s.tiers[2].size = 500; }, /bare value where a claim is required/);
});

test('a bare number in cost is rejected', () => {
  expectFail(s => { s.cost.fsi_hours_to_ilr3 = 828; }, /bare value where a claim is required/);
});

test('a claim missing "verified" is rejected', () => {
  expectFail(s => { delete s.tiers[0].size.verified; }, /missing required key "verified"/);
});

test('a claim missing "checked" is rejected', () => {
  expectFail(s => { delete s.variety.rationale.checked; }, /missing required key "checked"/);
});

test('a claim with an empty source is rejected', () => {
  expectFail(s => { s.cost.fsi_hours_to_ilr3.source = ''; }, /source must not be empty/);
});

test('a claim with a malformed date is rejected', () => {
  expectFail(s => { s.tiers[1].size.checked = '03/10/2026'; }, /must be YYYY-MM-DD/);
});

test('verified: false is ACCEPTED — honesty must not be a lint failure', () => {
  // This is the whole design. An unverified claim with a source is the correct
  // state for a hypothesis. Rejecting it would push authors to fabricate sources.
  const { ok } = lint(s => { s.tiers[2].size.verified = false; });
  assert.equal(ok, true);
});

// ---------------------------------------------------------------------------
// The bounded-loss guarantee
test('tier sizes must strictly increase', () => {
  expectFail(s => { s.tiers[2].size.value = 100; }, /must increase/);
});

test('tier 0 certainty below high is an ERROR, not a warning', () => {
  // The bounded-loss design collapses if Tier 0 is not near-certain.
  expectFail(s => { s.tiers[0].certainty = 'low'; }, /must be "high"/);
});

test('a missing tier is rejected — the tier count is part of the spine', () => {
  expectFail(s => { s.tiers.pop(); }, /ids must be 0,1,2,3 in order/);
});

test('a tier without an intent is rejected — an untestable tier', () => {
  expectFail(s => { delete s.tiers[1].intent; }, /intent/);
});

// ---------------------------------------------------------------------------
// The dialect decision
test('a default variety absent from the variants list is rejected', () => {
  expectFail(s => { s.variety.default = 'sw-UG'; }, /is not in variants/);
});

test('Latin script with a romanization scheme set is rejected', () => {
  expectFail(s => { s.language.romanization = 'iso15919'; }, /One of them is wrong/);
});

test('a non-Latin script with romanization set is accepted', () => {
  const { ok } = lint(s => { s.language.code = 'tam'; s.language.romanization = 'iso15919'; s.structure.script.primary = 'Tamil'; s.variety.romanization_scheme = 'iso15919'; });
  assert.equal(ok, true);
});

test('a non-Latin script with NO romanization is rejected', () => {
  // Otherwise the app shows one script and the learner must learn to read it to
  // use the app — which is explicitly not the goal.
  expectFail(
    s => { s.language.code = 'tam'; s.structure.script.primary = 'Tamil'; s.language.romanization = null; s.variety.romanization_scheme = null; },
    /requires a romanization scheme/,
  );
});

test('Latin script with a variety-level scheme is also rejected', () => {
  expectFail(s => { s.variety.romanization_scheme = 'practical'; }, /variety.romanization_scheme/);
});

test('a variant without a claim explaining why it exists is rejected', () => {
  expectFail(s => { s.variety.variants[1].notes = 'just because'; }, /must be a claim/);
});

// ---------------------------------------------------------------------------
// The Tier 0 sourcing policy
test('no curated source and no tier0_sources declaration is rejected', () => {
  expectFail(s => { s.resources[0].class = 'corpus'; }, /Declare "unavailable"/);
});

test('declaring unavailable without recording the gap is rejected', () => {
  expectFail(s => { s.resources = []; s.tier0_sources = 'unavailable'; }, /known_gaps/);
});

test('declaring unavailable WITH the gap recorded is accepted', () => {
  const { ok } = lint(s => {
    s.resources = [];
    s.tier0_sources = 'unavailable';
    s.review.known_gaps = ['Tier 0 has no curated source for this language.'];
  });
  assert.equal(ok, true);
});

test('declaring unavailable while a curated source exists is rejected', () => {
  expectFail(s => { s.tier0_sources = 'unavailable'; }, /declared "unavailable" but a human-reviewed resource is listed/);
});

// ---------------------------------------------------------------------------
// Resources rot
test('a resource with no licence is rejected', () => {
  expectFail(s => { delete s.resources[0].licence; }, /licence/);
});

test('a resource with no checked date is rejected', () => {
  expectFail(s => { delete s.resources[0].checked; }, /must be YYYY-MM-DD/);
});

test('an old checked date WARNS rather than failing', () => {
  // Resources rot. A stale link should nag, not block a rebuild.
  const { errors, warnings } = lint(s => { s.resources[0].checked = '2015-01-01'; });
  assert.equal(errors.length, 0, 'staleness must not be a build failure');
  assert.ok(warnings.some(w => /days old/.test(w)), 'expected a staleness warning');
});

// ---------------------------------------------------------------------------
test('fsi_category null without an explanatory note is rejected', () => {
  expectFail(s => { s.cost.fsi_category = null; }, /fsi_category_note/);
});

test('a missing top-level key is rejected', () => {
  expectFail(s => { delete s.register; }, /required top-level key is missing/);
});

test('gloss_mode must be declared', () => {
  expectFail(s => { delete s.language.gloss_mode; }, /gloss_mode/);
});

test('an unknown gloss_mode is rejected', () => {
  expectFail(s => { s.language.gloss_mode = 'maybe'; }, /gloss_mode/);
});

test('an authored resource satisfies the Tier 0 gate', () => {
  const { ok } = lint(s => { s.resources[0].class = 'authored'; });
  assert.equal(ok, true);
});

test('a corpus-only resource set still fails the Tier 0 gate', () => {
  expectFail(s => { s.resources[0].class = 'corpus'; }, /human-reviewed/);
});

test('non-semver spec_version is rejected', () => {
  expectFail(s => { s.spec_version = '1.0'; }, /must be semver/);
});