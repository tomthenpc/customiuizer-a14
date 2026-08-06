package tv.withaibuild.customiuizer.mods

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.VisibleForTesting
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.ScreenStateController
import tv.withaibuild.customiuizer.mods.utils.WeatherDataController
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.HookUtils
import tv.withaibuild.customiuizer.utils.PrefMap
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.concurrent.atomic.AtomicLong

object SystemClockHooks {

    /**
     * Immutable snapshot of all clock style and format preferences that the hot
     * tick path needs.
     *
     * - Built once per configuration / preference set.
     * - The `id` is used to skip re-applying identical style snapshots to a view.
     * - The `configuration` copy is used to detect resource / locale changes.
     */
    internal data class ClockStyleSnapshot(
        val id: Long,
        val configuration: Configuration,

        // Format
        val statusbarCustomFormat: String,
        val statusbarCustomFormatEnable: Boolean,
        val ccCustomFormat: String,
        val ccCustomFormatEnable: Boolean,
        val ccDateFormat: String,
        val drawerDateFormat: String,
        val statusbarShowSeconds: Boolean,
        val statusbarIs24: Boolean,
        val statusbarShowAmpm: Boolean,
        val statusbarHourLeadingZero: Boolean,
        val statusbarDefaultFormat: String,
        val enableWeatherParam: Boolean,

        // Status bar style
        val statusbarFontSize: Int,
        val statusbarAlign: Int,
        val statusbarBold: Boolean,
        val statusbarLeftMargin: Int,
        val statusbarRightMargin: Int,
        val statusbarVerticalOffset: Int,
        val statusbarChip: Boolean,
        val statusbarChipUseMonet: Boolean,
        val statusbarChipCustomTextColor: Boolean,
        val statusbarChipStartColor: Int,
        val statusbarChipEndColor: Int,
        val statusbarChipTextColor: Int,
        val statusbarChipOrientationVertical: Boolean,
        val statusbarChipHorizPadding: Int,
        val statusbarChipVertPadding: Int,
        val statusbarChipRadius: Int,
        val statusbarFixedWidth: Int,

        // Pre-computed seconds flags for the SecondTicker
        val showStatusBarSeconds: Boolean,
        val showCCSeconds: Boolean,
    )

    /** Maximum number of `mClockListeners` to iterate during a style refresh. */
    private const val MAX_CLOCK_LISTENERS = 64

    /** View tag key for the snapshot id that was last applied to a clock view. */
    private const val CLOCK_STYLE_SNAPSHOT_ID_FIELD = "clockStyleSnapshotId"

    /** All preference keys that can affect the clock style snapshot. */
    private val CLOCK_STYLE_PREFERENCE_KEYS = setOf(
        "system_statusbar_clock_customformat_enable",
        "system_statusbar_clock_customformat",
        "system_statusbar_clock_fontsize",
        "system_statusbar_clock_align",
        "system_statusbar_clock_bold",
        "system_statusbar_clock_leftmargin",
        "system_statusbar_clock_rightmargin",
        "system_statusbar_clock_verticaloffset",
        "system_statusbar_clock_chip",
        "system_statusbar_clock_chip_usemonet",
        "system_statusbar_clock_chip_customtextcolor",
        "system_statusbar_clock_chip_startcolor",
        "system_statusbar_clock_chip_endcolor",
        "system_statusbar_clock_chip_textcolor",
        "system_statusbar_clock_chip_orientation_vertical",
        "system_statusbar_clock_chip_horizpadding",
        "system_statusbar_clock_chip_verticalpadding",
        "system_statusbar_clock_chip_radius",
        "system_statusbar_clock_fixedcontent_width",
        "system_statusbar_clock_show_seconds",
        "system_statusbar_clock_24hour_format",
        "system_statusbar_clock_show_ampm",
        "system_statusbar_clock_leadingzero",
        "system_cc_clock_customformat_enable",
        "system_cc_clock_customformat",
        "system_cc_dateformat",
        "system_drawer_dateformat",
        "system_statusbar_enable_weather_param",
    )

    @Volatile
    private var clockStyleSnapshot: ClockStyleSnapshot? = null

    private val clockSnapshotId = AtomicLong(0L)

