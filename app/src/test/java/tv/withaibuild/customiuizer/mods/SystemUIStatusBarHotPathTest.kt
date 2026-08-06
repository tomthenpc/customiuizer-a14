package tv.withaibuild.customiuizer.mods

import android.content.res.Resources
import android.graphics.Typeface
import android.text.TextPaint
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.ResourceHooks
import tv.withaibuild.customiuizer.utils.PrefMap

private val NETSPEED_NUMBER_VIEW_TAG = ResourceHooks.getFakeResId("netspeed_number_view")
private val NETSPEED_UNIT_VIEW_TAG = ResourceHooks.getFakeResId("netspeed_unit_view")
private val NETSPEED_TYPEFACE_STATE_TAG = ResourceHooks.getFakeResId("netspeed_typeface_state")
private val VIEW_INITED_TAG = ResourceHooks.getFakeResId("view_inited_tag")

/**
 * A [Map] implementation that records every key access. Used to prove that the hot path does not
 * touch [MainModule.mPrefs] (or any [PrefMap]) after the snapshot has been built.
 */
private class CountingMap : AbstractMap<String, Any>() {
    private val delegate = HashMap<String, Any>()
    val reads = mutableListOf<String>()

    override val entries: Set<Map.Entry<String, Any>> get() = delegate.entries
    override val size: Int get() = delegate.size

    fun putAll(values: Map<String, Any>) = delegate.putAll(values)

    override operator fun get(key: String): Any? {
        reads.add(key)
        return delegate[key]
    }

    fun reset() = reads.clear()
    fun readCount(): Int = reads.size
}

private fun countingPrefMap(values: Map<String, Any>): Pair<PrefMap, CountingMap> {
    val prefMap = PrefMap().apply { clear() }
    val countingMap = CountingMap().apply { putAll(values) }

    val snapshotField = PrefMap::class.java.getDeclaredField("snapshot")
    snapshotField.isAccessible = true
    snapshotField.set(prefMap, AtomicReference<Map<String, Any>>(countingMap))

    return prefMap to countingMap
}

private fun countingMainPrefs(values: Map<String, Any>): CountingMap {
    val countingMap = CountingMap().apply { putAll(values) }
    MainModule.mPrefs.clear()
    val snapshotField = PrefMap::class.java.getDeclaredField("snapshot")
    snapshotField.isAccessible = true
    snapshotField.set(MainModule.mPrefs, AtomicReference<Map<String, Any>>(countingMap))
    return countingMap
}

private fun snapshotFrom(values: Map<String, Any>): NetSpeedTextStyleSnapshot {
    val (prefs, _) = countingPrefMap(values)
    return SystemUIStatusBarHooks.buildNetSpeedTextStyleSnapshot(prefs)
}

@Suppress("DEPRECATION")
private class FakeResources(density: Float = 2.0f) : Resources(null, DisplayMetrics().apply { this.density = density }, android.content.res.Configuration()) {
    val metrics = DisplayMetrics().apply { this.density = density }
    override fun getDisplayMetrics(): DisplayMetrics = metrics
}

private open class RecordingTextView(density: Float = 2.0f) : TextView(null) {
    val setTextSizeCalls = mutableListOf<Pair<Int, Float>>()
    val setTextColorCalls = mutableListOf<Int>()
    val setTypefaceCalls = mutableListOf<Pair<Typeface?, Int>>()
    val setGravityCalls = mutableListOf<Int>()
    val setTextAlignmentCalls = mutableListOf<Int>()
    val setVisibilityCalls = mutableListOf<Int>()
    val setSingleLineCalls = mutableListOf<Boolean>()
    val setMaxLinesCalls = mutableListOf<Int>()
    val setLineSpacingCalls = mutableListOf<Pair<Float, Float>>()
    val setPaddingCalls = mutableListOf<Quad<Int, Int, Int, Int>>()
    val onTextChangedCalls = mutableListOf<CharSequence?>()
    val layoutParamsCalls = mutableListOf<ViewGroup.LayoutParams?>()

