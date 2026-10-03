// Test fixture: the minimum spec that must pass.
// Lives in its own module so the test file and any debug script share one definition.

export function validSpec() {
  return {
    language: { code: 'swh', name: 'Swahili', endonym: 'Kiswahili', romanization: null, role: 'course' },
    spec_version: '1.0.0',
    status: 'draft',
    variety: {
      default: 'sw-KE',
      romanization_scheme: null,
      rationale: { value: 'coastal Kenyan', source: 'https://example.org/a', verified: false, checked: '2026-10-03' },
      variants: [
        { id: 'sw-KE', label: 'Kenyan', notes: { value: 'a', source: 'internal', verified: false, checked: '2026-10-03' } },
        { id: 'sw-TZ', label: 'Tanzanian', notes: { value: 'b', source: 'internal', verified: false, checked: '2026-10-03' } },
      ],
      fails_in: [],
    },
    structure: { morphology: 'analytic', tones: false, word_order: 'SVO', sounds_absent_from_l1: [], script: { primary: 'Latin', direction: 'ltr' } },
    register: { system: 'none', notes: { value: 'none', source: 'internal', verified: false, checked: '2026-10-03' } },
    coverage: { territories: { value: [], source: 'internal', verified: false, checked: '2026-10-03' }, adjacent: [], traps: [] },
    tiers: [
      { id: 0, name: 'Courtesy', intent: 'Not rude.', size: { value: 50, source: 'internal', verified: false, checked: '2026-10-03' }, certainty: 'high' },
      { id: 1, name: 'Transaction', intent: 'It works.', size: { value: 150, source: 'internal', verified: false, checked: '2026-10-03' }, certainty: 'medium' },
      { id: 2, name: 'Independence', intent: 'Unaided.', size: { value: 500, source: 'internal', verified: false, checked: '2026-10-03' }, certainty: 'low' },
      { id: 3, name: 'Conversation', intent: 'Small talk.', size: { value: 1200, source: 'internal', verified: false, checked: '2026-10-03' }, certainty: 'low' },
    ],
    cost: {
      fsi_category: { value: 'II', source: 'https://example.org/fsi', verified: true, checked: '2026-10-03' },
      fsi_hours_to_ilr3: { value: 828, source: 'https://example.org/fsi', verified: true, checked: '2026-10-03' },
      hours_to_floor: { value: { t0: 40 }, source: 'internal', verified: false, checked: '2026-10-03' },
    },
    resources: [
      { name: 'GLOSS', url: 'https://gloss.dliflc.edu', licence: 'Public domain', class: 'curated', checked: '2026-10-03',
        covers: { value: 'lessons', source: 'https://example.org', verified: true, checked: '2026-10-03' } },
    ],
    review: { flags_received: 0, last_reviewed: null, known_gaps: [] },
  };
}