    /**
     * Builds an immutable snapshot of clock style and format preferences.
     *
     * This is the only place that reads `mPrefs` for clock style. It is called
     * lazily from `initClockStyle`, `updateTime` (only when no snapshot exists),
     * `initSecondTicker`, and the preference observer.
     */
    internal fun buildClockStyleSnapshot(prefs: PrefMap, res: Resources): ClockStyleSnapshot {
        val statusbarCustomFormat = prefs.getString("system_statusbar_clock_customformat", "")
        val statusbarCustomFormatEnable = prefs.getBoolean("system_statusbar_clock_customformat_enable")
        val ccCustomFormat = prefs.getString("system_cc_clock_customformat", "")
        val ccCustomFormatEnable = prefs.getBoolean("system_cc_clock_customformat_enable")
        val ccDateFormat = prefs.getString("system_cc_dateformat", "")
        val drawerDateFormat = prefs.getString("system_drawer_dateformat", "")
        val statusbarShowSeconds = prefs.getBoolean("system_statusbar_clock_show_seconds")
        val statusbarIs24 = prefs.getBoolean("system_statusbar_clock_24hour_format")
        val statusbarShowAmpm = prefs.getBoolean("system_statusbar_clock_show_ampm")
        val statusbarHourLeadingZero = prefs.getBoolean("system_statusbar_clock_leadingzero")
        val enableWeatherParam = prefs.getBoolean("system_statusbar_enable_weather_param")

        val showStatusBarSeconds = (statusbarCustomFormatEnable && statusbarCustomFormat.contains("ss"))
            || (!statusbarCustomFormatEnable && statusbarShowSeconds)
        val showCCSeconds = ccCustomFormat.contains("ss")

        val statusbarDefaultFormat = buildStatusbarDefaultFormat(
            res,
            showAmpm = statusbarShowAmpm,
            is24 = statusbarIs24,
            hourLeadingZero = statusbarHourLeadingZero,
            showSeconds = statusbarShowSeconds,
        )

        val chip = prefs.getBoolean("system_statusbar_clock_chip")
        val chipUseMonet = chip && prefs.getBoolean("system_statusbar_clock_chip_usemonet")
        val chipCustomTextColor = chip && prefs.getBoolean("system_statusbar_clock_chip_customtextcolor")

        var chipStartColor = if (chip) {
            prefs.getInt("system_statusbar_clock_chip_startcolor", 0x8F7C4DFF.toInt())
        } else 0
        var chipEndColor = if (chip) {
            prefs.getInt("system_statusbar_clock_chip_endcolor", 0x2FA7FFEB.toInt())
        } else 0
        var chipTextColor = if (chip && chipCustomTextColor) {
            prefs.getInt("system_statusbar_clock_chip_textcolor", 0xFFFFFFFF.toInt())
        } else 0

        if (chip && chipUseMonet) {
            try {
                chipTextColor = res.getColor(android.R.color.system_accent1_0, null)
                val monetColor = res.getColor(android.R.color.system_accent1_600, null)
                chipStartColor = monetColor
                chipEndColor = monetColor
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                // Fall through to the last resolved value.
            }
        }

        return ClockStyleSnapshot(
            id = clockSnapshotId.incrementAndGet(),
            configuration = Configuration(res.configuration),
            statusbarCustomFormat = statusbarCustomFormat,
            statusbarCustomFormatEnable = statusbarCustomFormatEnable,
            ccCustomFormat = ccCustomFormat,
            ccCustomFormatEnable = ccCustomFormatEnable,
            ccDateFormat = ccDateFormat,
            drawerDateFormat = drawerDateFormat,
            statusbarShowSeconds = statusbarShowSeconds,
            statusbarIs24 = statusbarIs24,
            statusbarShowAmpm = statusbarShowAmpm,
            statusbarHourLeadingZero = statusbarHourLeadingZero,
            statusbarDefaultFormat = statusbarDefaultFormat,
            enableWeatherParam = enableWeatherParam,
            statusbarFontSize = prefs.getInt("system_statusbar_clock_fontsize", 13),
            statusbarAlign = prefs.getStringAsInt("system_statusbar_clock_align", 1),
            statusbarBold = prefs.getBoolean("system_statusbar_clock_bold"),
            statusbarLeftMargin = prefs.getInt("system_statusbar_clock_leftmargin", 0),
            statusbarRightMargin = prefs.getInt("system_statusbar_clock_rightmargin", 0),
            statusbarVerticalOffset = prefs.getInt("system_statusbar_clock_verticaloffset", 8),
            statusbarChip = chip,
            statusbarChipUseMonet = chipUseMonet,
            statusbarChipCustomTextColor = chipCustomTextColor,
            statusbarChipStartColor = chipStartColor,
            statusbarChipEndColor = chipEndColor,
            statusbarChipTextColor = chipTextColor,
            statusbarChipOrientationVertical = chip && prefs.getBoolean("system_statusbar_clock_chip_orientation_vertical"),
            statusbarChipHorizPadding = if (chip) prefs.getInt("system_statusbar_clock_chip_horizpadding", 0) else 0,
            statusbarChipVertPadding = if (chip) prefs.getInt("system_statusbar_clock_chip_verticalpadding", 0) else 0,
            statusbarChipRadius = if (chip) prefs.getInt("system_statusbar_clock_chip_radius", 0) else 0,
            statusbarFixedWidth = prefs.getInt("system_statusbar_clock_fixedcontent_width", 10),
            showStatusBarSeconds = showStatusBarSeconds,
            showCCSeconds = showCCSeconds,
        )
    }

