package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Per-ClassLoader orchestrator that combines:
 *  - config snapshotting at [GestureAction.DOWN]
 *  - dependency preparation with `Ready / NotReady / FailedTransient`
 *  - the pure [GestureStateMachine]
 *  - the [GestureSideEffectGate]
 *  - a single [GestureEffectExecutor]
 *
 * The [context] passed to [dispatch] is forwarded to the resolver and the effect
 * executor.  The orchestrator itself does not call Android services, but it does
 * need the Android Context for the concrete resolver/executor supplied by the caller.
 */
class GestureMachine(
    private val classLoaderIdentity: String,
    private val configResolver: () -> GestureConfig,
    private val depsResolver: GestureDependenciesResolver,
    private val effectExecutor: GestureEffectExecutor,
    private val gate: GestureSideEffectGate = GestureSideEffectGate(),
) {

    private val snapshots = mutableMapOf<Int, GestureSnapshot>()
    private val dependencies = mutableMapOf<Int, GestureDependencies>()
    private val configs = mutableMapOf<Int, GestureConfig>()

    /**
     * Prepare dependencies for the given owner at a safe lifecycle point.
     *
     * Returns `true` if dependencies are now ready.  This is the only place where
     * cold reflection / dependency resolution should happen.
     */
    fun prepare(ownerId: Int, context: Any): Boolean {
        return ensureDependencies(ownerId, context) != null
    }

    /**
     * Process one [event] and execute the allowed side-effects through [effectExecutor].
     */
    fun dispatch(event: GestureEvent, context: Any) {
        val ownerId = event.ownerId

        if (event.actionMasked == GestureAction.DOWN) {
            configs[ownerId] = configResolver()
        }

        val config = configs[ownerId] ?: return

        if (dependencies[ownerId] == null) {
            passThrough(event)
            return
        }
        val deps = dependencies[ownerId]!!

        val current = snapshots[ownerId] ?: GestureSnapshot()
        val (next, commands) = GestureStateMachine.process(
            current,
            event,
            config,
            deps.toGeometry(),
        )
        snapshots[ownerId] = next

        val allowed = gate.filter(event.entry, event, commands)
        effectExecutor.execute(allowed, deps, config, context)
    }

    private fun passThrough(event: GestureEvent): List<GestureCommand> {
        return gate.filter(event.entry, event, listOf(GestureCommand.PassThrough))
    }

    private fun ensureDependencies(ownerId: Int, context: Any): GestureDependencies? {
        val existing = dependencies[ownerId]
        if (existing != null && existing.classLoaderIdentity == classLoaderIdentity) {
            return existing
        }

        return try {
            when (val result = depsResolver.prepare(ownerId, classLoaderIdentity, context)) {
                is GestureDependenciesResult.Ready -> {
                    dependencies[ownerId] = result.dependencies
                    result.dependencies
                }
                is GestureDependenciesResult.NotReady,
                is GestureDependenciesResult.FailedTransient -> null
            }
        } catch (err: Throwable) {
            when (err) {
                is OutOfMemoryError, is ThreadDeath, is VirtualMachineError -> throw err
                else -> null
            }
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
