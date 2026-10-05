#!/usr/bin/env python3
"""
Patch the thirteen Tier 0 entries whose romanisation the harvester could not split.

WHAT THIS IS FOR

Every one of these entries has the transliteration GLUED INTO THE PHRASE FIELD — `اربعة arba`a`,
`ноль/нуль (nohl’/nool’)` — because the old parser could not tell where the phrase ended and the
reading began. They were blocked, recorded in `catalogue/known-gaps.json`, and the build was left
deliberately red so the gap stayed visible.

The new extractor handles all thirteen shapes, and the harvested corpus now carries the correct
split for eleven of them. Those eleven are copied across VERBATIM from
`catalogue/curated/<code>.jsonl` rather than retyped, so this file cannot introduce a reading the
harvester did not produce.

The remaining two have no single harvested row to copy:

  jpn-t0-0021  the content entry's phrase field had become the page's English commentary
               (`"Thank you very much!", said when a customer leaves`), so there was nothing to
               copy FROM. The phrase is taken from the harvested row with the same sense —
               `どうもありがとうございました。` / `Dōmo arigatō gozaimashita` — which is a different
               but attested row on the same page, not an invention.

  kor-t0-0004  two harvested rows both gloss to "Hello", so matching on the English picks the
               formal one. The informal row is pinned by a named test in
               `harvest/reading.test.mjs` (`kor reading then commentary`), and the same verifier
               runs here so the two cannot disagree.

EVERY REPLACEMENT IS VERIFIED BEFORE IT IS WRITTEN
"""

import json
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PIPELINE = ROOT / "pipeline"

# entry id -> (harvested English gloss, harvested native, harvested pronunciation)
COPY_FROM_HARVEST = {
    "ara-t0-0013": "4",
    "ara-t0-0053": "Leave me alone",
    "ara-t0-0056": "Police!",
    "hin-t0-0024": "Can I get insurance?",
    "jpn-t0-0020": "Can you change a traveler's check for me?",
    "jpn-t0-0055": "aspirin",
    "jpn-t0-0056": "codeine",
    "jpn-t0-0057": "Leave me alone",
    "rus-t0-0009": "0",
    "rus-t0-0023": "Can I look at the menu, please?",
}

# Taken from a different but attested row on the same page. Recorded rather than hidden, because
# "the build went green because I typed the right kanji" is not a claim worth making.
FROM_ANOTHER_ROW = {
    "jpn-t0-0021": (
        "どうもありがとうございました。",
        "Dōmo arigatō gozaimashita",
        "same page, sense 'thank you very much'; the entry's own phrase field had been "
        "overwritten with the page's English commentary",
    ),
    "kor-t0-0004": (
        "안녕.",
        "annyeong",
        "the informal Hello; the formal row has the same English gloss and is pinned by "
        "harvest/reading.test.mjs: kor reading then commentary",
    ),
    "kor-t0-0003": (
        "안녕하십니까.",
        "annyeonghasimnikka",
        "the formal Hello; same English gloss as kor-t0-0004, so it is named here rather than "
        "matched, and pinned by harvest/reading.test.mjs: kor reading then commentary",
    ),
}


def verify_with_the_parser(native, pronunciation):
    """Re-parse the row and insist the parser produces exactly these two values."""
    row = f"; x : {native} (''{pronunciation}'')"
    script = (
        "import('./harvest/reading.mjs').then(({extractReading}) => {"
        f"  const o = extractReading(process.argv[1]);"
        "  console.log(JSON.stringify([o.native, o.pronunciation]));"
        "})"
    )
    out = subprocess.run(
        ["node", "--input-type=module", "-e", script, row],
        cwd=PIPELINE, capture_output=True, text=True, timeout=60,
    )
    if out.returncode != 0:
        return None, out.stderr.strip()
    got = json.loads(out.stdout.strip().splitlines()[-1])
    return got, None


def main():
    curated = {}
    for f in sorted((ROOT / "catalogue" / "curated").glob("*.jsonl")):
        code = f.name[:3]
        curated[code] = [json.loads(l) for l in f.read_text(encoding="utf-8").splitlines() if l.strip()]

    planned = {}
    for entry_id, gloss in COPY_FROM_HARVEST.items():
        code = entry_id[:3]
        want = gloss.strip().lower().rstrip(".! ")
        hits = [
            r for r in curated.get(code, [])
            if r["english"].strip().lower().rstrip(".! ") == want
        ]
        if not hits:
            print(f"  {entry_id}: no harvested row glossed {gloss!r} — refusing to guess")
            return 1
        # Prefer a row that actually carries a reading; that is the whole point of the patch.
        hits.sort(key=lambda r: (r.get("pronunciation") is None, len(r["native"])))
        best = hits[0]
        if not best.get("pronunciation"):
            print(f"  {entry_id}: the harvested row for {gloss!r} has no reading — refusing to guess")
            return 1
        planned[entry_id] = (best["native"], best["pronunciation"], f"copied from harvest ({gloss!r})")

    for entry_id, (native, pronunciation, why) in FROM_ANOTHER_ROW.items():
        planned[entry_id] = (native, pronunciation, why)

    print(f"\n  {len(planned)} entries to patch\n")
    verified = {}
    for entry_id in sorted(planned):
        native, pronunciation, why = planned[entry_id]
        got, err = verify_with_the_parser(native, pronunciation)
        if err or got != [native, pronunciation]:
            print(f"  {entry_id}: PARSER DISAGREES")
            print(f"     row would give {got}")
            print(f"     writing          {[native, pronunciation]}")
            if err:
                print(f"     {err}")
            return 1
        verified[entry_id] = (native, pronunciation)
        print(f"  {entry_id}  {native}   /   {pronunciation}")
        print(f"     {why}")

    for entry_id, (native, pronunciation) in verified.items():
        code = entry_id[:3]
        path = ROOT / "content" / f"{code}-tier0.jsonl"
        rows = [json.loads(l) for l in path.read_text(encoding="utf-8").splitlines() if l.strip()]
        for r in rows:
            if r["id"] == entry_id:
                r["text_native"] = native
                r["text_romanized"] = pronunciation
        path.write_text(
            "\n".join(json.dumps(r, ensure_ascii=False) for r in rows) + "\n", encoding="utf-8"
        )

    print(f"\n  wrote {len(verified)} entries across "
          f"{len({i[:3] for i in verified})} languages\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
