package tv.withaibuild.customiuizer.mods.utils

import java.lang.ref.WeakReference
import java.util.WeakHashMap

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
 * The pending bucket uses [WeakHashMap] with the owner as the key for fast lookup, and a
 * strong [pendingStates] set to keep the [StatusBarDisplayState] reachable even if the
 * [WeakHashMap] expunges a cleared key before [prune] has had a chance to run the
 * registration cleanup.
 *
 * All mutation is expected to run on the SystemUI main thread.
 */
class StatusBarDisplayRegistry<O : Any, R : Any> {

    private val byDisplay = mutableMapOf<Int, StatusBarDisplayState<O, R>>()
    private val pendingByOwner = WeakHashMap<O, StatusBarDisplayState<O, R>>()
    private val pendingStates = mutableSetOf<StatusBarDisplayState<O, R>>()

    /**
     * Return or create the pending state for [owner] when the display is not yet known.
     * Multiple calls for the same owner return the same state; two different owners never
     * share a pending bucket.
     */
    fun getOrCreatePending(owner: O): StatusBarDisplayState<O, R> {
        val existing = pendingByOwner[owner]
        if (existing != null) {
            // Refresh the weak reference so the entry tracks the current owner.
            existing.generation = WeakReference(owner)
            return existing
        }
        val created = StatusBarDisplayState<O, R>(WeakReference(owner))
        pendingByOwner[owner] = created
        pendingStates.add(created)
        return created
    }

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
            existing.registrations.cleanupAll()
        }
        val state = pending ?: StatusBarDisplayState<O, R>(WeakReference(owner))
        if (pending != null) {
            pendingStates.remove(pending)
        }
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
     * Remove every state whose owner is gone and whose registration list is empty.
     *
     * Before a state is removed, all its registrations are cleaned. If the cleanup callbacks
     * re-enter and add new registrations, the state is kept.
     */
    fun prune() {
        val deadDisplays = mutableListOf<Int>()
        for ((displayId, state) in byDisplay) {
            val generationAlive = state.generation?.get() != null
            if (!generationAlive) {
                // The status bar view for this display is gone; release every registration.
                // A reentrant callback might register again, in which case the state is kept.
                state.registrations.cleanupAll()
                if (state.generation?.get() == null && state.registrations.size == 0) {
                    deadDisplays.add(displayId)
                }
            }
        }
        for (displayId in deadDisplays) {
            byDisplay.remove(displayId)
        }

        // Snapshot pending states so we can clean and remove them without concurrent mutation.
        val pendingSnapshot = pendingStates.toList()
        for (state in pendingSnapshot) {
            val owner = state.generation?.get()
            if (owner == null) {
                // The status bar view was never bound and has been garbage collected.
                // Run every registration cleanup before dropping the state.
                state.registrations.cleanupAll()
                if (state.generation?.get() == null && state.registrations.size == 0) {
                    pendingStates.remove(state)
                    pendingByOwner.values.remove(state)
                }
            }
        }
        // Expunge any cleared-key entries that became removable during this prune.
        @Suppress("UNUSED_VARIABLE")
        val expunge = pendingByOwner.size
    }

    /**
     * All current display and pending states.
     */
    fun allStates(): Collection<StatusBarDisplayState<O, R>> = byDisplay.values + pendingStates
}
