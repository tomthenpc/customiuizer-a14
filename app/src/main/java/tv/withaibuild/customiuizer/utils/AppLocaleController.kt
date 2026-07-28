package tv.withaibuild.customiuizer.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.content.res.Resources
import android.text.SpannableString
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.preference.Preference
import java.util.Locale
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.prefs.ListPreferenceEx

/**
 * Single owner for the application locale setting.
 *
 * The only persisted user choice is the value of [LOCALE_PREF_KEY]. Everything else
 * (AppCompat application locales, Context configuration, [Locale.getDefault()]) is a
 * derived, re-creatable state computed from that single source and the current system
 * locale.
 */
object AppLocaleController {

    const val LOCALE_PREF_KEY = "pref_key_miuizer_locale"
    private const val LEGACY_AUTO = "1"
    private const val TAG = "AppLocaleController"

    // Display order: auto, then the supported explicit locales.
    private val SUPPORTED_LOCALE_TAGS = listOf(
        "auto", "en", "zh-CN", "zh-TW", "ru-RU", "ja-JP", "vi-VN", "cs-CZ", "pt-BR", "tr-TR", "es-ES"
    )

    /**
     * Seam for unit tests and non-Android environments.
     * Production code leaves this null so [AppCompatDelegate.setApplicationLocales] is used.
     */
    @JvmField
    var applicationLocaleApplier: ((LocaleListCompat) -> Unit)? = null

    /**
     * Normalize a raw user selection or a persisted value.
     *
     * Accepts:
     * - `null`, empty, or unknown values -> `auto`
     * - legacy `"1"` -> `auto`
     * - any supported tag as-is
     */
    @JvmStatic
    fun normalizeLocaleTag(tag: String?): String = when {
        tag == null || tag.isBlank() -> "auto"
        tag == LEGACY_AUTO -> "auto"
        SUPPORTED_LOCALE_TAGS.contains(tag) -> tag
        tag.equals("auto", ignoreCase = true) -> "auto"
        else -> "auto"
    }

    /** Read the normalized user selection from the shared preferences. */
    @JvmStatic
    fun getUserLocale(prefs: SharedPreferences?): String =
        normalizeLocaleTag(prefs?.getString(LOCALE_PREF_KEY, "auto"))

    /**
     * Persist a new user selection synchronously and then apply it.
     *
     * The synchronous [commit] guarantees the next Activity/Fragment recreation reads the
     * same value. Language switching is a low-frequency cold path, so the blocking I/O is
     * acceptable here.
     */
    @JvmStatic
    fun setUserLocale(prefs: SharedPreferences, tag: String): Boolean {
        val normalized = normalizeLocaleTag(tag)
        val written = prefs.edit().putString(LOCALE_PREF_KEY, normalized).commit()
        if (!written) {
            Log.e(TAG, "setUserLocale commit failed for tag: $normalized")
            return false
        }
        applyLocale(normalized)
        return true
    }

    /**
     * Apply the normalized tag without writing.
     *
     * Used at application start-up, where the persisted value is already the source of truth.
     */
    @JvmStatic
    fun applyLocale(tag: String) {
        val normalized = normalizeLocaleTag(tag)
        val effective = getEffectiveLocale(normalized) { getSystemLocale() }
        Locale.setDefault(effective)
        applyToAppCompat(normalized, effective)
    }

    /**
     * Compute the effective locale from a user tag.
     *
     * `auto` resolves to the current system locale. The callback form lets unit tests
     * inject a deterministic system locale.
     */
    @JvmStatic
    fun getEffectiveLocale(
        tag: String,
        systemLocaleProvider: () -> Locale = { getSystemLocale() }
    ): Locale = when (normalizeLocaleTag(tag)) {
        "auto" -> try {
            systemLocaleProvider()
        } catch (t: Throwable) {
            Log.w(TAG, "system locale provider failed: ${t.message}; falling back to JVM default")
            Locale.getDefault()
        }
        else -> try {
            Locale.forLanguageTag(tag)
        } catch (t: Throwable) {
            try {
                systemLocaleProvider()
            } catch (_: Throwable) {
                Locale.getDefault()
            }
        }
    }

    /** Map a user tag to the [LocaleListCompat] that AppCompat expects. */
    @JvmStatic
    fun toLocaleListCompat(tag: String): LocaleListCompat = try {
        when (normalizeLocaleTag(tag)) {
            "auto" -> LocaleListCompat.getEmptyLocaleList()
            else -> try {
                val locale = Locale.forLanguageTag(tag)
                LocaleListCompat.create(locale)
            } catch (t: Throwable) {
                LocaleListCompat.getEmptyLocaleList()
            }
        }
    } catch (t: Throwable) {
        // LocaleListCompat may not be fully initialised in JVM unit tests.
        Log.w(TAG, "toLocaleListCompat failed: ${t.message}")
        throw t
    }

