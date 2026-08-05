package tv.withaibuild.customiuizer.mods.utils

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Process-scoped state for the battery / temperature monitor.
 *
 * The monitor is tied to the lifetime of a single `NetworkSpeedController` instance. When
 * SystemUI recreates the controller, a new generation starts and the previous generation's
 * handlers must stop scheduling ticks and stop posting UI updates.
 *
 * This class has no Android dependencies and is unit-testable.
 */
internal class DeviceInfoMonitorState {

    private val handlerIdGenerator = AtomicLong(0L)

    @Volatile
    var activeBgHandlerId: Long = -1
        private set

    @Volatile
    var activeMainHandlerId: Long = -1
        private set

    @Volatile
    var screenOn: Boolean = true

    val consecutiveFailCount = AtomicInteger(0)

    /**
     * Start a new handler generation. Returns the id that the new background and main handlers
     * must carry. Any message from a handler with a different id is stale and must be ignored.
     */
    fun startNewGeneration(): Long {
        val next = handlerIdGenerator.incrementAndGet()
        activeBgHandlerId = next
        activeMainHandlerId = next
        return next
    }

    /**
     * Returns true if [handlerId] is the current generation's id.
     */
    fun isActive(handlerId: Long): Boolean {
        return handlerId == activeBgHandlerId || handlerId == activeMainHandlerId
    }

    /**
     * Returns true if [handlerId] is the current background handler id.
     */
    fun isActiveBg(handlerId: Long): Boolean = handlerId == activeBgHandlerId

    /**
     * Returns true if [handlerId] is the current main handler id.
     */
    fun isActiveMain(handlerId: Long): Boolean = handlerId == activeMainHandlerId

    /**
     * Stop the monitor. All current handler ids become inactive; any message from an old handler
     * is dropped.
     */
    fun stop() {
        activeBgHandlerId = -1
        activeMainHandlerId = -1
    }

    /**
     * Reset the failure back-off count.
     */
    fun resetFailCount() {
        consecutiveFailCount.set(0)
    }

    /**
     * Increment the failure back-off count.
     */
    fun bumpFailCount() {
        consecutiveFailCount.incrementAndGet()
    }

    /**
     * Compute the next tick delay based on the current failure back-off count.
     */
    fun calculateDelay(baseMs: Long, maxMs: Long): Long {
        val count = consecutiveFailCount.get()
        if (count <= 0) return baseMs
        val multiplier = 1L shl count.coerceAtMost(5)
        return (baseMs * multiplier).coerceAtMost(maxMs)
    }
}
