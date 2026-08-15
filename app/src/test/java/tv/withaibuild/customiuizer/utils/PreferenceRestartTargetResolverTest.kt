package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure unit tests for [PreferenceRestartTargetResolver.resolveForKeys].
 *
 * Android preference-tree resolution is exercised through integration tests
 * on device; this class only tests the list-based lookup path.
 */
class PreferenceRestartTargetResolverTest {

    @Test
    fun empty_list_returns_empty_set() {
        assertTrue(PreferenceRestartTargetResolver.resolveForKeys(emptyList()).isEmpty())
    }

    @Test
    fun single_key_resolves_to_expected_target() {
        assertEquals(
            setOf(RestartTarget.LAUNCHER),
            PreferenceRestartTargetResolver.resolveForKeys(listOf("launcher_fixanim"))
        )
        assertEquals(
            setOf(RestartTarget.SYSTEMUI),
            PreferenceRestartTargetResolver.resolveForKeys(listOf("system_charginginfo"))
        )
        assertEquals(
            setOf(RestartTarget.SECURITY_CENTER),
            PreferenceRestartTargetResolver.resolveForKeys(listOf("various_disableapp"))
        )
    }

    @Test
    fun multi_host_key_returns_union() {
        assertEquals(
            setOf(RestartTarget.LAUNCHER, RestartTarget.SYSTEMUI),
            PreferenceRestartTargetResolver.resolveForKeys(listOf("controls_nonavbar"))
        )
        assertEquals(
            setOf(RestartTarget.LAUNCHER, RestartTarget.SYSTEMUI),
            PreferenceRestartTargetResolver.resolveForKeys(listOf("controls_fsg_assist_left_action"))
        )
        assertEquals(
            setOf(RestartTarget.LAUNCHER, RestartTarget.SYSTEMUI),
            PreferenceRestartTargetResolver.resolveForKeys(listOf("controls_fsg_assist_right_action"))
        )
    }

    @Test
    fun unknown_key_contributes_nothing() {
        assertEquals(
            setOf(RestartTarget.LAUNCHER),
            PreferenceRestartTargetResolver.resolveForKeys(listOf("launcher_fixanim", "not_a_real_key"))
        )
    }

    @Test
    fun null_key_is_ignored() {
        assertEquals(
            setOf(RestartTarget.SYSTEMUI),
            PreferenceRestartTargetResolver.resolveForKeys(listOf(null, "system_charginginfo"))
        )
    }

    @Test
    fun prefixed_and_canonical_keys_are_equivalent() {
        assertEquals(
            PreferenceRestartTargetResolver.resolveForKeys(listOf("pref_key_system_charginginfo")),
            PreferenceRestartTargetResolver.resolveForKeys(listOf("system_charginginfo"))
        )
    }

    @Test
    fun union_across_multiple_keys() {
        val keys = listOf(
            "launcher_fixanim",
            "system_charginginfo",
            "various_disableapp",
            "controls_nonavbar"
        )
        assertEquals(
            setOf(RestartTarget.LAUNCHER, RestartTarget.SYSTEMUI, RestartTarget.SECURITY_CENTER),
            PreferenceRestartTargetResolver.resolveForKeys(keys)
        )
    }
}
