package tv.withaibuild.customiuizer.mods

import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Typeface
import android.util.DisplayMetrics
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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

class SystemClockHotPathTest {

    private val fmtTime = "h:mm"
    private val fmtTimePm = "h:mm a"

    @Suppress("DEPRECATION")
    private inner class FakeResources(
        private val fmt: String = this@SystemClockHotPathTest.fmtTime,
        private val fmtPm: String = this@SystemClockHotPathTest.fmtTimePm,
        private val density: Float = 2.0f,
    ) : Resources(null, DisplayMetrics().apply { this.density = density }, Configuration()) {
        override fun getIdentifier(name: String?, defType: String?, defPackage: String?): Int {
            return when (name) {
                "fmt_time_12hour_minute" -> 1
                "fmt_time_12hour_minute_pm" -> 2
                else -> 0
            }
        }

        override fun getString(id: Int): String {
            return when (id) {
                1 -> fmt
                2 -> fmtPm
                else -> ""
            }
        }

        override fun getColor(id: Int, theme: android.content.res.Resources.Theme?): Int {
            return when (id) {
                android.R.color.system_accent1_0 -> 0xFFFF0000.toInt()
                android.R.color.system_accent1_600 -> 0xFF00FF00.toInt()
                else -> 0
            }
        }
    }

    private inner class RecordingTextView : TextView(null) {
        override fun getResources(): Resources = FakeResources()
        val setTextSizeCalls = mutableListOf<Pair<Int, Float>>()
        val setTextColorCalls = mutableListOf<Int>()
        val setBackgroundCalls = mutableListOf<android.graphics.drawable.Drawable?>()
        var translationYValue: Float? = null
        var singleLineValue: Boolean? = null
        var maxLinesValue: Int? = null

        override fun setTextSize(unit: Int, size: Float) {
            setTextSizeCalls.add(unit to size)
        }

        override fun setTextColor(color: Int) {
            setTextColorCalls.add(color)
        }

        override fun setBackground(background: android.graphics.drawable.Drawable?) {
            setBackgroundCalls.add(background)
        }

        override fun setTranslationY(translationY: Float) {
            translationYValue = translationY
        }

        override fun setSingleLine(singleLine: Boolean) {
            singleLineValue = singleLine
        }

        override fun setMaxLines(maxLines: Int) {
            maxLinesValue = maxLines
        }
    }

    private fun buildSnapshot(
        prefs: PrefMap,
    ): SystemClockHooks.ClockStyleSnapshot {
        return SystemClockHooks.buildClockStyleSnapshot(prefs, FakeResources())
    }

    @Test
    fun buildClockStyleSnapshot_defaultStatusbarFormat() {
        val prefs = PrefMap().apply {
            put("system_statusbar_clock_show_seconds", true)
            put("system_statusbar_clock_24hour_format", true)
            put("system_statusbar_clock_show_ampm", false)
            put("system_statusbar_clock_leadingzero", true)
        }
        val snapshot = buildSnapshot(prefs)

        assertEquals("HH:mm:ss", snapshot.statusbarDefaultFormat)
        assertTrue(snapshot.showStatusBarSeconds)
        assertFalse(snapshot.showCCSeconds)
    }

    @Test
    fun buildClockText_statusbarCustomFormat() {
        val prefs = PrefMap().apply {
            put("system_statusbar_clock_customformat_enable", true)
            put("system_statusbar_clock_customformat", "EEE HH:mm tq")
            put("system_statusbar_enable_weather_param", true)
        }
        val snapshot = buildSnapshot(prefs)

        val text = SystemClockHooks.buildClockText(
            "clock",
            snapshot,
            weatherInfo = "24C",
            statusbarClockTweak = true,
            ccClockTweak = true,
        )
        assertEquals("EEE HH:mm 24C", text)
    }

    @Test
    fun buildClockText_ccClock_usesCustomFormat() {
        val prefs = PrefMap().apply {
            put("system_cc_clock_customformat", "ss:mm")
        }
        val snapshot = buildSnapshot(prefs)

        val text = SystemClockHooks.buildClockText(
            "ccClock",
            snapshot,
            weatherInfo = null,
            statusbarClockTweak = true,
            ccClockTweak = true,
        )
        assertEquals("ss:mm", text)
    }

