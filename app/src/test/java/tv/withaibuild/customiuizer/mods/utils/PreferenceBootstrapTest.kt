package tv.withaibuild.customiuizer.mods.utils

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.utils.FakeSharedPreferences
import tv.withaibuild.customiuizer.utils.PrefMap

/**
 * Tests for [PreferenceBootstrap] state machine and snapshot behaviour.
 *
 * The bootstrap must transition through well-defined states, register the listener exactly once,
 * and load a second snapshot after registering so that changes made in the registration window
 * are not lost.
 */
class PreferenceBootstrapTest {

    @Test
    fun init_loadsNonEmptySnapshotAndSetsLoaded() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 20)

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }

        bootstrap.init()

        assertEquals(PreferenceBootstrap.State.LOADED, bootstrap.getState())
        assertTrue(bootstrap.isReady())
        assertEquals(20, prefs.getInt("system_statusbarheight", 11))
    }

    @Test
    fun init_emptyWithoutListener_setsEmptyPending() {
        val fake = FakeSharedPreferences()
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }

        bootstrap.init()

        assertEquals(PreferenceBootstrap.State.EMPTY_PENDING, bootstrap.getState())
        assertFalse(bootstrap.isReady())
    }

    @Test
    fun installListener_empty_setsValidEmpty() {
        val fake = FakeSharedPreferences()
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }

        assertTrue(bootstrap.installListener())

        assertEquals(PreferenceBootstrap.State.VALID_EMPTY, bootstrap.getState())
        assertTrue(bootstrap.isReady())
        assertTrue(bootstrap.isListenerRegistered())
    }

    @Test
    fun installListener_onlyOnce() {
        val fake = FakeSharedPreferences()
        var callCount = 0
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) {
            callCount++
            fake
        }

        assertTrue(bootstrap.installListener())
        assertTrue(bootstrap.installListener())

        // The source should be consulted once for the first listener installation, and the second
        // call must be a no-op.  The second snapshot is loaded inside the same synchronized block.
        assertEquals(1, callCount)
    }

    @Test
    fun installListener_capturesWindowedChange() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 12)

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }

        // First snapshot before the listener is registered.
        bootstrap.init()
        assertEquals(12, prefs.getInt("system_statusbarheight", 11))

        // The user changes a preference between the first snapshot and listener registration.
        fake.put("pref_key_system_statusbarheight", 24)

        // installListener should load a second snapshot after registering.
        assertTrue(bootstrap.installListener())

        assertEquals(PreferenceBootstrap.State.LOADED, bootstrap.getState())
        assertEquals(24, prefs.getInt("system_statusbarheight", 11))
    }

    @Test
    fun listenerCallback_updatesPrefMap() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 12)

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }
        bootstrap.installListener()

        // Simulate a remote change being delivered through the registered listener.  The fake
        // implementation is no-op, so we dispatch manually by reflecting the listener field.
        val listener = getListener(bootstrap)
        fake.put("pref_key_system_statusbarheight", 36)
        listener?.onSharedPreferenceChanged(fake, "pref_key_system_statusbarheight")

        assertEquals(36, prefs.getInt("system_statusbarheight", 11))
    }

    @Test
    fun listenerCallback_removesDeletedKey() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 12)

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }
        bootstrap.installListener()

        val listener = getListener(bootstrap)
        fake.edit().remove("pref_key_system_statusbarheight").apply()
        listener?.onSharedPreferenceChanged(fake, "pref_key_system_statusbarheight")

        assertEquals(11, prefs.getInt("system_statusbarheight", 11))
    }

    @Test
    fun unavailableBoundedRetries() {
        val prefs = PrefMap()
        var attempts = 0
        val bootstrap = PreferenceBootstrap.create(prefs) {
            attempts++
            throw IllegalStateException("remote not ready")
        }

        // The first five calls are allowed to retry.
        repeat(5) {
            bootstrap.init()
        }
        assertEquals(PreferenceBootstrap.State.UNAVAILABLE, bootstrap.getState())
        assertFalse(bootstrap.isReady())
        assertEquals(5, attempts)

        // The sixth call is a no-op because the retry budget is exhausted.
        bootstrap.init()
        assertEquals(5, attempts)
    }

    @Test
    fun installListener_resetsRetryBudget() {
        val prefs = PrefMap()
        var attempts = 0
        val fake = FakeSharedPreferences()
        var shouldFail = true

        val bootstrap = PreferenceBootstrap.create(prefs) {
            attempts++
            if (shouldFail) throw IllegalStateException("remote not ready") else fake
        }

        bootstrap.init()
        bootstrap.init()
        assertEquals(PreferenceBootstrap.State.UNAVAILABLE, bootstrap.getState())

        shouldFail = false
        assertTrue(bootstrap.installListener())
        assertEquals(PreferenceBootstrap.State.VALID_EMPTY, bootstrap.getState())
        assertTrue(bootstrap.isReady())
    }

    @Test
    fun getRemotePreferences_returnsNull_recordsUnavailable() {
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { null }

        bootstrap.init()

        assertEquals(PreferenceBootstrap.State.UNAVAILABLE, bootstrap.getState())
        assertFalse(bootstrap.isReady())
    }

    @Test
    fun getAll_returnsNull_recordsUnavailable() {
        val fake = object : SharedPreferences by FakeSharedPreferences() {
            override fun getAll(): Map<String, *>? = null
        }
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }

        bootstrap.init()

        assertEquals(PreferenceBootstrap.State.UNAVAILABLE, bootstrap.getState())
    }

    @Test
    fun validEmpty_onlyAfterListenerRegistered() {
        val fake = FakeSharedPreferences()
        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }

        // Without a listener, an empty map must be EMPTY_PENDING, not VALID_EMPTY.
        bootstrap.init()
        assertEquals(PreferenceBootstrap.State.EMPTY_PENDING, bootstrap.getState())

        // After the listener is installed, an empty map is VALID_EMPTY.
        assertTrue(bootstrap.installListener())
        assertEquals(PreferenceBootstrap.State.VALID_EMPTY, bootstrap.getState())
    }

    @Test
    fun listenerRegistered_onlyOnce() {
        val fake = FakeSharedPreferences()
        var registerCount = 0
        val trackingFake = object : SharedPreferences by fake {
            override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {
                registerCount++
            }
        }

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { trackingFake }

        bootstrap.installListener()
        bootstrap.installListener()

        assertEquals(1, registerCount)
    }

    @Test
    fun listenerTypeDispatch_keepsTypedValues() {
        val fake = FakeSharedPreferences()
        fake.put("pref_key_system_statusbarheight", 12)

        val prefs = PrefMap()
        val bootstrap = PreferenceBootstrap.create(prefs) { fake }
        bootstrap.installListener()

        val listener = getListener(bootstrap)
        fake.put("pref_key_system_statusbarheight", 48)
        listener?.onSharedPreferenceChanged(fake, "pref_key_system_statusbarheight")

        assertEquals(48, prefs.getInt("system_statusbarheight", 11))
    }

    @Suppress("UNCHECKED_CAST")
    private fun getListener(bootstrap: PreferenceBootstrap): SharedPreferences.OnSharedPreferenceChangeListener? {
        val field = PreferenceBootstrap::class.java.getDeclaredField("listener")
            .apply { isAccessible = true }
        return field.get(bootstrap) as? SharedPreferences.OnSharedPreferenceChangeListener
    }
}