    /** Current system primary locale. */
    @JvmStatic
    fun getSystemLocale(): Locale = try {
        val sysLocales = Resources.getSystem().configuration.locales
        if (sysLocales.isEmpty) Locale.getDefault() else sysLocales[0]
    } catch (t: Throwable) {
        Log.w(TAG, "getSystemLocale failed: ${t.message}; falling back to JVM default")
        Locale.getDefault()
    }

    /**
     * Return a [Context] with the user-selected locale applied.
     *
     * This is a fallback for non-AppCompat contexts (e.g. device-protected storage
     * contexts). Activities should rely on [AppCompatDelegate.setApplicationLocales] instead.
     */
    @JvmStatic
    fun getLocaleContext(base: Context, prefs: SharedPreferences?): Context {
        val tag = getUserLocale(prefs)
        val locale = getEffectiveLocale(tag)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        return base.createConfigurationContext(config)
    }

    /**
     * Build the parallel arrays needed by a [ListPreference] for the language selector.
     *
     * The returned [Pair] is `(displayEntries, entryValues)`.
     */
    @JvmStatic
    fun buildLocaleDisplayData(context: Context): Pair<Array<CharSequence>, Array<String>> =
        buildLocaleDisplayData(context.getString(R.string.array_system_default))

    /**
     * Testable version of the display data builder. The [systemDefaultLabel] is the only
     * Android-dependent value; every other label is derived from [java.util.Locale].
     */
    @JvmStatic
    fun buildLocaleDisplayData(systemDefaultLabel: String): Pair<Array<CharSequence>, Array<String>> {
        val displayNames = ArrayList<CharSequence>(SUPPORTED_LOCALE_TAGS.size)
        val values = ArrayList<String>(SUPPORTED_LOCALE_TAGS.size)
        for (tag in SUPPORTED_LOCALE_TAGS) {
            values.add(tag)
            displayNames.add(when (tag) {
                "auto" -> systemDefaultLabel
                "zh-TW" -> "繁體中文（台灣）"
                else -> buildLanguageDisplayName(tag)
            })
        }
        return Pair(displayNames.toTypedArray(), values.toTypedArray())
    }

    private fun buildLanguageDisplayName(tag: String): String {
        val loc = Locale.forLanguageTag(tag)
        val sb = StringBuilder(loc.getDisplayLanguage(loc))
        if (sb.isNotEmpty()) sb.setCharAt(0, Character.toUpperCase(sb[0]))
        if (tag == "pt-BR") sb.append(" (Brasil)")
        return sb.toString()
    }

    /**
     * Bind the language [ListPreferenceEx] to the single source of truth.
     *
     * - Sets stable [entries] and [entryValues] before any value is restored.
     * - Disables the preference's own persistence so only [setUserLocale] writes.
     * - Normalizes every change and applies it synchronously.
     * - Defensively falls back to `auto` if the current persisted value is missing from the
     *   supported set.
     */
    @JvmStatic
    fun setupLocalePreference(pref: ListPreferenceEx?, prefs: SharedPreferences?) {
        if (pref == null || prefs == null) return

        val (displayEntries, entryValues) = try {
            buildLocaleDisplayData(pref.context)
        } catch (t: Throwable) {
            Log.e(TAG, "Cannot build locale display data", t)
            return
        }

        if (displayEntries.size != entryValues.size) {
            Log.e(TAG, "Locale display data mismatch: entries=${displayEntries.size}, values=${entryValues.size}")
            return
        }

        pref.entries = displayEntries
        pref.entryValues = entryValues
        pref.isPersistent = false

        val current = getUserLocale(prefs)
        pref.value = if (entryValues.contains(current)) current else "auto"

        pref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            val tag = normalizeLocaleTag(newValue as? String)
            if (!entryValues.contains(tag)) {
                Log.w(TAG, "Rejected unknown locale tag: $newValue")
                return@OnPreferenceChangeListener false
            }
            // Update the preference display first so the UI is consistent even if the
            // Activity is recreated immediately after the locale is applied.
            pref.value = tag
            setUserLocale(prefs, tag)
        }
    }

    private fun applyToAppCompat(tag: String, effective: Locale) {
        try {
            val localeList = if (tag == "auto") {
                LocaleListCompat.getEmptyLocaleList()
            } else {
                try {
                    LocaleListCompat.create(effective)
                } catch (t: Throwable) {
                    LocaleListCompat.getEmptyLocaleList()
                }
            }
            (applicationLocaleApplier ?: { AppCompatDelegate.setApplicationLocales(it) })(localeList)
        } catch (t: Throwable) {
            // AppCompat may not be initialized in unit tests or very early process state.
            Log.w(TAG, "applyToAppCompat skipped: ${t.message}")
        }
    }
}
