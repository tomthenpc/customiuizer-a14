package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.utils.XposedServiceManager.State

/**
 * The bind state is what decides whether the user is told the module is not active.
 *
 * The regression these cover: a language change kills and relaunches the settings
 * process, the LSPosed bind then takes longer than the timeout, and a waiter that
 * treated TIMED_OUT as decided stopped waiting and reported a false negative.
 */
class XposedBindStateTest {

    @Test
    fun unknownIsProvisional() {
        assertTrue(State.UNKNOWN.isProvisional)
    }

    @Test
    fun timedOutIsProvisional() {
        // The whole point: the timeout fired, but nothing was observed. A waiter must
        // keep waiting rather than stop here and read it as a negative.
        assertTrue(State.TIMED_OUT.isProvisional)
    }

    @Test
    fun boundAndDisconnectedAreDecided() {
        assertFalse(State.BOUND.isProvisional)
        assertFalse(State.DISCONNECTED.isProvisional)
    }

    @Test
    fun onlyDisconnectedReportsInactiveWithoutWaiting() {
        assertTrue(XposedServiceManager.shouldReportInactive(State.DISCONNECTED))
        assertFalse(XposedServiceManager.shouldReportInactive(State.TIMED_OUT))
        assertFalse(XposedServiceManager.shouldReportInactive(State.UNKNOWN))
        assertFalse(XposedServiceManager.shouldReportInactive(State.BOUND))
    }

    @Test
    fun timedOutReportsInactiveOnlyAfterTheWholeBudgetIsSpent() {
        assertFalse(XposedServiceManager.shouldReportInactive(State.TIMED_OUT, bindStillPending = false))
        assertTrue(XposedServiceManager.shouldReportInactive(State.TIMED_OUT, bindStillPending = true))
    }

    @Test
    fun aSpentBudgetNeverTurnsBoundIntoAnInactiveReport() {
        assertFalse(XposedServiceManager.shouldReportInactive(State.BOUND, bindStillPending = true))
    }

    @Test
    fun unknownIsNeverReportedInactive() {
        // UNKNOWN can only survive a completed wait if the state machine was never
        // started; that is not evidence of an inactive module either.
        assertFalse(XposedServiceManager.shouldReportInactive(State.UNKNOWN, bindStillPending = true))
    }
}
