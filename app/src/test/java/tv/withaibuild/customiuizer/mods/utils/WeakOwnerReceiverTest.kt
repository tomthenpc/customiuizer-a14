package tv.withaibuild.customiuizer.mods.utils

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Regression test for owned receiver leak fix.
 *
 * The receiver must only hold a weak reference to the owner so that the hook
 * target can be collected even while the broadcast is still registered.
 */
class WeakOwnerReceiverTest {

    /**
     * A [Context] that lets us observe whether [unregisterReceiver] was called.
     * registerReceiver just returns a stub [Intent]; we are not testing the framework here.
     */
    private class TrackableContext : Application() {
        val unregisteredReceivers = ArrayList<BroadcastReceiver>()

        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent {
            return Intent("stub")
        }

        override fun registerReceiver(
            receiver: BroadcastReceiver?,
            filter: IntentFilter?,
            broadcastPermission: String?,
            scheduler: Handler?,
            flags: Int
        ): Intent {
            return Intent("stub")
        }

        override fun unregisterReceiver(receiver: BroadcastReceiver?) {
            if (receiver != null) unregisteredReceivers.add(receiver)
        }
    }

    @Test
    fun weakOwnerReceiver_forwardsBroadcastWhenOwnerAlive() {
        var calls = 0
        var receivedOwner: Any? = null
        val owner = Any()
        val context = TrackableContext()
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
        val context = TrackableContext()
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
    fun weakOwnerReceiver_unregistersWhenOwnerCollected() {
        val owner = Any()
        val context = TrackableContext()
        val intent = Intent()
        val intentFilter = IntentFilter("android.intent.action.TIME_TICK")

        val receiver = ModuleHelper.registerOwnedReceiver(
            context,
            owner,
            "testReceiver",
            intentFilter,
            Context.RECEIVER_NOT_EXPORTED
        ) { _, _, _, _ -> }

        // Simulate owner collection by clearing the WeakReference directly.
        val ownerRef = receiver.javaClass.getDeclaredField("ownerRef")
            .apply { isAccessible = true }
            .get(receiver) as WeakReference<*>
        ownerRef.clear()

        // Deliver a broadcast. The receiver must detect the owner is gone and clean itself up.
        receiver.onReceive(context, intent)

        assertTrue("receiver must be unregistered when owner is gone", context.unregisteredReceivers.contains(receiver))
        val ownedReceivers = getOwnedReceiversMap()
        assertFalse("empty receiver list must be removed", ownedReceivers.containsKey("testReceiver"))
    }

    @Test
    fun weakOwnerReceiver_repeatedRegistrationForSameOwnerReplacesOldReceiver() {
        val owner = Any()
        val context = TrackableContext()
        val intentFilter = IntentFilter("android.intent.action.TIME_TICK")

        val firstReceiver = ModuleHelper.registerOwnedReceiver(
            context,
            owner,
            "testReceiver",
            intentFilter,
            Context.RECEIVER_NOT_EXPORTED
        ) { _, _, _, _ -> }

        val secondReceiver = ModuleHelper.registerOwnedReceiver(
            context,
            owner,
            "testReceiver",
            intentFilter,
            Context.RECEIVER_NOT_EXPORTED
        ) { _, _, _, _ -> }

        assertTrue("first receiver must be unregistered when replaced", context.unregisteredReceivers.contains(firstReceiver))
        val ownedReceivers = getOwnedReceiversMap()
        val list = ownedReceivers["testReceiver"]
        assertEquals("only one registration must remain for the same owner/key", 1, list?.size ?: 0)
        val remainingReceiver = list?.firstOrNull()?.let { getReceiverFromRegistration(it) }
        assertTrue("remaining receiver must be the second one", remainingReceiver === secondReceiver)
    }

    @Test
    fun weakOwnerReceiver_callbackUsesPassedOwnerOnly() {
        var calls = 0
        val passedOwner = Any()
        val capturedOwner = Any()
        val context = TrackableContext()
        val intent = Intent()

        val receiver = ModuleHelper.Companion.WeakOwnerReceiver(passedOwner) { _, owner, _, _ ->
            calls++
            // The callback must receive the passed owner, never a captured outer one.
            assertNotSame("callback must not use captured owner", capturedOwner, owner)
            assertTrue("callback must receive the owner from the receiver", owner === passedOwner)
        }

        receiver.onReceive(context, intent)

        assertEquals("callback should be invoked once", 1, calls)
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

    @Suppress("UNCHECKED_CAST")
    private fun getOwnedReceiversMap(): ConcurrentHashMap<String, CopyOnWriteArrayList<*>> {
        val field = ModuleHelper::class.java.getDeclaredField("ownedReceivers")
            .apply { isAccessible = true }
        return field.get(null) as ConcurrentHashMap<String, CopyOnWriteArrayList<*>>
    }

    private fun getReceiverFromRegistration(registration: Any): BroadcastReceiver? {
        val field = registration.javaClass.getDeclaredField("receiver")
            .apply { isAccessible = true }
        return field.get(registration) as? BroadcastReceiver
    }
}