    @Test
    fun buildClockText_disabledFeature_returnsNull() {
        val prefs = PrefMap()
        val snapshot = buildSnapshot(prefs)

        assertNull(
            SystemClockHooks.buildClockText(
                "clock",
                snapshot,
                weatherInfo = null,
                statusbarClockTweak = false,
                ccClockTweak = true,
            )
        )
        assertNull(
            SystemClockHooks.buildClockText(
                "ccClock",
                snapshot,
                weatherInfo = null,
                statusbarClockTweak = true,
                ccClockTweak = false,
            )
        )
    }

    @Test
    fun buildClockText_100Ticks_noPrefReads() {
        val prefs = PrefMap().apply {
            put("system_statusbar_clock_show_seconds", true)
            put("system_statusbar_clock_24hour_format", true)
            put("system_statusbar_clock_show_ampm", false)
            put("system_statusbar_clock_leadingzero", false)
            put("system_cc_clock_customformat", "HH:mm:ss")
        }
        val snapshot = buildSnapshot(prefs)

        repeat(100) {
            SystemClockHooks.buildClockText(
                if (it % 2 == 0) "clock" else "ccClock",
                snapshot,
                weatherInfo = null,
                statusbarClockTweak = true,
                ccClockTweak = true,
            )
        }
    }

    @Test
    fun buildClockText_minuteMode_doesNotShowSeconds() {
        val prefs = PrefMap().apply {
            put("system_statusbar_clock_show_seconds", false)
            put("system_statusbar_clock_24hour_format", false)
            put("system_statusbar_clock_show_ampm", false)
            put("system_statusbar_clock_leadingzero", true)
        }
        val snapshot = buildSnapshot(prefs)

        assertEquals("hh:mm", snapshot.statusbarDefaultFormat)
        assertFalse(snapshot.showStatusBarSeconds)
    }

    @Test
    fun initClockStyle_noPrefsReadsAfterSnapshot() {
        val prefs = PrefMap().apply {
            put("system_statusbar_clock_fontsize", 20)
            put("system_statusbar_clock_bold", true)
            put("system_statusbar_clock_verticaloffset", 12)
        }
        val snapshot = buildSnapshot(prefs)
        val clock = RecordingTextView().apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        SystemClockHooks.initClockStyle(clock, "clock", snapshot)

        assertEquals(1, clock.setTextSizeCalls.size)
        assertEquals(10.0f, clock.setTextSizeCalls[0].second, 0.001f)
        assertEquals(Typeface.DEFAULT_BOLD, clock.typeface)
        assertNotNull(clock.translationYValue)
    }

    @Test
    fun initClockStyle_idempotentSameSnapshot() {
        val prefs = PrefMap().apply {
            put("system_statusbar_clock_fontsize", 20)
        }
        val snapshot = buildSnapshot(prefs)
        val clock = RecordingTextView().apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }

        repeat(3) {
            SystemClockHooks.initClockStyle(clock, "clock", snapshot)
        }

        assertEquals("repeated apply with same snapshot id must not call setTextSize again", 1, clock.setTextSizeCalls.size)
    }

    @Test
    fun initClockStyle_reapplyWithDifferentSnapshot() {
        val prefs = PrefMap().apply {
            put("system_statusbar_clock_fontsize", 20)
        }
        val snapshot1 = buildSnapshot(prefs)
        val clock = RecordingTextView().apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        SystemClockHooks.initClockStyle(clock, "clock", snapshot1)

        prefs.put("system_statusbar_clock_fontsize", 24)
        val snapshot2 = buildSnapshot(prefs)
        SystemClockHooks.initClockStyle(clock, "clock", snapshot2)

        assertEquals(2, clock.setTextSizeCalls.size)
        assertEquals(10.0f, clock.setTextSizeCalls[0].second, 0.001f)
        assertEquals(12.0f, clock.setTextSizeCalls[1].second, 0.001f)
    }

    @Test
    fun source_noMainModulePrefsInBuildClockTextOrInitClockStyle() {
        val path = "app/src/main/java/tv/withaibuild/customiuizer/mods/SystemClockHooks.kt"
        val text = source(path)

        val buildClockTextBody = text.substringAfter("internal fun buildClockText(")
            .substringAfter(")")
            .substringBefore("internal fun initClockStyle")

        val initClockStyleBody = text.substringAfter("internal fun initClockStyle(")
            .substringAfter(") {")
            .substringBefore("private fun initClockStyle(")

        assertFalse(
            "buildClockText must not read MainModule.mPrefs",
            buildClockTextBody.contains("MainModule.mPrefs")
        )
        assertFalse(
            "initClockStyle must not read MainModule.mPrefs",
            initClockStyleBody.contains("MainModule.mPrefs")
        )
    }
}
