package tv.withaibuild.customiuizer.tasker

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockTokenLogicTest {

    private val provider = UnlockTokenProvider()

    @Test
    fun createsTokenOnFirstAccess() {
        val prefs = FakeSharedPreferences()
        assertEquals(null, prefs.getString(UnlockTokenProvider.PREF_KEY_TOKEN, null))
        val token = provider.getOrCreateToken(prefs)
        assertNotNull(token)
        assertTrue(token.isNotEmpty())
        assertEquals(token, prefs.getString(UnlockTokenProvider.PREF_KEY_TOKEN, null))
    }

    @Test
    fun tokenIsStableAcrossReads() {
        val prefs = FakeSharedPreferences()
        val first = provider.getOrCreateToken(prefs)
        val second = provider.getOrCreateToken(prefs)
        val fetched = provider.getToken(prefs)
        assertEquals(first, second)
        assertEquals(first, fetched)
    }

    @Test
    fun correctTokenIsVerified() {
        val prefs = FakeSharedPreferences()
        val token = provider.getOrCreateToken(prefs)
        assertTrue(provider.verify(prefs, token))
    }

    @Test
    fun missingBundleTokenIsRejected() {
        val prefs = FakeSharedPreferences()
        provider.getOrCreateToken(prefs)
        assertFalse(provider.verifyBundle(prefs, null))
        assertFalse(provider.verifyBundle(prefs, Bundle()))
    }

    @Test
    fun invalidTokenIsRejected() {
        val prefs = FakeSharedPreferences()
        provider.getOrCreateToken(prefs)
        assertFalse(provider.verify(prefs, "forged-token"))
        assertFalse(provider.verifyBundle(prefs, Bundle().apply { putString(UnlockTokenProvider.BUNDLE_KEY_TOKEN, "forged-token") }))
    }

    @Test
    fun missingTokenIsRejected() {
        val prefs = FakeSharedPreferences()
        provider.getOrCreateToken(prefs)
        assertFalse(provider.verify(prefs, null))
        assertFalse(provider.verify(prefs, ""))
        assertFalse(provider.verifyBundle(prefs, null))
        assertFalse(provider.verifyBundle(prefs, Bundle()))
    }

    @Test
    fun generatedTokensAreRandom() {
        val prefs1 = FakeSharedPreferences()
        val prefs2 = FakeSharedPreferences()
        val t1 = provider.getOrCreateToken(prefs1)
        val t2 = provider.getOrCreateToken(prefs2)
        assertNotEquals(t1, t2)
    }
}
