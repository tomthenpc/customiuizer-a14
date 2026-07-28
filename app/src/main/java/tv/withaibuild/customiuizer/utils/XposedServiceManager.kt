package tv.withaibuild.customiuizer.utils

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
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
    enum class State { UNKNOWN, BOUND, TIMED_OUT, DISCONNECTED }

    @JvmField
    @Volatile
    var state: State = State.UNKNOWN

    @JvmField
    var service: XposedService? = null

    @JvmField
    var remotePrefs: RemotePreferences? = null

    private var initialized = false
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
