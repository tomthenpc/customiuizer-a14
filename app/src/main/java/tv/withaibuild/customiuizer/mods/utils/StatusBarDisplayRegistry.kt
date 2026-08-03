package tv.withaibuild.customiuizer.mods.utils

import java.lang.ref.WeakReference
import java.util.IdentityHashMap

/**
 * Per-display state for a status bar generation.
 *
 * @param O the owner type (typically the status bar view)
 * @param R the second-row container type
 */
data class StatusBarDisplayState<O : Any, R : Any>(
    var generation: WeakReference<O>?,
    var secondRow: WeakReference<R>? = null,
    val registrations: OwnedRegistrations<O> = OwnedRegistrations(),
)

/**
 * Per-display state for SystemUI status bar generations.
 *
 * SystemUI may host multiple status bars on different displays. Each display has its own
 * generation (the current status bar view), its own second-row container for network speed
 * and its own [OwnedRegistrations]. A view whose display is not yet known is kept in a
 * temporary identity-scoped pending bucket so two null-display views do not clean each other.
 *
 * All mutation is expected to run on the SystemUI main thread.
 */
class StatusBarDisplayRegistry<O : Any, R : Any> {

    private val byDisplay = mutableMapOf<Int, StatusBarDisplayState<O, R>>()
    private val pendingByOwner = IdentityHashMap<O, StatusBarDisplayState<O, R>>()

    /**
     * Return or create the pending state for [owner] when the display is not yet known.
     * Multiple calls for the same owner return the same state; two different owners never
     * share a pending bucket.
     */
    fun getOrCreatePending(owner: O): StatusBarDisplayState<O, R> =
        pendingByOwner.getOrPut(owner) { StatusBarDisplayState(WeakReference(owner)) }

    /**
     * Bind [owner] to [displayId], migrating any pending state. If the display already has a
     * state owned by a different (dead) generation, that state is fully cleaned before the
     * display bucket is replaced.
     */
    fun bind(owner: O, displayId: Int): StatusBarDisplayState<O, R> {
        val pending = pendingByOwner.remove(owner)
        val existing = byDisplay[displayId]
        if (existing != null && existing.generation?.get() === owner) {
            // Same view re-attached; keep its state and registrations.
            return existing
        }
        if (existing != null) {
            // Old generation for this display is being replaced; release everything it owned.
            existing.registrations.cleanupWhere { true }
        }
        val state = pending ?: StatusBarDisplayState(WeakReference(owner))
        state.generation = WeakReference(owner)
        if (pending != null && existing != null && pending !== existing) {
            // The pending state carries the row created at onFinishInflate; prefer it.
            if (state.secondRow == null) state.secondRow = pending.secondRow ?: existing.secondRow
        }
        byDisplay[displayId] = state
        return state
    }

    /**
     * Return the existing state for [displayId], or null if none has been bound.
     */
    fun get(displayId: Int): StatusBarDisplayState<O, R>? = byDisplay[displayId]

    /**
     * Remove every state whose generation and row are gone and whose registration list is empty.
     */
    fun prune() {
        val deadDisplays = mutableListOf<Int>()
        for ((displayId, state) in byDisplay) {
            val generationAlive = state.generation?.get() != null
            val rowAlive = state.secondRow?.get() != null
            if (!generationAlive && !rowAlive && state.registrations.size == 0) {
                deadDisplays.add(displayId)
            }
        }
        for (displayId in deadDisplays) {
            byDisplay.remove(displayId)
        }

        val deadOwners = mutableListOf<O>()
        for ((owner, state) in pendingByOwner) {
            val generationAlive = state.generation?.get() != null
            val rowAlive = state.secondRow?.get() != null
            if (!generationAlive && !rowAlive && state.registrations.size == 0) {
                deadOwners.add(owner)
            }
        }
        for (owner in deadOwners) {
            pendingByOwner.remove(owner)
        }
    }

    /**
     * All current display and pending states.
     */
    fun allStates(): Collection<StatusBarDisplayState<O, R>> = byDisplay.values + pendingByOwner.values
}
