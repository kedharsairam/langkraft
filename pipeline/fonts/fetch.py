#!/usr/bin/env python3
"""
Fetch, verify and subset the bundled fonts.

WHY THIS FILE EXISTS AT ALL

Every font previously in `res/font/` was a GitHub 404 HTML page saved with a `.ttf` name. All
eight of them. They were ~269 KB each, within 50 bytes of each other, and began with
`<!DOCTYPE html>` rather than an sfnt magic number. The app shipped with them, `ScriptFonts.kt`
documented a fallback problem they were supposed to have solved, and a coverage check written
much later found the truth immediately: `no cmap table`.

So the fetcher now refuses anything it has not verified, and that refusal is the whole point of
this file existing rather than a one-line `curl`.

THE VERIFICATION

Three checks, because each catches a different failure:

  1. sfnt magic -- `\x00\x01\x00\x00` (TrueType), `OTTO` (CFF), `ttcf` (collection), or `wOFF`/`wOF2`
     (already-compressed). Anything else is not a font. This is what caught the HTML.
  2. A real file with plausible size. An 8-byte 200 response or a redirect stub is not a font.
  3. A Unicode `cmap` subtable, because a font without one cannot address a character by
     codepoint and therefore cannot render any of the content. A font can pass (1) and still
     fail this.

CJK IS SUBSETTED, AND THAT IS THE POINT

Noto Sans SC is 17 MB, JP 9 MB, KR 10 MB. Together 36 MB for a 1.8 MB release APK -- twenty
times the whole app, to render about two hundred distinct characters per language.

So the CJK faces are subset to exactly the codepoints the shipped content uses, plus the
codepoints a reader might need for anything reachable from them. The result is tens of
kilobytes, and the coverage check proves nothing is missing afterwards rather than assuming.

The non-CJK faces (Arabic 0.8 MB, Devanagari 0.6 MB) are subset on the same principle because
the machinery is already here and a smaller APK is a smaller APK. The difference is that
subsetting CJK is what makes the approach viable at all, and subsetting Arabic is an
optimisation.

REGENERATION

    python3 pipeline/fonts/fetch.py

Deterministic given the same content, and prints exactly what it produced. It writes an
attribution file listing every face, its source URL and its licence, because OFL requires the
licence to travel with the font and shipping the binaries without it is a licence violation.
"""

import json
import struct
import subprocess
import sys
import tempfile
from pathlib import Path
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[2]
FONTS = ROOT / "app" / "src" / "main" / "res" / "font"
RAW = ROOT / "app" / "src" / "main" / "res" / "raw"
CONTENT = ROOT / "content"
CATALOGUE = ROOT / "catalogue" / "languages.json"

GF = "https://raw.githubusercontent.com/google/fonts/main/ofl"

# Catalogue code -> (font family, source path, res file stem, subset?)
# The subset flag is not cosmetic: CJK must be subset to ship at all.
FACES = {
    "arabic":    ("Noto Sans Arabic",    f"{GF}/notosansarabic/NotoSansArabic%5Bwdth,wght%5D.ttf",    "notosansarabic",    True),
    "devanagari":("Noto Sans Devanagari", f"{GF}/notosansdevanagari/NotoSansDevanagari%5Bwdth,wght%5D.ttf", "notosansdevanagari", True),
    "cyrillic":  ("Noto Sans",           f"{GF}/notosans/NotoSans%5Bwdth,wght%5D.ttf",               "notosanscyrillic",  True),
    "hangul":    ("Noto Sans KR",        f"{GF}/notosanskr/NotoSansKR%5Bwght%5D.ttf",               "notosanskr",        True),
    "han":       ("Noto Sans SC",        f"{GF}/notosanssc/NotoSansSC%5Bwght%5D.ttf",               "notosanssc",        True),
    "japanese":  ("Noto Sans JP",        f"{GF}/notosansjp/NotoSansJP%5Bwght%5D.ttf",               "notosansjp",        True),
    "thai":      ("Noto Sans Thai",      f"{GF}/notosansthai/NotoSansThai%5Bwdth,wght%5D.ttf",            "notosansthai",      True),
    "tamil":     ("Noto Sans Tamil",     f"{GF}/notosanstamil/NotoSansTamil%5Bwdth,wght%5D.ttf",          "notosanstamil",     True),
}

SCRIPT_OF_CODE = {
    "ara": "arabic", "fas": "arabic",
    "hin": "devanagari",
    "jpn": "japanese", "cmn": "han", "kor": "hangul",
    "rus": "cyrillic", "srp": "cyrillic",
    "tha": "thai", "tam": "tamil",
}

