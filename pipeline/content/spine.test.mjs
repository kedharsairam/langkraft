/**
 * The spine's invariants, enforced.
 *
 * WHAT THIS IS FOR
 *
 * The reason the spine exists is that twenty languages were each selected independently from
 * their own Wikivoyage page in page order, and ended up sharing nothing: 376 distinct English
 * captions across 1,050 entries, with ZERO present in all twenty. A traveller who learned "thank
 * you" in Japanese could not find "thank you" in Thai.
 *
 * An invariant that is only ever respected by remembering it has already failed once. These tests
 * make the shapes that broke it structural — a missing field, a duplicate id, a fuzzy match — fail
 * the build rather than produce a plausible file.
 *
 * WHAT IS DELIBERATELY NOT TESTED
 *
 * That every language FILLS every slot. That is not a property of the repository, it is a
 * measurement, and it changes whenever a page is re-harvested. Asserting it here would produce a
 * red build for something that is a finding rather than a defect, which is how people learn to
 * ignore a red build. `node content/spine.mjs` measures it; this file guards the shapes.
 */

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join } from 'node:path';

const ROOT = new URL('../..', import.meta.url).pathname;
const SPINE_DIR = join(ROOT, 'content', 'spine');

const tiers = existsSync(SPINE_DIR)
  ? readdirSync(SPINE_DIR).filter((f) => f.endsWith('.json')).sort()
  : [];

test('the spine exists and is not empty', () => {
  assert.ok(tiers.length > 0, 'no spine files under content/spine/ — every language is back to being independent');
});

for (const file of tiers) {
  const spine = JSON.parse(readFileSync(join(SPINE_DIR, file), 'utf8'));

  test(`${file}: every slot carries the fields the app and generator both need`, () => {
    for (const s of spine.slots) {
      for (const field of ['id', 'english', 'concept', 'domain', 'direction', 'match', 'why']) {
        assert.ok(
          s[field] !== undefined && s[field] !== null && s[field] !== '',
          `${file}: slot ${s.id ?? '(no id)'} is missing ${field}`,
        );
      }
      // Not a punctuation rule. "My name is" is a fragment and correctly has no full stop,
      // because you say "My name is Kedhar" — forcing a period onto it would produce a caption
      // that is not a thing anyone says. What is checked is that it is not empty or truncated,
      // which is the failure this rule was written for.
      assert.ok(s.english.trim().length >= 3 && !s.english.trim().endsWith(','),
        `${file}: slot ${s.id} english "${s.english}" looks empty or truncated`);
      assert.match(s.direction, /^(say|understand)$/, `${file}: slot ${s.id} has direction "${s.direction}"`);
      assert.ok(s.match.length > 0, `${file}: slot ${s.id} has no match patterns, so it can never fill`);
    }
  });

  test(`${file}: slot ids are unique`, () => {
    const ids = spine.slots.map((s) => s.id);
    assert.equal(new Set(ids).size, ids.length, `${file}: duplicate slot id — ids are never reused`);
  });

  test(`${file}: slot ids are namespaced and stable`, () => {
    for (const s of spine.slots) {
      assert.match(s.id, /^[a-z_]+\.[a-z0-9-]+$/,
        `${file}: slot id "${s.id}" is not <group>.<meaning>. An unnamespaced id cannot be read in a report and will collide with a future tier.`);
    }
  });

  test(`${file}: domains are in range and directions are legal`, () => {
    for (const s of spine.slots) {
      assert.ok(Number.isInteger(s.domain) && s.domain >= 1 && s.domain <= 12,
        `${file}: slot ${s.id} has domain ${s.domain}, outside 1-12`);
    }
  });

  /**
   * No slot may claim two different meanings, and no slot may be a near-duplicate of another in
   * the same group. Both happened in the pre-spine English Tier 0, which carried four separate
   * "sorry" phrases and two "thank you" phrases; twenty languages would then need eighty slots
   * filled before anyone could ask the price of a bus ticket.
   */
  test(`${file}: no duplicate meanings within a group`, () => {
    const byGroup = new Map();
    for (const s of spine.slots) {
      const group = s.id.split('.')[0];
      const key = s.english.trim().toLowerCase().replace(/[.!?,]/g, '');
      if (!byGroup.has(group)) byGroup.set(group, new Map());
      const seen = byGroup.get(group);
      assert.ok(!seen.has(key),
        `${file}: slots ${seen.get(key)} and ${s.id} in group "${group}" both mean "${s.english}" — collapse them, or the same meaning gets filled twice in every language`);
      seen.set(key, s.id);
    }
  });

  /**
   * Match patterns are written out by hand and stay that way.
   *
   * The one escape hatch is a trailing `*` for a prefix, which exists because some page captions
   * embed the language's own name — "How do you say this in Thai?" — and an exact list can never
   * match more than the page that happens to phrase it that way.
   *
   * A bare wildcard would let the nearest phrase fill a slot, and this project's content history
   * is a sequence of confident wrong answers that looked fine: 2,918 readings the old parser
   * invented from English glosses, and a Thai numbers table emptied by a reading that turned out
   * to sit in the middle of the phrase.
   */
  test(`${file}: match patterns are explicit — only a trailing * is a wildcard`, () => {
    for (const s of spine.slots) {
      for (const pattern of s.match) {
        // `?` is a LITERAL question mark in a caption — "how are you?" — not a regex
        // metacharacter, and rejecting it flagged the single most common pattern in the file.
        // Only `*` is treated as a wildcard, and only as a trailing one.
        assert.ok(!pattern.replace(/\*$/, '').includes('*'),
          `${file}: slot ${s.id} pattern ${JSON.stringify(pattern)} contains a bare wildcard. Only a trailing * is allowed, and only for a prefix.`);
        assert.ok(!pattern.includes('|'),
          `${file}: slot ${s.id} pattern ${JSON.stringify(pattern)} uses alternation. Write the alternatives as separate entries so each can be read and checked on its own.`);
        assert.ok(typeof pattern === 'string' && pattern.trim().length > 0,
          `${file}: slot ${s.id} has an empty match pattern`);
      }
    }
  });

  test(`${file}: the spine states its tier and intent`, () => {
    assert.ok(Number.isInteger(spine.tier), `${file}: no integer tier`);
    assert.ok(spine.name && spine.name.trim(), `${file}: no tier name`);
    assert.ok(spine.intent && spine.intent.trim().length > 20,
      `${file}: no intent — the intent is the sentence that says what this tier is for`);
  });
}