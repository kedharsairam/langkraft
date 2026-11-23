"""Hard validation gate for LangKraft content. Run: python3 pipeline/validate_content.py <code>

Every language must pass before it ships. Checks the rules that matter and that
tests do not currently cover: full lesson coverage, Latin-script-only, anchors,
provenance, and CAPS stress.
"""
import json, os, re, sys, unicodedata

BASE = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "langkraft")
REQUIRED = ["id", "lang", "theme", "english", "romanization", "youSay", "soundTip", "note", "source", "checked", "anchor"]
EXPECTED_LESSONS = {1,2,8,9,10,11,12,52,56,57,58,70,71,74,75,76,78,79,81,82,84,89,90,91,92,93,94,96,97,98,99,100,118,123,125}
MIN_ROWS, MAX_ROWS = 450, 470
ALLOWED_SOURCE = re.compile(r'^(Wikivoyage: |LangKraft authored)')


def _walk_strings(obj):
    if isinstance(obj, str):
        yield obj
    elif isinstance(obj, dict):
        for v in obj.values():
            yield from _walk_strings(v)
    elif isinstance(obj, list):
        for v in obj:
            yield from _walk_strings(v)


def is_latin_script(s):
    for c in s:
        if not c.isalpha():
            continue
        name = unicodedata.name(c, "")
        if "LATIN" not in name:
            return False
    return True


def validate(code):
    errors = []
    path = os.path.join(BASE, f"{code}.jsonl")
    if not os.path.exists(path):
        return [f"missing {path}"]
    rows = []
    for i, line in enumerate(open(path, encoding="utf-8"), 1):
        if not line.strip():
            continue
        try:
            rows.append(json.loads(line))
        except Exception as e:
            errors.append(f"line {i}: bad JSON {e}")
            continue
    if not (MIN_ROWS <= len(rows) <= MAX_ROWS):
        errors.append(f"row count {len(rows)} outside {MIN_ROWS}-{MAX_ROWS}")
    lessons = {r.get("theme") for r in rows}
    if lessons != EXPECTED_LESSONS:
        errors.append(f"lesson set mismatch: missing {sorted(EXPECTED_LESSONS - lessons)}, extra {sorted(lessons - EXPECTED_LESSONS)}")
    for i, r in enumerate(rows, 1):
        for k in REQUIRED:
            if k not in r:
                errors.append(f"row {i}: missing {k}")
        # note and soundTip are intentionally optional in value ("only when it matters").
        for k in ("id", "lang", "theme", "english", "romanization", "youSay", "source", "checked", "anchor"):
            if not str(r.get(k, "")).strip():
                errors.append(f"row {i}: empty {k}")
        if r.get("lang") != code:
            errors.append(f"row {i}: lang {r.get('lang')} != {code}")
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", str(r.get("checked", ""))):
            errors.append(f"row {i}: bad checked date")
        if not ALLOWED_SOURCE.match(str(r.get("source", ""))):
            errors.append(f"row {i}: bad source string")
        if not re.fullmatch(r"LingoHut L\d{2,3}: .+", str(r.get("anchor", ""))):
            errors.append(f"row {i}: bad anchor")
        ys = str(r.get("youSay", ""))
        if not any(c.isupper() for c in ys):
            errors.append(f"row {i}: youSay has no CAPS stress")
        if not re.fullmatch(r"[A-Za-zÀ-ɏ'’.\- ]+", ys):
            errors.append(f"row {i}: youSay has unexpected characters")
        # Telugu characters are allowed in soundTip/note as sound references,
        # but the target language must never appear as a native script.
        for field in ("english", "romanization", "youSay"):
            if not is_latin_script(str(r.get(field, ""))):
                errors.append(f"row {i}: {field} contains a non-Latin script")
    for extra, loader in (("numbers", True), ("soundcheck", False)):
        p = os.path.join(BASE, f"{extra}-{code}.json")
        if not os.path.exists(p):
            errors.append(f"missing {p}")
            continue
        try:
            d = json.load(open(p, encoding="utf-8"))
            if loader and len(d) < 20:
                errors.append(f"{extra} has only {len(d)} entries")
            if loader:
                for j, n in enumerate(d, 1):
                    ys = str(n.get("youSay", ""))
                    if not any(c.isupper() for c in ys):
                        errors.append(f"{extra} row {j}: youSay has no CAPS stress")
                    if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", str(n.get("checked", ""))):
                        errors.append(f"{extra} row {j}: bad checked date")
                    if not ALLOWED_SOURCE.match(str(n.get("source", ""))):
                        errors.append(f"{extra} row {j}: bad source string")
                values = [str(n.get("value", "")) for n in d]
                if len(values) != len(set(values)):
                    errors.append(f"{extra}: duplicate values")
            # No target-language script may be shown anywhere in the app.
            # Telugu is allowed as a sound reference in notes/soundTips.
            for field_value in _walk_strings(d):
                for c in field_value:
                    n = unicodedata.name(c, "")
                    if c.isalpha() and "LATIN" not in n and "TELUGU" not in n:
                        errors.append(f"{extra}: non-Latin script character {c!r} ({n})")
                        break
        except Exception as e:
            errors.append(f"{extra}: bad JSON {e}")
    cat = os.path.join(BASE, "catalog.json")
    if os.path.exists(cat):
        codes = {x["code"] for x in json.load(open(cat, encoding="utf-8"))}
        if code not in codes:
            errors.append("code missing from catalog.json")
    return errors


if __name__ == "__main__":
    code = sys.argv[1]
    errs = validate(code)
    if errs:
        print(f"FAIL {code}: {len(errs)} error(s)")
        for e in errs[:40]:
            print("  " + e)
        sys.exit(1)
    print(f"PASS {code}: rows in range, {len(EXPECTED_LESSONS)} lessons, Latin-only, anchored")