    private fun buildStatusbarDefaultFormat(
        res: Resources,
        showAmpm: Boolean,
        is24: Boolean,
        hourLeadingZero: Boolean,
        showSeconds: Boolean,
    ): String {
        val fmt = if (showAmpm) "fmt_time_12hour_minute_pm" else "fmt_time_12hour_minute"
        val fmtResId = HookUtils.getResId(res, fmt, "string", "com.android.systemui")
        if (fmtResId == 0) return ""

        val baseFormat = try {
            res.getString(fmtResId)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            ""
        }
        if (baseFormat.isEmpty()) return ""

        var format = baseFormat
        if (showSeconds) {
            val mmIdx = format.indexOf(":mm")
            if (mmIdx >= 0) {
                format = format.substring(0, mmIdx) + ":mm:ss" + format.substring(mmIdx + 3)
            }
        }

        val hourStr = when {
            is24 && hourLeadingZero -> "HH"
            is24 -> "H"
            hourLeadingZero -> "hh"
            else -> "h"
        }

        val colonIdx = format.indexOf(':')
        return if (colonIdx > 0) hourStr + format.substring(colonIdx) else format
    }

    /**
     * Returns the current snapshot, or `null` if one has not been built yet.
     */
    internal fun currentClockStyleSnapshot(): ClockStyleSnapshot? = clockStyleSnapshot

    /**
     * Ensures a snapshot exists and matches the current `Configuration`.
     *
     * This is not called from the per-tick path. It is used from
     * `initClockStyle`, `initSecondTicker`, and the preference observer refresh.
     */
    private fun ensureClockStyleSnapshot(res: Resources): ClockStyleSnapshot {
        val current = clockStyleSnapshot
        if (current != null && current.configuration == res.configuration) {
            return current
        }
        val newSnapshot = buildClockStyleSnapshot(MainModule.mPrefs, res)
        clockStyleSnapshot = newSnapshot
        return newSnapshot
    }

    /**
     * Builds a new snapshot and stores it. Used by the preference observer when
     * a relevant key has changed.
     */
    private fun refreshClockStyleSnapshot(res: Resources): ClockStyleSnapshot {
        val newSnapshot = buildClockStyleSnapshot(MainModule.mPrefs, res)
        clockStyleSnapshot = newSnapshot
        return newSnapshot
    }

    /**
     * Returns the format pattern to use for [clockName] based on the snapshot.
     *
     * - `clock` uses the custom status-bar format if enabled, otherwise the
     *   pre-computed default format.
     * - `ccClock` uses the custom CC format when `ccClockTweak` is enabled.
     * - `ccDate` and `drawerDate` use their respective custom formats.
     * - If weather is enabled and the format contains `tq`, it is replaced.
     */
    internal fun buildClockText(
        clockName: String?,
        snapshot: ClockStyleSnapshot,
        weatherInfo: String?,
        statusbarClockTweak: Boolean,
        ccClockTweak: Boolean,
    ): String? {
        if (clockName == null) return null
        val timeFmt = when (clockName) {
            "ccClock" -> if (ccClockTweak && snapshot.ccCustomFormat.isNotEmpty()) snapshot.ccCustomFormat else null
            "ccDate" -> if (snapshot.ccDateFormat.isNotEmpty()) snapshot.ccDateFormat else null
            "drawerDate" -> if (snapshot.drawerDateFormat.isNotEmpty()) snapshot.drawerDateFormat else null
            "clock" -> if (statusbarClockTweak) {
                if (snapshot.statusbarCustomFormatEnable && snapshot.statusbarCustomFormat.isNotEmpty()) {
                    snapshot.statusbarCustomFormat
                } else if (snapshot.statusbarDefaultFormat.isNotEmpty()) {
                    snapshot.statusbarDefaultFormat
                } else null
            } else null
            else -> null
        } ?: return null

        if (snapshot.enableWeatherParam && weatherInfo != null && timeFmt.contains("tq")) {
            return timeFmt.replace("tq", weatherInfo)
        }
        return timeFmt
    }

    /**
     * Applies the snapshot-backed style to a clock [TextView].
     *
     * - If the view has already been styled with this [snapshot.id], the call
     *   is a no-op to avoid repeated setters.
     * - `dp2px` and `TypedValue.applyDimension` use the current
     *   [Resources.displayMetrics], so density changes are still reflected when
     *   the snapshot is rebuilt.
     * - This function reads **no** `mPrefs` values.
     */
    internal fun initClockStyle(mClock: TextView, clockName: String, snapshot: ClockStyleSnapshot) {
        val lastId = XposedHelpers.getAdditionalInstanceField(mClock, CLOCK_STYLE_SNAPSHOT_ID_FIELD) as? Long
        if (lastId == snapshot.id) return
        XposedHelpers.setAdditionalInstanceField(mClock, CLOCK_STYLE_SNAPSHOT_ID_FIELD, snapshot.id)

        val res = mClock.resources
        val statusBarClock = clockName == "clock"
        val enableCustomFormat = !statusBarClock || snapshot.statusbarCustomFormatEnable
        val customFormat = if (statusBarClock) snapshot.statusbarCustomFormat else snapshot.ccCustomFormat
        val dualRows = enableCustomFormat && customFormat.contains("\n")

        if (statusBarClock) {
            val dimStep = 0.5f
            val fontSize = snapshot.statusbarFontSize
            if (fontSize > 13) {
                mClock.setTextSize(TypedValue.COMPLEX_UNIT_DIP, fontSize * dimStep)
            }
            if (dualRows) {
                val multiplier = if (0.5f * fontSize > 8.5f) 0.85f else 0.9f
                mClock.setLineSpacing(0f, multiplier)
            }
            when (snapshot.statusbarAlign) {
                2 -> mClock.textAlignment = View.TEXT_ALIGNMENT_TEXT_START
                3 -> mClock.textAlignment = View.TEXT_ALIGNMENT_CENTER
                4 -> mClock.textAlignment = View.TEXT_ALIGNMENT_TEXT_END
            }
            if (snapshot.statusbarBold) {
                mClock.typeface = Typeface.DEFAULT_BOLD
            }

            var leftMargin = snapshot.statusbarLeftMargin
            leftMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                leftMargin * dimStep,
                res.displayMetrics,
            ).toInt()
            var rightMargin = snapshot.statusbarRightMargin
            rightMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                rightMargin * dimStep,
                res.displayMetrics,
            ).toInt()

