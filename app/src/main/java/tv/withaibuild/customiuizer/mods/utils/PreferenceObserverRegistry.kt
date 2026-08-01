package tv.withaibuild.customiuizer.mods.utils

import android.os.Process
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Registry for preference observers.
 *
 * Process-scoped observers are held strongly and live for the lifetime of the process.
 * Owned observers are held weakly and strongly referenced only by the owner's additional
 * instance field, so they are dropped when the owner is garbage collected or explicitly
 * unregistered.
 */
object PreferenceObserverRegistry {

    interface PreferenceObserver {
        fun onChange(key: String?)
    }

    /** Process-scoped observers. Owned by module singletons, never collected. */
    private val observers = CopyOnWriteArraySet<PreferenceObserver>()

    /**
     * Observers whose lifetime is bound to a hooked object.
     *
     * The strong reference lives in the owner's additional instance field, which
     * [XposedHelpers] keeps in a `WeakHashMap`. Holding only a weak reference here means a
     * recreated hook target (theme change, density change, panel rebuild) drops its old
     * observer instead of pinning the dead instance for the life of the process.
     */
    private val observerOwners = CopyOnWriteArrayList<WeakReference<PreferenceObserver>>()
    private const val PREF_OBSERVER_FIELD = "customiuizer_prefObserver"

    /** Process name for diagnostics, falling back to the package or PID. */
    internal fun processName(): String = HookDiagnostics.currentProcessName
        ?: ModuleHelper.currentPackageName
        ?: Process.myPid().toString()

    fun observePreferenceChange(prefObserver: PreferenceObserver?) {
        if (prefObserver != null) observers.add(prefObserver)
    }

    fun observePreferenceChange(prefObserver: PreferenceObserver?, owner: Any?) {
        if (prefObserver == null) return
        if (owner == null) {
            observePreferenceChange(prefObserver)
            return
        }
        val old = XposedHelpers.getAdditionalInstanceField(owner, PREF_OBSERVER_FIELD)
        if (old is PreferenceObserver) {
            dropOwnedObserver(old)
        }
        XposedHelpers.setAdditionalInstanceField(owner, PREF_OBSERVER_FIELD, prefObserver)
        try {
            observerOwners.add(WeakReference(prefObserver))
        } catch (oom: OutOfMemoryError) {
            XposedHelpers.removeAdditionalInstanceField(owner, PREF_OBSERVER_FIELD)
            throw oom
        }
    }

    /**
     * Unregisters the observer bound to [owner].
     */
    fun unregisterPreferenceObserver(owner: Any?) = removeObserversForOwner(owner)

    private fun removeObserversForOwner(owner: Any?) {
        val old = XposedHelpers.removeAdditionalInstanceField(owner, PREF_OBSERVER_FIELD)
        if (old is PreferenceObserver) {
            dropOwnedObserver(old)
        }
    }

    /**
     * Removes [observer] and every reference the garbage collector has already cleared.
     *
     * Uses [CopyOnWriteArrayList.removeIf], which performs one atomic array copy. The Kotlin
     * `removeAll { }` extension would instead walk the list with indexed writes, copying the
     * backing array once per removal and without atomicity.
     */
    private fun dropOwnedObserver(observer: PreferenceObserver?) {
        observerOwners.removeIf { ref ->
            val referent = ref.get()
            referent == null || referent === observer
        }
    }

    /**
     * Fans a preference change out to every observer.
     *
     * Runs on the remote-preferences listener thread of system_server, SystemUI and Launcher.
     * A throwing observer must neither kill that process nor stop the remaining observers from
     * seeing the change, so each callback is isolated.
     */
    fun handlePreferenceChanged(key: String?) {
        for (prefObserver in observers) {
            try {
                prefObserver.onChange(key)
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                XposedHelpers.log(t)
            }
        }
        if (observerOwners.isEmpty()) return
        var sawCleared = false
        for (ref in observerOwners) {
            val prefObserver = ref.get()
            if (prefObserver == null) {
                sawCleared = true
                continue
            }
            try {
                prefObserver.onChange(key)
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                XposedHelpers.log(t)
            }
        }
        if (sawCleared) dropOwnedObserver(null)
    }
}
