package tv.withaibuild.customiuizer.mods.utils

/**
 * Framework-free state machine for a single dark-tint receiver registration.
 *
 * It tracks the lifecycle of one registration attempt: registered, attached, released.
 * The caller is responsible for the actual [registerFn]/[releaseFn] side effects; this
 * class only enforces the exact-once and idempotency invariants, which makes it
 * unit-testable without Android Views or ROM classes.
 */
internal class DarkTintRegistrationState(
    val owner: Any,
    val route: String,
) {

    private var registered: Boolean = false
    private var released: Boolean = false

    /**
     * True if the registration is currently live and the owner should be receiving
     * dark callbacks. A registration that was released and then re-registered is
     * considered a new lifecycle.
     */
    val isActive: Boolean get() = registered && !released

    /**
     * True if this state machine has been released at least once.
     */
    val isReleased: Boolean get() = released

    /**
     * True if this state is still eligible for a new registration.
     */
    fun canRegister(): Boolean = !registered && !released

    /**
     * Attempt to register. Calls [registerFn] at most once; if [applyInitialTint] is
     * non-null it is also called once after a successful registration.
     *
     * Returns the result of [registerFn] if registration happened, false if it was
     * already registered or already released.
     */
    fun register(
        registerFn: () -> Boolean,
        applyInitialTint: (() -> Boolean)? = null,
    ): Boolean {
        if (registered || released) return false
        val ok = registerFn()
        if (ok) {
            registered = true
            applyInitialTint?.invoke()
        }
        return ok
    }

    /**
     * Release the registration by calling [releaseFn] if the registration is active.
     * Idempotent: subsequent calls return false and do not re-run [releaseFn].
     */
    fun release(releaseFn: () -> Unit): Boolean {
        if (released) return false
        released = true
        if (registered) {
            registered = false
            releaseFn()
            return true
        }
        return false
    }

    /**
     * Reset to a clean state so the same owner can be re-registered.
     * This is only used after a confirmed release in tests or in an explicit
     * "replace" path where the caller knows the old registration is gone.
     */
    fun reset() {
        registered = false
        released = false
    }

    override fun toString(): String {
        return "DarkTintRegistrationState(owner=$owner, route=$route, registered=$registered, released=$released)"
    }
}
