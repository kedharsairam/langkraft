#!/usr/bin/env python3
"""
Glyph coverage check for bundled fonts.
WHY THIS EXISTS

A bundled font that lacks a glyph the content uses renders as tofu or a silent
fallback. That failure is invisible in a build and obvious on a phone, which is the worst
place to find it -- so it is checked here instead, against the ACTUAL characters the app
ships, and it fails the pipeline rather than waiting for a screenshot.

This checks the reverse direction too: a font carrying tens of thousands of glyphs when the
app uses two hundred is a large APK in exchange for nothing, which is why CJK needs a subset.

Usage:  python3 pipeline/content/font-coverage.py
Exit 0 when every script's characters are covered; 1 otherwise, naming what is missing.
"""

import json
import re
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CONTENT = ROOT / "content"
FONTS = ROOT / "app" / "src" / "main" / "res" / "font"

# Scripts that must render, and the catalogue codes that use them. Taken from the specs rather
# than hardcoded here, so adding a language without a font fails this check.
# Script -> the res/font file stem. Kept in step with pipeline/fonts/fetch.py.
FACE_FOR_SCRIPT = {
    "arabic": "notosansarabic",
    "cyrillic": "notosanscyrillic",
    "devanagari": "notosansdevanagari",
    "han": "notosanssc",
    "hangul": "notosanskr",
    "japanese": "notosansjp",
    "tamil": "notosanstamil",
    "thai": "notosansthai",
}

SCRIPT_OF_CODE = {
    "ara": "arabic", "fas": "arabic",
    "hin": "devanagari",
    "jpn": "japanese", "cmn": "han", "kor": "hangul",
    "rus": "cyrillic", "srp": "cyrillic",
    "tha": "thai", "tam": "tamil",
}


def read_cmap(path):
    """Codepoints a font can render, from its cmap table."""
    data = path.read_bytes()
    num_tables = struct.unpack(">H", data[4:6])[0]
    tables = {}
    for i in range(num_tables):
        off = 12 + i * 16
        tag = data[off:off + 4].decode("latin1")
        tables[tag] = (struct.unpack(">I", data[off + 8:off + 12])[0],
                       struct.unpack(">I", data[off + 12:off + 16])[0])

    if "cmap" not in tables:
        raise ValueError(f"{path.name}: no cmap table")

    base = tables["cmap"][0]
    n_sub = struct.unpack(">H", data[base + 2:base + 4])[0]
    best = None
    for i in range(n_sub):
        rec = base + 4 + i * 8
        pid, eid, offset = struct.unpack(">HHI", data[rec:rec + 8])
        # (3,10) is Windows UCS-4 and (3,1) is BMP; both are full Unicode maps.
        if (pid, eid) in ((3, 10), (3, 1), (0, 4), (0, 3), (0, 6)):
            best = base + offset

    if best is None:
        raise ValueError(f"{path.name}: no Unicode cmap subtable")

    cps = set()
    fmt = struct.unpack(">H", data[best:best + 2])[0]
    if fmt == 4:
        seg_x2 = struct.unpack(">H", data[best + 6:best + 8])[0]
        seg = seg_x2 // 2
        ends = [struct.unpack(">H", data[best + 14 + i * 2:best + 16 + i * 2])[0] for i in range(seg)]
        starts = [struct.unpack(">H", data[best + 16 + seg_x2 + i * 2:best + 18 + seg_x2 + i * 2])[0] for i in range(seg)]
        for s, e in zip(starts, ends):
            if s == 0xFFFF:
                continue
            cps.update(range(s, e + 1))
    elif fmt == 12:
        n_groups = struct.unpack(">I", data[best + 12:best + 16])[0]
        for i in range(n_groups):
            g = best + 16 + i * 12
            s, e = struct.unpack(">II", data[g:g + 8])
            cps.update(range(s, e + 1))
    else:
        raise ValueError(f"{path.name}: unsupported cmap format {fmt}")

    return cps


def characters_used():
    """Every character the app renders, per script, from the shipped content."""
    used = {}
    for f in sorted(CONTENT.glob("*-tier0.jsonl")):
        if not f.stem.endswith("tier0"):
            continue
        for line in f.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            rec = json.loads(line)
            script = SCRIPT_OF_CODE.get(rec.get("lang"))
            if not script:
                continue
            native = rec.get("text_native") or ""
            used.setdefault(script, set()).update(c for c in native if not c.isspace())
    return used


def main():
    used = characters_used()
    if not used:
        print("  no non-Latin content found — nothing to check")
        return 0

    print(f"\n  Glyph coverage for {len(used)} script(s) in content:\n")
    failures = []
    for script in sorted(used):
        # The font file for a script, by the naming already in res/font.
        # One face per SCRIPT, not per language: Chinese and Japanese share Noto Sans SC/JP
        # files under different names, so the map below is explicit. Globbing by name guessed
        # "han" and "hangul" were absent when both were bundled under notosanssc and
        # notosanskr -- the checker reported three languages as unrenderable when the fonts were
        # sitting in the same directory. A check that lies about missing things is worse than no
        # check.
        stem = FACE_FOR_SCRIPT.get(script)
        candidates = sorted(FONTS.glob(f"{stem}*.ttf")) if stem else []
        if not candidates:
            failures.append(f"{script}: NO FONT BUNDLED — {len(used[script])} characters render as tofu")
            print(f"  {script:<12} NO FONT")
            continue

        # Regular weight is the one every screen can hit.
        target = next((c for c in candidates if "regular" in c.name.lower()), candidates[0])
        covered = read_cmap(target)
        missing = sorted(c for c in used[script] if ord(c) not in covered)

        status = "OK" if not missing else f"MISSING {len(missing)}"
        print(f"  {script:<12} {target.name:<34} {len(used[script]):>4} used  {status}")
        if missing:
            shown = " ".join(f"{c!r}(U+{ord(c):04X})" for c in missing[:12])
            more = f" +{len(missing) - 12} more" if len(missing) > 12 else ""
            print(f"                 {shown}{more}")
            failures.append(f"{script}: {len(missing)} glyph(s) missing from {target.name}")

    if failures:
        print("\n  FAILED:")
        for f in failures:
            print(f"    {f}")
        return 1

    print("\n  every script's characters are covered by a bundled font\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())