package tv.withaibuild.customiuizer.mods.statusbarheight

import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-scoped runtime state for the status bar height feature.
 *
 * The class is explicit about ownership: [SystemStatusBarInsetsHooks] (or equivalent facade) will
 * hold the process instance.  No strong Android owner references are retained here.  All
 * per-WindowState identity uses [WeakReference] inside a bounded array.
 */
internal class StatusBarHeightRuntime {

    /** Bounded identity fast path: known status bar WindowStates. */
    private var knownOwners = arrayOfNulls<WeakReference<Any>>(MAX_TRACKED)
    private var knownCount: Int = 0

    /** Weak reference to the most recently laid-out status bar WindowState for traversal. */
    @Volatile
    var latestKnownStatusBar: WeakReference<Any>? = null

    /** True once `LayoutParams.type == TYPE_STATUS_BAR` has been observed on this ROM. */
    @Volatile
    var typeMatchObserved: Boolean = false

    /** Remaining budget for the packageName/toString fallback probe. */
    val fallbackProbeBudget: AtomicInteger = AtomicInteger(MAX_FALLBACK_PROBES)

    /** Last generation for which a traversal was requested, to coalesce duplicates. */
    val lastRefreshGeneration: AtomicLong = AtomicLong(-1L)

    /** Number of currently known non-null entries.  For tests and diagnostics only. */
    @Synchronized
    fun knownCountForTest(): Int = knownCount

    /** Test-only accessor for the identity snapshot. */
    @Synchronized
    fun knownSnapshotForTest(): Array<WeakReference<Any>?> = knownOwners.copyOf(knownCount)

    /** Test-only accessor for the latest ref. */
    @Synchronized
    fun latestRefForTest(): WeakReference<Any>? = latestKnownStatusBar

    /**
     * Check whether [owner] is already known as a status bar.
     *
     * This is the steady-state identity fast path:
     * - bounded linear scan
     * - no lock
     * - no allocation
     */
    fun isKnownStatusBar(owner: Any): Boolean {
        val snapshot = knownOwners
        val count = knownCount
        for (i in 0 until count) {
            val ref = snapshot[i]
            if (ref?.get() === owner) return true
        }
        return false
    }

    /**
     * Remember that [owner] is a status bar.
     *
     * This is the rare discovery path.  It may allocate one [WeakReference] and rebuild the small
     * bounded array.  The operation is synchronized so readers can read the array without a lock.
     */
    @Synchronized
    fun rememberStatusBar(owner: Any): KnownStatusBarEntry? {
        val snapshot = knownOwners
        val count = knownCount

        // Try to reuse an existing entry/ref and update latest.
        for (i in 0 until count) {
            val ref = snapshot[i]
            if (ref?.get() === owner) {
                latestKnownStatusBar = ref
                return KnownStatusBarEntry(ref)
            }
        }

        // Evict dead / null refs to make room before growing.
        val compacted = arrayOfNulls<WeakReference<Any>>(MAX_TRACKED)
        var j = 0
        for (i in 0 until count) {
            val ref = snapshot[i]
            if (ref != null && ref.get() != null) {
                compacted[j++] = ref
            }
        }

        // If still at capacity, drop the oldest entry.
        if (j >= MAX_TRACKED) {
            for (i in 1 until MAX_TRACKED) {
                compacted[i - 1] = compacted[i]
            }
            j = MAX_TRACKED - 1
            compacted[j] = null
        }

        val newRef = WeakReference(owner)
        compacted[j++] = newRef
        knownOwners = compacted
        knownCount = j
        latestKnownStatusBar = newRef

        return KnownStatusBarEntry(newRef)
    }

    /**
     * Forget all known status bars.  Used by tests and process reinitialization.
     */
    @Synchronized
    fun resetKnownStatusBars() {
        for (i in 0 until knownCount) {
            knownOwners[i] = null
        }
        knownCount = 0
        latestKnownStatusBar = null
        typeMatchObserved = false
        fallbackProbeBudget.set(MAX_FALLBACK_PROBES)
        lastRefreshGeneration.set(-1L)
    }

    /**
     * Bounded per-status-bar metadata entry.
     *
     * B1 keeps this minimal: only the owner identity.  Original height and display id are captured
     * on-demand by the Effect layer in B2.
     */
    data class KnownStatusBarEntry(
        val ownerRef: WeakReference<Any>,
    ) {
        val owner: Any? get() = ownerRef.get()
    }

    companion object {
        /** Upper bound for the identity fast path and for per-display diagnostic stamps. */
        const val MAX_TRACKED = 4

        /** Upper bound for the expensive packageName/toString fallback probe. */
        const val MAX_FALLBACK_PROBES = 4096

        /** `WindowManager.LayoutParams.TYPE_STATUS_BAR` = 2000. */
        const val TYPE_STATUS_BAR = 2000
    }
}
