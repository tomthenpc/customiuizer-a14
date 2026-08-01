package tv.withaibuild.customiuizer.mods.utils

import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Registry of all features the module can install.
 *
 * Each feature is identified by its [FeatureId] and is installed at most once per process.  The
 * registry matches by [FeatureTarget] and [InstallPhase], checks [FeatureSpec.isEnabled]
 * once, creates the [FeatureDefinition] only when enabled, and records the result.  A failing
 * feature is recorded once and does not stop the installation of the remaining features.
 *
 * This registry is intentionally a plain (non-concurrent) structure because it is only used from
 * the single LSPosed init thread during one installation call.  The process-scoped state is held
 * in [FeatureInstallState].
 */
class FeatureInstallRegistry {

    private val orderedFeatures = ArrayList<FeatureSpec>()
    private val definitions = HashMap<FeatureId, FeatureSpec>()
    private val activeDefinitions = HashMap<FeatureId, FeatureDefinition>()

    /**
     * Register a feature spec.  Safe to call multiple times with the same spec.
     * A different spec under the same [FeatureId] is rejected and recorded.
     */
    @Synchronized
    fun register(feature: FeatureSpec) {
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
        for (spec in orderedFeatures) {
            if (spec.target != target && spec.target != FeatureTarget.ANY) continue
            if (spec.phase != phase) continue
            val result = installOne(spec, prefs)
            if (results != null) results.add(result)
        }
        return results ?: emptyList()
    }

    private fun installOne(spec: FeatureSpec, prefs: PrefMap): FeatureInstallResult {
        if (!spec.isEnabled(prefs)) {
            return FeatureInstallResult.SKIPPED
        }

        val id = spec.id
        val state = FeatureInstallState.get(id)

        return when (state) {
            FeatureState.INSTALLED, FeatureState.INSTALLING -> FeatureInstallResult.ALREADY_INSTALLED
            FeatureState.FAILED_PERMANENT -> FeatureInstallResult.FAILED_PERMANENT
            FeatureState.RESTART_REQUIRED -> FeatureInstallResult.RESTART_LATER
            FeatureState.FAILED_TRANSIENT, FeatureState.NOT_INSTALLED -> {
                FeatureInstallState.set(id, FeatureState.INSTALLING)
                var definition: FeatureDefinition? = null
                val result = try {
                    val created = spec.create()
                    definition = created
                    activeDefinitions[id] = created
                    created.install()
                } catch (oom: OutOfMemoryError) {
                    activeDefinitions.remove(id)
                    FeatureInstallState.set(id, FeatureState.FAILED_TRANSIENT)
                    throw oom
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                    recordInstallFailure(spec, t)
                    FeatureInstallResult.FAILED_TRANSIENT
                }
                if (result != FeatureInstallResult.FAILED_TRANSIENT && definition != null) {
                    activeDefinitions[id] = definition
                } else {
                    activeDefinitions.remove(id)
                }
                FeatureInstallState.set(id, toState(result))
                result
            }
        }
    }

    private fun recordInstallFailure(spec: FeatureSpec, t: Throwable) {
        if (t is OutOfMemoryError) throw t
        HookDiagnostics.record(
            process = HookDiagnostics.currentProcessName ?: android.os.Process.myPid().toString(),
            kind = HookDiagnostics.Kind.FEATURE,
            targetClass = spec::class.java.name,
            targetMember = spec.id.name,
            descriptor = spec.name,
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
        for (spec in orderedFeatures) {
            val prefKey = spec.preferenceKey
            if (prefKey == null || (key != null && key != prefKey && !key.startsWith(prefKey))) continue

            val state = FeatureInstallState.get(spec.id)
            when (state) {
                FeatureState.INSTALLED, FeatureState.INSTALLING -> {
                    activeDefinitions[spec.id]?.onPreferenceChanged(key, prefs)
                }
                FeatureState.NOT_INSTALLED, FeatureState.FAILED_TRANSIENT -> {
                    if (spec.phase.isEarly && spec.isEnabled(prefs)) {
                        FeatureInstallState.set(spec.id, FeatureState.RESTART_REQUIRED)
                    }
                }
                FeatureState.FAILED_PERMANENT, FeatureState.RESTART_REQUIRED -> { /* nothing to do */ }
            }
        }
    }

    /** Mark a feature as needing re-evaluation.  Only safe for transient failures; not a reinstall hook. */
    @Synchronized
    fun markForReinstall(featureName: String) {
        for (spec in orderedFeatures) {
            if (spec.name == featureName) {
                val current = FeatureInstallState.get(spec.id)
                if (current == FeatureState.FAILED_TRANSIENT || current == FeatureState.RESTART_REQUIRED) {
                    FeatureInstallState.set(spec.id, FeatureState.NOT_INSTALLED)
                    activeDefinitions.remove(spec.id)
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

    /** Test-only access to the active definition map. */
    internal fun activeDefinitionForTest(id: FeatureId): FeatureDefinition? = activeDefinitions[id]
}
