import { test } from 'node:test';
import assert from 'node:assert/strict';

import { depthFor, COVEN_MAX, COVEN_MIN, NO_EVIDENCE } from './depth.mjs';

/**
 * The measured yields, as the harvester and selector actually reported them.
 *
 * These numbers are the real ones, not illustrations. If the arithmetic below is wrong for a
 * language that actually exists in the catalogue, the consequence is a spec declaring a size
 * the evidence cannot support.
 */
const MEASURED = {
  deu: { selected: 340, concepts_covered: 63, concepts_missing: 0 },
  ita: { selected: 325, concepts_covered: 60, concepts_missing: 3 },
  spa: { selected: 328, concepts_covered: 60, concepts_missing: 3 },
  por: { selected: 328, concepts_covered: 59, concepts_missing: 4 },
  ara: { selected: 267, concepts_covered: 55, concepts_missing: 8 },
  ind: { selected: 252, concepts_covered: 50, concepts_missing: 13 },
  hin: { selected: 167, concepts_covered: 41, concepts_missing: 22 },
  swh: { selected: 109, concepts_covered: 32, concepts_missing: 31 },
  srp: { selected: 125, concepts_covered: 24, concepts_missing: 39 },
  tam: { selected: 44, concepts_covered: 16, concepts_missing: 47 },
};

// ---- the constants themselves ----------------------------------------------

test('the courtesy floor cannot exceed COVEN_MAX however much evidence exists', () => {
  // The point of the cap: yield is not permission to write a course. English, the
  // calibration language, ships 48, and past roughly that a tier stops being a floor.
  const huge = { selected: 5000, concepts_covered: 63, concepts_missing: 0 };
  assert.equal(depthFor(huge).depth, COVEN_MAX);
});

test('a language with no usable evidence is not-yet, not a tiny tier', () => {
  const empty = { selected: 0, concepts_covered: 0, concepts_missing: 63 };
  const d = depthFor(empty);
  assert.equal(d.depth, 0);
  assert.equal(d.verdict, 'not-yet');
});

// ---- the language-dependent behaviour --------------------------------------

test('German is capped by the courtesy floor, not by its evidence', () => {
  const d = depthFor(MEASURED.deu);
  assert.equal(d.depth, COVEN_MAX);
  assert.match(d.reason, /Yield is not the constraint/);
});

test('Tamil lands on the minimum because coverage overrides the floor', () => {
  // 44 candidates is above the no-evidence floor, so Tamil ships something. But 16 of 63
  // concepts caps coverage at 15, BELOW the 24 minimum, so the floor overrides it. The
  // reason must say that, rather than claiming coverage "caps depth at 24" -- it does not.
  const d = depthFor(MEASURED.tam);
  assert.equal(d.depth, COVEN_MIN);
  assert.match(d.reason, /below the 24 minimum/);
  assert.match(d.reason, /47 concepts have no attestation/);
});

test('the reason never claims a number is below itself', () => {
  // This is the bug worth guarding. An earlier version ranked the floor alongside the real
  // limits and reported "coverage caps depth at 57, below the 24 minimum" for Spanish,
  // because 24 sorts smallest. A reason that is arithmetically absurd is worse than none,
  // because it reads as though the number had been checked.
  //
  // Only reasons that actually mention the minimum are checked: "caps depth at 57" on its
  // own is a correct statement, and only "caps depth at 57, below the 24 minimum" is absurd.
  for (const [code, rec] of Object.entries(MEASURED)) {
    const d = depthFor(rec);
    if (!/below the \d+ minimum/.test(d.reason)) continue;
    const cap = Number(d.reason.match(/caps depth at (\d+)/)?.[1]);
    assert.ok(Number.isFinite(cap), `${code} mentions the minimum but names no cap`);
    assert.ok(
      cap < COVEN_MIN,
      `${code}: reason claims "${cap}" is below ${COVEN_MIN}, which is false`,
    );
  }
});

test('depth is monotonically non-decreasing in coverage', () => {
  // Two languages with similar volume but different coverage must not get the same tier.
  const thin = depthFor({ selected: 300, concepts_covered: 20, concepts_missing: 43 });
  const wide = depthFor({ selected: 300, concepts_covered: 55, concepts_missing: 8 });
  assert.ok(wide.depth > thin.depth,
    'coverage must raise depth; volume alone must not decide it');
});

test('German and Tamil get very different tiers, which is the whole point', () => {
  assert.ok(depthFor(MEASURED.deu).depth > depthFor(MEASURED.tam).depth * 2,
    'a constant tier size would have been wrong in both directions');
});

test('no measured language lands on zero depth except an unimaginably thin one', () => {
  for (const [code, rec] of Object.entries(MEASURED)) {
    const d = depthFor(rec);
    assert.ok(d.depth > 0, `${code} should ship something`);
    assert.ok(d.depth >= COVEN_MIN || d.depth === COVEN_MAX,
      `${code} depth ${d.depth} is neither the floor nor the cap; the arithmetic is suspect`);
  }
});

test('every depth sits within the declared bounds', () => {
  for (const [code, rec] of Object.entries(MEASURED)) {
    const d = depthFor(rec);
    assert.ok(d.depth >= COVEN_MIN && d.depth <= COVEN_MAX, `${code} out of bounds: ${d.depth}`);
  }
});

test('the no-evidence threshold is what decides not-yet, and nothing else', () => {
  // Just under the threshold must be not-yet; just over must ship, even with poor coverage,
  // because the minimum is the minimum.
  const justUnder = { selected: NO_EVIDENCE - 1, concepts_covered: 10, concepts_missing: 53 };
  const justOver = { selected: NO_EVIDENCE + 1, concepts_covered: 10, concepts_missing: 53 };
  assert.equal(depthFor(justUnder).verdict, 'not-yet');
  assert.equal(depthFor(justOver).verdict, 'set');
  assert.equal(depthFor(justOver).depth, COVEN_MIN);
});

test('the not-yet verdict explains what to do rather than just refusing', () => {
  // A refusal with no alternative wastes the reader's time. The message must name the
  // options: hold as calibration-pending, or author and label honestly.
  const d = depthFor({ selected: 5, concepts_covered: 3, concepts_missing: 60 });
  assert.match(d.reason, /author deliberately/i);
  assert.match(d.reason, /label/i);
});

test('every verdict carries a reason', () => {
  // A bare number invites the reader to assume it was chosen. The reason is the part that
  // makes it reviewable.
  for (const rec of [...Object.values(MEASURED), { selected: 0, concepts_covered: 0, concepts_missing: 63 }]) {
    const d = depthFor(rec);
    assert.ok(d.reason && d.reason.length > 20, 'every depth must be explained');
  }
});

test('depthFor is pure — the same input always gives the same depth', () => {
  // It writes nothing and reads nothing, so two calls cannot diverge.
  const rec = { selected: 200, concepts_covered: 45, concepts_missing: 18 };
  assert.deepEqual(depthFor(rec), depthFor(rec));
});

test('the arithmetic matches hand-computed coverage caps', () => {
  // coverageCap = round(covered / 63 * 60). Verify against the real language numbers rather
  // than trusting the implementation to agree with itself.
  assert.equal(Math.round((60 / 63) * COVEN_MAX), depthFor(MEASURED.ita).depth);
  assert.equal(Math.round((50 / 63) * COVEN_MAX), depthFor(MEASURED.ind).depth);
  assert.equal(Math.round((41 / 63) * COVEN_MAX), depthFor(MEASURED.hin).depth);
  assert.equal(Math.round((32 / 63) * COVEN_MAX), depthFor(MEASURED.swh).depth);
});