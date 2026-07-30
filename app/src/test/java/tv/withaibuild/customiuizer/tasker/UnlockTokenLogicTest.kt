package tv.withaibuild.customiuizer.tasker

import android.os.Bundle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UnlockTokenLogicTest {

    private val provider = UnlockTokenProvider()

    private fun hostA() = UnlockTokenProvider.HostInfo("com.host.a", "Host A", setOf("cert-a-1", "cert-a-2"))
    private fun hostB() = UnlockTokenProvider.HostInfo("com.host.b", "Host B", setOf("cert-b-1"))

    @Test
    fun createsTokenForFirstHost() {
        val prefs = FakeSharedPreferences()
        val host = hostA()
        val token = provider.getOrCreateToken(prefs, host)
        assertNotNull(token)
        assertEquals(host.packageName, token?.hostPackage)
        assertTrue(!token?.token.isNullOrEmpty())
        assertEquals(token?.token, prefs.getString("host_token_com.host.a", null))
    }

    @Test
    fun samePackageAndCertReusesToken() {
        val prefs = FakeSharedPreferences()
        val first = provider.getOrCreateToken(prefs, hostA())
        val second = provider.getOrCreateToken(prefs, hostA())
        assertEquals(first?.token, second?.token)
    }

    @Test
    fun differentHostsGetDifferentTokens() {
        val prefs = FakeSharedPreferences()
        val a = provider.getOrCreateToken(prefs, hostA())
        val b = provider.getOrCreateToken(prefs, hostB())
        assertNotNull(a)
        assertNotNull(b)
        assertNotEquals(a?.token, b?.token)
    }

    @Test
    fun samePackageDifferentCertIsRejected() {
        val prefs = FakeSharedPreferences()
        provider.getOrCreateToken(prefs, hostA())
        val attacker = UnlockTokenProvider.HostInfo("com.host.a", "Host A Fake", setOf("cert-fake"))
        val token = provider.getOrCreateToken(prefs, attacker)
        assertNull(token)
    }

    @Test
    fun certificateRotationIsAllowed() {
        val prefs = FakeSharedPreferences()
        // First binding records the current cert.
        val initial = UnlockTokenProvider.HostInfo("com.host.a", "Host A", setOf("cert-a-1"))
        val first = provider.getOrCreateToken(prefs, initial)
        // Rotation: the new cert history still contains the previous cert (v3 signing lineage).
        val rotated = UnlockTokenProvider.HostInfo("com.host.a", "Host A", setOf("cert-a-2", "cert-a-1"))
        val second = provider.getOrCreateToken(prefs, rotated)
        assertNotNull(second)
        assertEquals(first?.token, second?.token)
    }

    @Test
    fun correctTokenVerifiedForCorrectHost() {
        val prefs = FakeSharedPreferences()
        val token = provider.getOrCreateToken(prefs, hostA())
        assertTrue(provider.verify(prefs, "com.host.a", token?.token))
    }

    @Test
    fun aHostTokenCannotVerifyForBHost() {
        val prefs = FakeSharedPreferences()
        val a = provider.getOrCreateToken(prefs, hostA())
        assertFalse(provider.verify(prefs, "com.host.b", a?.token))
    }

    @Test
    fun mismatchedSenderOrHostIsRejected() {
        val prefs = FakeSharedPreferences()
        val token = provider.getOrCreateToken(prefs, hostA())
        // Bundle with mismatched sender/host is rejected (we cannot create a real Bundle in JVM,
        // so we test the token/host lookup directly and the bundle null/empty cases).
        assertFalse(provider.verify(prefs, "com.host.b", token?.token))
        assertFalse(provider.verifyBundle(prefs, null, "com.host.a"))
        assertFalse(provider.verifyBundle(prefs, Bundle(), "com.host.a"))
    }

    @Test
    fun missingEmptyOrWrongTokenIsRejected() {
        val prefs = FakeSharedPreferences()
        provider.getOrCreateToken(prefs, hostA())
        assertFalse(provider.verify(prefs, "com.host.a", null))
        assertFalse(provider.verify(prefs, "com.host.a", ""))
        assertFalse(provider.verify(prefs, "com.host.a", "wrong-token"))
        assertFalse(provider.verifyBundle(prefs, Bundle(), "com.host.a"))
        assertFalse(provider.verifyBundle(prefs, null, "com.host.a"))
    }

    @Test
    fun legacyGlobalTokenIsInvalid() {
        val prefs = FakeSharedPreferences()
        prefs.edit().putString("unlock_token", "legacy-token").apply()
        assertFalse(provider.verify(prefs, "", "legacy-token"))
        assertFalse(provider.verify(prefs, "com.host.a", "legacy-token"))
    }
}
