package tv.withaibuild.customiuizer.utils

import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.CornerPathEffect
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.media.audiofx.Visualizer
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.palette.graphics.Palette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.io.File
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class AudioVisualizer @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var mHeight = 0
    private var mWidth = 0
    private val mDensity: Float = context.resources.displayMetrics.density
    private val mPaint: Paint
    private var mGlowPaint = Paint()
    @Volatile
    private var mVisualizer: Visualizer? = null
    private val visualizerLock = Any()
    @Volatile
    private var detached = false
    internal val isDisposed: Boolean
        get() = detached
    internal var onDisposed: ((AudioVisualizer) -> Unit)? = null
    private var mVisualizerColorAnimator: ObjectAnimator? = null
    private var mVisualizerGlowColorAnimator: ObjectAnimator? = null

    private val mFFTPoints = FloatArray(128)
    private val mBands = floatArrayOf(
        50f, 90f, 130f, 180f, 220f, 260f, 320f, 380f, 430f, 520f, 610f, 700f, 770f, 920f, 1080f,
        1270f, 1480f, 1720f, 2000f, 2320f, 2700f, 3135f, 3700f, 4400f, 5300f, 6400f, 7700f, 9500f,
        10500f, 12000f, 16000f
    )
    private var maxDb = 50f
    private val maxDp = 280
    private val mBandsNum = 31
    private var mFftSize = 0
    private val mBandBinLimits = IntArray(mBandsNum) { Int.MAX_VALUE }
    private val mBandStarts = FloatArray(mBandsNum) { Float.MAX_VALUE }
    private val mBandTargets = FloatArray(mBandsNum) { Float.MAX_VALUE }
    private val mPendingTargets = FloatArray(mBandsNum) { Float.MAX_VALUE }
    private val mFrameLock = Any()

    private var isMusicPlaying = false
    @JvmField
    var isScreenOn = false
    private var isOnKeyguard = false
    private var isExpandedPanel = false
    private var isOnCustomLockScreen = false
    private var mPlaying = false
    private var mDisplaying = false
    private var mOpaqueColor = Color.TRANSPARENT
    private var mColor = Color.TRANSPARENT

    private var mArt: Bitmap? = null
    private var mProcessedArt: Bitmap? = null

    private val mRainbow = IntArray(mBandsNum)
    private val mRainbowVertical = IntArray(mBandsNum)
    private val mPositions = FloatArray(mBandsNum)
    private val mLinePath = Path()
    private val mHsv = FloatArray(3)
    private val mDashIntervals = FloatArray(2)

    @Volatile
    private var mNewDataPending = false
    private var mFrameStartTime = 0L
    private var mFrameCallbackScheduled = false
    private val mFrameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (detached || !mDisplaying) {
                mFrameCallbackScheduled = false
                return
            }
            if (mNewDataPending) {
                applyNewData(frameTimeNanos)
            }
            val elapsedMs = (frameTimeNanos - mFrameStartTime) / 1_000_000f
            val fraction = (elapsedMs / animDur).coerceIn(0f, 1f)
            var needsInvalidate = false
            for (i in 0 until mBandsNum) {
                val start = mBandStarts[i]
                val target = mBandTargets[i]
                if (start == Float.MAX_VALUE || target == Float.MAX_VALUE) continue
                val interp = if (target < start) decel.getInterpolation(fraction) else accel.getInterpolation(fraction)
                val newVal = start + (target - start) * interp
                if (mFFTPoints[i * 4 + 3] != newVal) {
                    mFFTPoints[i * 4 + 3] = newVal
                    needsInvalidate = true
                }
            }
            if (needsInvalidate) postInvalidateOnAnimation()
            if (mDisplaying) {
                Choreographer.getInstance().postFrameCallback(this)
            } else {
                mFrameCallbackScheduled = false
            }
        }
    }

    private fun applyNewData(frameTimeNanos: Long) {
        synchronized(mFrameLock) {
            if (mNewDataPending) {
                System.arraycopy(mPendingTargets, 0, mBandTargets, 0, mBandsNum)
                mNewDataPending = false
            }
        }
        for (i in 0 until mBandsNum) {
            mBandStarts[i] = mFFTPoints[i * 4 + 3]
        }
        mFrameStartTime = frameTimeNanos
    }

    private fun startFrameScheduler() {
        if (mFrameCallbackScheduled) return
        mFrameCallbackScheduled = true
        Choreographer.getInstance().postFrameCallback(mFrameCallback)
    }

    private fun stopFrameScheduler() {
        mFrameCallbackScheduled = false
        Choreographer.getInstance().removeFrameCallback(mFrameCallback)
    }

    @JvmField
    var showOnCustom = false
    private var animDur = 0
    private var transparency = 0
    private lateinit var colorMode: ColorMode
    private lateinit var barStyle: BarStyle
    private lateinit var renderType: RenderType
    private var glowLevel = 0
    private var customColor = 0
    private var randomizeInterval = 0
    @JvmField
    var showInDrawer = false
    @JvmField
    var showWithControllerOnly = false

    private val accel = AccelerateInterpolator()
    private val decel = DecelerateInterpolator()

    private val viewScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var randomizeColorJob: Job? = null
    private var paletteGenerationJob: Job? = null

    enum class BarStyle {
        DUMMY, SOLID, SOLID_ROUNDED, DASHED, CIRCLES, LINE
    }

    enum class ColorMode {
        DUMMY, MATCH, STATIC, RAINBOW_H, RAINBOW_V, DYNAMIC
    }

    enum class RenderType {
        AUTO, LINES, PATH
    }

    private val preferenceObserver = object : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) {
            if (detached) return
            try {
                when (key) {
                    "pref_key_system_visualizer_animdur" ->
                        animDur = MainModule.mPrefs.getInt("system_visualizer_animdur", 65)
                    "pref_key_system_visualizer_transp" -> {
                        transparency = (255f - 255f * MainModule.mPrefs.getInt("system_visualizer_transp", 40) / 100f).roundToInt()
                        setColor(mOpaqueColor)
                        updateRainbowColors()
                    }
                    "pref_key_system_visualizer_color" -> {
                        colorMode = ColorMode.values()[MainModule.mPrefs.getStringAsInt("system_visualizer_color", 1)]
                        updateBarStyle()
                        updateColorMode()
                    }
                    "pref_key_system_visualizer_style" -> {
                        barStyle = BarStyle.values()[MainModule.mPrefs.getStringAsInt("system_visualizer_style", 1)]
                        updateBarStyle()
                    }
                    "pref_key_system_visualizer_render" -> {
                        renderType = RenderType.values()[MainModule.mPrefs.getStringAsInt("system_visualizer_render", 0)]
                        updateBarStyle()
                    }
                    "pref_key_system_visualizer_glowlevel" -> {
                        glowLevel = MainModule.mPrefs.getInt("system_visualizer_glowlevel", 50)
                        updateGlowPaint()
                    }
                    "pref_key_system_visualizer_colorval" -> {
                        customColor = MainModule.mPrefs.getInt("system_visualizer_colorval", Color.WHITE)
                        setColor(customColor)
                    }
                    "pref_key_system_visualizer_dyntime" -> {
                        randomizeInterval = MainModule.mPrefs.getInt("system_visualizer_dyntime", 10) * 1000
                        randomizeColorJob?.cancel()
                        randomizeColorJob = viewScope.launch { runRandomizeColor() }
                    }
                    "pref_key_system_visualizer_drawer" ->
                        showInDrawer = MainModule.mPrefs.getBoolean("system_visualizer_drawer", false)
                    "pref_key_system_visualizer_controller" ->
                        showWithControllerOnly = MainModule.mPrefs.getBoolean("system_visualizer_controller", false)
                }
            } catch (t: Throwable) {
                XposedHelpers.log(t)
            }
        }
    }

    init {
        setLayerType(LAYER_TYPE_HARDWARE, null)

        val res: Resources = context.resources
        mHeight = if (res.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            res.displayMetrics.heightPixels
        } else {
            res.displayMetrics.widthPixels
        }
        mWidth = if (res.configuration.orientation == Configuration.ORIENTATION_PORTRAIT) {
            res.displayMetrics.widthPixels
        } else {
            res.displayMetrics.heightPixels
        }

        mPaint = Paint().apply {
            isAntiAlias = true
            style = Paint.Style.STROKE
            strokeJoin = Paint.Join.MITER
            color = mColor
        }

        animDur = MainModule.mPrefs.getInt("system_visualizer_animdur", 65)
        for (i in 0 until mBandsNum) {
            mBandStarts[i] = mHeight.toFloat()
            mBandTargets[i] = mHeight.toFloat()
            mPendingTargets[i] = mHeight.toFloat()
        }

        for (i in 0 until mBandsNum) {
            mPositions[i] = (i + 1) / mBandsNum.toFloat()
        }

        showOnCustom = MainModule.mPrefs.getBoolean("system_visualizer_custom")
        transparency = (255f - 255f * MainModule.mPrefs.getInt("system_visualizer_transp", 40) / 100f).roundToInt()
        colorMode = ColorMode.values()[MainModule.mPrefs.getStringAsInt("system_visualizer_color", 1)]
        barStyle = BarStyle.values()[MainModule.mPrefs.getStringAsInt("system_visualizer_style", 1)]
        renderType = RenderType.values()[MainModule.mPrefs.getStringAsInt("system_visualizer_render", 0)]
        glowLevel = MainModule.mPrefs.getInt("system_visualizer_glowlevel", 50)
        customColor = MainModule.mPrefs.getInt("system_visualizer_colorval", Color.WHITE)
        randomizeInterval = MainModule.mPrefs.getInt("system_visualizer_dyntime", 10) * 1000
        showInDrawer = MainModule.mPrefs.getBoolean("system_visualizer_drawer")
        showWithControllerOnly = MainModule.mPrefs.getBoolean("system_visualizer_controller")

        updateBarStyle()
        updateGlowPaint()
        updateRainbowColors()

        ModuleHelper.observePreferenceChange(preferenceObserver, this)
    }

    private val mVisualizerListener = object : Visualizer.OnDataCaptureListener {
        private var real: Byte = 0
        private var imaginary: Byte = 0
        private var dbValue = 0
        private var magnitude = 0f

        override fun onWaveFormDataCapture(visualizer: Visualizer, bytes: ByteArray, samplingRate: Int) {}

        override fun onFftDataCapture(visualizer: Visualizer, fft: ByteArray, samplingRate: Int) {
            try {
                if (detached || !mDisplaying) return

                if (mFftSize != fft.size) {
                    computeBandBinLimits(fft.size)
                }

                val silentFrame = allZeros(fft)
                var band = 0
                var i = 1
                val maxHeight = min(0.85f * maxDp * mDensity, mHeight / 2.0f)

                while (band < mBandsNum && i < mFftSize / 2) {
                    magnitude = 0f

                    if (!silentFrame) {
                        while (i < mBandBinLimits[band]) {
                            real = fft[i * 2]
                            imaginary = fft[i * 2 + 1]
                            magnitude = max(magnitude, (real * real + imaginary * imaginary).toFloat())
                            i++
                        }
                    }

                    dbValue = if (magnitude > 0) (10 * log10(magnitude)).toInt() else 0
                    maxDb = max(maxDb, dbValue.toFloat())
                    val newVal = mFFTPoints[band * 4 + 1] - maxHeight * dbValue / maxDb

                    synchronized(mFrameLock) {
                        mPendingTargets[band] = newVal
                        mNewDataPending = true
                    }

                    band++
                }

                // Make sure the frame scheduler is running. Choreographer must be used
                // from the main thread, so post to the view's handler.
                if (!mFrameCallbackScheduled) {
                    post { startFrameScheduler() }
                }
            } catch (t: Throwable) {
                XposedHelpers.log(t)
            }
        }
    }

    private fun computeBandBinLimits(fftSize: Int) {
        mFftSize = fftSize
        val half = fftSize / 2
        for (band in 0 until mBandsNum) {
            val limit = (mBands[band] * fftSize / 44100f).toInt()
            mBandBinLimits[band] = if (limit < half) limit else half
        }
    }

    private fun allZeros(array: ByteArray): Boolean = array.all { it == 0.toByte() }

    private fun getRandomColor(): Int {
        mHsv[0] = (Math.random() * 360f).toFloat()
        mHsv[1] = 0.5f + (Math.random() * 0.5f).toFloat()
        mHsv[2] = 0.75f + (Math.random() * 0.25f).toFloat()
        return Color.HSVToColor(mHsv)
    }

    private fun updateGlowPaint() {
        mGlowPaint = Paint(mPaint)
        if (glowLevel == 0) return
        val scale = glowLevel / 100f
        mGlowPaint.pathEffect = null
        mGlowPaint.maskFilter = BlurMaskFilter(15 * mDensity * (1.25f + 0.25f * scale), BlurMaskFilter.Blur.NORMAL)
        mGlowPaint.alpha = min(transparency, 180)
        mGlowPaint.strokeWidth = (0.5f + 1.25f * scale) * mPaint.strokeWidth * if (barStyle == BarStyle.LINE) 4f else if (colorMode == ColorMode.RAINBOW_H) 1.15f else 1.3f
        if (barStyle == BarStyle.SOLID || barStyle == BarStyle.DASHED || mGlowPaint.strokeCap == Paint.Cap.ROUND) {
            mGlowPaint.strokeCap = Paint.Cap.SQUARE
        }
    }

    private val onPaletteGenerated: (Palette?) -> Unit = { palette ->
        try {
            var color = palette?.let {
                var c = Color.TRANSPARENT
                c = it.getLightVibrantColor(c)
                if (c == Color.TRANSPARENT) c = it.getVibrantColor(c)
                if (c == Color.TRANSPARENT) c = it.getDarkVibrantColor(c)
                c
            } ?: Color.TRANSPARENT
            setColor(color)
        } catch (t: Throwable) {
            XposedHelpers.log(t)
        }
    }

    fun setBitmap() {
        try {
            if (mProcessedArt === mArt && mArt != null) return
            mProcessedArt = mArt
            val art = mProcessedArt
            if (art != null) {
                paletteGenerationJob?.cancel()
                paletteGenerationJob = viewScope.launch {
                    val palette = withContext(Dispatchers.Default) { Palette.from(art).generate() }
                    if (!isActive) return@launch
                    onPaletteGenerated(palette)
                }
            } else {
                setColor(Color.TRANSPARENT)
            }
        } catch (t: Throwable) {
            XposedHelpers.log(t)
        }
    }

    fun setColor(color: Int) {
        var c = color
        if (c == Color.TRANSPARENT) c = Color.WHITE
        val newColor = Color.argb(transparency, Color.red(c), Color.green(c), Color.blue(c))
        if (mColor == newColor) return
        mColor = newColor
        mOpaqueColor = c

        val viz = mVisualizer
        if (viz != null) {
            mVisualizerColorAnimator?.cancel()
            mVisualizerColorAnimator = ObjectAnimator.ofArgb(mPaint, "color", mPaint.color, mColor).apply {
                startDelay = (600 * animDur / 65f).roundToInt().toLong()
                duration = (1200 * animDur / 65f).roundToInt().toLong()
                start()
            }

            if (glowLevel > 0) {
                mVisualizerGlowColorAnimator?.cancel()
                mVisualizerGlowColorAnimator = ObjectAnimator.ofArgb(mGlowPaint, "color", mGlowPaint.color, mColor).apply {
                    startDelay = (600 * animDur / 65f).roundToInt().toLong()
                    duration = (1200 * animDur / 65f).roundToInt().toLong()
                    start()
                }
            }
        } else {
            mPaint.color = mColor
            if (glowLevel > 0) mGlowPaint.color = mColor
        }
    }

    private fun updateColorMode() {
        if (!isMusicPlaying) return
        when (colorMode) {
            ColorMode.MATCH -> setBitmap()
            ColorMode.DYNAMIC -> setColor(getRandomColor())
            ColorMode.STATIC -> setColor(customColor)
            else -> setColor(Color.WHITE)
        }
    }

    private fun updateRainbowColors() {
        val jump = 300f / mBandsNum
        mHsv[1] = 1.0f
        mHsv[2] = 1.0f
        for (i in 0 until mRainbow.size) {
            mHsv[0] = jump * i
            mRainbow[i] = Color.HSVToColor(transparency, mHsv)
        }

        for (i in 0 until mRainbowVertical.size) {
            var h = 140f + jump * i
            if (h > 360) h -= 360f
            mHsv[0] = h
            mRainbowVertical[i] = Color.HSVToColor(transparency, mHsv)
        }
    }

    private fun updateBarStyle() {
        when (colorMode) {
            ColorMode.RAINBOW_H -> mPaint.shader = LinearGradient(0f, 0f, mWidth.toFloat(), 0f, mRainbow, mPositions, Shader.TileMode.MIRROR)
            ColorMode.RAINBOW_V -> {
                val maxHeight = min(0.85f * maxDp * mDensity, mHeight / 2.0f)
                mPaint.shader = LinearGradient(0f, mHeight.toFloat(), 0f, mHeight - maxHeight, mRainbowVertical, mPositions, Shader.TileMode.CLAMP)
            }
            else -> mPaint.shader = null
        }

        when (barStyle) {
            BarStyle.SOLID -> {
                mPaint.pathEffect = null
                mPaint.strokeCap = Paint.Cap.BUTT
            }
            BarStyle.SOLID_ROUNDED -> {
                mPaint.pathEffect = null
                mPaint.strokeCap = Paint.Cap.ROUND
            }
            BarStyle.DASHED -> {
                mDashIntervals[0] = 4 * mDensity
                mDashIntervals[1] = 2 * mDensity
                mPaint.pathEffect = DashPathEffect(mDashIntervals, 0f)
                mPaint.strokeCap = Paint.Cap.BUTT
            }
            BarStyle.CIRCLES -> {
                mDashIntervals[0] = 1.0f
                mDashIntervals[1] = mPaint.strokeWidth + mDensity
                mPaint.pathEffect = DashPathEffect(mDashIntervals, 0f)
                mPaint.strokeCap = Paint.Cap.ROUND
            }
            BarStyle.LINE -> {
                mPaint.pathEffect = CornerPathEffect(18 * mDensity)
                mPaint.strokeCap = Paint.Cap.ROUND
                mPaint.strokeWidth = 3 * mDensity
            }
            else -> {}
        }

        updateGlowPaint()
    }

    public override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        dispose()
    }

    internal fun dispose() {
        if (detached) return
        detached = true
        ModuleHelper.removePreferenceObserver(this)
        val visualizer = synchronized(visualizerLock) {
            val current = mVisualizer
            mVisualizer = null
            current
        }
        releaseVisualizer(visualizer)
        stopFrameScheduler()
        mDisplaying = false
        animate().cancel()
        mVisualizerColorAnimator?.cancel()
        mVisualizerGlowColorAnimator?.cancel()
        randomizeColorJob?.cancel()
        paletteGenerationJob?.cancel()
        mArt = null
        mProcessedArt = null
        viewScope.cancel()
        val callback = onDisposed
        onDisposed = null
        callback?.invoke(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        val barUnit = w / mBandsNum.toFloat()
        val barWidth = barUnit * 0.80f
        mHeight = h
        mWidth = w
        mPaint.strokeWidth = barWidth
        updateBarStyle()

        for (i in 0 until mBandsNum) {
            mFFTPoints[i * 4] = i * barUnit + (barWidth / 2)
            mFFTPoints[i * 4 + 1] = h.toFloat()
            mFFTPoints[i * 4 + 2] = mFFTPoints[i * 4]
            mFFTPoints[i * 4 + 3] = h.toFloat()
            mBandStarts[i] = h.toFloat()
            mBandTargets[i] = h.toFloat()
            mPendingTargets[i] = h.toFloat()
        }
    }

    override fun hasOverlappingRendering(): Boolean = mDisplaying

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        try {
            if (mVisualizer?.enabled != true) return
        } catch (t: Throwable) {
            return
        }

        if (barStyle == BarStyle.LINE) {
            mLinePath.reset()
            mLinePath.moveTo(0f, mFFTPoints[3])
            for (i in 1 until mBandsNum) {
                mLinePath.lineTo(if (i == mBandsNum - 1) mWidth.toFloat() else mFFTPoints[i * 4 + 2], mFFTPoints[i * 4 + 3])
            }
            if (glowLevel > 0) {
                canvas.drawPath(mLinePath, mGlowPaint)
            }
            canvas.drawPath(mLinePath, mPaint)
            return
        }

        val drawAsLines = when (renderType) {
            RenderType.LINES -> true
            RenderType.PATH -> false
            else -> glowLevel == 0
        }

        if (drawAsLines) {
            if (glowLevel > 0) {
                canvas.drawLines(mFFTPoints, mGlowPaint)
            }
            canvas.drawLines(mFFTPoints, mPaint)
        } else {
            mLinePath.reset()
            for (i in 0 until mBandsNum) {
                mLinePath.moveTo(mFFTPoints[i * 4], mFFTPoints[i * 4 + 1])
                mLinePath.lineTo(mFFTPoints[i * 4], mFFTPoints[i * 4 + 3])
            }
            if (glowLevel > 0) {
                canvas.drawPath(mLinePath, mGlowPaint)
            }
            canvas.drawPath(mLinePath, mPaint)
        }
    }

    fun setPlaying(playing: Boolean) {
        if (mPlaying != playing) {
            mPlaying = playing
            checkStateChanged()
        }
    }

    private suspend fun linkVisualizer() = withContext(Dispatchers.IO) {
        var visualizer: Visualizer? = null
        try {
            visualizer = Visualizer(0)
            visualizer.enabled = false
            visualizer.captureSize = Visualizer.getCaptureSizeRange()[1]
            visualizer.scalingMode = Visualizer.SCALING_MODE_NORMALIZED
            visualizer.setDataCaptureListener(mVisualizerListener, Visualizer.getMaxCaptureRate(), false, true)
            visualizer.enabled = true
            var previous: Visualizer? = null
            val installed = synchronized(visualizerLock) {
                if (detached) {
                    false
                } else {
                    previous = mVisualizer
                    mVisualizer = visualizer
                    true
                }
            }
            if (installed) {
                if (previous !== visualizer) releaseVisualizer(previous)
                visualizer = null
            }
        } catch (t: Throwable) {
            XposedHelpers.log(t)
        } finally {
            releaseVisualizer(visualizer)
        }
    }

    private suspend fun unlinkVisualizer() = withContext(Dispatchers.IO) {
        val visualizer = synchronized(visualizerLock) {
            val current = mVisualizer
            mVisualizer = null
            current
        }
        releaseVisualizer(visualizer)
    }

    private fun releaseVisualizer(visualizer: Visualizer?) {
        if (visualizer == null) return
        try {
            visualizer.enabled = false
        } catch (t: Throwable) {
            XposedHelpers.log(t)
        }
        try {
            visualizer.release()
        } catch (t: Throwable) {
            XposedHelpers.log(t)
        }
    }

    private suspend fun runRandomizeColor() {
        while (coroutineContext.isActive && colorMode == ColorMode.DYNAMIC) {
            setColor(getRandomColor())
            delay(randomizeInterval.toLong())
        }
    }

    fun updateViewState(isPlaying: Boolean, isKeyguard: Boolean, isExpanded: Boolean) {
        isMusicPlaying = isPlaying
        isOnKeyguard = isKeyguard
        isExpandedPanel = showInDrawer && !isOnKeyguard && isExpanded
        isOnCustomLockScreen = File("/data/system/theme/lockscreen").exists()
        updatePlaying()
    }

    fun updateScreenOn(isOn: Boolean) {
        isScreenOn = isOn
        updatePlaying()
    }

    fun updateMusicArt(art: Bitmap?) {
        mArt = art
        updateColorMode()
    }

    fun updatePlaying() {
        setPlaying(isScreenOn && isMusicPlaying && ((isOnKeyguard && (!isOnCustomLockScreen || showOnCustom)) || isExpandedPanel))
    }

    private fun checkStateChanged() {
        if (detached) return
        if (mPlaying) {
            if (!mDisplaying) {
                mDisplaying = true
                startFrameScheduler()
                viewScope.launch { linkVisualizer() }
                randomizeColorJob?.cancel()
                randomizeColorJob = viewScope.launch { runRandomizeColor() }
                animate().alpha(1.0f).withEndAction(null).setDuration((800 * animDur / 65f).roundToInt().toLong())
            }
        } else {
            if (mDisplaying) {
                mDisplaying = false
                stopFrameScheduler()
                randomizeColorJob?.cancel()
                if (isOnKeyguard) {
                    animate()
                        .alpha(0.0f)
                        .withEndAction { viewScope.launch { unlinkVisualizer() } }
                        .setDuration((600 * animDur / 65f).roundToInt().toLong())
                } else {
                    alpha = 0.0f
                    viewScope.launch { unlinkVisualizer() }
                }
            }
        }
    }
}
