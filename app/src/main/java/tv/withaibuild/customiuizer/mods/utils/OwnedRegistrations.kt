package tv.withaibuild.customiuizer.mods.utils

/**
 * Tracks registrations this module creates inside a hooked system process (for example
 * DarkIconDispatcher dark receivers or StatusBarIconController icon groups), keyed by the
 * owner object whose lifetime controls them.
 *
 * SystemUI re-inflates the status bar on theme, density, display and fold changes. The system
 * singleton on the other side of each registration holds a strong reference to whatever was
 * registered, so a dead generation of module Views stays reachable for the whole process
 * lifetime unless the registration is explicitly removed. Callers run [cleanupWhere] at a
 * generation boundary (a new inflation or attach) with a predicate that identifies stale
 * owners.
 *
 * Multiple registrations may share one owner. Not thread-safe: every caller runs on the
 * owning process main thread, matching the SystemUI view lifecycle callbacks that drive it.
 */
class OwnedRegistrations<V : Any> {

    private class Entry<V>(val owner: V, val cleanup: (V) -> Unit)

    private val entries = ArrayList<Entry<V>>(4)

    val size: Int get() = entries.size

    fun register(owner: V, cleanup: (V) -> Unit) {
        entries.add(Entry(owner, cleanup))
    }

    /**
     * Runs and drops every entry whose owner matches [isStale]. Cleanup failures are isolated
     * per entry (a missing ROM method on one registration must not keep the others alive),
     * but fatal JVM errors always propagate.
     */
    fun cleanupWhere(isStale: (V) -> Boolean) {
        for (i in entries.indices.reversed()) {
            val entry = entries[i]
            if (!isStale(entry.owner)) continue
            entries.removeAt(i)
            try {
                entry.cleanup(entry.owner)
            } catch (t: Throwable) {
                FatalErrors.rethrowIfFatal(t)
                XposedHelpers.log(t)
            }
        }
    }
}
