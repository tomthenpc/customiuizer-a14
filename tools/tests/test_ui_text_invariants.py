#!/usr/bin/env python3
"""Invariants for module App UI text styling and attribution display.

This test guards against two recurring regressions:

1. Custom Preference subclasses losing AndroidX default style by passing
   defStyleAttr = 0 to the parent constructor (A13 regression).
2. The module App UI hard-coding a font family or typeface instead of
   inheriting the system/Theme font.

SystemUI/status bar/lock screen hooks are allowed to manipulate TextView
fonts because they replace ROM UI, not the module's own UI.
"""

import re
import unittest
from pathlib import Path


REPO_ROOT = Path(__file__).resolve().parents[2]
APP_ROOT = REPO_ROOT / "app" / "src" / "main"
PREFS_DIR = APP_ROOT / "java" / "tv" / "withaibuild" / "customiuizer" / "prefs"

# File globs that are part of the module's own App UI and must not
# hard-code font families.
APP_UI_DIRS = (
    APP_ROOT / "java" / "tv" / "withaibuild" / "customiuizer",
    APP_ROOT / "res" / "layout",
    APP_ROOT / "res" / "values",
    APP_ROOT / "res" / "xml",
)

# Paths that are allowed to reference Typeface because they target SystemUI,
# status bar, lock screen, launcher or other ROM surfaces, not the module App.
TYPEFACE_ALLOWLIST = {
    APP_ROOT / "java" / "tv" / "withaibuild" / "customiuizer" / "mods" / "SystemUIStatusBarHooks.kt",
    APP_ROOT / "java" / "tv" / "withaibuild" / "customiuizer" / "mods" / "SystemClockHooks.kt",
    APP_ROOT / "java" / "tv" / "withaibuild" / "customiuizer" / "mods" / "SystemUIBatteryHooks.kt",
}


def _all_kotlin_and_xml(paths):
    for base in paths:
        if not base.exists():
            continue
        for p in base.rglob("*"):
            if p.is_file() and (p.suffix in (".kt", "") or p.name.endswith(".xml")):
                yield p


