package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Enforces a single authoritative owner for each physical touch gesture.
 *
 * Status Bar and Control Center share one arbiter so the same physical stream cannot
 * produce duplicated brightness, volume, double-tap or long-press side effects.
 */
class PhysicalGestureArbiter {

    data class Token(
        val downTime: Long,
        val deviceId: Int,
        val source: Int,
    )

    private val owners = mutableMapOf<Token, Int>()

    /**
     * Attempt to claim authority over [event] for [ownerId].
     *
     * Returns `true` if this owner is the first to acquire the physical token, or if it
     * already owns it.  Returns `false` if another owner has already claimed it.
     */
    fun tryAcquire(ownerId: Int, event: GestureEvent): Boolean {
        val token = tokenOf(event)
        val existing = owners[token]
        if (existing == null) {
            owners[token] = ownerId
            return true
        }
        return existing == ownerId
    }

    /**
     * Release the specific token held by [ownerId] for [event], if any.
     */
    fun release(ownerId: Int, event: GestureEvent) {
        val token = tokenOf(event)
        if (owners[token] == ownerId) {
            owners.remove(token)
        }
    }

    /**
     * Release all tokens held by [ownerId].  Called when the owner is detached or reset.
     */
    fun releaseOwner(ownerId: Int) {
        val iterator = owners.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value == ownerId) {
                iterator.remove()
            }
        }
    }

    /** Drop every held token, e.g. when the ClassLoader is torn down. */
    fun releaseAll() {
        owners.clear()
    }

    private fun tokenOf(event: GestureEvent): Token =
        Token(event.downTime, event.deviceId, event.source)
}
