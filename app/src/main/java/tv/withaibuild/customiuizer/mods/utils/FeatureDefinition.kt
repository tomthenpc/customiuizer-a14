package tv.withaibuild.customiuizer.mods.utils

import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Declaration of one feature that can be installed by the module.
 *
 * A feature is the unit of "one preference, one hook site, one process".  The definition is
 * intentionally small: it knows whether it should run, where it should run, and how to install
 * itself.  The registry handles ordering and idempotency.
 */
interface FeatureDefinition {

    /** Human-readable, unique name.  Used for diagnostics and for late-preference tracking. */
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
}
