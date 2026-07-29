package tv.withaibuild.customiuizer.utils

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import io.github.libxposed.service.RemotePreferences
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper

/**
 * Single application-level owner for the LSPosed/Vector service connection.
 *
 * UI code must not treat a provisional state as "not active": see [State].
 * [AppHelper.moduleActive] is kept in sync for existing call sites.
 */
object XposedServiceManager {

    /**
     * - [UNKNOWN]: registration attempted, no callback yet.
     * - [BOUND]: the service is connected.
     * - [TIMED_OUT]: we stopped waiting. **This is not proof of anything.** The service can
     *   still bind afterwards, and on a device that has just restarted this process it often
     *   does — a captured log showed LSPosed service transactions taking up to 1.7 s while
     *   the settings app was being killed and relaunched repeatedly.
     * - [DISCONNECTED]: `onServiceDied`, or registration threw. A proven negative.
     *
     * Only [DISCONNECTED] justifies telling the user the module is not active without
     * further waiting.
     */
    enum class State {
        UNKNOWN, BOUND, TIMED_OUT, DISCONNECTED;

        /**
         * True while the state is still only provisional, i.e. the bind may yet succeed.
         *
         * [TIMED_OUT] is provisional for the same reason [UNKNOWN] is: nothing has been
         * observed. A caller that waits only while the state is [UNKNOWN] stops the moment
         * the timeout fires and then reads [TIMED_OUT] as a negative — the exact
         * misjudgement that made "module not active" appear after a language change
         * restarted this process.
         */
        val isProvisional: Boolean
            get() = this == UNKNOWN || this == TIMED_OUT
    }

    /**
     * Whether the module may be reported as not connected to the user.
     *
     * Only [State.DISCONNECTED] is a proven negative. [State.TIMED_OUT] qualifies only once
     * [FULL_DECISION_BUDGET_MS] has elapsed since [init], because at that point a caller
     * that keeps silent would never report a module that really is inactive.
     *
     * That elapsed check is deliberately owned here rather than passed in by callers. It
     * used to be a `bindStillPending` flag, and the very first caller added after the
     * flag - the soft-reboot menu item - passed `true` without waiting for anything,
     * reintroducing the misjudgement the flag existed to prevent.
     */
    @JvmStatic
    @JvmOverloads
    fun shouldReportInactive(current: State = state): Boolean =
        when (current) {
            State.DISCONNECTED -> true
            State.TIMED_OUT -> decisionBudgetElapsed()
            // UNKNOWN means the timeout has not even fired yet, so nothing has been
            // observed and there is nothing to report.
            State.UNKNOWN, State.BOUND -> false
        }

    /**
     * Whether the bind state can still change the answer [shouldReportInactive] gives.
     *
     * A caller that is about to act on the state should wait for this, not for the state
     * to stop being [State.isProvisional] - a bind that never arrives leaves the state
     * provisional forever, and this is what puts a bound on the wait.
     */
    @JvmStatic
    fun isDecided(): Boolean = !state.isProvisional || decisionBudgetElapsed()

    /**
     * Whether enough time has passed since [init] that a still-pending bind counts against
     * the module.
     *
     * Measured from the registration attempt with [SystemClock.elapsedRealtime], so it is
     * unaffected by the wall clock and by how long any particular screen has been open.
     * Before [init] runs there is nothing to time, and nothing has been observed either,
     * so the budget has not elapsed.
     */
    @JvmStatic
    fun decisionBudgetElapsed(): Boolean {
        val started = initElapsedRealtime
        if (started == NOT_STARTED) return false
        return SystemClock.elapsedRealtime() - started >= FULL_DECISION_BUDGET_MS
    }

    @JvmField
    @Volatile
    var state: State = State.UNKNOWN

    @JvmField
    var service: XposedService? = null

    @JvmField
    var remotePrefs: RemotePreferences? = null

    private const val NOT_STARTED = 0L

    private var initialized = false

    /** [SystemClock.elapsedRealtime] at [init], or [NOT_STARTED]. */
    @Volatile
    private var initElapsedRealtime = NOT_STARTED

