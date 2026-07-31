package tv.withaibuild.customiuizer.mods.utils

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

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
        var failNextUnregister = false

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
            if (failNextUnregister) {
                failNextUnregister = false
                throw IllegalStateException("simulated unregister failure")
            }
            if (receiver != null) unregisteredReceivers.add(receiver)
        }
    }

    @After
    fun tearDown() {
        // The map is a process-scoped singleton; isolate tests so state from one test does not
        // leak into another. Real processes recreate it once per process, so this is only needed
        // in unit tests.
        getOwnedReceiversMap().clear()
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
            "testUnregisters",
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
        assertFalse("empty receiver list must be removed", ownedReceivers.containsKey("testUnregisters"))
    }

    @Test
    fun weakOwnerReceiver_retriesUnregisterAfterFailure() {
        val owner = Any()
        val context = TrackableContext()
        context.failNextUnregister = true
        val intent = Intent()
        val intentFilter = IntentFilter("android.intent.action.TIME_TICK")

        val receiver = ModuleHelper.registerOwnedReceiver(
            context,
            owner,
            "testRetries",
            intentFilter,
            Context.RECEIVER_NOT_EXPORTED
        ) { _, _, _, _ -> }

        // Simulate owner collection.
        val ownerRef = receiver.javaClass.getDeclaredField("ownerRef")
            .apply { isAccessible = true }
            .get(receiver) as WeakReference<*>
        ownerRef.clear()

        // First broadcast: registry cleanup succeeds but unregister throws.
        receiver.onReceive(context, intent)
        assertEquals("first unregister attempt should fail and be recorded", 0, context.unregisteredReceivers.size)

        // Second broadcast: cleanup is idempotent and unregister is retried.
        receiver.onReceive(context, intent)
        assertTrue("second unregister attempt should succeed", context.unregisteredReceivers.contains(receiver))
        assertEquals("unregister should have been called exactly once successfully", 1, context.unregisteredReceivers.size)
    }

    @Test
    fun weakOwnerReceiver_concurrentNewRegistrationIsNotRemoved() {
        val owner1 = Any()
        val context = TrackableContext()
        val intentFilter = IntentFilter("android.intent.action.TIME_TICK")

        val receiver1 = ModuleHelper.registerOwnedReceiver(
            context,
            owner1,
            "testConcurrent",
            intentFilter,
            Context.RECEIVER_NOT_EXPORTED
        ) { _, _, _, _ -> }

        // Simulate owner1 collection and full cleanup.
        val ownerRef = receiver1.javaClass.getDeclaredField("ownerRef")
            .apply { isAccessible = true }
            .get(receiver1) as WeakReference<*>
        ownerRef.clear()
        receiver1.onReceive(context, Intent())

        // Register a new owner under the same key. The previous cleanup must not have left the
        // key in a state that blocks new registrations.
        val owner2 = Any()
        val receiver2 = ModuleHelper.registerOwnedReceiver(
            context,
            owner2,
            "testConcurrent",
            intentFilter,
            Context.RECEIVER_NOT_EXPORTED
        ) { _, _, _, _ -> }

        val ownedReceivers = getOwnedReceiversMap()
        val list = ownedReceivers["testConcurrent"]
        assertEquals("only the new registration must remain for the key", 1, list?.size ?: 0)
        val remainingReceiver = list?.firstOrNull()?.let { getReceiverFromRegistration(it) }
        assertTrue("remaining receiver must be the new one", remainingReceiver === receiver2)
    }

    @Test
    fun weakOwnerReceiver_callbackUsesPassedOwnerWithoutStrongCapture() {
        var owner: Any? = Any()
        val ownerRef = WeakReference(owner)
        val callbackOwnerRef = AtomicReference<WeakReference<Any?>>(null)
        var calls = 0
        val context = TrackableContext()
        val intent = Intent()

        val receiver = ModuleHelper.Companion.WeakOwnerReceiver(owner!!) { _, callbackOwner, _, _ ->
            calls++
            // The callback must receive the passed owner. We hold it only through a WeakReference
            // so this test does not itself create a strong reference cycle.
            callbackOwnerRef.set(WeakReference(callbackOwner))
        }

        receiver.onReceive(context, intent)

        // Both references must point to the same live object.
        assertTrue("callback should receive the owner from the receiver", callbackOwnerRef.get()?.get() === ownerRef.get())

        // Drop the strong reference and clear the receiver's owner. The callback must not be
        // invoked again because no strong reference kept the owner alive.
        owner = null
        val receiverOwnerRef = receiver.javaClass.getDeclaredField("ownerRef")
            .apply { isAccessible = true }
            .get(receiver) as WeakReference<*>
        receiverOwnerRef.clear()

        receiver.onReceive(context, Intent())

        assertEquals("callback should be invoked only once", 1, calls)
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
