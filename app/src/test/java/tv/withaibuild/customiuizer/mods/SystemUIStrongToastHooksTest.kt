package tv.withaibuild.customiuizer.mods

import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.StrongToastPosition
import tv.withaibuild.customiuizer.mods.utils.StrongToastPresentationMode
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastRuntimeSnapshot
import tv.withaibuild.customiuizer.mods.utils.feature.StrongToastRuntimeState
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.reflect.Proxy
import java.util.concurrent.atomic.AtomicReference

class SystemUIStrongToastHooksTest {

    class FakeKeyguard {
        @JvmField
        var mShowing: Boolean = true
    }

    class FakeControl {
        @JvmField
        var mKeyguardStateController: FakeKeyguard = FakeKeyguard()
    }

    @After
    fun tearDown() {
        StrongToastRuntimeState.instance?.let { state ->
            tv.withaibuild.customiuizer.mods.utils.PreferenceObserverRegistry.observers.remove(state.preferenceObserver)
        }
        StrongToastRuntimeState.instance = null
        StrongToastRuntimeState.installed = false
        SystemUIStrongToastHooks.snapshotRef = null
        SystemUIStrongToastHooks.installed = false
    }

    private fun fakePackageReadyParam(): XposedModuleInterface.PackageReadyParam {
        return Proxy.newProxyInstance(
            XposedModuleInterface.PackageReadyParam::class.java.classLoader,
            arrayOf(XposedModuleInterface.PackageReadyParam::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getPackageName" -> "com.android.systemui"
                "getClassLoader" -> ClassLoader.getSystemClassLoader()
                "getApplicationInfo" -> null
                else -> null
            }
        } as XposedModuleInterface.PackageReadyParam
    }

    private fun fakeChain(thisObject: Any, onProceed: () -> Any? = { null }): XposedInterface.Chain {
        return Proxy.newProxyInstance(
            XposedInterface.Chain::class.java.classLoader,
            arrayOf(XposedInterface.Chain::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getThisObject" -> thisObject
                "proceed" -> onProceed()
                "getExecutable" -> null
                "getArgs" -> emptyList<Any>()
                "getArg" -> null
                "proceedWith" -> null
                "equals" -> false
                "hashCode" -> java.lang.System.identityHashCode(this)
                "toString" -> "FakeChain"
                else -> null
            }
        } as XposedInterface.Chain
    }

    private fun setDynamicIslandSnapshot() {
        SystemUIStrongToastHooks.snapshotRef =
            AtomicReference(StrongToastRuntimeSnapshot(StrongToastPresentationMode.DYNAMIC_ISLAND, StrongToastPosition.TOP, 0))
    }

    private fun setHideSnapshot() {
        SystemUIStrongToastHooks.snapshotRef =
            AtomicReference(StrongToastRuntimeSnapshot(StrongToastPresentationMode.HIDE, StrongToastPosition.TOP, 0))
    }

    private fun setSystemDefaultSnapshot() {
        SystemUIStrongToastHooks.snapshotRef =
            AtomicReference(StrongToastRuntimeSnapshot(StrongToastPresentationMode.SYSTEM_DEFAULT, StrongToastPosition.TOP, 0))
    }

    @Test
    fun install_setsInstalledFlag_andReusesExistingInstallation() {
        val lpparam = fakePackageReadyParam()
        val snapshot = AtomicReference(StrongToastRuntimeSnapshot(StrongToastPresentationMode.DYNAMIC_ISLAND, StrongToastPosition.TOP, 0))

        SystemUIStrongToastHooks.install(lpparam, snapshot)

        assertTrue(SystemUIStrongToastHooks.installed)
        assertSame(snapshot, SystemUIStrongToastHooks.snapshotRef)

        val anotherSnapshot = AtomicReference(StrongToastRuntimeSnapshot(StrongToastPresentationMode.HIDE, StrongToastPosition.TOP, 0))
        SystemUIStrongToastHooks.install(lpparam, anotherSnapshot)

        // A second call does not try to install the hooks again.
        assertTrue(SystemUIStrongToastHooks.installed)
    }

    @Test
    fun controlHook_systemDefault_proceedsOnce() {
        setSystemDefaultSnapshot()
        val control = FakeControl()
        val controllerField = FakeControl::class.java.getDeclaredField("mKeyguardStateController")
        val showingField = FakeKeyguard::class.java.getDeclaredField("mShowing")
        val hook = SystemUIStrongToastHooks.StrongToastControlHook(
            controllerField,
            showingField,
            null,
            true
        )

        var proceedCount = 0
        val chain = fakeChain(control) { proceedCount++; null }

        assertNull(hook.intercept(chain))
        assertEquals(1, proceedCount)
        assertTrue(control.mKeyguardStateController.mShowing)
    }

