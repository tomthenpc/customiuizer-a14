package tv.withaibuild.customiuizer.mods.notificationautoexpand

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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
    }

    @After
    fun tearDown() {
        NotificationAutoExpandRuntimeState.resetForTest()
        PreferenceObserverRegistry.resetForTest()
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

}