            val defaultVerticalOffset = 8
            val verticalOffset = snapshot.statusbarVerticalOffset
            if (verticalOffset != defaultVerticalOffset) {
                val marginTop = TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP,
                    (verticalOffset - defaultVerticalOffset) * dimStep,
                    res.displayMetrics,
                )
                mClock.translationY = marginTop
            }

            if (snapshot.statusbarChip) {
                val lp = mClock.layoutParams as LinearLayout.LayoutParams
                lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                lp.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                if (leftMargin > 0) lp.leftMargin = leftMargin
                if (rightMargin > 0) lp.rightMargin = rightMargin
                mClock.layoutParams = lp

                if (snapshot.statusbarChipUseMonet || snapshot.statusbarChipCustomTextColor) {
                    mClock.setTextColor(snapshot.statusbarChipTextColor)
                }

                val chipDrawable = GradientDrawable()
                chipDrawable.orientation = if (snapshot.statusbarChipOrientationVertical) {
                    GradientDrawable.Orientation.TOP_BOTTOM
                } else {
                    GradientDrawable.Orientation.LEFT_RIGHT
                }
                chipDrawable.colors = intArrayOf(snapshot.statusbarChipStartColor, snapshot.statusbarChipEndColor)
                chipDrawable.shape = GradientDrawable.RECTANGLE

                var horizPadding = snapshot.statusbarChipHorizPadding
                var vertPadding = snapshot.statusbarChipVertPadding
                if (horizPadding > 0) {
                    horizPadding = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        horizPadding.toFloat(),
                        res.displayMetrics,
                    ).toInt()
                }
                if (vertPadding > 0) {
                    vertPadding = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        vertPadding.toFloat(),
                        res.displayMetrics,
                    ).toInt()
                }
                if (horizPadding > 0 || vertPadding > 0) {
                    chipDrawable.setPadding(horizPadding, vertPadding, horizPadding, vertPadding)
                }

                var radiusPx = snapshot.statusbarChipRadius
                if (radiusPx > 0) {
                    radiusPx = TypedValue.applyDimension(
                        TypedValue.COMPLEX_UNIT_DIP,
                        radiusPx.toFloat(),
                        res.displayMetrics,
                    ).toInt()
                    chipDrawable.cornerRadius = radiusPx.toFloat()
                }
                mClock.background = chipDrawable
            } else {
                if (leftMargin > 0 || rightMargin > 0) {
                    val lp = mClock.layoutParams as LinearLayout.LayoutParams
                    lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
                    lp.gravity = Gravity.CENTER_VERTICAL or Gravity.START
                    if (leftMargin > 0) lp.leftMargin = leftMargin
                    if (rightMargin > 0) lp.rightMargin = rightMargin
                    mClock.layoutParams = lp
                }
            }

            val fixedWidth = snapshot.statusbarFixedWidth
            if (fixedWidth > 10) {
                val lp = mClock.layoutParams
                lp.width = (res.displayMetrics.density * fixedWidth).toInt()
                mClock.layoutParams = lp
            }
        }

        if (dualRows) {
            mClock.setSingleLine(false)
            mClock.maxLines = 2
        }
    }

    private fun initClockStyle(mClock: TextView, clockName: String) {
        initClockStyle(mClock, clockName, ensureClockStyleSnapshot(mClock.resources))
    }

    /**
     * Starts or stops the one-second ticker and refreshes the `showSeconds`
     * tags on every view in `mClockListeners`.
     *
     * A new ticker is only created when the seconds flags actually change, or
     * when there is no previous ticker. This avoids re-posting the same
     * runnable and re-registering the same screen-state listener on repeated
     * preference notifications. The `SecondTicker` holds a `WeakReference` to
     * the controller so that a garbage-collected controller does not keep the
     * ticker (and its associated screen-state listener) alive.
     */
    @VisibleForTesting
    internal fun initSecondTicker(clockController: Any) {
        val mContext = XposedHelpers.getObjectField(clockController, "mContext") as Context
        val snapshot = currentClockStyleSnapshot() ?: ensureClockStyleSnapshot(mContext.resources)

        @Suppress("UNCHECKED_CAST")
        val clockListeners = XposedHelpers.getObjectField(clockController, "mClockListeners") as? ArrayList<Any>
        if (clockListeners != null) {
            for (listener in clockListeners) {
                val clock = listener as? View ?: continue
                val clockName = ModuleHelper.getViewInfo(clock, "clockName") as? String ?: continue
                val showSeconds = when (clockName) {
                    "clock" -> snapshot.showStatusBarSeconds
                    "ccClock" -> snapshot.showCCSeconds
                    else -> false
                }
                if (showSeconds) {
                    ModuleHelper.setViewInfo(clock, "showSeconds", true)
                } else {
                    ModuleHelper.setViewInfo(clock, "showSeconds", null)
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        val previousTicker = XposedHelpers.getAdditionalInstanceField(clockController, "secondTicker") as SecondTicker?
        val needsTicker = snapshot.showCCSeconds || snapshot.showStatusBarSeconds

        if (needsTicker) {
            if (previousTicker != null
                && previousTicker.showStatusBarSeconds == snapshot.showStatusBarSeconds
                && previousTicker.showCCSeconds == snapshot.showCCSeconds
            ) {
                // Same seconds configuration: keep the existing ticker to avoid
                // restarting the timer and re-registering listeners.
                return
            }
            previousTicker?.dispose()
            val ticker = SecondTicker(clockController, mContext, snapshot.showStatusBarSeconds, snapshot.showCCSeconds)
            XposedHelpers.setAdditionalInstanceField(clockController, "secondTicker", ticker)
            ticker.start()
        } else {
            previousTicker?.dispose()
            XposedHelpers.removeAdditionalInstanceField(clockController, "secondTicker")
        }
    }

    /**
     * Returns the [SecondTicker] currently stored on [clockController], or null.
     */
    @VisibleForTesting
    internal fun activeSecondTicker(clockController: Any): Any? {
        return XposedHelpers.getAdditionalInstanceField(clockController, "secondTicker")
    }

    /**
     * One-second ticker that posts a runnable on the provided looper. It holds
     * a [WeakReference] to the clock controller to avoid pinning a short-lived
     * SystemUI controller from a static listener list.
     */
    private class SecondTicker(
        clockController: Any,
        private val context: Context,
        internal val showStatusBarSeconds: Boolean,
        internal val showCCSeconds: Boolean,
    ) : Runnable, ScreenStateController.ScreenStateListener {
        private val clockControllerRef = WeakReference(clockController)
        private val handler = context.mainLooper?.let { Handler(it) }
        private var running = false
        private var screenStateRegistered = false

        fun start() {
            if (running) return
            if (clockControllerRef.get() == null) {
                dispose()
                return
            }
            if (!screenStateRegistered) {
                screenStateRegistered = true
                ScreenStateController.addListener(context, this)
                if (running) return
            }
            running = true
            scheduleNextTick()
        }

        fun stop() {
            running = false
            handler?.removeCallbacks(this)
            if (clockControllerRef.get() == null) {
                screenStateRegistered = false
                ScreenStateController.removeListener(this)
            }
        }

        fun dispose() {
            stop()
            if (screenStateRegistered) {
                screenStateRegistered = false
                ScreenStateController.removeListener(this)
            }
        }

        override fun onScreenStateChanged(isOn: Boolean) = ModuleHelper.guarded {
            if (isOn) start() else stop()
        }

        override fun run() {
            if (!running) return
            val clockController = clockControllerRef.get()
            if (clockController == null) {
                dispose()
                return
            }
            ModuleHelper.guarded {
                val calendar = XposedHelpers.getObjectField(clockController, "mCalendar")
                XposedHelpers.callMethod(calendar, "setTimeInMillis", java.lang.System.currentTimeMillis())
                XposedHelpers.setObjectField(clockController, "mIs24", DateFormat.is24HourFormat(context))
                @Suppress("UNCHECKED_CAST")
                val clockListeners = XposedHelpers.getObjectField(clockController, "mClockListeners") as ArrayList<Any>
                for (listener in clockListeners) {
                    val clock = listener as View
                    if (ModuleHelper.getViewInfo(clock, "showSeconds") != null) {
                        XposedHelpers.callMethod(clock, "updateTime")
                    }
                }
            }
            scheduleNextTick()
        }

        private fun scheduleNextTick() {
            if (!running || handler == null) return
            val delay = 1000L - java.lang.System.currentTimeMillis() % 1000L
            handler.postDelayed(this, delay)
        }
    }

    private fun initWeatherInfoHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllConstructors("com.android.systemui.statusbar.policy.MiuiStatusBarClockController", lpparam.classLoader, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    WeatherDataController.initContext(mContext, thisObject)

                } catch (oom: OutOfMemoryError) {
                    throw oom
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })
    }

    @JvmStatic
    fun StatusBarClockTweakHook(lpparam: PackageReadyParam) {
        val enableWeatherParam = MainModule.mPrefs.getBoolean("system_statusbar_enable_weather_param")
        if (enableWeatherParam) {
            initWeatherInfoHook(lpparam)
        }
        val hideStatusbarClock = MainModule.mPrefs.getBoolean("system_statusbaricons_clock")
        val statusbarClockTweak = !hideStatusbarClock && MainModule.mPrefs.getBoolean("system_statusbar_clocktweak")
        val ccClockTweak = MainModule.mPrefs.getBoolean("system_cc_clocktweak")
        val hideDateView = MainModule.mPrefs.getBoolean("system_cc_hidedate")
        val hideDrawerDate = MainModule.mPrefs.getBoolean("system_drawer_hidedate")

        val scheduleHook = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val mContext = XposedHelpers.getObjectField(thisObject, "mContext") as Context
                    ensureClockStyleSnapshot(mContext.resources)
                    initSecondTicker(thisObject)

                    if (currentClockStyleSnapshot()?.showStatusBarSeconds == true ||
                        currentClockStyleSnapshot()?.showCCSeconds == true
                    ) {
                        val timeSetIntent = IntentFilter()
                        timeSetIntent.addAction("android.intent.action.TIME_SET")
                        ModuleHelper.registerOwnedReceiver(
                            mContext,
                            thisObject,
                            "clockTimeSetReceiver",
                            timeSetIntent,
                            Context.RECEIVER_NOT_EXPORTED,
                        ) { _, owner, _, _ ->
                            ModuleHelper.guarded { initSecondTicker(owner) }
                        }
                    }

                    val controllerRef = WeakReference(thisObject)
                    val handler = Handler(mContext.mainLooper)
                    val observer = object : ModuleHelper.PreferenceObserver {
                        override fun onChange(key: String?) {
                            if (key !in CLOCK_STYLE_PREFERENCE_KEYS) return
                            val controller = controllerRef.get() ?: return
                            val context = XposedHelpers.getObjectField(controller, "mContext") as? Context ?: return
                            handler.post {
                                try {
                                    val res = context.resources
                                    val freshSnapshot = refreshClockStyleSnapshot(res)

                                    @Suppress("UNCHECKED_CAST")
                                    val clockListeners = XposedHelpers.getObjectField(controller, "mClockListeners") as? ArrayList<Any>
                                    if (clockListeners != null) {
                                        val count = minOf(clockListeners.size, MAX_CLOCK_LISTENERS)
                                        for (i in 0 until count) {
                                            val listener = clockListeners[i] as? View ?: continue
                                            val clockName = ModuleHelper.getViewInfo(listener, "clockName") as? String ?: continue
                                            if (clockName == "clock" || clockName == "ccClock") {
                                                initClockStyle(listener as TextView, clockName, freshSnapshot)
                                            }
                                            XposedHelpers.callMethod(listener, "updateTime")
                                        }
                                    }

                                    initSecondTicker(controller)
                                } catch (oom: OutOfMemoryError) {
                                    throw oom
                                } catch (t: Throwable) {
                                    FatalErrors.unwrapAndRethrowIfFatal(t)
                                    XposedHelpers.log(t)
                                }
                            }
                        }
                    }
                    ModuleHelper.observePreferenceChange(observer, thisObject)

                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }
        if (ccClockTweak || statusbarClockTweak) {
            ModuleHelper.hookAllConstructors("com.android.systemui.statusbar.policy.MiuiStatusBarClockController", lpparam.classLoader, scheduleHook)
        }

        ModuleHelper.hookAllConstructors("com.android.systemui.statusbar.views.MiuiClock", lpparam.classLoader, object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject
                    val args = chain.args

                    val clock = thisObject as TextView
                    if (args.size != 3) { return XposedHelpers.throwOrReturn(throwable, result) }
                    val clockId = HookUtils.getResId(clock.resources, "clock", "id", "com.android.systemui")
                    val bigClockId = HookUtils.getResId(clock.resources, "big_time", "id", "com.android.systemui")
                    val dateClockId = HookUtils.getResId(clock.resources, "date_time", "id", "com.android.systemui")
                    val horizDateClockId = HookUtils.getResId(clock.resources, "horizontal_date_time", "id", "com.android.systemui")
                    val thisClockId = clock.id

                    val snapshot = ensureClockStyleSnapshot(clock.resources)

                    if (clockId == thisClockId) {
                        ModuleHelper.setViewInfo(clock, "clockName", "clock")
                        if (statusbarClockTweak && snapshot.showStatusBarSeconds) {
                            ModuleHelper.setViewInfo(clock, "showSeconds", true)
                        }
                    } else if (bigClockId == thisClockId) {
                        ModuleHelper.setViewInfo(clock, "clockName", "ccClock")
                        if (ccClockTweak) {
                            if (snapshot.showCCSeconds) {
                                ModuleHelper.setViewInfo(clock, "showSeconds", true)
                            }
                            initClockStyle(clock, "ccClock", snapshot)
                        }
                    } else if (thisClockId == horizDateClockId) {
                        ModuleHelper.setViewInfo(clock, "clockName", "drawerDate")
                    } else if (dateClockId == thisClockId) {
                        val ccDate = clock.javaClass.canonicalName?.contains("ControlCenterDateView") ?: false
                        if (ccDate) {
                            ModuleHelper.setViewInfo(clock, "clockName", "ccDate")
                        }
                        if (!ccDate) {
                            ModuleHelper.setViewInfo(clock, "clockName", "drawerDate")
                        }
                    }

                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        })

        val clockFormatBuilder = object : ThreadLocal<StringBuilder>() {
            override fun initialValue(): StringBuilder {
                return StringBuilder(32)
            }
        }
        val clockTextBuilder = object : ThreadLocal<StringBuilder>() {
            override fun initialValue(): StringBuilder {
                return StringBuilder(32)
            }
        }
        val updateTimeHook = object : MethodHook(XposedInterface.PRIORITY_HIGHEST) {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var skipped = false
                var result: Any? = null
                var throwable: Throwable? = null
                try {

                    val clock = chain.thisObject as TextView
                    val clockName = ModuleHelper.getViewInfo(clock, "clockName") as String?
                    if (("ccDate" == clockName && hideDateView)
                        || ("drawerDate" == clockName && hideDrawerDate)
                        || ("clock" == clockName && hideStatusbarClock)
                    ) {
                        clock.text = ""
                        return XposedHelpers.throwOrReturn(throwable, result)
                    }

                    val snapshot = currentClockStyleSnapshot() ?: ensureClockStyleSnapshot(clock.context.resources)

                    val mMiuiStatusBarClockController = XposedHelpers.getObjectField(clock, "mMiuiStatusBarClockController")
                    val mCalendar = XposedHelpers.getObjectField(mMiuiStatusBarClockController, "mCalendar")

                    val timeFmt = buildClockText(
                        clockName,
                        snapshot,
                        WeatherDataController.weatherInfo,
                        statusbarClockTweak,
                        ccClockTweak,
                    )
                    if (timeFmt != null) {
                        val formatSb = clockFormatBuilder.get()!!
                        formatSb.setLength(0)
                        formatSb.append(timeFmt)
                        val textSb = clockTextBuilder.get()!!
                        textSb.setLength(0)
                        XposedHelpers.callMethod(mCalendar, "format", clock.context, textSb, formatSb)
                        clock.text = textSb.toString()
                        skipped = true; result = null; throwable = null
                    }

                    if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.views.MiuiClock", lpparam.classLoader, "updateTime", updateTimeHook)
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.views.MiuiStatusBarClock", lpparam.classLoader, "updateTime", updateTimeHook)
        if (hideDateView || hideDrawerDate || hideStatusbarClock) {
            ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.views.MiuiClock", lpparam.classLoader, "onAttachedToWindow", object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var result: Any? = null
                    var throwable: Throwable? = null
                    val thisObject = chain.thisObject
                    try {

                        val clock = thisObject as TextView
                        val clockName = ModuleHelper.getViewInfo(clock, "clockName") as String?
                        if (("ccDate" == clockName && hideDateView)
                            || ("drawerDate" == clockName && hideDrawerDate)
                            || ("clock" == clockName && hideStatusbarClock)
                        ) {
                            XposedHelpers.setObjectField(thisObject, "mAttached", true)
                        }

                        result = chain.proceed()
                    } catch (t: Throwable) {
                        throwable = t
                        result = null
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })
        }
        if (statusbarClockTweak) {
            ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "onAttachedToWindow", object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var result: Any?
                    var throwable: Throwable? = null
                    try {
                        result = chain.proceed()
                    } catch (t: Throwable) {
                        throwable = t
                        result = null
                    }
                    try {
                        val thisObject = chain.thisObject

                        val clock = XposedHelpers.getObjectField(thisObject, "mClock") as TextView
                        initClockStyle(clock, "clock")

                    } catch (t: Throwable) {
                        FatalErrors.unwrapAndRethrowIfFatal(t)
                        XposedHelpers.log(t)
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })
            val customTextColor = MainModule.mPrefs.getBoolean("system_statusbar_clock_chip_customtextcolor")
            val useMonet = MainModule.mPrefs.getBoolean("system_statusbar_clock_chip_usemonet")
            if (MainModule.mPrefs.getBoolean("system_statusbar_clock_chip") && (customTextColor || useMonet)) {
                ModuleHelper.hookAllMethods("com.android.systemui.statusbar.views.MiuiClock", lpparam.classLoader, "onDarkChanged", object : MethodHook() {
                    override fun intercept(chain: XposedInterface.Chain): Any? {
                        var skipped = false
                        var result: Any? = null
                        var throwable: Throwable? = null
                        val thisObject = chain.thisObject
                        try {

                            val clock = thisObject as TextView
                            val clockName = ModuleHelper.getViewInfo(clock, "clockName") as String?
                            if ("clock" == clockName) {
                                skipped = true; result = null; throwable = null
                            }

                            if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                            result = chain.proceed()
                        } catch (t: Throwable) {
                            throwable = t
                            result = null
                        }
                        return XposedHelpers.throwOrReturn(throwable, result)
                    }
                })
            }
            ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.policy.FakeStatusBarClockController", lpparam.classLoader, "initState", object : MethodHook() {
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var skipped = false
                    var result: Any? = null
                    var throwable: Throwable? = null
                    val thisObject = chain.thisObject
                    try {

                        val useLeft = XposedHelpers.getBooleanField(thisObject, "useLeft")
                        if (!useLeft) {
                            val mFakeClock = XposedHelpers.getObjectField(thisObject, "fakeStatusBarClock")
                            if (mFakeClock == null) { skipped = true; result = null; throwable = null }
                        }

                        if (skipped) { return XposedHelpers.throwOrReturn(throwable, result) }
                        result = chain.proceed()
                    } catch (t: Throwable) {
                        throwable = t
                        result = null
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            })
        }
    }

    @JvmStatic
    fun CCClockTweakHook(lpparam: PackageReadyParam) {
        val ccClockSize = MainModule.mPrefs.getInt("system_cc_clock_fontsize", 9)
        if (ccClockSize > 9) {
            MainModule.resHooks.setThemeValueReplacement("com.android.systemui", "dimen", "qs_control_header_clock_size", ccClockSize)
        }
        val ccClockHook = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val clock = XposedHelpers.getObjectField(thisObject, "mBigTime") as TextView
                    val ccClockTweak = MainModule.mPrefs.getBoolean("system_cc_clocktweak")
                    val useSystemFonts = MainModule.mPrefs.getBoolean("system_qs_force_systemfonts")
                    if (ccClockTweak) {
                        val defaultVerticalOffset = 10
                        val verticalOffset = MainModule.mPrefs.getInt("system_cc_clock_verticaloffset", defaultVerticalOffset)
                        if (verticalOffset != defaultVerticalOffset) {
                            val marginTop = HookUtils.dp2px((verticalOffset - defaultVerticalOffset).toFloat())
                            clock.translationY = marginTop
                        }
                    }
                    if (useSystemFonts) {
                        clock.typeface = Typeface.DEFAULT
                    }

                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }
        ModuleHelper.findAndHookMethod("com.android.systemui.qs.MiuiNotificationHeaderView", lpparam.classLoader, "updateResources", ccClockHook)
    }

    @JvmStatic
    fun CCClockCenterAlignHook(lpparam: PackageReadyParam) {
        val centerClock = MainModule.mPrefs.getBoolean("system_cc_clock_centeralign")
        val centerDate = !MainModule.mPrefs.getBoolean("system_drawer_hidedate") && MainModule.mPrefs.getBoolean("system_drawer_date_centeralign")
        val ccClockHook = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    val clock = XposedHelpers.getObjectField(thisObject, "mBigTime") as TextView
                    val mPolicyVisibility = XposedHelpers.getIntField(clock, "mPolicyVisibility")
                    val clockContainer = XposedHelpers.getObjectField(thisObject, "mNotificationHeaderClockContainer") as LinearLayout
                    if (mPolicyVisibility == 0 || mPolicyVisibility == 4) {
                        clockContainer.gravity = Gravity.CENTER_HORIZONTAL
                    } else {
                        clockContainer.gravity = Gravity.START
                    }

                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }
        if (centerClock) {
            ModuleHelper.findAndHookMethod("com.android.systemui.qs.MiuiNotificationHeaderView", lpparam.classLoader, "updateLayout", ccClockHook)
        }
        val clockMarginHook = object : MethodHook() {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                var result: Any?
                var throwable: Throwable? = null
                try {
                    result = chain.proceed()
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                try {
                    val thisObject = chain.thisObject

                    if (centerClock) {
                        val clock = XposedHelpers.getObjectField(thisObject, "mBigTime") as TextView
                        val lp = clock.layoutParams as LinearLayout.LayoutParams
                        lp.leftMargin = 0
                        clock.layoutParams = lp

                        val mWeatherCity = ModuleHelper.getObjectFieldSilently(thisObject, "mWeatherCity")
                        if (mWeatherCity != ModuleHelper.NOT_EXIST_SYMBOL) {
                            val weatherContainer = (mWeatherCity as View).parent as ViewGroup
                            weatherContainer.visibility = View.GONE
                        }
                    }
                    if (centerDate) {
                        val dateView = XposedHelpers.getObjectField(thisObject, "mDateView") as TextView
                        val dateContainer = dateView.parent as LinearLayout
                        dateContainer.gravity = Gravity.CENTER_HORIZONTAL
                    }

                } catch (t: Throwable) {
                    FatalErrors.unwrapAndRethrowIfFatal(t)
                    XposedHelpers.log(t)
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }
        ModuleHelper.findAndHookMethod("com.android.systemui.qs.MiuiNotificationHeaderView", lpparam.classLoader, "onFinishInflate", clockMarginHook)
    }
}
