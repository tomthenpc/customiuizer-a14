package tv.withaibuild.customiuizer.mods.utils

import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Registry of all features the module can install.
 *
 * Each feature is installed at most once per process.  The registry matches by [FeatureTarget] and
 * [InstallPhase], checks [FeatureDefinition.isEnabled] once, and records the result.  A failing
 * feature is recorded once and does not stop the installation of the remaining features.
 */
class FeatureInstallRegistry {

    private val features = mutableListOf<FeatureDefinition>()
    private val installed = mutableSetOf<String>()
    private val failed = mutableSetOf<String>()

    /** Register a feature definition.  Safe to call multiple times. */
    fun register(feature: FeatureDefinition) {
        features.add(feature)
    }

    /**
     * Install all features matching [target] and [phase].
     *
     * Returns a summary of the results.  The result list preserves the registration order so a
     * human reading a bug report can see which feature stopped if the process died.
     */
    fun installAll(
        target: FeatureTarget,
        phase: InstallPhase,
        prefs: PrefMap,
    ): List<FeatureInstallResult> {
        val results = mutableListOf<FeatureInstallResult>()
        for (feature in features) {
            if (feature.target != target && feature.target != FeatureTarget.ANY) continue
            if (feature.phase != phase) continue
            if (!feature.isEnabled(prefs)) {
                results.add(FeatureInstallResult.Skipped("${feature.name} disabled by preference"))
                continue
            }
            if (installed.contains(feature.name)) {
                results.add(FeatureInstallResult.AlreadyInstalled)
                continue
            }
            if (failed.contains(feature.name)) {
                results.add(FeatureInstallResult.FailedPermanent("${feature.name} already failed in this process"))
                continue
            }
            val result = try {
                feature.install()
            } catch (t: Throwable) {
                XposedHelpers.log(t)
                FeatureInstallResult.FailedTransient(t.javaClass.name)
            }
            when (result) {
                is FeatureInstallResult.Installed -> installed.add(feature.name)
                is FeatureInstallResult.AlreadyInstalled -> installed.add(feature.name)
                is FeatureInstallResult.FailedPermanent -> failed.add(feature.name)
                is FeatureInstallResult.FailedTransient -> { /* allow retry on next phase */ }
                is FeatureInstallResult.Skipped,
                is FeatureInstallResult.RestartLater -> { /* nothing to record */ }
            }
            results.add(result)
        }
        return results
    }

    /** Mark a feature as needing re-evaluation after a preference change. */
    fun markForReinstall(featureName: String) {
        installed.remove(featureName)
        failed.remove(featureName)
    }

    /** Process a preference change.  Features whose key matches are marked for re-evaluation. */
    fun onPreferenceChanged(key: String?) {
        for (feature in features) {
            val prefKey = feature.preferenceKey
            if (prefKey != null && (key == null || prefKey == key || key.startsWith(prefKey))) {
                markForReinstall(feature.name)
            }
        }
    }
}
