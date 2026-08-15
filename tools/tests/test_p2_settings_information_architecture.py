"""P2 Settings information-architecture structural contract checks."""

import subprocess
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
XML_DIR = REPO_ROOT / "app" / "src" / "main" / "res" / "xml"
RES_ROOT = REPO_ROOT / "app" / "src" / "main" / "res"
LOCALES = (
    "values-zh-rCN",
    "values-zh-rTW",
    "values-ru-rRU",
    "values-ja-rJP",
    "values-vi-rVN",
    "values-cs-rCZ",
    "values-pt-rBR",
    "values-tr-rTR",
    "values-es-rES",
)
P2_BASE_SHA = "79a0eb20c96604743c3129675d0ec0678a703471"
ANDROID_NS = "http://schemas.android.com/apk/res/android"


def _git_show(path: str, ref: str = P2_BASE_SHA) -> str:
    return subprocess.run(
        ["git", "show", f"{ref}:{path}"],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        check=True,
    ).stdout


def _preference_items(root: ET.Element) -> list[tuple[ET.Element, list[str]]]:
    """Return (element, parent_path) for every non-category, non-screen child."""
    items = []
    tag_prefix = "{http://www.w3.org/1999/xhtml}"

    def visit(el: ET.Element, path: list[str]):
        for child in el:
            if child.tag.endswith("PreferenceCategoryEx"):
                visit(child, path + [child.get(f"{{{ANDROID_NS}}}title", "category")])
            elif child.tag.endswith("PreferenceScreen"):
                visit(child, path + ["screen"])
            else:
                items.append((child, path))

    visit(root, [])
    return items


def _pref_key(el: ET.Element) -> str | None:
    return el.get(f"{{{ANDROID_NS}}}key")


def _important_attrs(el: ET.Element) -> dict[str, str]:
    keep = (
        f"{{{ANDROID_NS}}}key",
        f"{{{ANDROID_NS}}}title",
        f"{{{ANDROID_NS}}}entries",
        f"{{{ANDROID_NS}}}entryValues",
        f"{{{ANDROID_NS}}}defaultValue",
        f"{{{ANDROID_NS}}}dependency",
        f"{{{ANDROID_NS}}}fragment",
        f"{{{ANDROID_NS}}}persistent",
        f"{{{ANDROID_NS}}}summary",
    )
    return {k: v for k, v in el.attrib.items() if k in keep}


class P2SettingsInformationArchitectureTest(unittest.TestCase):
    def test_new_connectivity_category_is_localized(self):
        missing = []
        for locale in LOCALES:
            tree = ET.parse(RES_ROOT / locale / "strings.xml")
            names = {el.attrib["name"] for el in tree.getroot() if el.tag == "string"}
            if "system_mods_connectivity" not in names:
                missing.append(locale)
        self.assertFalse(missing, f"system_mods_connectivity missing in {missing}")

    def test_no_duplicate_preference_keys(self):
        keys: dict[str, str] = {}
        for path in sorted(XML_DIR.glob("prefs_*.xml")):
            root = ET.parse(path).getroot()
            for el, _ in _preference_items(root):
                key = _pref_key(el)
                if key:
                    self.assertNotIn(
                        key,
                        keys,
                        f"Duplicate key {key!r} in {path.name} and {keys.get(key)}",
                    )
                    keys[key] = path.name

    def test_usb_preference_contract(self):
        root = ET.parse(XML_DIR / "prefs_system.xml").getroot()
        usb = None
        for el, _ in _preference_items(root):
            if _pref_key(el) == "pref_key_system_usb_default_function":
                usb = el
                break
        self.assertIsNotNone(usb, "USB default preference must exist")
        self.assertEqual(usb.tag, "tv.withaibuild.customiuizer.prefs.ListPreferenceEx")
        self.assertEqual(
            usb.get(f"{{{ANDROID_NS}}}title"),
            "@string/system_usb_default_function_title",
        )
        self.assertEqual(
            usb.get(f"{{{ANDROID_NS}}}entries"),
            "@array/usb_default_functions",
        )
        self.assertEqual(
            usb.get(f"{{{ANDROID_NS}}}entryValues"),
            "@array/usb_default_functions_val",
        )
        self.assertEqual(usb.get(f"{{{ANDROID_NS}}}defaultValue"), "0")

    def test_recents_hide_app_name_contract(self):
        root = ET.parse(XML_DIR / "prefs_system.xml").getroot()
        recents = None
        for el, _ in _preference_items(root):
            if _pref_key(el) == "pref_key_system_recents_card_style":
                recents = el
                break
        self.assertIsNotNone(recents, "Recents card style preference must exist")
        self.assertEqual(recents.tag, "tv.withaibuild.customiuizer.prefs.CheckBoxPreferenceEx")
        self.assertEqual(
            recents.get(f"{{{ANDROID_NS}}}title"),
            "@string/system_recents_card_style_hide_title",
        )

    def test_structural_contract_preserved(self):
        failures = []
        for path in sorted(XML_DIR.glob("prefs_*.xml")):
            current = ET.parse(path).getroot()
            try:
                base_text = _git_show(f"app/src/main/res/xml/{path.name}")
            except subprocess.CalledProcessError:
                continue  # file may not exist in base (should not happen for prefs)
            base = ET.fromstring(base_text)

            base_items: dict[str, dict[str, str]] = {}
            for el, _ in _preference_items(base):
                key = _pref_key(el)
                if key:
                    base_items[key] = _important_attrs(el)

            current_items: dict[str, dict[str, str]] = {}
            for el, _ in _preference_items(current):
                key = _pref_key(el)
                if key:
                    current_items[key] = _important_attrs(el)

            # No keys lost or added in this file.
            added = set(current_items) - set(base_items)
            removed = set(base_items) - set(current_items)
            if added or removed:
                failures.append(f"{path.name}: added={added}, removed={removed}")
                continue

            for key, base_attrs in base_items.items():
                cur_attrs = current_items[key]
                for attr, base_val in base_attrs.items():
                    # android:summary may be intentionally removed or empty.
                    if attr.endswith("}summary"):
                        if not cur_attrs.get(attr):
                            continue
                        if cur_attrs.get(attr) == base_val:
                            continue
                        failures.append(
                            f"{path.name}:{key}: {attr} changed from {base_val!r} to {cur_attrs.get(attr)!r}"
                        )
                        continue
                    if cur_attrs.get(attr) != base_val:
                        failures.append(
                            f"{path.name}:{key}: {attr} changed from {base_val!r} to {cur_attrs.get(attr)!r}"
                        )

        self.assertFalse(failures, "\n".join(failures))

    def test_no_cross_page_key_migration(self):
        base_by_key: dict[str, str] = {}
        for path in sorted(XML_DIR.glob("prefs_*.xml")):
            try:
                base_text = _git_show(f"app/src/main/res/xml/{path.name}")
            except subprocess.CalledProcessError:
                continue
            base = ET.fromstring(base_text)
            for el, _ in _preference_items(base):
                key = _pref_key(el)
                if key:
                    base_by_key[key] = path.name

        current_by_key: dict[str, str] = {}
        for path in sorted(XML_DIR.glob("prefs_*.xml")):
            current = ET.parse(path).getroot()
            for el, _ in _preference_items(current):
                key = _pref_key(el)
                if key:
                    current_by_key[key] = path.name

        migrated = {
            key: (base_by_key.get(key), current_by_key[key])
            for key in current_by_key
            if key in base_by_key and base_by_key[key] != current_by_key[key]
        }
        self.assertFalse(migrated, f"Preferences migrated across pages: {migrated}")


if __name__ == "__main__":
    unittest.main()
