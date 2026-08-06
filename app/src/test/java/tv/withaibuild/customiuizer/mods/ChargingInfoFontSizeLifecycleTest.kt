package tv.withaibuild.customiuizer.mods

import android.util.TypedValue
import android.widget.TextView
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import tv.withaibuild.customiuizer.utils.PrefMap

private fun source(relativePath: String): String {
    var directory = File(java.lang.System.getProperty("user.dir").orEmpty()).absoluteFile
    while (true) {
        val candidate = File(directory, relativePath)
        if (candidate.isFile) return candidate.readText()
        directory = directory.parentFile
            ?: error("Repository root not found while locating $relativePath")
    }
}

/**
 * Lifecycle and style-application tests for the lock-screen charging info font size fix.
 *
 * These tests focus on the re-apply path added to close the cold-boot gap: the view is
 * inflated before the remote preference snapshot is guaranteed to be ready, so it must
 * be updated when the value becomes available or changes.
 */
class ChargingInfoFontSizeLifecycleTest {

    /**
     * Minimal [TextView] double that records the styling calls we care about.
     *
     * The real [TextView] lives in the framework; this records only what
     * [applyChargingInfoStyle] touches so the tests can verify idempotency and
     * fallback without a device.
     */
    private class RecordingTextView : TextView(null) {
        val setTextSizeCalls = mutableListOf<Pair<Int, Float>>()
        var isSingleLineValue: Boolean? = null

        override fun setTextSize(unit: Int, size: Float) {
            setTextSizeCalls.add(unit to size)
        }

        override fun setSingleLine(singleLine: Boolean) {
            isSingleLineValue = singleLine
        }
    }

    @Test
    fun applyChargingInfoStyle_defaultFontSize_doesNotCallSetTextSize() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo_fontsize", 16)
        }
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)

        assertTrue("default font size must not call setTextSize", textView.setTextSizeCalls.isEmpty())
        assertEquals("opt 1 must set isSingleLine to false", false, textView.isSingleLineValue)
    }

    @Test
    fun applyChargingInfoStyle_nonDefaultFontSize_appliesSpSize() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo_fontsize", 20)
        }
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)

        assertEquals(1, textView.setTextSizeCalls.size)
        val (unit, size) = textView.setTextSizeCalls[0]
        assertEquals(TypedValue.COMPLEX_UNIT_SP, unit)
        assertEquals(10.0f, size, 0.001f)
    }

    @Test
    fun applyChargingInfoStyle_repeatedCallsAreIdempotent() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo_fontsize", 24)
        }
        repeat(3) {
            SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)
        }

        assertEquals("repeated apply must not produce cumulative scaling", 3, textView.setTextSizeCalls.size)
        for ((_, size) in textView.setTextSizeCalls) {
            assertEquals(12.0f, size, 0.001f)
        }
    }

    @Test
    fun applyChargingInfoStyle_configChangeUsesLatestValue() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo_fontsize", 18)
        }
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)
        assertEquals(9.0f, textView.setTextSizeCalls.last().second, 0.001f)

        prefs.put("system_charginginfo_fontsize", 22)
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)
        assertEquals(11.0f, textView.setTextSizeCalls.last().second, 0.001f)
    }

    @Test
    fun applyChargingInfoStyle_invalidFontSize_ignoresAndFallsBack() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo_fontsize", 41)
        }
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)

        assertTrue("out-of-range font size must not call setTextSize", textView.setTextSizeCalls.isEmpty())
    }

    @Test
    fun applyChargingInfoStyle_opt1ForcesSingleLineFalse() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo_fontsize", 20)
            put("system_charginginfo_view", 1)
        }
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)

        assertEquals(false, textView.isSingleLineValue)
    }

    @Test
    fun applyChargingInfoStyle_opt2LeavesSingleLineUnmodified() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo_fontsize", 20)
            put("system_charginginfo_view", 2)
        }
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)

        assertNull("opt != 1 must not set isSingleLine", textView.isSingleLineValue)
    }

    @Test
    fun chargingInfoHook_usesPreferenceObserverAndWeakReference() {
        val source = source("app/src/main/java/tv/withaibuild/customiuizer/mods/SystemLockScreenHooks.kt")

        assertTrue("must apply style through a reusable helper", source.contains("applyChargingInfoStyle("))
        assertTrue(
            "must register a preference observer bound to the view",
            source.contains("ModuleHelper.observePreferenceChange")
        )
        assertTrue("must use a WeakReference to avoid pinning the view", source.contains("WeakReference"))
        assertTrue(
            "must post UI updates to the view's handler",
            source.contains("view.post") || source.contains("indicator.post") || source.contains(".post {")
        )

        val chargingInfoHookStart = source.indexOf("fun ChargingInfoHook")
        assertTrue("ChargingInfoHook must exist in source", chargingInfoHookStart >= 0)
        val nextStaticHook = source.indexOf("\n    @JvmStatic\n    fun ", chargingInfoHookStart + 1)
        val chargingInfoHookBody = if (nextStaticHook > 0) {
            source.substring(chargingInfoHookStart, nextStaticHook)
        } else {
            source.substring(chargingInfoHookStart)
        }
        assertFalse(
            "ChargingInfoHook must not use postDelayed to mask init order",
            chargingInfoHookBody.contains("postDelayed")
        )
    }

    @Test
    fun applyChargingInfoStyle_featureDisabled_doesNotModifySize() {
        val textView = RecordingTextView()
        val prefs = PrefMap().apply {
            put("system_charginginfo", false)
            put("system_charginginfo_fontsize", 20)
        }
        SystemLockScreenHooks.applyChargingInfoStyle(textView, prefs)

        assertTrue("disabled feature must not call setTextSize", textView.setTextSizeCalls.isEmpty())
    }
}