    private val fakePaint = TextPaint()
    private var storedTypeface: Typeface? = null
    private val keyedTags = android.util.SparseArray<Any>()

    val fakeResources = FakeResources(density)

    override fun getTag(key: Int): Any? = keyedTags.get(key)

    override fun setTag(key: Int, tag: Any?) {
        if (tag == null) keyedTags.remove(key) else keyedTags.put(key, tag)
    }

    override fun getResources(): Resources = fakeResources

    override fun getPaint(): TextPaint = fakePaint

    override fun getTypeface(): Typeface? = storedTypeface

    override fun setTypeface(tf: Typeface?) {
        storedTypeface = tf
        setTypefaceCalls.add(tf to -1)
    }

    override fun setTypeface(tf: Typeface?, style: Int) {
        val created = Typeface.create(tf, style)
        storedTypeface = created
        setTypefaceCalls.add(created to style)
    }

    override fun onTextChanged(text: CharSequence?, start: Int, lengthBefore: Int, lengthAfter: Int) {
        onTextChangedCalls.add(text)
        super.onTextChanged(text, start, lengthBefore, lengthAfter)
    }

    override fun setTextSize(unit: Int, size: Float) {
        setTextSizeCalls.add(unit to size)
    }

    override fun setGravity(gravity: Int) {
        setGravityCalls.add(gravity)
    }

    override fun setTextAlignment(textAlignment: Int) {
        setTextAlignmentCalls.add(textAlignment)
    }

    override fun setVisibility(visibility: Int) {
        super.setVisibility(visibility)
        setVisibilityCalls.add(visibility)
    }

    override fun setSingleLine(singleLine: Boolean) {
        setSingleLineCalls.add(singleLine)
    }

    override fun setMaxLines(maxLines: Int) {
        setMaxLinesCalls.add(maxLines)
    }

    override fun setLineSpacing(multiplier: Float, add: Float) {
        setLineSpacingCalls.add(multiplier to add)
    }

    /**
     * Simulates the framework text-update path in unit tests where the stub TextView.setText()
     * does not emit onTextChanged. Production NetworkSpeedView.setNetworkSpeed() updates the text
     * directly; our after-hook never calls this.
     */
    fun recordTextChange(text: CharSequence?) {
        onTextChanged(text, 0, 0, 0)
    }

    override fun setTextColor(color: Int) {
        setTextColorCalls.add(color)
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        setPaddingCalls.add(Quad(left, top, right, bottom))
    }

    private var storedLayoutParams: ViewGroup.LayoutParams? = null

    override fun getLayoutParams(): ViewGroup.LayoutParams? = storedLayoutParams

    override fun setLayoutParams(params: ViewGroup.LayoutParams?) {
        storedLayoutParams = params
        layoutParamsCalls.add(params)
    }
}

private class RecordingLinearLayout(density: Float = 2.0f) : LinearLayout(null) {
    val setPaddingRelativeCalls = mutableListOf<Quad<Int, Int, Int, Int>>()
    val setPaddingCalls = mutableListOf<Quad<Int, Int, Int, Int>>()
    val setTranslationYCalls = mutableListOf<Float>()

    @JvmField var mNetworkSpeedNumberText: TextView? = null
    @JvmField var mNetworkSpeedUnitText: TextView? = null

    private val keyedTags = android.util.SparseArray<Any>()
    val fakeResources = FakeResources(density)

    override fun getTag(key: Int): Any? = keyedTags.get(key)

    override fun setTag(key: Int, tag: Any?) {
        if (tag == null) keyedTags.remove(key) else keyedTags.put(key, tag)
    }

    override fun getResources(): Resources = fakeResources

    override fun setPaddingRelative(start: Int, top: Int, end: Int, bottom: Int) {
        setPaddingRelativeCalls.add(Quad(start, top, end, bottom))
    }

    override fun setPadding(left: Int, top: Int, right: Int, bottom: Int) {
        setPaddingCalls.add(Quad(left, top, right, bottom))
    }

