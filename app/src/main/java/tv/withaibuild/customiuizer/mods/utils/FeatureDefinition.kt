package tv.withaibuild.customiuizer.mods.utils

import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Declaration of one feature that can be installed by the module.
 *
 * A feature is the unit of "one preference, one hook site, one process".  The [id] is its typed
 * identity; [name] is only for diagnostics.  The registry handles ordering, idempotency and
 * per-preference invalidation.
 */
interface FeatureDefinition {

    /** Typed identity.  Equality is identity-based (usually an `object` or `data object`). */
    val id: FeatureId

    /** Human-readable name for diagnostics and late-preference tracking. */
    val name: String

    /** The preference key that enables this feature, or null if it is always enabled. */
    val preferenceKey: String?

    /** The process/apk in which this feature must be installed. */
    val target: FeatureTarget

    /** The earliest lifecycle phase at which this feature may be installed. */
    val phase: InstallPhase

    /**
     * Whether the feature should be installed now, based on the current preference snapshot.
     * The registry calls this before [install] so disabled features cost one map lookup.
     */
    fun isEnabled(prefs: PrefMap): Boolean

    /**
     * Attempt to install the feature.  Implementations must be idempotent and must not throw.
     */
    fun install(): FeatureInstallResult

    /**
     * Called when a relevant preference changes while the feature is active.
     * Implementations should update their runtime state only; they must not re-install or throw.
     */
    fun onPreferenceChanged(key: String?, prefs: PrefMap) {
        // Default: runtime values are static or handled elsewhere.
    }
}
