package tv.withaibuild.customiuizer.mods.utils

/**
 * Outcome of installing a single feature.
 *
 * Feature installation must be explicit about what happened.  A feature is a unit of
 * functionality guarded by a preference and a process/phase target.  The result tells the
 * installer whether the feature is now active, was skipped because it is disabled or not
 * applicable, failed in a way that should be retried, or failed permanently.
 */
sealed class FeatureInstallResult {

    /** The feature was installed for the first time in this process. */
    data object Installed : FeatureInstallResult()

    /** The feature was already installed; the request was idempotent. */
    data object AlreadyInstalled : FeatureInstallResult()

    /**
     * The feature was skipped because it is disabled, not applicable to the current process/phase,
     * or a precondition was not met.  [reason] is for diagnostics only.
     */
    data class Skipped(val reason: String) : FeatureInstallResult()

    /**
     * Installation failed but may succeed later (for example, the target class is not yet loaded).
     * The installer may retry the feature on a later phase or package-ready callback.
     */
    data class FailedTransient(val reason: String) : FeatureInstallResult()

    /**
     * Installation failed and should not be retried automatically (for example, a class or method
     * is missing in this ROM and the feature cannot work).  The failure is recorded once.
     */
    data class FailedPermanent(val reason: String) : FeatureInstallResult()

    /**
     * The feature's setup was deferred because a full installation requires a process restart.
     * The module remembers the result and tries to finish installation when the process reloads.
     */
    data object RestartLater : FeatureInstallResult()

    /** Whether this result means the feature is active in the target process. */
    val isActive: Boolean
        get() = this is Installed || this is AlreadyInstalled
}
