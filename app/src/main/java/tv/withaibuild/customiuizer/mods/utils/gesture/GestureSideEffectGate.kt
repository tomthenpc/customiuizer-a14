package tv.withaibuild.customiuizer.mods.utils.gesture

/**
 * Deduplicates business side-effects of a physical touch event across multiple
 * MotionEvent entry points.
 *
 * The gate is not responsible for state transitions; it only decides which commands
 * from the pure state machine are allowed to reach the side-effect executor.
 */
class GestureSideEffectGate(
    private val maxFingerprints: Int = 32,
) {

    private val order = ArrayDeque<GestureEventFingerprint>(maxFingerprints)
    private val seen = LinkedHashSet<GestureEventFingerprint>(maxFingerprints)

    /**
     * Entries that are allowed to update state and execute business side-effects.
     *
     * `STATUS_BAR_INTERCEPT` is intentionally absent: it may observe the event but must
     * defer state changes and side-effects to `STATUS_BAR_TOUCH`.
     */
    private val effectEntries: Set<GestureEntry> = setOf(
        GestureEntry.STATUS_BAR_TOUCH,
        GestureEntry.CONTROL_CENTER_TOUCH,
    )

    private fun isBusinessEffect(command: GestureCommand): Boolean = when (command) {
        is GestureCommand.ApplyTemporaryBrightness,
        is GestureCommand.AdjustVolume,
        is GestureCommand.CommitBrightness,
        is GestureCommand.TriggerDoubleTap,
        GestureCommand.TriggerLongPress -> true
        else -> false
    }

    private fun fingerprint(event: GestureEvent): GestureEventFingerprint =
        GestureEventFingerprint(
            downTime = event.downTime,
            eventTime = event.eventTime,
            actionMasked = event.actionMasked,
            pointerCount = event.pointerCount,
            deviceId = event.deviceId,
            source = event.source,
        )

    /**
     * Filters [commands] for the given [entry] and [event].
     *
     * Returns the commands that are allowed to execute.  Business side-effects are
     * dropped for non-effect entries and deduplicated by physical event identity.
     */
    fun filter(
        entry: GestureEntry,
        event: GestureEvent,
        commands: List<GestureCommand>,
    ): List<GestureCommand> {
        if (commands.isEmpty()) return commands
        if (entry !in effectEntries) return emptyList()

        val business = commands.filter(::isBusinessEffect)
        if (business.isEmpty()) return commands

        val fp = fingerprint(event)
        if (fp in seen) return emptyList()

        if (order.size >= maxFingerprints) {
            val oldest = order.removeFirst()
            seen.remove(oldest)
        }
        order.addLast(fp)
        seen.add(fp)

        return commands
    }

    /** Clears all stored fingerprints, e.g. when the owner is destroyed. */
    fun clear() {
        order.clear()
        seen.clear()
    }
}
