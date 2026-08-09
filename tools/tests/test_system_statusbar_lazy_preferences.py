"""Contracts for the first system-settings lazy-loading slice."""

import unittest
import xml.etree.ElementTree as ET
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parent.parent.parent
SYSTEM_XML = REPO_ROOT / "app" / "src" / "main" / "res" / "xml" / "prefs_system.xml"
STATUS_BAR_XML = REPO_ROOT / "app" / "src" / "main" / "res" / "xml" / "prefs_system_statusbar.xml"
CATEGORY_SELECTOR = (
    REPO_ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "tv"
    / "withaibuild"
    / "customiuizer"
    / "subs"
    / "CategorySelector.kt"
)
MAIN_FRAGMENT = (
    REPO_ROOT
    / "app"
    / "src"
    / "main"
    / "java"
    / "tv"
    / "withaibuild"
    / "customiuizer"
    / "MainFragment.kt"
)

ANDROID_KEY = "{http://schemas.android.com/apk/res/android}key"
STATUS_BAR_KEY = "pref_key_system_cat_statusbar"
RESOLVER_CALL = "SystemPreferenceResourceResolver.resolve("


def _element_signature(element: ET.Element) -> tuple:
    """Compare preference tags, attributes and order while ignoring formatting."""
    return (
        element.tag,
        tuple(sorted(element.attrib.items())),
        tuple(_element_signature(child) for child in list(element)),
    )


class SystemStatusBarLazyPreferencesTest(unittest.TestCase):

    def test_split_resource_contains_only_the_original_status_bar_category(self) -> None:
        source_root = ET.parse(SYSTEM_XML).getroot()
        source_category = next(
            child for child in list(source_root) if child.get(ANDROID_KEY) == STATUS_BAR_KEY
        )

        split_root = ET.parse(STATUS_BAR_XML).getroot()
        split_children = list(split_root)
        self.assertEqual(1, len(split_children))
        self.assertEqual(STATUS_BAR_KEY, split_children[0].get(ANDROID_KEY))
        self.assertEqual(
            _element_signature(source_category),
            _element_signature(split_children[0]),
        )

    def test_both_category_and_search_navigation_use_the_resolver(self) -> None:
        self.assertIn(RESOLVER_CALL, CATEGORY_SELECTOR.read_text(encoding="utf-8"))
        self.assertIn(RESOLVER_CALL, MAIN_FRAGMENT.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
