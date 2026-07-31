package tv.withaibuild.customiuizer.mods.utils

import tv.withaibuild.customiuizer.utils.PrefMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of all features the module can install.
 *
 * Each feature is identified by its [FeatureId] and is installed at most once per process.  The
 * registry matches by [FeatureTarget] and [InstallPhase], checks [FeatureDefinition.isEnabled]
 * once, and records the result.  A failing feature is recorded once and does not stop the
 * installation of the remaining features.
 */
class FeatureInstallRegistry {

    private val orderedFeatures = ArrayList<FeatureDefinition>()
    private val definitions = ConcurrentHashMap<FeatureId, FeatureDefinition>()
    private val states = ConcurrentHashMap<FeatureId, FeatureState>()

    /**
     * Register a feature definition.  Safe to call multiple times with the same definition.
     * A different definition under the same [FeatureId] is rejected and recorded.
     */
    @Synchronized
    fun register(feature: FeatureDefinition) {
        val existing = definitions.putIfAbsent(feature.id, feature)
        if (existing != null && existing !== feature) {
            val message = "Duplicate feature id ${feature.id.name}: ${existing::class.java.name} vs ${feature::class.java.name}"
            HookDiagnostics.record(
                process = HookDiagnostics.currentProcessName ?: android.os.Process.myPid().toString(),
                kind = HookDiagnostics.Kind.FEATURE,
                targetClass = FeatureInstallRegistry::class.java.name,
                targetMember = feature.id.name,
                status = HookDiagnostics.Status.DUPLICATE_FEATURE,
                exceptionType = message,
            )
            throw IllegalArgumentException(message)
        }
        if (existing == null) {
            orderedFeatures.add(feature)
            states.putIfAbsent(feature.id, FeatureState.NOT_INSTALLED)
        }
    }

    /**
     * Install all features matching [target] and [phase].
     *
     * Returns a summary of the results.  The result list preserves the registration order so a
     * human reading a bug report can see which feature stopped if the process died.
     */
    @Synchronized
    fun installAll(
        target: FeatureTarget,
        phase: InstallPhase,
        prefs: PrefMap,
    ): List<FeatureInstallResult> {
        val results = mutableListOf<FeatureInstallResult>()
        for (feature in orderedFeatures) {
            if (feature.target != target && feature.target != FeatureTarget.ANY) continue
            if (feature.phase != phase) continue
            val result = installOne(feature, prefs)
            results.add(result)
        }
        return results
    }

    private fun installOne(feature: FeatureDefinition, prefs: PrefMap): FeatureInstallResult {
        if (!feature.isEnabled(prefs)) {
            return FeatureInstallResult.Skipped("${feature.name} disabled by preference")
        }

        val id = feature.id
        val state = states[id] ?: FeatureState.NOT_INSTALLED

        return when (state) {
            FeatureState.INSTALLED, FeatureState.INSTALLING -> FeatureInstallResult.AlreadyInstalled
            FeatureState.FAILED_PERMANENT -> FeatureInstallResult.FailedPermanent("${feature.name} already failed permanently")
            FeatureState.RESTART_REQUIRED -> FeatureInstallResult.RestartLater
            FeatureState.FAILED_TRANSIENT, FeatureState.NOT_INSTALLED -> {
                states[id] = FeatureState.INSTALLING
                val result = try {
                    feature.install()
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                    FeatureInstallResult.FailedTransient(t.javaClass.name)
                }
                states[id] = toState(result)
                result
            }
        }
    }

    /**
     * Process a preference change.
     *
     * Active features have their [FeatureDefinition.onPreferenceChanged] called so they can update
     * runtime values.  Features that are not yet installed and cannot be installed in a running
     * process (early phases) are marked [FeatureState.RESTART_REQUIRED].  Other not-installed
     * features are left to be picked up by the next matching [installAll].
     */
    @Synchronized
    fun onPreferenceChanged(key: String?, prefs: PrefMap) {
        for (feature in orderedFeatures) {
            val prefKey = feature.preferenceKey
            if (prefKey == null || (key != null && key != prefKey && !key.startsWith(prefKey))) continue

            val state = states[feature.id] ?: FeatureState.NOT_INSTALLED
            when (state) {
                FeatureState.INSTALLED, FeatureState.INSTALLING -> feature.onPreferenceChanged(key, prefs)
                FeatureState.NOT_INSTALLED, FeatureState.FAILED_TRANSIENT -> {
                    if (feature.phase.isEarly) {
                        states[feature.id] = FeatureState.RESTART_REQUIRED
                    }
                }
                FeatureState.FAILED_PERMANENT, FeatureState.RESTART_REQUIRED -> { /* nothing to do */ }
            }
        }
    }

    /** Mark a feature as needing re-evaluation.  Only safe for transient failures; not a reinstall hook. */
    @Synchronized
    fun markForReinstall(featureName: String) {
        for (feature in orderedFeatures) {
            if (feature.name == featureName) {
                val current = states[feature.id]
                if (current == FeatureState.FAILED_TRANSIENT || current == FeatureState.RESTART_REQUIRED) {
                    states[feature.id] = FeatureState.NOT_INSTALLED
                }
            }
        }
    }

    private val InstallPhase.isEarly: Boolean
        get() = this == InstallPhase.MODULE_LOADED || this == InstallPhase.SYSTEM_SERVER_STARTING

    private fun toState(result: FeatureInstallResult): FeatureState = when (result) {
        is FeatureInstallResult.Installed, is FeatureInstallResult.AlreadyInstalled -> FeatureState.INSTALLED
        is FeatureInstallResult.FailedPermanent -> FeatureState.FAILED_PERMANENT
        is FeatureInstallResult.FailedTransient -> FeatureState.FAILED_TRANSIENT
        is FeatureInstallResult.RestartLater -> FeatureState.RESTART_REQUIRED
        is FeatureInstallResult.Skipped -> FeatureState.NOT_INSTALLED
    }
}
