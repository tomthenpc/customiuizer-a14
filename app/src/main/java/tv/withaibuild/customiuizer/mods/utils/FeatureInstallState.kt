package tv.withaibuild.customiuizer.mods.utils

/**
 * Process-scoped storage of feature installation state.
 *
 * One [FeatureInstallState] object lives per process in the module's class loader.  It is keyed by
 * the stable integer [FeatureId.id] rather than by the [FeatureId] object.  This keeps lookups
 * fast and avoids the per-installer state maps that used to be created by each
 * [FeatureInstallRegistry].
 *
 * The current implementation uses a plain [HashMap] because it is available both on Android and in
 * the JVM unit-test runtime.  Once a SparseArray/BitSet implementation is available in both
 * runtimes, the storage can be switched without changing callers.
 */
object FeatureInstallState {

    private val states = HashMap<Int, FeatureState>()

    /** Return the current state for [featureId], defaulting to [FeatureState.NOT_INSTALLED]. */
    @JvmStatic
    fun get(featureId: Int): FeatureState = synchronized(states) {
        states[featureId] ?: FeatureState.NOT_INSTALLED
    }

    /** Set the current state for [featureId]. */
    @JvmStatic
    fun set(featureId: Int, state: FeatureState) = synchronized(states) {
        states[featureId] = state
    }

    /** Convenience: get the state for a [FeatureId]. */
    @JvmStatic
    fun get(featureId: FeatureId): FeatureState = get(featureId.id)

    /** Convenience: set the state for a [FeatureId]. */
    @JvmStatic
    fun set(featureId: FeatureId, state: FeatureState) = set(featureId.id, state)

    /** Reset all state.  Intended for tests and module reload. */
    @JvmStatic
    fun reset() = synchronized(states) { states.clear() }
}