    @Test
    fun controlHook_hideWithAllowHide_skipsAndDoesNotProceed() {
        setHideSnapshot()
        val control = FakeControl()
        val controllerField = FakeControl::class.java.getDeclaredField("mKeyguardStateController")
        val showingField = FakeKeyguard::class.java.getDeclaredField("mShowing")
        val hook = SystemUIStrongToastHooks.StrongToastControlHook(
            controllerField,
            showingField,
            null,
            true
        )

        var proceedCount = 0
        val chain = fakeChain(control) { proceedCount++; null }

        assertNull(hook.intercept(chain))
        assertEquals(0, proceedCount)
        assertTrue(control.mKeyguardStateController.mShowing)
    }

    @Test
    fun controlHook_hideWithoutAllowHide_proceedsOnce() {
        setHideSnapshot()
        val control = FakeControl()
        val controllerField = FakeControl::class.java.getDeclaredField("mKeyguardStateController")
        val showingField = FakeKeyguard::class.java.getDeclaredField("mShowing")
        val hook = SystemUIStrongToastHooks.StrongToastControlHook(
            controllerField,
            showingField,
            null,
            false
        )

        var proceedCount = 0
        val chain = fakeChain(control) { proceedCount++; null }

        assertNull(hook.intercept(chain))
        assertEquals(1, proceedCount)
        assertTrue(control.mKeyguardStateController.mShowing)
    }

    @Test
    fun controlHook_dynamicIsland_opensAndClosesGate() {
        setDynamicIslandSnapshot()
        val control = FakeControl()
        val controllerField = FakeControl::class.java.getDeclaredField("mKeyguardStateController")
        val showingField = FakeKeyguard::class.java.getDeclaredField("mShowing")
        val hook = SystemUIStrongToastHooks.StrongToastControlHook(
            controllerField,
            showingField,
            null,
            true
        )

        var proceedCount = 0
        val chain = fakeChain(control) { proceedCount++; null }

        assertNull(hook.intercept(chain))
        assertEquals(1, proceedCount)
        assertTrue(control.mKeyguardStateController.mShowing)
    }

    @Test
    fun controlHook_nestedDynamicIsland_usesLifoGateOwnership() {
        setDynamicIslandSnapshot()
        val control = FakeControl()
        val controllerField = FakeControl::class.java.getDeclaredField("mKeyguardStateController")
        val showingField = FakeKeyguard::class.java.getDeclaredField("mShowing")
        val outerHook = SystemUIStrongToastHooks.StrongToastControlHook(
            controllerField,
            showingField,
            null,
            true
        )
        val innerHook = SystemUIStrongToastHooks.StrongToastControlHook(
            controllerField,
            showingField,
            null,
            true
        )

        var outerProceed = 0
        var innerProceed = 0

        val innerChain = fakeChain(control) {
            innerProceed++
            assertFalse("inner should see gate already open", control.mKeyguardStateController.mShowing)
            null
        }

        val outerChain = fakeChain(control) {
            outerProceed++
            assertFalse("outer should have opened the gate", control.mKeyguardStateController.mShowing)
            innerHook.intercept(innerChain)
            // Inner has finished; the outer still owns the gate.
            assertFalse("outer gate must still be open after inner exits", control.mKeyguardStateController.mShowing)
            null
        }

        assertNull(outerHook.intercept(outerChain))
        assertEquals(1, outerProceed)
        assertEquals(1, innerProceed)
        assertTrue("outer must restore the gate after the whole chain", control.mKeyguardStateController.mShowing)
    }

    @Test
    fun controlHook_ordinaryFailure_restoresGateAndRethrows() {
        setDynamicIslandSnapshot()
        val control = FakeControl()
        val controllerField = FakeControl::class.java.getDeclaredField("mKeyguardStateController")
        val showingField = FakeKeyguard::class.java.getDeclaredField("mShowing")
        val hook = SystemUIStrongToastHooks.StrongToastControlHook(
            controllerField,
            showingField,
            null,
            true
        )

        val failure = RuntimeException("chain failed")
        val chain = fakeChain(control) { throw failure }

        val thrown = assertThrows(RuntimeException::class.java) { hook.intercept(chain) }
        assertSame(failure, thrown)
        assertTrue(control.mKeyguardStateController.mShowing)
    }

    @Test
    fun controlHook_fatalError_restoresGateAndPropagates() {
        setDynamicIslandSnapshot()
        val control = FakeControl()
        val controllerField = FakeControl::class.java.getDeclaredField("mKeyguardStateController")
        val showingField = FakeKeyguard::class.java.getDeclaredField("mShowing")
        val hook = SystemUIStrongToastHooks.StrongToastControlHook(
            controllerField,
            showingField,
            null,
            true
        )

        val error = InternalError("fatal")
        val chain = fakeChain(control) { throw error }

        val thrown = assertThrows(InternalError::class.java) { hook.intercept(chain) }
        assertSame(error, thrown)
        assertTrue(control.mKeyguardStateController.mShowing)
    }
}
