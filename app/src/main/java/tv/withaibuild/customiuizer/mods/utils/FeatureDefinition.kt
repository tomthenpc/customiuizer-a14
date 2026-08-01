package tv.withaibuild.customiuizer.mods.utils

import tv.withaibuild.customiuizer.utils.PrefMap
import tv.withaibuild.customiuizer.utils.RestartRequirement

/**
 * Declaration of one feature that can be installed by the module.
 *
 * A feature is the unit of "one preference, one hook site, one process".  The [id] is its typed
 * identity; [name] is only for diagnostics.  The registry handles ordering, idempotency and
 * per-preference invalidation.
 *
 * A [FeatureDefinition] is also a [FeatureSpec] whose [create] returns itself.  This lets the
 * install registry accept either a ready-to-install [FeatureDefinition] or a lazy [FeatureSpec]
 * that defers construction.
 */
interface FeatureDefinition : FeatureSpec {

    override fun create(): FeatureDefinition = this

    /** Typed identity.  Equality is identity-based (usually an `object` or `data object`). */
    override val id: FeatureId

    /** Human-readable name for diagnostics and late-preference tracking. */
    override val name: String

    /** The preference key that enables this feature, or null if it is always enabled. */
    override val preferenceKey: String?

    /** The process/apk in which this feature must be installed. */
    override val target: FeatureTarget

    /** The earliest lifecycle phase at which this feature may be installed. */
    override val phase: InstallPhase

    /** Policy for whether this feature may be installed after its preferred [InstallPhase]. */
    override val lateInstallPolicy: LateInstallPolicy get() = LateInstallPolicy.NONE

    /** The restart/exit action required for a preference change to take effect. */
    override val restartRequirement: RestartRequirement get() = RestartRequirement.NONE

    /**
     * Whether the feature should be installed now, based on the current preference snapshot.
     * The registry calls this before [install] so disabled features cost one map lookup.
     */
    override fun isEnabled(prefs: PrefMap): Boolean

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
