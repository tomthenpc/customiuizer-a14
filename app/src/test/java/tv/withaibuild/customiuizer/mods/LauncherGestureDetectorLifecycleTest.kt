package tv.withaibuild.customiuizer.mods

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.util.concurrent.atomic.AtomicInteger

/**
 * Behavioral tests for the HotSeats horizontal gesture detector lifecycle.
 */
class LauncherGestureDetectorLifecycleTest {

    private var savedFactory: ((Any, android.content.Context?) -> Any)? = null

    @Before
    fun setUp() {
        savedFactory = LauncherGestureHooks.hotSeatDetectorFactory
    }

    @After
    fun tearDown() {
        savedFactory?.let { LauncherGestureHooks.hotSeatDetectorFactory = it }
        XposedHelpers.removeAdditionalInstanceField(FakeOwner.detached, "customiuizer_hotseat_horizontal_detector")
    }

    @Test
    fun noProcessSingletonFieldForHorizontalDetector() {
        val fields = LauncherGestureHooks::class.java.declaredFields
        assertFalse("process singleton must not retain a horizontal detector", fields.any { it.name == "mDetectorHorizontal" })
    }

    @Test
    fun ownersDoNotShareDetectors() {
        val ownerA = FakeOwner("A")
        val ownerB = FakeOwner("B")

        LauncherGestureHooks.hotSeatDetectorFactory = { owner, _ -> "detector-for-$owner" }

        val detectorA = LauncherGestureHooks.obtainHotSeatDetector(ownerA)
        val detectorB = LauncherGestureHooks.obtainHotSeatDetector(ownerB)

        assertNotSame(detectorA, detectorB)
        assertEquals("detector-for-$ownerA", detectorA)
        assertEquals("detector-for-$ownerB", detectorB)
    }

    @Test
    fun repeatedAccessReusesDetectorAndFactoryCalledOnce() {
        val owner = FakeOwner("shared")
        val createCount = AtomicInteger(0)

        LauncherGestureHooks.hotSeatDetectorFactory = { _, _ ->
            createCount.incrementAndGet()
            "single-detector"
        }

        val first = LauncherGestureHooks.obtainHotSeatDetector(owner)
        val second = LauncherGestureHooks.obtainHotSeatDetector(owner)

        assertSame("repeated access must reuse the same detector", first, second)
        assertEquals("factory must be called exactly once per owner", 1, createCount.get())
    }

    @Test
    fun detectorIsStoredAsAdditionalInstanceFieldOnOwner() {
        val owner = FakeOwner("field-check")

        LauncherGestureHooks.hotSeatDetectorFactory = { _, _ -> "stored-detector" }

        val detector = LauncherGestureHooks.obtainHotSeatDetector(owner)
        val fromField = XposedHelpers.getAdditionalInstanceField(owner, "customiuizer_hotseat_horizontal_detector")

        assertSame(detector, fromField)
    }

    @Test
    fun factoryReceivesOwnersContext() {
        val owner = FakeOwner("with-context")
        val capturedContext = mutableListOf<android.content.Context?>()

        LauncherGestureHooks.hotSeatDetectorFactory = { _, context ->
            capturedContext.add(context)
            "detector-with-context"
        }

        LauncherGestureHooks.obtainHotSeatDetector(owner)

        assertEquals(1, capturedContext.size)
        assertSame(owner.context, capturedContext[0])
    }

    @Test
    fun newOwnerAfterRecreationGetsNewDetector() {
        val first = FakeOwner("recreated-1")
        val second = FakeOwner("recreated-2")

        LauncherGestureHooks.hotSeatDetectorFactory = { owner, _ -> "detector-for-$owner" }

        val firstDetector = LauncherGestureHooks.obtainHotSeatDetector(first)
        val secondDetector = LauncherGestureHooks.obtainHotSeatDetector(second)

        assertNotSame(firstDetector, secondDetector)
        assertTrue((firstDetector as String).contains("recreated-1"))
        assertTrue((secondDetector as String).contains("recreated-2"))
    }

    class FakeOwner(val id: String) {

        val context: android.content.Context? = android.app.Application()

        override fun toString(): String = id

        override fun equals(other: Any?): Boolean = other is FakeOwner && other.id == id

        override fun hashCode(): Int = id.hashCode()

        companion object {
            val detached = Any()
        }
    }
}