    private val handler = Handler(Looper.getMainLooper())

    /**
     * Upper bound on how long [state] may stay [State.UNKNOWN].
     *
     * A caller that needs a decided state must not give up before this. It used to pick
     * its own, shorter deadline, so it stopped waiting while the state was still UNKNOWN
     * and drew no conclusion; the "module not active" dialog then appeared at whatever
     * arbitrary later moment the screen was next entered — most visibly right after the
     * cold restart that a language change forces, which is exactly when binding is
     * slowest.
     */
    const val BIND_DECISION_TIMEOUT_MS = 3500L

    /**
     * Total time from [init] before a still-pending bind is allowed to count against the
     * module. Two bind windows plus a margin: the first covers an ordinary start, the
     * second covers a bind still in flight after a process restart - which is what a
     * language change forces, and when binding is slowest.
     *
     * This lives here, not in a screen, so that every caller answers the question the same
     * way regardless of when it happened to open.
     */
    const val FULL_DECISION_BUDGET_MS = BIND_DECISION_TIMEOUT_MS * 2 + 500L

    private val timeoutRunnable = Runnable {
        if (state == State.UNKNOWN) {
            // Give up waiting, but do not claim the module is inactive: the listener stays
            // registered and a later bind still promotes this to BOUND.
            state = State.TIMED_OUT
            AppHelper.moduleActive = false
        }
    }

    private val IGNORE_KEYS = setOf(
        "pref_key_miuizer_locale",
        "pref_key_miuizer_locale_applied",
        "pref_key_miuizer_launchericon",
        "pref_key_miuizer_synced_from_lsposed"
    )

    private val prefsChanged = SharedPreferences.OnSharedPreferenceChangeListener { sharedPreferences, key ->
        val remote = remotePrefs ?: return@OnSharedPreferenceChangeListener

        if (key == null) {
            val edit = remote.edit()
            for (remoteKey in remote.all.keys) edit.remove(remoteKey)
            edit.apply()
            return@OnSharedPreferenceChangeListener
        }

        if (IGNORE_KEYS.contains(key)) return@OnSharedPreferenceChangeListener

        val value = sharedPreferences.all[key] ?: run {
            remote.edit().remove(key).apply()
            return@OnSharedPreferenceChangeListener
        }

        val edit = remote.edit()
        when (value) {
            is Boolean -> edit.putBoolean(key, value)
            is Float -> edit.putFloat(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is String -> edit.putString(key, value)
            is Set<*> -> @Suppress("UNCHECKED_CAST") edit.putStringSet(key, value as Set<String>)
            is MutableSet<*> -> @Suppress("UNCHECKED_CAST") edit.putStringSet(key, value as Set<String>)
        }
        edit.apply()
    }

    @JvmStatic
    @Synchronized
    fun init(appPrefs: SharedPreferences?) {
        if (initialized) return
        initialized = true
        initElapsedRealtime = SystemClock.elapsedRealtime()

        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, BIND_DECISION_TIMEOUT_MS)

        try {
            XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
                override fun onServiceBind(s: XposedService) {
                    handler.removeCallbacks(timeoutRunnable)
                    state = State.BOUND
                    service = s
                    remotePrefs = s.getRemotePreferences(AppHelper.prefsName + "_remote") as RemotePreferences
                    AppHelper.remotePrefs = remotePrefs
                    AppHelper.moduleActive = true
                    appPrefs?.registerOnSharedPreferenceChangeListener(prefsChanged)
                }

                override fun onServiceDied(s: XposedService) {
                    handler.removeCallbacks(timeoutRunnable)
                    state = State.DISCONNECTED
                    service = null
                    remotePrefs = null
                    AppHelper.remotePrefs = null
                    AppHelper.moduleActive = false
                    try {
                        appPrefs?.unregisterOnSharedPreferenceChangeListener(prefsChanged)
                    } catch (_: Throwable) {
                    }
                }
            })
        } catch (t: Throwable) {
            handler.removeCallbacks(timeoutRunnable)
            state = State.DISCONNECTED
            AppHelper.moduleActive = false
        }
    }
}
