package tv.withaibuild.customiuizer.utils

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Process
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.prefs.ListPreferenceEx

/**
 * Single owner for the application locale setting.
 *
 * The only persisted user choice is the value of [LOCALE_PREF_KEY]. Everything else
 * (AppCompat application locales, [Locale.getDefault()]) is a derived, re-creatable
 * state computed from that single source and the current system locale.
 *
 * Locale changes are deferred: the user confirms once, the choice is persisted
 * synchronously, the settings application exits, and the new language takes effect
 * the next time the application is opened.
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
     * Seam for unit tests. Production code leaves this null so
     * [AppCompatDelegate.setApplicationLocales] is used.
     */
    @JvmField
    var applicationLocaleApplier: ((LocaleListCompat) -> Unit)? = null

    /**
     * Seam for unit tests. Production code leaves this null so
     * [AppCompatDelegate.getApplicationLocales] is used.
     */
    @JvmField
    var applicationLocaleProvider: (() -> LocaleListCompat)? = null

    /**
     * Normalize a raw user selection or a persisted value.
     *
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
     * Persist a new user selection synchronously.
     *
     * The only persisted source of truth is [LOCALE_PREF_KEY]. The application exits
     * after a successful commit and the next start applies the new locale.
     */
    @JvmStatic
    fun setUserLocale(prefs: SharedPreferences, tag: String): Boolean {
        val normalized = normalizeLocaleTag(tag)
        val written = prefs.edit()
            .putString(LOCALE_PREF_KEY, normalized)
            .commit()
        if (!written) {
            Log.e(TAG, "setUserLocale commit failed for tag: $normalized")
            return false
        }
        return true
    }

    /**
     * Apply the stored user locale if it differs from the current runtime state.
     *
     * This is called once during application start-up. It avoids unconditional calls
     * to [AppCompatDelegate.setApplicationLocales] and prevents apply/restart loops.
     */
    @JvmStatic
    fun apply(prefs: SharedPreferences?): Boolean {
        val tag = getUserLocale(prefs)
        val targetLocaleList = toLocaleListCompat(tag)
        val currentLocaleList = getCurrentApplicationLocales()
        val effective = getEffectiveLocale(tag)
        val currentDefault = Locale.getDefault()

        val appLocaleChanged = !areLocaleListsEqual(currentLocaleList, targetLocaleList)
        val defaultChanged = currentDefault != effective

        if (defaultChanged) {
            Locale.setDefault(effective)
        }

        if (appLocaleChanged) {
            Log.i(TAG, "Applying locale: tag=$tag")
            (applicationLocaleApplier ?: { AppCompatDelegate.setApplicationLocales(it) })(targetLocaleList)
        }

        return appLocaleChanged
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
            else -> LocaleListCompat.forLanguageTags(normalizeLocaleTag(tag))
        }
    } catch (t: Throwable) {
        Log.w(TAG, "toLocaleListCompat failed: ${t.message}; returning empty list")
        LocaleListCompat.getEmptyLocaleList()
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
     * Prepare the language [ListPreferenceEx] with stable entries and values.
     *
     * - Sets stable [entries] and [entryValues] before any value is restored.
     * - Disables the preference's own persistence so only [setUserLocale] writes.
     * - Defensively falls back to `auto` if the current persisted value is missing from the
     *   supported set.
     *
     * The caller (usually [AboutFragment]) is responsible for installing the
     * [androidx.preference.Preference.OnPreferenceChangeListener] that shows the
     * confirmation dialog and exits the application.
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
        val safeValue = if (entryValues.contains(current)) current else "auto"
        if (safeValue != current) {
            Log.e(TAG, "Invalid persisted locale '$current'; falling back to 'auto'")
            // Repair the persisted value. The
            // application will apply this value on the next start through [apply].
            prefs.edit().putString(LOCALE_PREF_KEY, safeValue).apply()
        }

        pref.value = safeValue

        // The value must resolve to a non-null entry for the summary.
        if (pref.entry.isNullOrEmpty()) {
            Log.e(TAG, "Locale preference summary resolved to empty for value: $safeValue")
            pref.value = "auto"
        }
    }

    /** Exit the settings application after a successful locale save. */
    @JvmStatic
    fun exitApplicationAfterLocaleSave(activity: Activity) {
        activity.finishAffinity()
        Process.killProcess(Process.myPid())
    }

    /** Current AppCompat application locales, with a test seam. */
    @JvmStatic
    private fun getCurrentApplicationLocales(): LocaleListCompat = try {
        applicationLocaleProvider?.invoke()
            ?: AppCompatDelegate.getApplicationLocales()
            ?: LocaleListCompat.getEmptyLocaleList()
    } catch (t: Throwable) {
        Log.w(TAG, "getCurrentApplicationLocales failed: ${t.message}; returning empty list")
        LocaleListCompat.getEmptyLocaleList()
    }

    /** Compare two [LocaleListCompat] by their language tags. */
    @JvmStatic
    private fun areLocaleListsEqual(a: LocaleListCompat, b: LocaleListCompat): Boolean {
        if (a.isEmpty && b.isEmpty) return true
        return toLanguageTags(a) == toLanguageTags(b)
    }

    /** Return the language tags string, or `null` if it cannot be read. */
    @JvmStatic
    private fun toLanguageTags(list: LocaleListCompat): String? = try {
        list.toLanguageTags()
    } catch (t: Throwable) {
        null
    }
}
