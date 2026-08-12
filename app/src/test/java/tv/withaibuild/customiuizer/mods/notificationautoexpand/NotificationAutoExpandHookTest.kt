package tv.withaibuild.customiuizer.mods.notificationautoexpand

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.HookDiagnostics
import tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry

/**
 * Component tests for [NotificationAutoExpandHook].
 *
 * Evidence classification: RUNTIME_TESTED_COMPONENT.
 */
class NotificationAutoExpandHookTest {

    @Before
    fun setUp() {
        NotificationAutoExpandRuntimeState.resetForTest()
        PreferenceObserverRegistry.resetForTest()
        HookDiagnostics.reset()
    }

    @After
    fun tearDown() {
        NotificationAutoExpandRuntimeState.resetForTest()
        PreferenceObserverRegistry.resetForTest()
        HookDiagnostics.reset()
    }

    @Test
    fun install_targetClassMissing_doesNotCreateRuntimeState() {
        val missingLoader = object : ClassLoader(this.javaClass.classLoader) {
            override fun loadClass(name: String?, resolve: Boolean): Class<*> {
                if (name == "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow") {
                    throw ClassNotFoundException("simulated missing target class")
                }
                return super.loadClass(name, resolve)
            }
        }

        NotificationAutoExpandHook.install(missingLoader)

        assertFalse(
            "Runtime state must not be installed when target class is missing",
            NotificationAutoExpandRuntimeState.isInstalled(),
        )
    }

    @Test
    fun install_ordinaryProbeFailure_doesNotCreateRuntimeStateAndRecordsFailure() {
        val failingLoader = object : ClassLoader(this.javaClass.classLoader) {
            override fun loadClass(name: String?, resolve: Boolean): Class<*> {
                if (name == "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow") {
                    throw IllegalStateException("simulated ordinary class-loading failure")
                }
                return super.loadClass(name, resolve)
            }
        }

        var thrown: Throwable? = null
        try {
            NotificationAutoExpandHook.install(failingLoader)
        } catch (t: Throwable) {
            thrown = t
        }

        assertEquals(
            "Ordinary class-loading failure must not be swallowed by the pre-probe",
            null,
            thrown,
        )
        assertFalse(
            "Runtime state must not be installed when the target probe fails ordinarily",
            NotificationAutoExpandRuntimeState.isInstalled(),
        )

        val record = HookDiagnostics.snapshot().find {
            it.targetClass == "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow" &&
                it.targetMember == "setFeedbackIcon"
        }
        assertNotNull("Hook diagnostics must record the install failure", record)
        assertEquals(
            "Probe failure must be recorded as ALL_METHODS / INSTALL_FAILED",
            HookDiagnostics.Status.INSTALL_FAILED,
            record!!.status,
        )
        assertEquals(
            "Exception type must identify the ordinary failure",
            "IllegalStateException",
            record.exceptionType,
        )
    }

    @Test
    fun install_fatalProbeFailure_rethrowsAndDoesNotCreateRuntimeState() {
        val fatalLoader = object : ClassLoader(this.javaClass.classLoader) {
            override fun loadClass(name: String?, resolve: Boolean): Class<*> {
                if (name == "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow") {
                    throw OutOfMemoryError("simulated fatal class-loading failure")
                }
                return super.loadClass(name, resolve)
            }
        }

        var thrown: Throwable? = null
        try {
            NotificationAutoExpandHook.install(fatalLoader)
        } catch (t: Throwable) {
            thrown = t
        }

        assertTrue("Fatal class-loading failure must propagate", thrown is OutOfMemoryError)
        assertFalse(
            "Runtime state must not be installed after a fatal failure",
            NotificationAutoExpandRuntimeState.isInstalled(),
        )
    }
}