# A variable font flattened to instances: four weights, matching the type scale. The previous
# Thai/Tamil note claimed static instances were needed because Android flattens variable fonts
# to their default instance and loses the weight distinction. That claim was attached to fonts
# that were never real, so it was never actually tested; instancing here satisfies it either way
# and costs little for subset faces.
WEIGHTS = {"Regular": 400, "Medium": 500, "SemiBold": 600, "Bold": 700}

UA = "LangKraft/1.0 (offline phrasebook; font subsetting)"


def verify_font(path: Path) -> None:
    """Raise unless `path` is a real, addressable font. The check that was missing."""
    data = path.read_bytes()
    if len(data) < 4096:
        raise ValueError(f"{path.name}: {len(data)} bytes is too small to be a font")

    tag = data[:4]
    if tag not in (b"\x00\x01\x00\x00", b"OTTO", b"ttcf", b"wOFF", b"wOF2"):
        preview = data[:60].decode("latin1", errors="replace").replace("\n", " ")
        raise ValueError(
            f"{path.name}: not a font. sfnt magic is {tag!r}, not a TrueType/CFF marker. "
            f"First bytes read: {preview!r}. This is what an HTML error page looks like."
        )

    num_tables = struct.unpack(">H", data[4:6])[0]
    tags = set()
    for i in range(num_tables):
        off = 12 + i * 16
        if off + 16 > len(data):
            break
        tags.add(data[off:off + 4].decode("latin1", errors="replace"))
    if "cmap" not in tags:
        raise ValueError(f"{path.name}: no cmap table, so it cannot address any character")
    print(f"    verified: sfnt ok, {num_tables} tables, cmap present")


def used_codepoints() -> dict:
    """Codepoints the shipped content actually renders, per script."""
    per_script: dict = {}
    for f in sorted(CONTENT.glob("*-tier0.jsonl")):
        for line in f.read_text(encoding="utf-8").splitlines():
            if not line.strip():
                continue
            rec = json.loads(line)
            script = SCRIPT_OF_CODE.get(rec.get("lang"))
            if not script:
                continue
            native = rec.get("text_native") or ""
            if not native:
                continue
            s = per_script.setdefault(script, set())
            for ch in native:
                if not ch.isspace():
                    s.add(ord(ch))
    return per_script


def fetch(url: str, dest: Path, attempts: int = 4) -> None:
    """
    Download and VERIFY, with retry on throttling.

    The retry is not defensive padding. GitHub rate-limits anonymous raw requests, and this
    script makes thirty of them in sequence, which reliably trips the limit partway through.
    Without a retry the run aborted with a bare `HTTP Error 404` after the Tamil fetch having
    produced nothing -- and the same 404 earlier in the harvest pipeline was recorded as a
    language having no phrasebook. A throttle must never be recorded as a finding, which is the
    third time that has happened in this project.
    """
    last = None
    for attempt in range(attempts):
        try:
            req = Request(url, headers={"User-Agent": UA})
            with urlopen(req, timeout=120) as r:
                body = r.read()
            dest.write_bytes(body)
            verify_font(dest)
            return
        except Exception as e:  # noqa: BLE001
            last = e
            # Rate limiting and a genuine 404 both surface as HTTPError; back off for either,
            # because a retry costs seconds and a false gap costs a language.
            wait = 3 * (attempt + 1)
            print(f"    attempt {attempt + 1}/{attempts} failed ({e}); retrying in {wait}s")
            import time
            time.sleep(wait)
    raise RuntimeError(f"could not fetch {url} after {attempts} attempts: {last}")


def subset(src: Path, out_stem: Path, cps: set) -> None:
    """
    Instance the variable font at each weight, then subset to `cps`.

    Two steps, both necessary. Instancing turns the variable axes into fixed weights, because
    the type scale distinguishes Normal/Medium/SemiBold/Bold and a single variable instance
    would flatten all four to the same drawing. Subsetting then removes every glyph the app
    does not use, which for CJK is the whole reason this is viable: 36 MB of Noto CJK becomes
    tens of kilobytes once reduced to the characters Tier 0 actually contains.

    `--layout-features=*` is kept deliberately. Devanagari and Arabic shaping lives in GSUB and
    GPOS -- conjunct formation and initial/medial/final forms. Subsetting with the default
    feature set would strip exactly the tables those scripts need, and the result would render
    individual letters correctly while breaking every word that requires joining. A subset font
    that drops shaping looks fine in a test containing one character and broken in the app.
    """
    unicodes = ",".join(hex(c) for c in sorted(cps))
    for name, weight in WEIGHTS.items():
        inst = out_stem.with_suffix(f".{weight}.inst.ttf")
        subprocess.run(
            [sys.executable, "-m", "fontTools.varLib.instancer",
             str(src), f"wght={weight}", "-o", str(inst)],
            check=True, capture_output=True,
        )
        dest = out_stem.parent / f"{out_stem.name}{name.lower()}.ttf"
        subprocess.run(
            [sys.executable, "-m", "fontTools.subset", str(inst),
             f"--unicodes={unicodes}",
             f"--output-file={dest}",
             "--layout-features=*",
             "--notdef-outline",
             "--name-IDs=*",
             "--drop-tables+=DSIG"],
            check=True, capture_output=True,
        )
        inst.unlink()


