// Emits the Android assets from the authored sources.
//
//   specs/*.yaml  ->  app/src/main/assets/specs.json
//   content/*.jsonl -> app/src/main/assets/content.jsonl
//
// The app cannot read YAML — there is no YAML parser in the Android runtime — so this
// step converts the specs into the subset of fields the app actually uses. Provenance,
// `why` notes and the review block stay in the repository: they are for the build and
// for review, not for a phone.
//
// Both outputs are build artefacts and are gitignored. If you find yourself wanting to
// commit either of them, the pipeline has stopped being reproducible.

import YAML from 'yaml';
import { readdirSync, readFileSync, writeFileSync, mkdirSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..', '..');
const SPECS = join(ROOT, 'specs');
const CONTENT = join(ROOT, 'content');
const ASSETS = join(ROOT, 'app', 'src', 'main', 'assets');

// Exactly the fields ContentRepository.kt reads. Adding a field here without adding it
// there produces an asset the app silently ignores, which is worse than a failure.
function specToAsset(doc) {
  return {
    language: {
      code: doc.language.code,
      name: doc.language.name,
      endonym: doc.language.endonym ?? doc.language.name,
      romanization: doc.language.romanization ?? null,
      role: doc.language.role ?? 'course',
      gloss_mode: doc.language.gloss_mode ?? 'required',
    },
    variety: {
      default: doc.variety?.default ?? null,
      variants: (doc.variety?.variants ?? []).map(v => ({ id: v.id, label: v.label ?? v.id })),
    },
    structure: {
      script: {
        primary: doc.structure?.script?.primary ?? 'Latin',
        direction: doc.structure?.script?.direction ?? 'ltr',
      },
    },
    register: {
      system: doc.register?.system ?? 'none',
      // The gender question, carried to the app because it cannot be answered in the spec.
      // Thai's polite particle marks the SPEAKER's gender and there is no verified neutral
      // form; a learner must therefore choose, and the app cannot choose for them or ask.
      gender_marked: doc.register?.gender_marked === true,
      neutral_option_verified: doc.register?.neutral_option_verified === true,
    },
    // Attribution is a LICENCE OBLIGATION, not documentation. It was declared in every
    // spec and had nowhere to live in the app, so CC BY-SA content was being
    // redistributed with no in-app credit anywhere. Emitted per language so that adding a
    // language discharges its own obligations without a second edit somewhere.
    attribution: (doc.attribution ?? []).map(a => ({
      source: a.source ?? 'Unknown',
      licence: a.licence ?? 'Unknown',
      author_credit: a.author_credit ?? null,
    })),
    romanization_note: doc.romanization_note?.scheme ?? null,
    tiers: (doc.tiers ?? []).map(t => ({
      id: t.id,
      name: t.name,
      intent: (t.intent ?? '').trim(),
      size: t.size?.value ?? 0,
      certainty: t.certainty,
    })),
  };
}

function main() {
  mkdirSync(ASSETS, { recursive: true });

  const specFiles = readdirSync(SPECS).filter(f => /\.(ya?ml)$/.test(f) && f !== 'SCHEMA.md');
  if (!specFiles.length) throw new Error('no specs found — run the pipeline before emitting');

  const specs = specFiles
    .map(f => YAML.parse(readFileSync(join(SPECS, f), 'utf8')))
    .map(specToAsset);

  // Validate the shape the app depends on. A spec that lints can still be missing a
  // field the renderer needs, and a crash on first launch is how that gets discovered.
  for (const s of specs) {
    if (!s.language.code || !s.language.name) throw new Error(`spec for "${s.language?.code}" has no code or name`);
    if (!s.structure.script.primary) throw new Error(`spec ${s.language.code}: structure.script.primary is required by the renderer`);
    if (!s.tiers.length) throw new Error(`spec ${s.language.code}: no tiers, the path screen has nothing to show`);
  }

  const credits = {
    // Shown on the in-app credits screen. Static text, no URLs the app would open — the
    // product has no network permission and no external links by rule, but naming the
    // licence and the author is what the licence actually requires.
    app_credits: [
      {
        source: 'LangKraft',
        licence: 'Source code, CC BY-SA 4.0',
        author_credit: 'Kedhar Sairam',
        note: 'The app itself: specs, authored content, and the code.',
      },
      {
        source: 'Noto Sans Thai, Noto Sans Tamil',
        licence: 'SIL Open Font License 1.1',
        author_credit: 'Google',
        note: 'Bundled in the APK. The OFL requires the licence to travel with the font, ' +
          'which is why this line exists.',
      },
    ],
  };

  writeFileSync(join(ASSETS, 'specs.json'), JSON.stringify(specs, null, 2));
  writeFileSync(join(ASSETS, 'credits.json'), JSON.stringify(credits, null, 2));

  const records = readdirSync(CONTENT)
    .filter(f => f.endsWith('.jsonl'))
    .flatMap(f => readFileSync(join(CONTENT, f), 'utf8')
      .split('\n')
      .map(l => l.trim())
      .filter(l => l && !l.startsWith('//')));

  for (const [i, r] of records.entries()) {
    try { JSON.parse(r); }
    catch (e) { throw new Error(`content record ${i} is not valid JSON: ${e.message}`); }
  }
  writeFileSync(join(ASSETS, 'content.jsonl'), records.join('\n') + '\n');

  const entries = records.map(r => JSON.parse(r)).filter(r => !r.turns).length;
  console.log(`emitted ${specs.length} spec(s), ${records.length} record(s) (${entries} entries)`);
  console.log(`  ${join('app/src/main/assets', 'specs.json')}`);
  console.log(`  ${join('app/src/main/assets', 'content.jsonl')}`);
  console.log(`  ${join('app/src/main/assets', 'credits.json')}`);
}

main();