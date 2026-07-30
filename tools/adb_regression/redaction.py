"""Redaction helpers for ADB regression output and reports."""

from __future__ import annotations

import re


def redact(text: str, serial: str | None = None) -> str:
    """Redact raw serials, Tasker tokens, long base64, dumps, and secrets."""
    if not text:
        return text

    if serial:
        text = text.replace(serial, "<SERIAL>")

    # Device serial-like strings (alphanumeric, 6+ chars, common formats).
    # This is intentionally conservative; it does not touch short words.
    text = re.sub(r"\b[A-Z0-9]{12,}\b", "<SERIAL>", text)
    text = re.sub(r"\bFAKE\d{3,}\b", "<SERIAL>", text)

    # Tasker / Locale tokens and bundle keys
    text = re.sub(r"\btoken\s*=\s*\S+", "token=<REDACTED>", text, flags=re.IGNORECASE)
    text = re.sub(r"\bextra\s*(?:name|key)\s*=\s*\S+", r"\g<0>=<REDACTED>", text, flags=re.IGNORECASE)

    # Long base64 strings
    text = re.sub(r"\b[A-Za-z0-9+/]{40,}={0,2}\b", "<BASE64>", text)

    # SharedPreferences dumps
    text = re.sub(r"SharedPreferences.*", "<SharedPreferences>", text)
    text = re.sub(r"<map.*?</map>", "<SharedPreferences>", text, flags=re.DOTALL)
    text = re.sub(r"\bsp\s*=\s*\{[^}]*\}", "<SharedPreferences>", text)

    # Bundle dumps
    text = re.sub(r"Bundle\s*\{[^}]*\}", "<Bundle>", text)
    text = re.sub(r"\bBundle\b.*", "<Bundle>", text)

    # Keystore / password strings
    text = re.sub(r"keystore.*password.*", "<keystore>", text, flags=re.IGNORECASE)
    text = re.sub(r"\bpassword\s*=\s*\S+", "password=<REDACTED>", text, flags=re.IGNORECASE)
    text = re.sub(r"\bsecret\s*=\s*\S+", "secret=<REDACTED>", text, flags=re.IGNORECASE)

    # Tasker FIRE_SETTING / extra patterns
    text = re.sub(r"\b(com\.twofortyfouram\.locale\.intent\.[A-Z_]+)\s*=\s*\S+", r"\1=<REDACTED>", text)

    return text