class AppTextInvariantsTest(unittest.TestCase):

    def test_custom_preferences_do_not_use_zero_def_style_attr(self):
        """Custom Preference subclasses must preserve AndroidX default style.

        Two-arg constructors delegate the default defStyleAttr to the parent
        (e.g. SwitchPreference(context, attrs) uses R.attr.switchPreferenceStyle).
        If a class uses @JvmOverloads / three-arg, it must pass the matching
        androidx.preference style attribute, not 0.
        """
        bad = []
        for path in sorted(PREFS_DIR.glob("*.kt")):
            text = path.read_text(encoding="utf-8")
            # Look for any explicit defStyleAttr default of 0 in Preference
            # subclass constructors.
            for m in re.finditer(r"defStyleAttr\s*:\s*Int\s*=\s*(\d+)", text):
                if m.group(1) == "0":
                    bad.append(f"{path.relative_to(REPO_ROOT)}:{m.start()}")
            # Also reject direct parent calls with a literal 0 third argument
            # in custom preference constructors, unless it is the style constant.
            for m in re.finditer(
                r"(\b(?:SwitchPreference|DropDownPreference|EditTextPreference|"
                r"ListPreference|PreferenceCategory|Preference))\s*\(\s*context\s*,\s*attrs\s*,\s*(\d+)\s*\)",
                text,
            ):
                if m.group(2) == "0":
                    bad.append(f"{path.relative_to(REPO_ROOT)}:{m.start()}")

        self.assertFalse(
            bad,
            "Preference subclasses must not pass defStyleAttr = 0: "
            + ", ".join(bad),
        )

    def test_app_ui_layouts_do_not_hardcode_font_family(self):
        """App UI XML must not set android:fontFamily or android:typeface."""
        bad = []
        for path in sorted((APP_ROOT / "res" / "layout").rglob("*.xml")):
            text = path.read_text(encoding="utf-8")
            for m in re.finditer(r"android:(?:fontFamily|typeface)\s*=", text):
                bad.append(f"{path.relative_to(REPO_ROOT)}:{m.start()}")
        self.assertFalse(
            bad,
            "App UI layouts must not hardcode fontFamily/typeface: "
            + ", ".join(bad),
        )

    def test_app_ui_values_do_not_hardcode_font_family(self):
        """App theme/values must not set fontFamily or typeface attributes."""
        bad = []
        for path in sorted((APP_ROOT / "res" / "values").rglob("*.xml")):
            text = path.read_text(encoding="utf-8")
            for m in re.finditer(r"(fontFamily|typeface)\s*[=:]", text, re.IGNORECASE):
                bad.append(f"{path.relative_to(REPO_ROOT)}:{m.start()}")
        self.assertFalse(
            bad,
            "App theme/values must not hardcode fontFamily/typeface: "
            + ", ".join(bad),
        )

    def test_app_ui_code_does_not_use_typeface_for_font_family(self):
        """App UI code must not create or apply a Typeface to force a font.

        Style-only usage (StyleSpan(Typeface.ITALIC/BOLD)) is allowed because
        it changes style, not the font family.
        """
        bad = []
        style_span_pattern = re.compile(r"StyleSpan\s*\(\s*Typeface\.(?:ITALIC|BOLD)\s*\)")

        for path in sorted(_all_kotlin_and_xml((APP_ROOT / "java" / "tv" / "withaibuild" / "customiuizer",))):
            if path in TYPEFACE_ALLOWLIST:
                continue
            if path.relative_to(APP_ROOT).parts[1:2] == ("mods",):
                # mods/ code is allowed to touch ROM surfaces; only the listed
                # files currently do so, but other mods are also out of scope.
                continue

            text = path.read_text(encoding="utf-8")
            text_without_style_spans = style_span_pattern.sub("", text)
            text_without_imports = re.sub(r"^\s*import\s+.*Typeface.*$", "", text_without_style_spans, flags=re.MULTILINE)
            for m in re.finditer(r"\bTypeface\b", text_without_imports):
                bad.append(f"{path.relative_to(REPO_ROOT)}:{m.start()}")

        self.assertFalse(
            bad,
            "App UI code must not use Typeface to set a font family "
            "(allowed: StyleSpan(Typeface.ITALIC/BOLD) in non-hook code): "
            + ", ".join(bad),
        )

    def test_about_attribution_text_views_allow_wrapping(self):
        """The about page attribution TextViews must not be single-line ellipsized."""
        about_head = APP_ROOT / "res" / "layout" / "fragment_about_head.xml"
        self.assertTrue(about_head.is_file(), "fragment_about_head.xml must exist")
        text = about_head.read_text(encoding="utf-8")

        about_ids = ("about_maintainer", "about_based_on", "about_version")
        for view_id in about_ids:
            with self.subTest(view=view_id):
                section = re.search(
                    rf"android:id=\"@\+id/{view_id}\".*?>(?=\s*<TextView|\s*</LinearLayout|\s*<View)",
                    text,
                    re.DOTALL,
                )
                self.assertIsNotNone(
                    section,
                    f"Could not find {view_id} TextView in fragment_about_head.xml",
                )
                self.assertNotIn(
                    'android:ellipsize="end"',
                    section.group(0),
                    f"{view_id} must not ellipsize attribution text",
                )
                self.assertNotIn(
                    'android:maxLines="1"',
                    section.group(0),
                    f"{view_id} must not be limited to one line",
                )
                self.assertNotIn(
                    'android:singleLine="true"',
                    section.group(0),
                    f"{view_id} must not be singleLine",
                )

    def test_font_resource_directory_does_not_exist(self):
        """The module App must not ship its own font resources."""
        font_dir = APP_ROOT / "res" / "font"
        self.assertFalse(font_dir.is_dir(), f"App must not contain {font_dir}")


if __name__ == "__main__":
    unittest.main()
