package tv.withaibuild.customiuizer.mods.utils

import android.content.res.Resources
import android.util.DisplayMetrics
import tv.withaibuild.customiuizer.utils.PrefMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-scoped, immutable-ish configuration for the status bar height feature.
 *
 * The values are read once on the cold installation path and then published as plain
 * primitives so the hot InsetsSource path only reads fields, never the preference map,
 * never re-reflects and never re-computes dp->px.
 *
 * Enabled semantics:
 * - `system_statusbarheight == 11` (DEFAULT_SENTINEL) → disabled, behaves like stock.
 * - any other value → enabled, `configuredDp` equals the raw value (12, 27, 28, ...).
 *
 * Note: 27 is the default visual height, but a user that explicitly sets 27 is still
 * "enabled" and should have their 27dp respected.
 *
 * Density handling:
 * - `Resources.getSystem()` returns the global system density (e.g. ro.sf.lcd_density).
 * - The authoritative display may have a different logical density (e.g. per-display
 *   scaling on HyperOS). The hot path uses `configuredPx` which is computed from the
 *   display density that the status bar window is actually rendered with.
 */
object StatusBarHeightConfig {

    const val PREF_KEY = "system_statusbarheight"
    const val DEFAULT_SENTINEL = 11
    const val DEFAULT_DP = 27

    @Volatile
    var enabled = false
        private set

    @Volatile
    var rawPreferenceDp = DEFAULT_SENTINEL
        private set

    @Volatile
    var configuredDp = DEFAULT_DP
        private set

    @Volatile
    var configuredPx = -1
        private set

    @Volatile
    var densityDpi = -1
        private set

    @Volatile
    var density = -1.0f
        private set

    /** Monotonically increasing generation, bumped on every reconfigure. */
    val generation = AtomicLong(0L)

    /**
     * Pure dp to px conversion for the supplied [Resources].
     */
    /**
     * Pure dp to px conversion for the supplied [Resources].
     *
     * Uses rounding to match `TypedValue.complexToDimensionPixelSize`.
     */
    @JvmStatic
    fun dpToPx(dp: Int, resources: Resources?): Int {
        val densityDpiValue = resources?.displayMetrics?.densityDpi ?: 160
        return Math.round(dp * densityDpiValue / 160f)
    }

    /**
     * Pure dp to px conversion for the supplied [DisplayMetrics].
     *
     * Uses rounding to match `TypedValue.complexToDimensionPixelSize`.
     */
    @JvmStatic
    fun dpToPx(dp: Int, metrics: DisplayMetrics?): Int {
        val densityDpiValue = metrics?.densityDpi ?: 160
        return Math.round(dp * densityDpiValue / 160f)
    }

    /**
     * Pure dp to px conversion using the cached display density.
     *
     * Uses rounding to match `TypedValue.complexToDimensionPixelSize`.
     */
    @JvmStatic
    fun dpToPx(dp: Int): Int {
        return Math.round(dp * densityDpi / 160f)
    }

    /**
     * Pure preference to dp resolution. Sentinel 11 maps to the framework default 27dp
     * so callers always get a usable height; use [isEnabled] to distinguish stock mode.
     */
    @JvmStatic
    fun resolveHeightDp(prefs: PrefMap): Int {
        val opt = prefs.getInt(PREF_KEY, DEFAULT_SENTINEL)
        return if (opt == DEFAULT_SENTINEL) DEFAULT_DP else opt
    }

    /**
     * Returns true when the user has set a custom value (anything above the sentinel).
     */
    @JvmStatic
    fun isEnabled(prefs: PrefMap): Boolean {
        val raw = prefs.getInt(PREF_KEY, DEFAULT_SENTINEL)
        return raw > DEFAULT_SENTINEL
    }

    /**
     * Configure this process's cache from preferences and the supplied display metrics.
     *
     * This is the single cold-path call; the Insets hook reads only [enabled],
     * [configuredDp] and [configuredPx] afterwards.
     */
    @JvmStatic
    @JvmOverloads
    fun configure(
        prefs: PrefMap,
        resources: Resources? = null,
        metrics: DisplayMetrics? = null,
    ) {
        val raw = prefs.getInt(PREF_KEY, DEFAULT_SENTINEL)
        val dp = if (raw == DEFAULT_SENTINEL) DEFAULT_DP else raw

        val effectiveMetrics = metrics ?: resources?.displayMetrics
        val effectiveDensityDpi = effectiveMetrics?.densityDpi ?: 160
        val effectiveDensity = effectiveMetrics?.density ?: (effectiveDensityDpi / 160f)

        rawPreferenceDp = raw
        enabled = raw > DEFAULT_SENTINEL
        configuredDp = dp
        densityDpi = effectiveDensityDpi
        density = effectiveDensity
        configuredPx = dpToPx(dp)
        generation.incrementAndGet()
    }

    /**
     * Reconfigure from preferences using the current cached metrics.
     * Called on the preference-change observer after the hook is already installed.
     */
    @JvmStatic
    fun reconfigure(prefs: PrefMap) {
        val raw = prefs.getInt(PREF_KEY, DEFAULT_SENTINEL)
        val dp = if (raw == DEFAULT_SENTINEL) DEFAULT_DP else raw

        rawPreferenceDp = raw
        enabled = raw > DEFAULT_SENTINEL
        configuredDp = dp
        configuredPx = dpToPx(dp)
        generation.incrementAndGet()
    }

    /**
     * Recompute px after a configuration/density change. This should only be called when
     * the process is notified of a display or configuration change and the hook is
     * already installed, so the InsetsSource frames can be adjusted to the new px.
     */
    @JvmStatic
    fun recomputePx(metrics: DisplayMetrics) {
        densityDpi = metrics.densityDpi
        density = metrics.density
        if (enabled) {
            configuredPx = dpToPx(configuredDp)
        }
    }

    /**
     * Recompute px from a [Resources] after a configuration/density change.
     */
    @JvmStatic
    fun recomputePx(resources: Resources) {
        recomputePx(resources.displayMetrics)
    }

    /**
     * Returns the configured px for a specific density without mutating the cache.
     */
    @JvmStatic
    fun configuredPxFor(dp: Int, metrics: DisplayMetrics): Int {
        return Math.round(dp * metrics.densityDpi / 160f)
    }

    /**
     * Reset for tests. Not used in production.
     */
    @JvmStatic
    internal fun resetForTest() {
        enabled = false
        rawPreferenceDp = DEFAULT_SENTINEL
        configuredDp = DEFAULT_DP
        configuredPx = -1
        densityDpi = -1
        density = -1.0f
        generation.set(0L)
    }
}
