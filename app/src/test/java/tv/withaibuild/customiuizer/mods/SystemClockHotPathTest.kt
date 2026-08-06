package tv.withaibuild.customiuizer.mods

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Looper

import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.lang.ref.WeakReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.ScreenStateController
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
        override fun getDisplayMetrics(): DisplayMetrics =
            DisplayMetrics().apply { this.density = this@FakeResources.density }

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

    @Suppress("DEPRECATION", "NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    private inner class FakeContext : ContextWrapper(null) {
        private val fakeResources = FakeResources()
        override fun getMainLooper(): Looper? = null
        override fun getResources(): Resources = fakeResources
        override fun getApplicationContext(): Context = this
        override fun getSystemService(name: String): Any? = null
        override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, flags: Int): Intent? = null
        override fun unregisterReceiver(receiver: BroadcastReceiver?) {}
    }

    private class FakeController {
        lateinit var mContext: Context
        var mCalendar: Any = java.util.Calendar.getInstance()
        val mClockListeners = ArrayList<Any>()
    }

    private inner class RecordingClockView : RecordingTextView() {
        val updateTimeCalls = mutableListOf<Any?>()
        fun updateTime() {
            updateTimeCalls.add(null)
        }
    }

    private fun setCurrentSnapshot(snapshot: SystemClockHooks.ClockStyleSnapshot?) {
        val field = SystemClockHooks::class.java.getDeclaredField("clockStyleSnapshot")
        field.isAccessible = true
        field.set(SystemClockHooks, snapshot)
    }

    private fun screenStateListeners(): ArrayList<*> {
        val listenersField = ScreenStateController::class.java.getDeclaredField("listeners")
        listenersField.isAccessible = true
        return listenersField.get(ScreenStateController) as ArrayList<*>
    }

    @Before
    fun setUp() {
        MainModule.mPrefs.clear()
    }

    @After
    fun tearDown() {
        val listeners = screenStateListeners()
        @Suppress("UNCHECKED_CAST")
        val copy = ArrayList(listeners as ArrayList<ScreenStateController.ScreenStateListener>)
        for (listener in copy) {
            ScreenStateController.removeListener(listener)
        }
    }

    private fun makeControllerWithClock(clock: View = RecordingClockView()): FakeController {
        val controller = FakeController()
        controller.mContext = FakeContext()
        ModuleHelper.setViewInfo(clock, "clockName", "clock")
        controller.mClockListeners.add(clock)
        return controller
    }

    private fun makeSnapshotWithSeconds(statusBar: Boolean, cc: Boolean): SystemClockHooks.ClockStyleSnapshot {
        val prefs = PrefMap().apply {
            put("system_statusbar_clock_show_seconds", statusBar)
            put("system_statusbar_clock_24hour_format", true)
            put("system_statusbar_clock_show_ampm", false)
            put("system_statusbar_clock_leadingzero", true)
            put("system_cc_clock_customformat", if (cc) "HH:mm:ss" else "")
        }
        return SystemClockHooks.buildClockStyleSnapshot(prefs, FakeResources())
    }

    private fun disposeTicker(ticker: Any?) {
        if (ticker != null) {
            ticker.javaClass.getMethod("dispose").invoke(ticker)
        }
    }

    private open inner class RecordingTextView : TextView(null) {
        override fun getResources(): Resources = FakeResources()

        // Recorded setter calls for assertions.  Each setter also delegates to the
        // real TextView implementation so that final getters such as textColors,
        // textSize, and translationY reflect the recorded state.
        val setTextSizeCalls = mutableListOf<Pair<Int, Float>>()
        val setTextColorCalls = mutableListOf<android.content.res.ColorStateList?>()
        val setBackgroundCalls = mutableListOf<android.graphics.drawable.Drawable?>()
        val setTypefaceCalls = mutableListOf<Pair<Typeface?, Int>>()
        val setLayoutParamsCalls = mutableListOf<ViewGroup.LayoutParams?>()
        val setLineSpacingCalls = mutableListOf<Pair<Float, Float>>()
        val setTextAlignmentCalls = mutableListOf<Int>()
        var singleLineValue: Boolean? = null
        var maxLinesValue: Int? = null
        var translationYValue: Float? = null
        var currentTextColorValue: Int = Color.BLACK
        var layoutParamsValue: ViewGroup.LayoutParams? = null
        var typefaceValue: Typeface? = null
        var textColorsValue: ColorStateList? = null
        var backgroundValue: Drawable? = null
        var textSizeValue: Float = 15f
        var textAlignmentValue: Int = View.TEXT_ALIGNMENT_GRAVITY
        var lineSpacingExtraValue: Float = 0f
        var lineSpacingMultiplierValue: Float = 1.0f
        private val keyedTags = mutableMapOf<Int, Any?>()

        override fun getTypeface(): Typeface? = typefaceValue

        override fun getBackground(): Drawable? = backgroundValue

        override fun getTextSize(): Float = textSizeValue

        override fun getTextAlignment(): Int = textAlignmentValue

        override fun getLineSpacingMultiplier(): Float = lineSpacingMultiplierValue

        override fun getLineSpacingExtra(): Float = lineSpacingExtraValue

        override fun setTag(key: Int, tag: Any?) {
            keyedTags[key] = tag
            super.setTag(key, tag)
        }

        override fun getTag(key: Int): Any? = keyedTags[key] ?: super.getTag(key)

        override fun getMaxLines(): Int = maxLinesValue ?: 1

        override fun isSingleLine(): Boolean = singleLineValue ?: true

        override fun setTextSize(unit: Int, size: Float) {
            setTextSizeCalls.add(unit to size)
            textSizeValue = when (unit) {
                TypedValue.COMPLEX_UNIT_PX -> size
                TypedValue.COMPLEX_UNIT_DIP -> size * resources.displayMetrics.density
                else -> size
            }
            super.setTextSize(unit, size)
        }

        override fun setTextColor(color: Int) {
            currentTextColorValue = color
            val colors = ColorStateList.valueOf(color) ?: ColorStateList(arrayOf(IntArray(0)), intArrayOf(color))
            textColorsValue = colors
            setTextColorCalls.add(colors)
            super.setTextColor(colors)
        }

        override fun setTextColor(colors: android.content.res.ColorStateList?) {
            textColorsValue = colors
            currentTextColorValue = colors?.defaultColor ?: Color.BLACK
            setTextColorCalls.add(colors)
            if (colors != null) super.setTextColor(colors)
        }

        override fun setBackground(background: android.graphics.drawable.Drawable?) {
            setBackgroundCalls.add(background)
            backgroundValue = background
            super.setBackground(background)
        }

        override fun setTypeface(tf: Typeface?) {
            setTypefaceCalls.add(tf to Typeface.NORMAL)
            typefaceValue = tf
            super.setTypeface(tf)
        }

        override fun setTypeface(tf: Typeface?, style: Int) {
            setTypefaceCalls.add(tf to style)
            if (style == Typeface.NORMAL) typefaceValue = tf
            super.setTypeface(tf, style)
        }

        override fun setTextAlignment(textAlignment: Int) {
            setTextAlignmentCalls.add(textAlignment)
            textAlignmentValue = textAlignment
            super.setTextAlignment(textAlignment)
        }

        override fun setLineSpacing(add: Float, mult: Float) {
            setLineSpacingCalls.add(add to mult)
            lineSpacingExtraValue = add
            lineSpacingMultiplierValue = mult
            super.setLineSpacing(add, mult)
        }

        override fun getTranslationY(): Float = translationYValue ?: 0f

        override fun setTranslationY(translationY: Float) {
            translationYValue = translationY
            super.setTranslationY(translationY)
        }

        override fun setSingleLine(singleLine: Boolean) {
            singleLineValue = singleLine
            super.setSingleLine(singleLine)
        }

        override fun setMaxLines(maxLines: Int) {
            maxLinesValue = maxLines
            super.setMaxLines(maxLines)
        }

        override fun getLayoutParams(): ViewGroup.LayoutParams? = layoutParamsValue

        override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
            setLayoutParamsCalls.add(params)
            layoutParamsValue = params
            super.setLayoutParams(params)
        }
    }

    private fun buildSnapshot(
        prefs: PrefMap,
    ): SystemClockHooks.ClockStyleSnapshot {
        return SystemClockHooks.buildClockStyleSnapshot(prefs, FakeResources())
    }

    private fun statusbarSnapshotWith(
        fontSize: Int = 13,
        bold: Boolean = false,
        align: Int = 1,
        leftMargin: Int = 0,
        rightMargin: Int = 0,
        verticalOffset: Int = 8,
        chip: Boolean = false,
        chipUseMonet: Boolean = false,
        chipCustomTextColor: Boolean = false,
        chipTextColor: Int = Color.WHITE,
        fixedWidth: Int = 10,
        customFormat: String = "",
        customFormatEnable: Boolean = false,
    ): SystemClockHooks.ClockStyleSnapshot {
        val prefs = PrefMap().apply {
            put("system_statusbar_clock_fontsize", fontSize)
            put("system_statusbar_clock_bold", bold)
            put("system_statusbar_clock_align", align)
            put("system_statusbar_clock_leftmargin", leftMargin)
            put("system_statusbar_clock_rightmargin", rightMargin)
            put("system_statusbar_clock_verticaloffset", verticalOffset)
            put("system_statusbar_clock_chip", chip)
            put("system_statusbar_clock_chip_usemonet", chipUseMonet)
            put("system_statusbar_clock_chip_customtextcolor", chipCustomTextColor)
            put("system_statusbar_clock_chip_textcolor", chipTextColor)
            put("system_statusbar_clock_fixedcontent_width", fixedWidth)
            put("system_statusbar_clock_customformat_enable", customFormatEnable)
            put("system_statusbar_clock_customformat", customFormat)
        }
        return buildSnapshot(prefs)
    }

    private fun originalTypeface(): Typeface? {
        val base = Typeface.DEFAULT_BOLD
        return if (base != null) {
            Typeface.create(base, Typeface.ITALIC)
        } else {
            null
        }
    }

    private fun colorStateList(color: Int): ColorStateList =
        ColorStateList.valueOf(color) ?: ColorStateList(arrayOf(IntArray(0)), intArrayOf(color))

    private fun setOriginalStyle(
        clock: RecordingTextView,
        textSizePx: Float = 28f,
        typeface: Typeface? = originalTypeface(),
        textColor: Int = Color.BLACK,
        textAlignment: Int = View.TEXT_ALIGNMENT_CENTER,
        translationY: Float = 0f,
        background: android.graphics.drawable.Drawable? = ColorDrawable(Color.RED),
        singleLine: Boolean = true,
        maxLines: Int = 1,
        lineSpacingExtra: Float = 0f,
        lineSpacingMultiplier: Float = 1f,
        width: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
        leftMargin: Int = 5,
        rightMargin: Int = 5,
        topMargin: Int = 2,
        bottomMargin: Int = 2,
        gravity: Int = Gravity.CENTER_VERTICAL or Gravity.START,
    ) {
        clock.textSizeValue = textSizePx
        clock.setTypeface(typeface)
        clock.setTextColor(textColor)
        clock.setTextAlignment(textAlignment)
        clock.setTranslationY(translationY)
        clock.setBackground(background)
        clock.setSingleLine(singleLine)
        if (!singleLine) clock.setMaxLines(maxLines)
        clock.setLineSpacing(lineSpacingExtra, lineSpacingMultiplier)
        val lp = LinearLayout.LayoutParams(width, height).apply {
            this.width = width
            this.height = height
            this.leftMargin = leftMargin
            this.rightMargin = rightMargin
            this.topMargin = topMargin
            this.bottomMargin = bottomMargin
            this.gravity = gravity
        }
        clock.setLayoutParams(lp)
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
    fun initClockStyle_boldTrueThenFalseRestoresOriginalTypeface() {
        val clock = RecordingTextView()
        val original = originalTypeface()
        setOriginalStyle(clock, typeface = original)

        val boldOn = statusbarSnapshotWith(bold = true)
        val boldOff = statusbarSnapshotWith(bold = false)

        SystemClockHooks.initClockStyle(clock, "clock", boldOn)
        val afterBoldOn = clock.setTypefaceCalls.size

        SystemClockHooks.initClockStyle(clock, "clock", boldOff)
        assertEquals("bold off must restore original typeface", original, clock.typeface)
        assertTrue(
            "bold off must call setTypeface to restore the original",
            clock.setTypefaceCalls.size > afterBoldOn,
        )
    }

    @Test
    fun initClockStyle_chipTrueThenFalseRestoresOriginalBackgroundAndColors() {
        val clock = RecordingTextView()
        val originalBackground = ColorDrawable(Color.RED)
        setOriginalStyle(clock, background = originalBackground, textColor = Color.BLACK)
        val originalTextColors = clock.textColors

        val chipOn = statusbarSnapshotWith(
            chip = true,
            chipCustomTextColor = true,
            chipTextColor = Color.GREEN,
        )
        val chipOff = statusbarSnapshotWith(chip = false)

        SystemClockHooks.initClockStyle(clock, "clock", chipOn)
        assertTrue("chip on must set a new background", clock.background !== originalBackground)
        assertEquals(Color.GREEN, clock.currentTextColorValue)

        SystemClockHooks.initClockStyle(clock, "clock", chipOff)
        assertSame("chip off must restore original background", originalBackground, clock.background)
        assertSame("chip off must restore original text colors", originalTextColors, clock.textColors)
    }

    @Test
    fun initClockStyle_align2Then1RestoresOriginalTextAlignment() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, textAlignment = View.TEXT_ALIGNMENT_TEXT_END)

        val align2 = statusbarSnapshotWith(align = 2)
        val align1 = statusbarSnapshotWith(align = 1)

        SystemClockHooks.initClockStyle(clock, "clock", align2)
        assertEquals(View.TEXT_ALIGNMENT_TEXT_START, clock.textAlignment)

        SystemClockHooks.initClockStyle(clock, "clock", align1)
        assertEquals("align 1 must restore original text alignment", View.TEXT_ALIGNMENT_TEXT_END, clock.textAlignment)
    }

    @Test
    fun initClockStyle_verticalOffsetCustomThenDefaultRestoresOriginalTranslationY() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, translationY = 5f)

        val offset12 = statusbarSnapshotWith(verticalOffset = 12)
        val offset8 = statusbarSnapshotWith(verticalOffset = 8)

        SystemClockHooks.initClockStyle(clock, "clock", offset12)
        assertNotNull(clock.translationYValue)
        assertTrue(clock.translationYValue != 0f)

        SystemClockHooks.initClockStyle(clock, "clock", offset8)
        assertEquals("vertical offset 8 must restore original translationY", 5f, clock.translationYValue ?: Float.NaN, 0.001f)
    }

    @Test
    fun initClockStyle_leftRightMarginCustomThenDefaultRestoresOriginalMargins() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, leftMargin = 5, rightMargin = 7)

        val marginOn = statusbarSnapshotWith(leftMargin = 20, rightMargin = 30)
        val marginOff = statusbarSnapshotWith(leftMargin = 0, rightMargin = 0)

        SystemClockHooks.initClockStyle(clock, "clock", marginOn)
        val lp1 = clock.layoutParams as LinearLayout.LayoutParams
        assertTrue(lp1!!.leftMargin > 0)
        assertTrue(lp1.rightMargin > 0)

        SystemClockHooks.initClockStyle(clock, "clock", marginOff)
        val lp2 = clock.layoutParams as LinearLayout.LayoutParams
        assertEquals("left margin must restore", 5, lp2!!.leftMargin)
        assertEquals("right margin must restore", 7, lp2.rightMargin)
    }

    @Test
    fun initClockStyle_fixedWidthCustomThenDefaultRestoresOriginalWidth() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, width = 123)

        val widthOn = statusbarSnapshotWith(fixedWidth = 50)
        val widthOff = statusbarSnapshotWith(fixedWidth = 10)

        SystemClockHooks.initClockStyle(clock, "clock", widthOn)
        val lp1 = clock.layoutParams!!
        assertTrue(lp1.width != 123)

        SystemClockHooks.initClockStyle(clock, "clock", widthOff)
        val lp2 = clock.layoutParams!!
        assertEquals("fixed width off must restore original width", 123, lp2.width)
    }

    @Test
    fun initClockStyle_dualRowsFalseThenTrueThenFalseRestoresSingleLineAndLineSpacing() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, singleLine = true, maxLines = 1, lineSpacingMultiplier = 1.25f)

        val dualOn = statusbarSnapshotWith(customFormatEnable = true, customFormat = "HH\nmm")
        val dualOff = statusbarSnapshotWith()

        SystemClockHooks.initClockStyle(clock, "clock", dualOn)
        assertEquals(false, clock.singleLineValue)
        assertEquals(2, clock.maxLinesValue)

        SystemClockHooks.initClockStyle(clock, "clock", dualOff)
        assertEquals("single line must restore", true, clock.singleLineValue)
        assertEquals("max lines must restore", 1, clock.maxLinesValue)
        assertEquals("line spacing multiplier must restore", 1.25f, clock.lineSpacingMultiplier, 0.001f)
    }

    @Test
    fun initClockStyle_fontSizeCustomThenDefaultRestoresOriginalTextSize() {
        val clock = RecordingTextView()
        setOriginalStyle(clock, textSizePx = 40f)

        val bigFont = statusbarSnapshotWith(fontSize = 20)
        val defaultFont = statusbarSnapshotWith(fontSize = 13)

        SystemClockHooks.initClockStyle(clock, "clock", bigFont)
        assertEquals(1, clock.setTextSizeCalls.size)

        SystemClockHooks.initClockStyle(clock, "clock", defaultFont)
        assertEquals(2, clock.setTextSizeCalls.size)
        val last = clock.setTextSizeCalls.last()
        assertEquals("default font size must restore original px", TypedValue.COMPLEX_UNIT_PX, last.first)
        assertEquals(40f, last.second, 0.001f)
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

    @Test
    fun secondTicker_enablingSecondsCreatesOneActiveTicker() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val controller = makeControllerWithClock()
        SystemClockHooks.initSecondTicker(controller)

        val ticker = SystemClockHooks.activeSecondTicker(controller)
        assertNotNull("one active ticker must be stored on the controller", ticker)
        assertTrue("ScreenStateController must hold exactly one listener", screenStateListeners().size == 1)

        disposeTicker(ticker)
    }

    @Test
    fun secondTicker_repeatedSameSeconds_doesNotRestartTicker() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        val controller = makeControllerWithClock()
        SystemClockHooks.initSecondTicker(controller)
        val first = SystemClockHooks.activeSecondTicker(controller)

        SystemClockHooks.initSecondTicker(controller)
        val second = SystemClockHooks.activeSecondTicker(controller)

        assertSame("same seconds flags must keep the same ticker instance", first, second)
        assertEquals("only one ScreenStateController listener", 1, screenStateListeners().size)

        disposeTicker(second)
    }

    @Test
    fun secondTicker_disablingSecondsDisposesAndRemovesTicker() {
        val onSnapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(onSnapshot)

        val controller = makeControllerWithClock()
        SystemClockHooks.initSecondTicker(controller)
        val ticker = SystemClockHooks.activeSecondTicker(controller)
        assertNotNull(ticker)

        val offSnapshot = makeSnapshotWithSeconds(statusBar = false, cc = false)
        setCurrentSnapshot(offSnapshot)
        SystemClockHooks.initSecondTicker(controller)

        assertNull("ticker must be removed when seconds are off", SystemClockHooks.activeSecondTicker(controller))
        assertTrue("ScreenStateController listeners must be empty", screenStateListeners().isEmpty())
    }

    @Test
    fun secondTicker_onThenOffThenOn_createsNewTickerDoesNotStack() {
        val onSnapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(onSnapshot)

        val controller = makeControllerWithClock()
        SystemClockHooks.initSecondTicker(controller)
        val first = SystemClockHooks.activeSecondTicker(controller)!!

        val offSnapshot = makeSnapshotWithSeconds(statusBar = false, cc = false)
        setCurrentSnapshot(offSnapshot)
        SystemClockHooks.initSecondTicker(controller)

        val second = SystemClockHooks.activeSecondTicker(controller)
        assertNull(second)

        setCurrentSnapshot(onSnapshot)
        SystemClockHooks.initSecondTicker(controller)
        val third = SystemClockHooks.activeSecondTicker(controller)!!

        assertTrue("new ticker must not be the disposed old one", first !== third)
        assertEquals("exactly one listener at the end", 1, screenStateListeners().size)

        disposeTicker(third)
    }

    @Test
    fun secondTicker_holdsControllerWeakly() {
        val snapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(snapshot)

        fun createAndDrop(): Pair<Any, WeakReference<Any>> {
            val controller = makeControllerWithClock() as Any
            SystemClockHooks.initSecondTicker(controller)
            val ticker = SystemClockHooks.activeSecondTicker(controller)!!
            @Suppress("UNCHECKED_CAST")
            val ref = WeakReference(controller)
            return ticker to ref
        }

        val (ticker, controllerRef) = createAndDrop()
        java.lang.System.gc()
        Thread.sleep(200L)

        assertNull("ticker must not keep the controller alive after it is dropped", controllerRef.get())
        assertNotNull("ScreenStateController still holds the ticker", screenStateListeners().find { it === ticker })

        disposeTicker(ticker)
    }

    @Test
    fun secondTicker_tickerDoesNotHoldViewOrControllerStrongly() {
        val tickerClass = Class.forName("tv.withaibuild.customiuizer.mods.SystemClockHooks\$SecondTicker")
        val controllerField = tickerClass.getDeclaredField("clockControllerRef")
        assertEquals("SecondTicker must hold controller through a WeakReference", WeakReference::class.java, controllerField.type)

        val viewFields = tickerClass.declaredFields.filter {
            View::class.java.isAssignableFrom(it.type)
        }
        assertTrue("SecondTicker must not hold any View field strongly", viewFields.isEmpty())
    }

    @Test
    fun secondTicker_disablingSecondsStopsPendingRuitables() {
        val onSnapshot = makeSnapshotWithSeconds(statusBar = true, cc = false)
        setCurrentSnapshot(onSnapshot)

        val controller = makeControllerWithClock()
        SystemClockHooks.initSecondTicker(controller)

        val offSnapshot = makeSnapshotWithSeconds(statusBar = false, cc = false)
        setCurrentSnapshot(offSnapshot)
        SystemClockHooks.initSecondTicker(controller)

        val ticker = SystemClockHooks.activeSecondTicker(controller)
        assertNull(ticker)
    }
}
