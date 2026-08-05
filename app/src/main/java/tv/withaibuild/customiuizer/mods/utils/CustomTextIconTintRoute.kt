package tv.withaibuild.customiuizer.mods.utils

import android.view.View

/**
 * Per-View dark receiver registration for module-created type 91/92 text icons.
 *
 * The route uses [View.OnAttachStateChangeListener] to mirror the View lifecycle:
 * a View is registered with [DarkIconDispatcher] when attached and released when
 * detached. This matches the way SystemUI already manages its own dark receivers,
 * without duplicating the per-generation [StatusBarDisplayRegistry].
 *
 * Registrations are exact-once per View: calling [register] twice for the same View
 * is a no-op unless the View was previously released (for example after a full
 * detach/re-attach cycle). This is safe because the dispatcher itself is the
 * authoritative registry, and the listener keeps the View from being re-registered.
 *
 * All operations are expected to run on the SystemUI main looper.
 */
internal object CustomTextIconTintRoute {

    private val registrations = mutableMapOf<View, DarkTintRegistration>()

    /**
     * Register [view] with the ROM [DarkIconDispatcher] when it is attached, and
     * remove the registration when it is detached.
     *
     * The [route] string is used only for diagnostics (for example "left", "right").
     * [classLoader] is used to look up the ROM `DarkIconDispatcher` plugin instance.
     * A non-null [darkIconDispatcher] is used directly instead of resolving from
     * [classLoader], which is useful for tests and for callers that already have
     * the dispatcher in hand.
     */
    @JvmOverloads
    fun register(
        view: View,
        classLoader: ClassLoader,
        route: String,
        darkIconDispatcher: Any? = null,
    ) {
        val existing = synchronized(registrations) {
            val old = registrations[view]
            if (old != null && old.state.isActive) {
                return
            }
            if (old != null) {
                old.releaseSilently("superseded")
                registrations.remove(view)
            }
            val registration = DarkTintRegistration(view, classLoader, route, darkIconDispatcher)
            registrations[view] = registration
            registration
        }

        view.addOnAttachStateChangeListener(existing.listener)

        // If the View is already attached when we register, the listener will not
        // fire for the current attach; trigger it manually.
        if (view.isAttachedToWindow) {
            existing.attach()
        }

        if (existing.state.isActive) {
            XposedHelpers.log("CustomTextIconTintRoute: registered ${existing.describe()}")
        }
    }

    /**
     * Release every outstanding registration. This is a cold-path helper for feature
     * stop or process-level teardown. The listener is removed and the map is cleared.
     */
    fun releaseAll() {
        val snapshot: List<DarkTintRegistration>
        synchronized(registrations) {
            snapshot = registrations.values.toList()
            registrations.clear()
        }
        for (registration in snapshot) {
            registration.releaseSilently("release-all")
            try {
                registration.view.removeOnAttachStateChangeListener(registration.listener)
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                // The View may already be gone; do not let ordinary cleanup failures stop the stop.
            }
        }
    }

    /**
     * Test-only accessor: the number of View registrations currently tracked in memory.
     */
    fun trackedCount(): Int = synchronized(registrations) { registrations.size }

    private class DarkTintRegistration(
        val view: View,
        private val classLoader: ClassLoader,
        val route: String,
        initialDispatcher: Any?,
    ) {
        val state = DarkTintRegistrationState(view, route)
        private var darkIconDispatcher: Any? = initialDispatcher

        val listener = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                CallbackGuard.guarded { attach() }
            }

            override fun onViewDetachedFromWindow(v: View) {
                CallbackGuard.guarded { releaseSilently("view-detached") }
                v.removeOnAttachStateChangeListener(this)
            }
        }

        fun attach() {
            if (!state.canRegister()) return
            if (darkIconDispatcher == null) {
                darkIconDispatcher = try {
                    ModuleHelper.getDepInstance(classLoader, "com.android.systemui.plugins.DarkIconDispatcher")
                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    null
                }
            }
            val dispatcher = darkIconDispatcher
            if (dispatcher == null) {
                XposedHelpers.log("CustomTextIconTintRoute: no DarkIconDispatcher for ${describe()}")
                return
            }

            val registered = try {
                XposedHelpers.callMethod(dispatcher, "addDarkReceiver", view)
                true
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                false
            }

            state.register(registerFn = { registered })

            if (registered) {
                XposedHelpers.log("CustomTextIconTintRoute: attached ${describe()}")
            } else {
                XposedHelpers.log("CustomTextIconTintRoute: attach-failed ${describe()}")
            }
        }

        fun releaseSilently(reason: String) {
            val released = state.release {
                val dispatcher = darkIconDispatcher
                if (dispatcher != null) {
                    try {
                        XposedHelpers.callMethod(dispatcher, "removeDarkReceiver", view)
                    } catch (t: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(t)
                    }
                }
                synchronized(registrations) { registrations.remove(view) }
            }
            if (released) {
                XposedHelpers.log("CustomTextIconTintRoute: released ${describe()}; reason=$reason")
            }
        }

        fun describe(): String {
            val viewName = view.javaClass.name
            val identity = System.identityHashCode(view)
            return "$route/$viewName@$identity"
        }
    }
}