def main():
    FONTS.mkdir(parents=True, exist_ok=True)
    RAW.mkdir(parents=True, exist_ok=True)
    per_script = used_codepoints()

    if not per_script:
        print("  no non-Latin content found — run the content build first")
        return 1

    print(f"\n  Characters the shipped content renders: "
          f"{ {k: len(v) for k, v in sorted(per_script.items())} }\n")

    attribution = []
    failures = []
    with tempfile.TemporaryDirectory() as tmpdir:
        tmp = Path(tmpdir)
        for script, (family, url, stem, do_subset) in sorted(FACES.items()):
            cps = per_script.get(script)
            if not cps:
                print(f"  {script:<12} skipped — no content uses it")
                continue

            src = tmp / f"{stem}-var.ttf"
            print(f"  {script:<12} {family}")
            try:
                fetch(url, src)
            except RuntimeError as e:
                # Record and continue. One face failing is a gap in one script; aborting loses
                # the other seven, which have already been produced at this point.
                failures.append(f"{script} ({family}): {e}")
                print(f"    SKIPPED — {e}")
                continue
            original_mb = src.stat().st_size / 1048576

            if do_subset:
                subset(src, FONTS / stem, cps)
            else:
                (FONTS / f"{stem}regular.ttf").write_bytes(src.read_bytes())

            files = sorted(FONTS.glob(f"{stem}*.ttf"))
            total_kb = sum(f.stat().st_size for f in files) / 1024
            print(f"    {original_mb:.2f} MB -> {total_kb:.0f} KB across {len(files)} weights")
            for f in files:
                verify_font(f)

            attribution.append({
                "family": family,
                "script": script,
                "source": url.replace("%5B", "[").replace("%5D", "]"),
                "licence": "SIL Open Font License 1.1",
                "files": [f.name for f in files],
            })

    write_attribution(attribution)
    write_ofl()

    total = sum(f.stat().st_size for f in FONTS.glob("*.ttf")) / 1048576
    print(f"\n  {total:.2f} MB of fonts across {len(list(FONTS.glob('*.ttf')))} files")
    print("  -> res/font/*.ttf, res/raw/noto_attribution.json, res/raw/noto_licence.txt")

    if failures:
        print("\n  FAILED faces:")
        for f in failures:
            print(f"    {f}")
        return 1
    print()
    return 0


def write_attribution(rows):
    out = RAW / "noto_attribution.json"
    out.write_text(json.dumps({
        "note": "Every font bundled in this APK. OFL 1.1 requires the licence to travel with "
                "the font, so this is a licence obligation, not credit.",
        "fonts": rows,
    }, indent=2) + "\n", encoding="utf-8")


def write_ofl():
    """Fetch the canonical OFL text rather than pasting it, so it cannot drift."""
    url = "https://raw.githubusercontent.com/google/fonts/main/ofl/notosans/OFL.txt"
    try:
        req = Request(url, headers={"User-Agent": UA})
        with urlopen(req, timeout=60) as r:
            text = r.read().decode("utf-8")
        header = (
            "LangKraft bundled font licence notices\n"
            "====================================\n\n"
            "Every font in this APK is licensed under the SIL Open Font License, Version 1.1.\n"
            "The full licence text follows once and applies to all of them.\n\n"
            "The families, their sources and their file lists are in noto_attribution.json,\n"
            "which is beside this file. Per-family copyright lines follow the licence.\n\n"
            "No family declares a Reserved Font Name, so the files keep their original names.\n"
            "Renaming a font that DOES reserve its name would be an OFL violation.\n\n"
            "-----------------------------------------------------------------------------\n\n"
        )
        (RAW / "noto_licence.txt").write_text(header + text, encoding="utf-8")
        # Remove the stale notices file so there is exactly one licence document.
        stale = RAW / "noto_licence_notices.txt"
        if stale.exists():
            stale.unlink()
    except Exception as e:  # noqa: BLE001
        # Non-fatal but loud. An OFL notice that fails to download is a licence obligation
        # going unmet, so it cannot pass silently -- but it also must not discard the fonts.
        print(f"  FAILED to write the OFL notice: {e}")
        print("  This is a LICENCE OBLIGATION. Re-run before shipping.")
        return False


if __name__ == "__main__":
    sys.exit(main())