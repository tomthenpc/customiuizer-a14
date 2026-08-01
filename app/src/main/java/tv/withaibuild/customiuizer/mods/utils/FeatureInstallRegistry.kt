package tv.withaibuild.customiuizer.mods.utils

import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Registry of all features the module can install.
 *
 * Each feature is identified by its [FeatureId] and is installed at most once per process.  The
 * registry matches by [FeatureTarget] and [InstallPhase], checks [FeatureDefinition.isEnabled]
 * once, and records the result.  A failing feature is recorded once and does not stop the
 * installation of the remaining features.
 *
 * This registry is intentionally a plain (non-concurrent) structure because it is only used from
 * the single LSPosed init thread during one installation call.  The process-scoped state is held
 * in [FeatureInstallState].
 */
class FeatureInstallRegistry {

    private val orderedFeatures = ArrayList<FeatureDefinition>()
    private val definitions = HashMap<FeatureId, FeatureDefinition>()

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
            FeatureInstallState.set(feature.id, FeatureState.NOT_INSTALLED)
        }
    }

    /**
     * Install all features matching [target] and [phase].
     *
     * The default release path does **not** allocate a result list.  Pass [collectResults] = `true`
     * when tests or diagnostics need the ordered result list.
     */
    @Synchronized
    @JvmOverloads
    fun installAll(
        target: FeatureTarget,
        phase: InstallPhase,
        prefs: PrefMap,
        collectResults: Boolean = false,
    ): List<FeatureInstallResult> {
        val results = if (collectResults) ArrayList<FeatureInstallResult>() else null
        for (feature in orderedFeatures) {
            if (feature.target != target && feature.target != FeatureTarget.ANY) continue
            if (feature.phase != phase) continue
            val result = installOne(feature, prefs)
            if (results != null) results.add(result)
        }
        return results ?: emptyList()
    }

    private fun installOne(feature: FeatureDefinition, prefs: PrefMap): FeatureInstallResult {
        if (!feature.isEnabled(prefs)) {
            return FeatureInstallResult.SKIPPED
        }

        val id = feature.id
        val state = FeatureInstallState.get(id)

        return when (state) {
            FeatureState.INSTALLED, FeatureState.INSTALLING -> FeatureInstallResult.ALREADY_INSTALLED
            FeatureState.FAILED_PERMANENT -> FeatureInstallResult.FAILED_PERMANENT
            FeatureState.RESTART_REQUIRED -> FeatureInstallResult.RESTART_LATER
            FeatureState.FAILED_TRANSIENT, FeatureState.NOT_INSTALLED -> {
                FeatureInstallState.set(id, FeatureState.INSTALLING)
                val result = try {
                    feature.install()
                } catch (oom: OutOfMemoryError) {
                    FeatureInstallState.set(id, FeatureState.FAILED_TRANSIENT)
                    throw oom
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                    recordInstallFailure(feature, t)
                    FeatureInstallResult.FAILED_TRANSIENT
                }
                FeatureInstallState.set(id, toState(result))
                result
            }
        }
    }

    private fun recordInstallFailure(feature: FeatureDefinition, t: Throwable) {
        if (t is OutOfMemoryError) throw t
        HookDiagnostics.record(
            process = HookDiagnostics.currentProcessName ?: android.os.Process.myPid().toString(),
            kind = HookDiagnostics.Kind.FEATURE,
            targetClass = feature::class.java.name,
            targetMember = feature.id.name,
            descriptor = feature.name,
            status = HookDiagnostics.Status.INSTALL_FAILED,
            exceptionType = t.javaClass.name,
        )
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

            val state = FeatureInstallState.get(feature.id)
            when (state) {
                FeatureState.INSTALLED, FeatureState.INSTALLING -> feature.onPreferenceChanged(key, prefs)
                FeatureState.NOT_INSTALLED, FeatureState.FAILED_TRANSIENT -> {
                    if (feature.phase.isEarly) {
                        FeatureInstallState.set(feature.id, FeatureState.RESTART_REQUIRED)
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
                val current = FeatureInstallState.get(feature.id)
                if (current == FeatureState.FAILED_TRANSIENT || current == FeatureState.RESTART_REQUIRED) {
                    FeatureInstallState.set(feature.id, FeatureState.NOT_INSTALLED)
                }
            }
        }
    }

    private val InstallPhase.isEarly: Boolean
        get() = this == InstallPhase.MODULE_LOADED || this == InstallPhase.SYSTEM_SERVER_STARTING

    private fun toState(result: FeatureInstallResult): FeatureState = when (result) {
        FeatureInstallResult.INSTALLED, FeatureInstallResult.ALREADY_INSTALLED -> FeatureState.INSTALLED
        FeatureInstallResult.FAILED_PERMANENT -> FeatureState.FAILED_PERMANENT
        FeatureInstallResult.FAILED_TRANSIENT -> FeatureState.FAILED_TRANSIENT
        FeatureInstallResult.RESTART_LATER -> FeatureState.RESTART_REQUIRED
        FeatureInstallResult.SKIPPED -> FeatureState.NOT_INSTALLED
    }
}
