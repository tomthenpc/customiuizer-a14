package tv.withaibuild.customiuizer.mods.utils

import android.content.SharedPreferences
import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Thread-safe, idempotent bootstrap for the process-local preference snapshot.
 *
 * The class manages the full lifecycle of [RemotePreferences]:
 *
 *     1. Obtain the remote [SharedPreferences].
 *     2. First [SharedPreferences.getAll] to get an initial snapshot.
 *     3. Register a single [OnSharedPreferenceChangeListener].
 *     4. Second [SharedPreferences.getAll] to cover changes made during the
 *        listener registration window.
 *     5. Publish the final snapshot to the [PrefMap] and transition to [State.LOADED]
 *        or [State.VALID_EMPTY].
 *
 * Retries are bounded, no thread is blocked with sleep/wait, and all state transitions
 * happen under a single lock.  The [PrefMap] itself is a [ConcurrentHashMap] so hot hook
 * paths read the snapshot without further synchronization.
 */
class PreferenceBootstrap private constructor(
    private val prefs: PrefMap,
    private val remoteSource: RemotePreferenceSource,
) {

    /**
     * Source of the remote [SharedPreferences].  This is a function because
     * [XposedModule.getRemotePreferences] is a protected method and must be supplied by the
     * module instance.
     *
     * The return type is nullable because a remote source may legitimately return null in some
     * error paths.
     */
    fun interface RemotePreferenceSource {
        fun get(name: String): SharedPreferences?
    }

    enum class State {
        UNINITIALIZED,
        UNAVAILABLE,
        EMPTY_PENDING,
        VALID_EMPTY,
        LOADED,
    }

    private val lock = Any()
    private val remoteName = ModuleHelper.prefsName + "_remote"

    @Volatile
    private var currentState = State.UNINITIALIZED

    private var remotePrefs: SharedPreferences? = null
    private var listener: SharedPreferences.OnSharedPreferenceChangeListener? = null

    @Volatile
    private var listenerRegistered = false

    private var initAttempts = 0
    private var emptyPendingAttempts = 0

    private var unavailableReported = false
    private var emptyPendingReported = false
    private var validEmptyReported = false

    companion object {
        const val MAX_PREF_INIT_ATTEMPTS = 5
        const val MAX_EMPTY_PENDING_ATTEMPTS = 3

        @JvmStatic
        fun create(prefs: PrefMap, source: RemotePreferenceSource): PreferenceBootstrap {
            return PreferenceBootstrap(prefs, source)
        }
    }

    /** The current bootstrap state.  Reads and writes are memory-visible without blocking. */
    fun getState(): State = currentState

    /** Whether the snapshot is safe to use for feature installation decisions. */
    fun isReady(): Boolean {
        val s = currentState
        return s == State.LOADED || s == State.VALID_EMPTY
    }

    /** Whether the live listener is registered. */
    fun isListenerRegistered(): Boolean = listenerRegistered

    /**
     * Attempt to load the snapshot.  This is safe to call repeatedly from any thread and is the
     * first step in both [onSystemServerStarting]/[onPackageReady] and the deferred SystemUI
     * initialization.
     */
    fun init() {
        synchronized(lock) {
            val s = currentState
            if (s == State.LOADED || s == State.VALID_EMPTY) return

            if (s == State.UNAVAILABLE && initAttempts >= MAX_PREF_INIT_ATTEMPTS) return

            if (s == State.EMPTY_PENDING) {
                if (emptyPendingAttempts >= MAX_EMPTY_PENDING_ATTEMPTS && !listenerRegistered) {
                    if (!emptyPendingReported) {
                        emptyPendingReported = true
                        XposedHelpers.log("Remote preferences empty-pending: retry limit reached, continuing without final state")
                    }
                    return
                }
                emptyPendingAttempts++
            }

            if (s == State.UNINITIALIZED || s == State.UNAVAILABLE) {
                // Count the attempt before making it so the retry budget limits the total number
                // of attempts, not total minus one.
                initAttempts++
                if (remotePrefs == null) {
                    try {
                        remotePrefs = remoteSource.get(remoteName)
                    } catch (t: Throwable) {
                        currentState = State.UNAVAILABLE
                        HookDiagnostics.recordPreferencesUnavailable(t.javaClass.name, "getRemotePreferences")
                        return
                    }
                }
                if (remotePrefs == null) {
                    currentState = State.UNAVAILABLE
                    HookDiagnostics.recordPreferencesUnavailable("", "getRemotePreferences returned null")
                    return
                }
            }

            loadSnapshotLocked()
        }
    }

    /**
     * Register the live preference listener.  The registration is idempotent: once it succeeds,
     * subsequent calls return [true] without re-registering.
     *
     * After the listener is registered, a second snapshot is loaded immediately so that any
     * preference changes that occurred between the first snapshot and the listener registration
     * are not lost.
     */
    fun installListener(): Boolean {
        synchronized(lock) {
            if (listenerRegistered) return true

            if (remotePrefs == null) {
                try {
                    remotePrefs = remoteSource.get(remoteName)
                } catch (t: Throwable) {
                    HookDiagnostics.recordPreferencesUnavailable(t.javaClass.name, "getRemotePreferences")
                    return false
                }
            }
            if (remotePrefs == null) {
                HookDiagnostics.recordPreferencesUnavailable("", "getRemotePreferences returned null")
                return false
            }

            val newListener = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
                onPreferenceChanged(sharedPreferences, key)
            }
            listener = newListener

            return try {
                remotePrefs!!.registerOnSharedPreferenceChangeListener(newListener)
                listenerRegistered = true
                // A live watcher is in place. Reset retry counters so a later [init] can use
                // [listenerRegistered] as a readiness signal.
                initAttempts = 0
                emptyPendingAttempts = 0
                // Second snapshot: cover the listener-registration window.
                loadSnapshotLocked()
                true
            } catch (t: Throwable) {
                HookDiagnostics.recordPreferencesUnavailable(t.javaClass.name, "registerOnSharedPreferenceChangeListener")
                false
            }
        }
    }

    /**
     * Replace the published snapshot with [allPrefs].  Called while holding [lock].
     */
    private fun loadSnapshotLocked() {
        val allPrefs = try {
            remotePrefs!!.getAll()
        } catch (t: Throwable) {
            currentState = State.UNAVAILABLE
            HookDiagnostics.recordPreferencesUnavailable(t.javaClass.name, "getAll")
            return
        }

        if (allPrefs == null) {
            currentState = State.UNAVAILABLE
            if (!unavailableReported) {
                unavailableReported = true
                XposedHelpers.log("Remote preferences unavailable: getAll returned null")
            }
            return
        }

        if (allPrefs.isEmpty()) {
            if (listenerRegistered) {
                currentState = State.VALID_EMPTY
                prefs.clear()
                if (!validEmptyReported) {
                    validEmptyReported = true
                    XposedHelpers.log("Remote preferences are valid but empty (watcher confirmed)")
                }
            } else {
                currentState = State.EMPTY_PENDING
                HookDiagnostics.recordPreferencesEmptyPending()
                if (!emptyPendingReported) {
                    emptyPendingReported = true
                    XposedHelpers.log("Remote preferences empty-pending: provider reachable but map is empty")
                }
            }
            return
        }

        publishSnapshot(allPrefs)
        currentState = State.LOADED
    }

    /**
     * Publish [allPrefs] into the process-local [PrefMap].  Null values are skipped because
     * [PrefMap.put] requires a non-null [Any].
     */
    private fun publishSnapshot(allPrefs: Map<String, *>) {
        prefs.clear()
        for ((key, value) in allPrefs) {
            if (value != null) {
                prefs.put(key, value)
            }
        }
    }

    /**
     * Listener callback.  Runs on the remote-preference binder thread, so it updates the
     * [PrefMap] directly and fans the change out to observers.  Each observer is isolated to
     * keep one failing observer from taking down the host process.
     */
    private fun onPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        if (sharedPreferences == null || key == null) return
        try {
            val oldValue = prefs.get(key)
            val value = if (sharedPreferences.contains(key)) {
                when (oldValue) {
                    is Boolean -> sharedPreferences.getBoolean(key, false)
                    is Int -> sharedPreferences.getInt(key, 0)
                    is Long -> sharedPreferences.getLong(key, 0L)
                    is Float -> sharedPreferences.getFloat(key, 0f)
                    is String -> sharedPreferences.getString(key, null)
                    is Set<*> -> sharedPreferences.getStringSet(key, null)
                    else -> sharedPreferences.all?.get(key)
                }
            } else {
                null
            }

            if (value == null) {
                prefs.remove(key)
            } else {
                prefs.put(key, value)
            }

            if (key != "pref_key_systemui_restart_time") {
                ModuleHelper.handlePreferenceChanged(key)
            }
        } catch (t: Throwable) {
            XposedHelpers.log(t)
        }
    }
}
