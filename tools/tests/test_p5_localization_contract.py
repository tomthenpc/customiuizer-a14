"""P5-C localization contract: user-visible resources must be localized,
correctly formatted, and free of hard-coded visible text.

User-visible required set:
* XML (prefs)   : android:title / android:summary
* Layouts       : android:text / android:hint
* Menus         : android:title
* Kotlin/Java UI: R.string.* used in UI-facing APIs

Non-translatable authority: base values/strings.xml android:translatable="false".
"""

import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
RES = REPO / "app" / "src" / "main" / "res"
JAVA_DIR = REPO / "app" / "src" / "main" / "java"
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

PLACEHOLDER_RE = re.compile(
    r"%"
    r"(?:\d+\$)?"
    r"(?:[-+#0,]+)?"
    r"(?:\d+|\*)?"
    r"(?:\.\d+)?"
    r"([sdxXfFc%])"
)


def _parse(path: Path) -> ET.Element:
    return ET.parse(path).getroot()


def _load_strings(path: Path) -> tuple[dict[str, str], set[str], set[str]]:
    root = _parse(path)
    result = {}
    non_translatable = set()
    non_formatted = set()
    for el in root:
        if el.tag == "string" and "name" in el.attrib:
            result[el.attrib["name"]] = "".join(el.itertext())
            if (el.get("translatable", "").lower() == "false" or
                el.get(f"{{{ANDROID_NS}}}translatable", "").lower() == "false"):
                non_translatable.add(el.attrib["name"])
            if (el.get("formatted", "").lower() == "false" or
                el.get(f"{{{ANDROID_NS}}}formatted", "").lower() == "false"):
                non_formatted.add(el.attrib["name"])
    return result, non_translatable, non_formatted


def _extract_placeholders(text: str) -> list:
    """Return a normalized placeholder/percent-literal contract.

    Tokens are:
        ("placeholder", index, conversion_type)
        ("literal", "%")
    Non-positional placeholders are assigned a sequential 1-based index.
    Placeholders are sorted by index so reordering still keeps the contract.
    """
    placeholders = []
    literals = []
    seq = 1
    for m in PLACEHOLDER_RE.finditer(text):
        conv = m.group(1)
        raw = m.group(0)
        if conv == "%":
            literals.append(("literal", "%"))
            continue
        idx_match = re.match(r"%(\d+)\$", raw)
        if idx_match:
            idx = int(idx_match.group(1))
        else:
            idx = seq
            seq += 1
        placeholders.append(("placeholder", idx, conv.lower()))
    placeholders.sort(key=lambda t: t[1])
    return placeholders + literals


def _find_xml_visible_refs() -> set[str]:
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


def _find_code_ui_refs() -> set[str]:
    refs = set()
    R_PATTERN = re.compile(r"(?<!android\.)R\.string\.([A-Za-z_][A-Za-z0-9_]*)")
    UI_APIS = [
        "setTitle", "setMessage", "setText", "setHint", "setSummary",
        "setPositiveButton", "setNegativeButton", "setNeutralButton",
        "makeText", "Toast", "Snackbar", "setTitleText", "setContentText",
        "setSubTitle", "setAction", "setButton",
    ]
    for f in JAVA_DIR.rglob("*.kt"):
        try:
            lines = f.read_text(encoding="utf-8").splitlines()
        except OSError:
            continue
        for line in lines:
            if not any(api in line for api in UI_APIS):
                continue
            for m in R_PATTERN.finditer(line):
                refs.add(m.group(1))
    return refs


def _find_user_visible_refs() -> set[str]:
    return _find_xml_visible_refs() | _find_code_ui_refs()


