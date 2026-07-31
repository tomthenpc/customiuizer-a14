package tv.withaibuild.customiuizer.mods.utils

import android.app.Application
import android.content.Context
import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.WeakReference

/**
 * Regression test for owned receiver leak fix.
 *
 * The receiver must only hold a weak reference to the owner so that the hook
 * target can be collected even while the broadcast is still registered.
 */
class WeakOwnerReceiverTest {

    @Test
    fun weakOwnerReceiver_forwardsBroadcastWhenOwnerAlive() {
        var calls = 0
        var receivedOwner: Any? = null
        val owner = Any()
        val context = Application()
        val intent = Intent()

        val receiver = ModuleHelper.Companion.WeakOwnerReceiver(owner) { _, o, _, _ ->
            calls++
            receivedOwner = o
        }

        receiver.onReceive(context, intent)

        assertEquals("callback should be invoked once", 1, calls)
        assertTrue("callback should receive the owner", receivedOwner === owner)
    }

    @Test
    fun weakOwnerReceiver_dropsBroadcastWhenOwnerCollected() {
        var calls = 0
        val owner = Any()
        val context = Application()
        val intent = Intent()

        val receiver = ModuleHelper.Companion.WeakOwnerReceiver(owner) { _, _, _, _ ->
            calls++
        }

        // Simulate owner collection by clearing the WeakReference directly.
        val ownerRef = receiver.javaClass.getDeclaredField("ownerRef")
            .apply { isAccessible = true }
            .get(receiver) as WeakReference<*>
        ownerRef.clear()

        receiver.onReceive(context, intent)

        assertEquals("callback should not be invoked after owner is gone", 0, calls)
    }

    @Test
    fun weakOwnerReceiver_storesWeakReferenceNotStrong() {
        val owner = Any()
        val receiver = ModuleHelper.Companion.WeakOwnerReceiver(owner) { _, _, _, _ -> }

        val ownerRef = receiver.javaClass.getDeclaredField("ownerRef")
            .apply { isAccessible = true }
            .get(receiver)

        assertTrue("ownerRef must be a WeakReference", ownerRef is WeakReference<*>)
        assertTrue("ownerRef must point to the owner", (ownerRef as WeakReference<*>).get() === owner)
    }
}
