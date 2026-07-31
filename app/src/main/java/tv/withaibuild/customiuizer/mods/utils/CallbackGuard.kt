package tv.withaibuild.customiuizer.mods.utils

/**
 * Zero-allocation wrappers for framework and listener callbacks.
 *
 * Framework-invoked callbacks run outside the [MethodHook] try/catch, so an
 * unhandled throw from one of them can kill the host process. These inline
 * helpers log the failure and continue, leaving the host's behavior unchanged.
 */
object CallbackGuard {

    /**
     * Runs [block], logging instead of propagating any failure.
     *
     * Framework-invoked callbacks — `Handler.handleMessage`, `BroadcastReceiver.onReceive`,
     * `ContentObserver.onChange`, `Runnable.run` — execute outside the [MethodHook] try/catch.
     * A throw there kills system_server, SystemUI or Launcher, so every such body is wrapped.
     * The function is inline: no object is allocated and no frame is added on the hot path.
     */
    inline fun guarded(block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            XposedHelpers.log(t)
        }
    }

    /**
     * [guarded] for a callback that has to return a value, such as `OnLongClickListener`.
     *
     * [fallback] is what the framework sees when the body fails, so it must be the answer
     * that leaves the host's own behavior intact — usually "not consumed".
     */
    inline fun <T> guarded(fallback: T, block: () -> T): T {
        return try {
            block()
        } catch (t: Throwable) {
            XposedHelpers.log(t)
            fallback
        }
    }
}