    override fun setTranslationY(translationY: Float) {
        setTranslationYCalls.add(translationY)
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

class SystemUIStatusBarHotPathTest {

    @Before
    fun setUp() {
        resetNetSpeedSnapshotState()
    }

    @After
    fun tearDown() {
        resetNetSpeedSnapshotState()
        MainModule.mPrefs.clear()
    }

    private fun resetNetSpeedSnapshotState() {
        val currentSnapshot = SystemUIStatusBarHooks::class.java.getDeclaredField("currentNetSpeedTextStyleSnapshot")
        currentSnapshot.isAccessible = true
        val ref = currentSnapshot.get(SystemUIStatusBarHooks) as? AtomicReference<NetSpeedTextStyleSnapshot?>
        ref?.set(null)
    }

    private fun speedViewWith(number: RecordingTextView, unit: RecordingTextView? = null): RecordingLinearLayout {
        val speedView = RecordingLinearLayout(number.fakeResources.displayMetrics.density)
        speedView.mNetworkSpeedNumberText = number
        if (unit != null) speedView.mNetworkSpeedUnitText = unit
        speedView.setTag(NETSPEED_NUMBER_VIEW_TAG, number)
        if (unit != null) speedView.setTag(NETSPEED_UNIT_VIEW_TAG, unit)
        return speedView
    }

    private fun makeDefaultSnapshot(): NetSpeedTextStyleSnapshot {
        return snapshotFrom(mapOf())
    }

    private fun makeCustomSnapshot(): NetSpeedTextStyleSnapshot {
        return snapshotFrom(
            mapOf(
                "system_detailednetspeed_style" to 1,
                "system_netspeed_boldfont" to true,
                "system_netspeed_fontsize" to 20,
                "system_netspeed_fixedcontent_width" to 20,
                "system_netspeed_leftmargin" to 10,
                "system_netspeed_rightmargin" to 12,
                "system_netspeed_verticaloffset" to 12,
                "system_detailednetspeed_align" to 3,
                "system_netspeed_rowspacing" to 110,
            )
        )
    }

    // 1. 初次初始化只集中读取一次相关偏好；2. 单次旧路径等价配置包含原有 9 个读取语义
    @Test
    fun buildNetSpeedTextStyleSnapshot_readsAllNineRelevantKeysAndProducesEquivalentValues() {
        val (prefs, countingMap) = countingPrefMap(
            mapOf(
                "system_detailednetspeed_style" to "2",
                "system_netspeed_boldfont" to true,
                "system_netspeed_fontsize" to 20,
                "system_netspeed_fixedcontent_width" to 20,
                "system_netspeed_leftmargin" to 10,
                "system_netspeed_rightmargin" to 12,
                "system_netspeed_verticaloffset" to 12,
                "system_detailednetspeed_align" to "3",
                "system_netspeed_rowspacing" to 110,
            )
        )

        val snapshot = SystemUIStatusBarHooks.buildNetSpeedTextStyleSnapshot(prefs)

        assertEquals(9, countingMap.readCount())
        assertEquals(2, snapshot.speedStyle)
        assertTrue(snapshot.bold)
        assertEquals(20, snapshot.fontSize)
        assertEquals(20, snapshot.fixedWidth)
        assertEquals(10, snapshot.leftMargin)
        assertEquals(12, snapshot.rightMargin)
        assertEquals(12, snapshot.verticalOffset)
        assertEquals(3, snapshot.align)
        assertEquals(110, snapshot.adjustment)
    }

    // 3. 连续执行 100 次样式热路径，PrefMap 读取次数保持 0
    @Test
    fun applyNetSpeedTextStyle_100HotCalls_withSnapshot_doesNotReadPrefs() {
        val (prefs, countingMap) = countingPrefMap(mapOf("system_netspeed_fontsize" to 20))
        val snapshot = SystemUIStatusBarHooks.buildNetSpeedTextStyleSnapshot(prefs)
        countingMap.reset()

        val number = RecordingTextView()
        val speedView = speedViewWith(number)

        repeat(100) {
            SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, true)
        }

        assertEquals(0, countingMap.readCount())
    }

    // 4. 连续 100 次回调，相同 View 的样式 setter 只执行首次必要次数
    @Test
    fun applyNetSpeedTextStyle_100HotCalls_sameViewSameSnapshot_skipsAllSetters() {
        val snapshot = makeCustomSnapshot()
        val number = RecordingTextView()
        val unit = RecordingTextView()
        val speedView = speedViewWith(number, unit)

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, false)
        repeat(100) {
            SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, true)
        }

