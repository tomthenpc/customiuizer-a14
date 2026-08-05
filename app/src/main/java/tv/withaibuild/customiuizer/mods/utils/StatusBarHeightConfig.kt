package tv.withaibuild.customiuizer.mods.utils

import android.content.res.Resources
import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Process-scoped, immutable-ish configuration for the status bar height feature.
 *
 * The values are read once on the cold installation path and then published as plain
 * primitives so the hot InsetsSource path only reads fields, never the preference map,
 * never re-reflects and never re-computes dp->px.
 */
object StatusBarHeightConfig {

    const val PREF_KEY = "system_statusbarheight"
    const val DEFAULT_SENTINEL = 11
    const val DEFAULT_DP = 27

    @Volatile
    var enabled = false
        private set

    @Volatile
    var configuredDp = DEFAULT_DP
        private set

    @Volatile
    var configuredPx = -1
        private set

    /**
     * Pure dp to px conversion for the current display.
     *
     * Caller must supply a [Resources] with the density of the display that the
     * status bar InsetsSource is expressed in. In system_server this is
     * `Resources.getSystem()`; in app/PackageReady processes this is the current
     * package resources.
     */
    @JvmStatic
    fun dpToPx(dp: Int, resources: Resources): Int {
        return (dp * resources.displayMetrics.densityDpi / 160f).toInt()
    }

    /**
     * Pure preference to dp resolution. Sentinel 11 maps to the framework default 27dp.
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
    fun isEnabled(prefs: PrefMap): Boolean = prefs.getInt(PREF_KEY, DEFAULT_SENTINEL) > DEFAULT_SENTINEL

    /**
     * Configure this process's cache from preferences and the supplied Resources.
     *
     * This is the single cold-path call; the Insets hook reads only [enabled],
     * [configuredDp] and [configuredPx] afterwards.
     */
    @JvmStatic
    fun configure(prefs: PrefMap, resources: Resources) {
        val dp = resolveHeightDp(prefs)
        enabled = dp > DEFAULT_DP
        configuredDp = dp
        configuredPx = dpToPx(dp, resources)
    }

    /**
     * Recompute px after a configuration/density change. This should only be called when
     * the process is notified of a display or configuration change and the hook is
     * already installed, so the InsetsSource frames can be adjusted to the new px.
     */
    @JvmStatic
    fun recomputePx(resources: Resources) {
        if (enabled) {
            configuredPx = dpToPx(configuredDp, resources)
        }
    }

    /**
     * Reset for tests. Not used in production.
     */
    @JvmStatic
    internal fun resetForTest() {
        enabled = false
        configuredDp = DEFAULT_DP
        configuredPx = -1
    }
}
