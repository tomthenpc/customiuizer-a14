package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Per-ClassLoader orchestrator that combines:
 *  - config snapshotting at [GestureAction.DOWN]
 *  - dependency preparation with `Ready / NotReady / FailedTransient`
 *  - the pure [GestureStateMachine]
 *  - the [GestureSideEffectGate]
 *
 * The caller still has to run the resulting [GestureCommand] list through a
 * [GestureEffectExecutor].  This class itself does not call Android APIs.
 */
class GestureMachine(
    private val classLoaderIdentity: String,
    private val configResolver: () -> GestureConfig,
    private val depsResolver: GestureDependenciesResolver,
    private val gate: GestureSideEffectGate = GestureSideEffectGate(),
) {

    private val snapshots = mutableMapOf<Int, GestureSnapshot>()
    private val dependencies = mutableMapOf<Int, GestureDependencies>()
    private val configs = mutableMapOf<Int, GestureConfig>()

    /**
     * Process one [event] and return the commands that are allowed to execute.
     */
    fun dispatch(event: GestureEvent): List<GestureCommand> {
        val ownerId = event.ownerId

        if (event.actionMasked == GestureAction.DOWN) {
            configs[ownerId] = configResolver()
        }

        val config = configs[ownerId] ?: return passThrough(event)

        val deps = ensureDependencies(ownerId) ?: return passThrough(event)

        val current = snapshots[ownerId] ?: GestureSnapshot()
        val (next, commands) = GestureStateMachine.process(
            current,
            event,
            config,
            deps.toGeometry(),
        )
        snapshots[ownerId] = next

        return gate.filter(event.entry, event, commands)
    }

    private fun passThrough(event: GestureEvent): List<GestureCommand> {
        return gate.filter(event.entry, event, listOf(GestureCommand.PassThrough))
    }

    private fun ensureDependencies(ownerId: Int): GestureDependencies? {
        val existing = dependencies[ownerId]
        if (existing != null && existing.classLoaderIdentity == classLoaderIdentity) {
            return existing
        }

        return when (val result = depsResolver.prepare(ownerId, classLoaderIdentity)) {
            is GestureDependenciesResult.Ready -> {
                dependencies[ownerId] = result.dependencies
                result.dependencies
            }
            is GestureDependenciesResult.NotReady,
            is GestureDependenciesResult.FailedTransient -> null
        }
    }

    /** Drop all per-owner state and the side-effect gate. */
    fun clear(ownerId: Int) {
        snapshots.remove(ownerId)
        dependencies.remove(ownerId)
        configs.remove(ownerId)
    }

    /** Reset the whole machine, e.g. when the ClassLoader is torn down. */
    fun clear() {
        snapshots.clear()
        dependencies.clear()
        configs.clear()
        gate.clear()
    }
}