class PlaceholderContractTest(unittest.TestCase):

    def _contract(self, text: str) -> list:
        return _extract_placeholders(text)

    def test_simple_placeholders(self):
        self.assertEqual(
            self._contract("Hello %s"),
            [("placeholder", 1, "s")],
        )
        self.assertEqual(
            self._contract("Count: %d"),
            [("placeholder", 1, "d")],
        )

    def test_positional_placeholders(self):
        self.assertEqual(
            self._contract("%1$s %2$d"),
            [("placeholder", 1, "s"), ("placeholder", 2, "d")],
        )

    def test_percent_literal(self):
        self.assertEqual(
            self._contract("Battery %% charged"),
            [("literal", "%")],
        )

    def test_lone_percent_followed_by_word_is_not_placeholder(self):
        # 100% or 0% followed by a word must not be parsed as a placeholder.
        self.assertEqual(self._contract("100% charged"), [])
        self.assertEqual(self._contract("0% durante"), [])

    def test_mixed_positional_and_literal(self):
        self.assertEqual(
            self._contract("Hello %1$s: %2$d%%"),
            [("placeholder", 1, "s"), ("placeholder", 2, "d"), ("literal", "%")],
        )

    def test_width_and_precision(self):
        contract = self._contract("Value %05d or %.2f")
        self.assertEqual(
            contract,
            [("placeholder", 1, "d"), ("placeholder", 2, "f")],
        )

    def test_reordering_keeps_contract(self):
        base = self._contract("%1$s: %2$d")
        tr = self._contract("%2$d %1$s")
        self.assertEqual(base, tr)

    def test_missing_placeholder_fails_contract(self):
        base = self._contract("%1$s: %2$d")
        tr = self._contract("%1$s")
        self.assertNotEqual(base, tr)

    def test_type_mismatch_fails_contract(self):
        base = self._contract("%1$s")
        tr = self._contract("%1$d")
        self.assertNotEqual(base, tr)

    def test_index_swap_mismatch_fails_contract(self):
        base = self._contract("%1$s %2$d")
        # Wrong: s now gets second arg and d gets first arg
        tr = self._contract("%2$s %1$d")
        self.assertNotEqual(base, tr)


class P5LocalizationContractTest(unittest.TestCase):

    def test_no_hardcoded_user_visible_text(self):
        """android:title/summary/text/hint must reference a string resource."""
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
                            violations.append(f"{f.relative_to(REPO)} android:{attr}={val!r}")
        self.assertFalse(violations, "\n".join(violations))

    def test_user_visible_strings_exist_in_all_formal_locales(self):
        base, base_non_tr, _ = _load_strings(RES / "values" / "strings.xml")
        locale_data = {loc: _load_strings(RES / loc / "strings.xml") for loc in FORMAL_LOCALES}
        locale_strings = {loc: d[0] for loc, d in locale_data.items()}

        # Non-translatable authority is the base values contract only.
        required = _find_user_visible_refs() - base_non_tr

        missing_base = [k for k in required if k not in base]
        self.assertFalse(missing_base, f"Missing in values: {missing_base}")

        missing_by_locale = {}
        for loc in FORMAL_LOCALES:
            missing = [k for k in required if k not in locale_strings[loc]]
            if missing:
                missing_by_locale[loc] = missing
        self.assertFalse(missing_by_locale, f"Missing translations: {missing_by_locale}")

    def test_placeholder_parity(self):
        base, base_non_tr, base_non_fmt = _load_strings(RES / "values" / "strings.xml")
        locale_data = {loc: _load_strings(RES / loc / "strings.xml") for loc in FORMAL_LOCALES}
        locale_strings = {loc: d[0] for loc, d in locale_data.items()}
        locale_non_fmt = {loc: d[2] for loc, d in locale_data.items()}

        required = _find_user_visible_refs() - base_non_tr

        mismatches = []
        for loc in FORMAL_LOCALES:
            loc_map = locale_strings[loc]
            for key in required:
                if key not in base or key not in loc_map:
                    continue
                if key in base_non_fmt or key in locale_non_fmt[loc]:
                    continue
                base_contract = _extract_placeholders(base[key])
                loc_contract = _extract_placeholders(loc_map[key])
                if base_contract != loc_contract:
                    mismatches.append(f"{loc}/{key}: {base_contract} != {loc_contract}")
        self.assertFalse(mismatches, "\n".join(mismatches[:50]))