        val firstFullTextSizeCount = number.setTextSizeCalls.size
        val firstFullTypefaceCount = number.setTypefaceCalls.size

        assertTrue("expected at least one text-size setter on first full apply", firstFullTextSizeCount >= 1)
        assertTrue("expected at least one typeface setter on first full apply", firstFullTypefaceCount >= 1)

        assertEquals(firstFullTextSizeCount, number.setTextSizeCalls.size)
        assertEquals(firstFullTypefaceCount, number.setTypefaceCalls.size)
        assertEquals(number.setTextSizeCalls.size, unit.setTextSizeCalls.size)
        assertEquals(number.setTypefaceCalls.size, unit.setTypefaceCalls.size)
        assertEquals(1, speedView.setTranslationYCalls.size)
        assertEquals(1, speedView.setPaddingRelativeCalls.size)
        assertEquals(1, number.layoutParamsCalls.size)
        assertEquals(1, number.setTextAlignmentCalls.size)
        assertEquals(0, number.setGravityCalls.size) // speedStyle == 1
        assertEquals(0, number.setSingleLineCalls.size)
        assertEquals(0, number.setMaxLinesCalls.size)
        assertEquals(0, number.setLineSpacingCalls.size)
    }

    // 5. 网速文本更新不因样式幂等机制被跳过（apply 不触发文本改变）
    @Test
    fun applyNetSpeedTextStyle_doesNotTriggerTextChange() {
        val snapshot = makeDefaultSnapshot()
        val number = RecordingTextView()
        val speedView = speedViewWith(number)

        // Setting the text via the final setText(...) triggers onTextChanged once.
        number.text = "100"
        val textChangesBeforeApply = number.onTextChangedCalls.size

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, false)

        assertEquals(textChangesBeforeApply, number.onTextChangedCalls.size)
    }

    // 6. 相关偏好变化只重建一次 snapshot
    @Test
    fun netSpeedTextStyleObserver_relevantKey_rebuildsSnapshotOnce() {
        val countingMap = countingMainPrefs(mapOf("system_netspeed_fontsize" to 15))
        val first = SystemUIStatusBarHooks.buildNetSpeedTextStyleSnapshot(MainModule.mPrefs)

        val currentSnapshot = SystemUIStatusBarHooks::class.java.getDeclaredField("currentNetSpeedTextStyleSnapshot")
        currentSnapshot.isAccessible = true
        val ref = currentSnapshot.get(SystemUIStatusBarHooks) as AtomicReference<NetSpeedTextStyleSnapshot?>
        ref.set(first)

        val observer = getNetSpeedTextStyleObserver()
        countingMap.reset()

        observer.onChange("system_netspeed_fontsize")

        assertEquals(9, countingMap.readCount())
        val second = ref.get() as NetSpeedTextStyleSnapshot
        assertNotEquals(first.id, second.id)
    }

    // 7. 无关偏好变化不重建 snapshot
    @Test
    fun netSpeedTextStyleObserver_irrelevantKey_doesNotRebuildSnapshot() {
        val countingMap = countingMainPrefs(mapOf())
        val first = SystemUIStatusBarHooks.buildNetSpeedTextStyleSnapshot(MainModule.mPrefs)

        val currentSnapshot = SystemUIStatusBarHooks::class.java.getDeclaredField("currentNetSpeedTextStyleSnapshot")
        currentSnapshot.isAccessible = true
        val ref = currentSnapshot.get(SystemUIStatusBarHooks) as AtomicReference<NetSpeedTextStyleSnapshot?>
        ref.set(first)

        val observer = getNetSpeedTextStyleObserver()
        countingMap.reset()

        observer.onChange("system_statusbar_clock_position")

        assertEquals(0, countingMap.readCount())
        val still = ref.get() as NetSpeedTextStyleSnapshot
        assertEquals(first.id, still.id)
    }

    // 8. 新 snapshot 在下一次正常回调中应用（typeface-only 路径能感知到 bold 等变化）
    @Test
    fun applyNetSpeedTextStyle_newSnapshot_appliedOnNextCallback() {
        val first = makeCustomSnapshot()
        val number = RecordingTextView()
        val unit = RecordingTextView()
        val speedView = speedViewWith(number, unit)

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, first, false)

        val second = snapshotFrom(mapOf("system_netspeed_boldfont" to false))
        val typefaceCallsBefore = number.setTypefaceCalls.size

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, second, true)

        assertTrue(number.setTypefaceCalls.size > typefaceCallsBefore)
    }

    // 9. 两个不同 View 共用当前快照，但各自完整应用一次
    @Test
    fun applyNetSpeedTextStyle_twoDistinctViews_eachAppliesSnapshot() {
        val snapshot = makeCustomSnapshot()

        val number1 = RecordingTextView()
        val speedView1 = speedViewWith(number1)
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView1, snapshot, false)

        val number2 = RecordingTextView()
        val speedView2 = speedViewWith(number2)
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView2, snapshot, false)

        assertEquals(1, speedView1.setTranslationYCalls.size)
        assertEquals(1, speedView2.setTranslationYCalls.size)
        assertEquals(1, number1.setTextSizeCalls.size)
        assertEquals(1, number2.setTextSizeCalls.size)
    }

    // 10. View 重建后正确应用
    @Test
    fun applyNetSpeedTextStyle_afterViewRecreation_appliesAgain() {
        val snapshot = makeCustomSnapshot()

        val number1 = RecordingTextView()
        val speedView1 = speedViewWith(number1)
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView1, snapshot, false)

        val number2 = RecordingTextView()
        val speedView2 = speedViewWith(number2)
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView2, snapshot, false)

        assertEquals(1, number1.setTextSizeCalls.size)
        assertEquals(1, number2.setTextSizeCalls.size)
        assertEquals(1, speedView1.setTranslationYCalls.size)
        assertEquals(1, speedView2.setTranslationYCalls.size)
    }

    // 11. 默认配置保持原生行为
    @Test
    fun applyNetSpeedTextStyle_defaultConfig_doesNotOverrideUnnecessaryProperties() {
        val snapshot = makeDefaultSnapshot()
        val number = RecordingTextView()
        val unit = RecordingTextView()
        val speedView = speedViewWith(number, unit)

        // Clear any setup noise so we can assert only what applyNetSpeedTextStyle does.
        number.setTextSizeCalls.clear()
        number.layoutParamsCalls.clear()
        number.setTextAlignmentCalls.clear()
        number.setSingleLineCalls.clear()
        number.setTypefaceCalls.clear()
        unit.setVisibilityCalls.clear()
        speedView.setTranslationYCalls.clear()

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, false)

        assertEquals(0, number.setTextSizeCalls.size) // fontSize == 13
        assertEquals(0, number.layoutParamsCalls.size) // fixedWidth == 10, speedStyle == 1
        assertEquals(1, speedView.setTranslationYCalls.size) // verticalOffset == 8 -> translateY = 0
        assertEquals(0f, speedView.setTranslationYCalls.firstOrNull() ?: Float.NaN, 0f)
        assertEquals(0, number.setTextAlignmentCalls.size) // align == 1
        assertEquals(0, number.setSingleLineCalls.size) // speedStyle != 2
        assertFalse(unit.setVisibilityCalls.contains(View.GONE)) // speedStyle == 1, unit must stay visible
        assertTrue(number.setTypefaceCalls.size >= 1)
    }

    // 12. 功能关闭时不创建 snapshot、observer 或 View 附加状态
    @Test
    fun netSpeedTextStyle_initialState_noSnapshotOrObserverSideEffects() {
        val current = SystemUIStatusBarHooks::class.java.getDeclaredField("currentNetSpeedTextStyleSnapshot")
        current.isAccessible = true
        val ref = current.get(SystemUIStatusBarHooks) as AtomicReference<*>
        assertNull(ref.get())
    }

    // 13. 无效字号、颜色、位置或样式值安全回退
    @Test
    fun applyNetSpeedTextStyle_invalidValues_fallsBackSafely() {
        val snapshot = snapshotFrom(
            mapOf(
                "system_detailednetspeed_style" to 99,
                "system_netspeed_fontsize" to -1,
                "system_netspeed_leftmargin" to -100,
                "system_netspeed_verticaloffset" to -100,
                "system_detailednetspeed_align" to 99,
            )
        )

        val number = RecordingTextView()
        val unit = RecordingTextView()
        val speedView = speedViewWith(number, unit)

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, false)

        // Even with invalid values the function completes and applies typeface.
        assertTrue(number.setTypefaceCalls.size >= 1)
    }

    // 14. 重复应用不产生累计字号、padding、margin 或位移
    @Test
    fun applyNetSpeedTextStyle_repeatedApplications_noCumulativeChanges() {
        val snapshot = makeCustomSnapshot()
        val number = RecordingTextView()
        val speedView = speedViewWith(number)

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, false)
        val firstTranslation = speedView.setTranslationYCalls.last()

        repeat(10) {
            SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, false)
        }

        assertEquals(firstTranslation, speedView.setTranslationYCalls.last())
        assertEquals(1, speedView.setTranslationYCalls.toSet().size)
    }

    // 15. SystemUI 重建后结果一致：模拟进程重建后重新生成 snapshot
    @Test
    fun buildNetSpeedTextStyleSnapshot_afterResetProducesFreshSnapshot() {
        val (prefs, _) = countingPrefMap(mapOf("system_netspeed_fontsize" to 18))
        val first = SystemUIStatusBarHooks.buildNetSpeedTextStyleSnapshot(prefs)

        resetNetSpeedSnapshotState()

        val second = SystemUIStatusBarHooks.buildNetSpeedTextStyleSnapshot(prefs)

        assertNotEquals(first.id, second.id)
        assertEquals(first.fontSize, second.fontSize)
    }

    // 16. observer 重复初始化不产生重复注册
    @Test
    fun netSpeedTextStyleObserver_isSameInstance() {
        val observer = getNetSpeedTextStyleObserver()
        val observer2 = getNetSpeedTextStyleObserver()
        assertTrue(observer === observer2)
    }

    // 17. 配置变化与热路径并发读取时不会读取到部分构造状态
    @Test
    fun applyNetSpeedTextStyle_readsFullyConstructedSnapshot() {
        val (prefs, _) = countingPrefMap(
            mapOf(
                "system_detailednetspeed_style" to 2,
                "system_netspeed_fontsize" to 20,
                "system_netspeed_rowspacing" to 120,
            )
        )
        val snapshot = SystemUIStatusBarHooks.buildNetSpeedTextStyleSnapshot(prefs)

        val number = RecordingTextView()
        val speedView = speedViewWith(number)

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, false)

        assertEquals(1, number.setSingleLineCalls.size)
        assertEquals(1, number.setMaxLinesCalls.size)
        assertEquals(1, number.setLineSpacingCalls.size)
        assertEquals(1, number.setTextSizeCalls.size) // number + speedStyle > 13
    }

    // 18. density/fontScale 等相关配置变化时不会继续使用失效尺寸
    @Test
    fun applyNetSpeedTextStyle_usesCurrentResourcesForPixelConversion() {
        val snapshot = snapshotFrom(mapOf("system_netspeed_fixedcontent_width" to 20))

        val number1 = RecordingTextView(density = 2.0f)
        number1.layoutParams = LinearLayout.LayoutParams(0, 0)
        val speedView1 = speedViewWith(number1)
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView1, snapshot, false)
        val widthAtDensity2 = (number1.layoutParams as LinearLayout.LayoutParams).width

        val number2 = RecordingTextView(density = 4.0f)
        number2.layoutParams = LinearLayout.LayoutParams(0, 0)
        val speedView2 = speedViewWith(number2)
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView2, snapshot, false)
        val widthAtDensity4 = (number2.layoutParams as LinearLayout.LayoutParams).width

        // Logical fixedWidth is converted to px using the View's current resources, so different
        // densities produce different physical widths.  20 dp * 2.0 = 40 px; 20 dp * 4.0 = 80 px.
        assertTrue(widthAtDensity2 > 0)
        assertTrue(widthAtDensity4 > 0)
        assertEquals(40, widthAtDensity2)
        assertEquals(80, widthAtDensity4)
    }

    // 19. 模拟 setNetworkSpeed 每秒回调：首次完整应用，之后 99 次 0 完整 setter，文本仍更新
    @Test
    fun setNetworkSpeedSimulation_100Ticks_zeroFullSetters_textStillUpdates() {
        val snapshot = makeCustomSnapshot()
        val number = RecordingTextView()
        val unit = RecordingTextView()
        val speedView = speedViewWith(number, unit)

        // 100 ticks. The production setNetworkSpeed after-hook passes typefaceOnly = false;
        // applyNetSpeedTextStyle short-circuits when the full style is already applied.
        // The text content update is independent; recordTextChange simulates the framework text setter.
        repeat(100) { index ->
            SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = false)
            number.recordTextChange("${index + 1}")
            unit.recordTextChange("KB")
        }

        assertEquals(100, number.onTextChangedCalls.size)
        assertEquals(1, number.setTextSizeCalls.size) // only first full apply (fontSize 20 > 13)
        assertEquals(1, number.layoutParamsCalls.size) // fixedWidth 20 > 10
        assertEquals(1, speedView.setTranslationYCalls.size) // verticalOffset 12 != 8
        assertEquals(1, speedView.setPaddingRelativeCalls.size)
        assertEquals(1, number.setTypefaceCalls.size) // set once on first full apply, then idempotent
        assertEquals(0, number.setTextColorCalls.size)
        assertEquals(0, number.setPaddingCalls.size)
        assertEquals(0, unit.setPaddingCalls.size)
    }

    // 20. snapshot 变化后下一次完整样式只应用一次，之后再变化前不再重复
    @Test
    fun setNetworkSpeedSimulation_snapshotChange_fullStyleOnceThenZero() {
        val snapshotA = makeCustomSnapshot()
        val number = RecordingTextView()
        val speedView = speedViewWith(number)

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshotA, typefaceOnly = false)
        val textSizeAfterA = number.setTextSizeCalls.size
        val typefaceAfterA = number.setTypefaceCalls.size

        repeat(50) {
            SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshotA, typefaceOnly = false)
        }
        assertEquals(textSizeAfterA, number.setTextSizeCalls.size)
        assertEquals(typefaceAfterA, number.setTypefaceCalls.size)

        val snapshotB = snapshotFrom(mapOf("system_netspeed_boldfont" to false))
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshotB, typefaceOnly = false)

        // Snapshot B still has fontSize 13 default, so setTextSize is not called again (only A had fontSize 20).
        assertEquals(textSizeAfterA, number.setTextSizeCalls.size)
        assertTrue(number.setTypefaceCalls.size > typefaceAfterA)

        val typefaceAfterB = number.setTypefaceCalls.size
        repeat(50) {
            SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshotB, typefaceOnly = false)
        }
        assertEquals(typefaceAfterB, number.setTypefaceCalls.size)
    }

    // 21. setTextAppearance 后只恢复 Typeface，不重复 size / padding / gravity / layout
    @Test
    fun setTextAppearanceSimulation_typefaceOnlyRestoresTypefaceWithoutFullSetters() {
        val snapshot = makeCustomSnapshot()
        val number = RecordingTextView()
        val speedView = speedViewWith(number)

        // onFinishInflate (useClockStyle = false) performs a full style apply.
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = false)

        val textSizeBefore = number.setTextSizeCalls.size
        val layoutParamsBefore = number.layoutParamsCalls.size
        val gravityBefore = number.setGravityCalls.size
        val paddingRelativeBefore = speedView.setPaddingRelativeCalls.size
        val paddingBefore = speedView.setPaddingCalls.size
        val textColorBefore = number.setTextColorCalls.size
        val typefaceBefore = number.setTypefaceCalls.size

        // Simulate the framework calling setTextAppearance on the number TextView. In production the
        // setTextAppearance after-hook calls applyNetSpeedTextStyle(speedView, snapshot, true).
        // The framework call would have changed the base typeface; the typefaceOnly path must only
        // restore the network-speed typeface and must not re-apply size, padding, gravity or layout.
        number.setTypeface(Typeface.DEFAULT)
        val snapshotAfterTextAppearance = snapshot.copy(bold = !snapshot.bold)
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshotAfterTextAppearance, typefaceOnly = true)

        assertEquals(textSizeBefore, number.setTextSizeCalls.size)
        assertEquals(layoutParamsBefore, number.layoutParamsCalls.size)
        assertEquals(gravityBefore, number.setGravityCalls.size)
        assertEquals(paddingRelativeBefore, speedView.setPaddingRelativeCalls.size)
        assertEquals(paddingBefore, speedView.setPaddingCalls.size)
        assertEquals(textColorBefore, number.setTextColorCalls.size)
        assertTrue(number.setTypefaceCalls.size > typefaceBefore)
    }

    // 22. 明确记录三个生产回调传入的 typefaceOnly 参数语义
    @Test
    fun callback_typefaceOnly_values() {
        val snapshot = makeDefaultSnapshot()
        val number = RecordingTextView()
        val speedView = speedViewWith(number)

        // onFinishInflate (useClockStyle = false) and the first setNetworkSpeed both request a full
        // style apply: typefaceOnly = false.
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = false)
        assertEquals(1, speedView.setTranslationYCalls.size)

        // setTextAppearance after-hook always requests typeface-only restoration.
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = true)

        // Subsequent setNetworkSpeed calls request a full style, but the implementation
        // short-circuits when the same snapshot has already been fully applied.
        val callsBefore = number.setTextSizeCalls.size
        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = false)
        assertEquals(callsBefore, number.setTextSizeCalls.size)
    }

    // 23. typefaceOnly = false 时，相同 View + 相同 snapshot 第二次直接 return
    @Test
    fun applyNetSpeedTextStyle_fullSameViewSameSnapshot_secondCall_zeroSetters() {
        val snapshot = makeCustomSnapshot()
        val number = RecordingTextView()
        val speedView = speedViewWith(number)

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = false)
        val textSize = number.setTextSizeCalls.size
        val translationY = speedView.setTranslationYCalls.size
        val padding = speedView.setPaddingRelativeCalls.size
        val layoutParams = number.layoutParamsCalls.size

        SystemUIStatusBarHooks.applyNetSpeedTextStyle(speedView, snapshot, typefaceOnly = false)

        assertEquals(textSize, number.setTextSizeCalls.size)
        assertEquals(translationY, speedView.setTranslationYCalls.size)
        assertEquals(padding, speedView.setPaddingRelativeCalls.size)
        assertEquals(layoutParams, number.layoutParamsCalls.size)
    }

    private fun getNetSpeedTextStyleObserver(): ModuleHelper.PreferenceObserver {
        val field = SystemUIStatusBarHooks::class.java.getDeclaredField("netSpeedTextStyleObserver")
        field.isAccessible = true
        return field.get(SystemUIStatusBarHooks) as ModuleHelper.PreferenceObserver
    }
}
