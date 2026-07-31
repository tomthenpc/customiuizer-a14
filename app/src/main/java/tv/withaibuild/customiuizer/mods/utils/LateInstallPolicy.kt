package tv.withaibuild.customiuizer.mods.utils

/**
 * Policy for whether a feature may be installed after its preferred [InstallPhase] has passed.
 *
 * Most features must be installed at the exact phase they declare.  A few may be installed
 * later if they were skipped because preferences were not yet ready, but they must never be
 * re-installed or reset to an uninstalled state.
 */
enum class LateInstallPolicy {
    /** The feature must be installed at its declared phase; late installation is not allowed. */
    NONE,

    /** The feature may be installed later if it was missed at its phase. */
    ALLOWED,

    /** The feature can only be installed after a process restart. */
    REQUIRES_RESTART,
}
