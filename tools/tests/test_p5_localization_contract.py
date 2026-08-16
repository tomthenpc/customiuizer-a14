"""P5-A/B localization contract: user-visible resources must be localised and
reachable across all formal locales."""

import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
RES = REPO / "app" / "src" / "main" / "res"
FORMAL_LOCALES = [
    "values-zh-rCN",
    "values-zh-rTW",
    "values-cs-rCZ",
    "values-es-rES",
    "values-ja-rJP",
    "values-pt-rBR",
    "values-ru-rRU",
    "values-tr-rTR",
    "values-vi-rVN",
]

ANDROID_NS = "http://schemas.android.com/apk/res/android"
USER_VISIBLE_ATTRS = ("title", "summary", "text", "hint")


def _parse(path: Path) -> ET.Element:
    return ET.parse(path).getroot()


def _load_strings(path: Path) -> tuple[dict[str, str], set[str]]:
    root = _parse(path)
    result = {}
    non_translatable = set()
    for el in root:
        if el.tag == "string" and "name" in el.attrib:
            result[el.attrib["name"]] = "".join(el.itertext())
            if (el.get("translatable", "").lower() == "false" or
                el.get(f"{{{ANDROID_NS}}}translatable", "").lower() == "false"):
                non_translatable.add(el.attrib["name"])
    return result, non_translatable


def _find_user_visible_refs() -> set[str]:
    refs = set()
    for subdir in ("xml", "layout", "menu"):
        for f in (RES / subdir).rglob("*.xml"):
            try:
                root = _parse(f)
            except ET.ParseError:
                continue
            for el in root.iter():
                for attr in USER_VISIBLE_ATTRS:
                    val = el.get(f"{{{ANDROID_NS}}}{attr}", "")
                    if val.startswith("@string/"):
                        refs.add(val[8:])
    return refs


class P5LocalizationContractTest(unittest.TestCase):

    def test_no_hardcoded_user_visible_text(self):
        """android:title/summary/text/hint must not contain literal visible text."""
        allowed_literals = {
            ("prefs_system_hideicons.xml", "title", "SIM 1"),
            ("prefs_system_hideicons.xml", "title", "SIM 2"),
            ("fragment_selectcolor.xml", "text", "HSV"),
        }
        violations = []
        for subdir in ("xml", "layout", "menu"):
            for f in (RES / subdir).rglob("*.xml"):
                try:
                    root = _parse(f)
                except ET.ParseError:
                    continue
                for el in root.iter():
                    for attr in USER_VISIBLE_ATTRS:
                        val = el.get(f"{{{ANDROID_NS}}}{attr}", "")
                        if val and not val.startswith("@") and not val.startswith("?"):
                            key = (f.name, attr, val)
                            if key in allowed_literals:
                                continue
                            violations.append(f"{f.relative_to(REPO)} android:{attr}={val!r}")
        self.assertFalse(violations, "\n".join(violations))

    def test_user_visible_strings_exist_in_all_formal_locales(self):
        base, base_non_tr = _load_strings(RES / "values" / "strings.xml")
        locale_data = {loc: _load_strings(RES / loc / "strings.xml") for loc in FORMAL_LOCALES}
        locale_strings = {loc: d[0] for loc, d in locale_data.items()}
        locale_non_tr = set().union(*[d[1] for d in locale_data.values()])
        non_translatable = base_non_tr | locale_non_tr

        required = _find_user_visible_refs() - non_translatable

        missing_base = [k for k in required if k not in base]
        self.assertFalse(missing_base, f"Missing in values: {missing_base}")

        missing_by_locale = {}
        for loc in FORMAL_LOCALES:
            missing = [k for k in required if k not in locale_strings[loc]]
            if missing:
                missing_by_locale[loc] = missing
        self.assertFalse(missing_by_locale, f"Missing translations: {missing_by_locale}")

    def test_placeholder_parity(self):
        base, base_non_tr = _load_strings(RES / "values" / "strings.xml")
        locale_data = {loc: _load_strings(RES / loc / "strings.xml") for loc in FORMAL_LOCALES}
        locale_strings = {loc: d[0] for loc, d in locale_data.items()}
        locale_non_tr = set().union(*[d[1] for d in locale_data.values()])
        non_translatable = base_non_tr | locale_non_tr

        required = _find_user_visible_refs() - non_translatable
        placeholder_re = re.compile(r"%[%\d]*(?:\$\d+)?[sdxXfFc]")

        mismatches = []
        for loc in FORMAL_LOCALES:
            loc_strings = _load_strings(RES / loc / "strings.xml")
            for key in required:
                if key not in base or key not in loc_strings:
                    continue
                base_ph = set(placeholder_re.findall(base[key]))
                loc_ph = set(placeholder_re.findall(loc_strings[key]))
                if base_ph != loc_ph:
                    mismatches.append(f"{loc}/{key}: {base_ph} != {loc_ph}")
        self.assertFalse(mismatches, "\n".join(mismatches[:50]))
