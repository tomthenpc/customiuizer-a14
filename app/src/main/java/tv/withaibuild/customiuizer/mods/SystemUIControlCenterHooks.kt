package tv.withaibuild.customiuizer.mods

import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.os.Handler
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import java.lang.ref.WeakReference
import miui.process.ProcessManager
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.R
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.ResourceHooks
import tv.withaibuild.customiuizer.mods.utils.ShadeExpansionTracker
import tv.withaibuild.customiuizer.mods.utils.StepCounterController
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.util.ArrayList
import java.util.Comparator
import java.lang.System
import tv.withaibuild.customiuizer.utils.HookUtils
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureConfigResolver
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureEntry
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureEvent
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureMachine
import tv.withaibuild.customiuizer.mods.utils.gesture.StatusBarGestureDependenciesResolver
import tv.withaibuild.customiuizer.mods.utils.gesture.StatusBarGestureEffectExecutor

/**
 * Control centre, volume dialog and brightness UI hooks.
 * StatusBarGesturesHook lives here rather than with the status bar: the status bar
 * brightness slide and the control centre brightness overlay share the same slider
 * state, so they cannot be separated without inventing shared mutable state.
 */
object SystemUIControlCenterHooks {

    @JvmStatic
    fun AddCustomTileHook(lpparam: PackageReadyParam) {
        SystemUIMonitorAndTileHooks.AddCustomTileHook(lpparam)
    }

    private var pluginLoader: ClassLoader? = null

    /**
     * Extract the miui.systemui.plugin ClassLoader from a PluginFactory instance.
     * Tolerates field-name changes by falling back to type-based reflection.
     */
    private fun extractPluginLoader(factory: Any?): ClassLoader? {
        val safeFactory = factory ?: return null
        val clazz = safeFactory.javaClass
        val appInfo = try {
            XposedHelpers.getObjectField(safeFactory, "mAppInfo") as? ApplicationInfo
        } catch (e: Throwable) {
            val field = clazz.declaredFields.firstOrNull { it.type == ApplicationInfo::class.java } ?: return null
            field.isAccessible = true
            field.get(safeFactory) as? ApplicationInfo
        } ?: return null
        if (appInfo.packageName != "miui.systemui.plugin") return null

        val loaderFactory = try {
            XposedHelpers.getObjectField(safeFactory, "mClassLoaderFactory")
        } catch (e: Throwable) {
            val field = clazz.declaredFields.firstOrNull { f ->
                f.name.contains("ClassLoader", ignoreCase = true) && try {
                    f.type.getMethod("get").parameterTypes.isEmpty()
                } catch (e: NoSuchMethodException) {
                    false
                }
            } ?: return null
            field.isAccessible = true
            field.get(safeFactory)
        } ?: return null
        return XposedHelpers.callMethod(loaderFactory, "get") as? ClassLoader
    }

    @JvmStatic
    fun VolumeDialogAutohideDelayHook(classLoader: ClassLoader) {
        ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.MiuiVolumeDialogImpl", classLoader, "computeTimeoutH", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val mHovering = XposedHelpers.getBooleanField(param.getThisObject(), "mHovering")
                if (mHovering) {
                    param.returnAndSkip(16000)
                    return
                }
                val mSafetyWarning = try {
                    XposedHelpers.getObjectField(param.getThisObject(), "mIsSafetyShowing") as Boolean
                } catch (e: Throwable) {
                    XposedHelpers.getObjectField(param.getThisObject(), "mSafetyWarning") as Boolean
                }
                if (mSafetyWarning) {
                    val opt = MainModule.mPrefs.getInt("system_volumedialogdelay_expanded", 0)
                    param.returnAndSkip(if (opt > 0) opt else 5000)
                    return
                }
                val mExpanded = XposedHelpers.getBooleanField(param.getThisObject(), "mExpanded")
                val opt = MainModule.mPrefs.getInt(if (mExpanded) "system_volumedialogdelay_expanded" else "system_volumedialogdelay_collapsed", 0)
                if (opt > 0) param.returnAndSkip(opt)
            }
        })
    }

    private var blurCollapsed = 0.0f

    private var blurExpanded = 0.0f

    private var volumeBlurObserverRegistered = false

    private val volumeBlurPreferenceObserver = object : ModuleHelper.PreferenceObserver {
        override fun onChange(key: String?) = ModuleHelper.guarded {
            if (key == "pref_key_system_volumeblur_collapsed") {
                blurCollapsed = MainModule.mPrefs.getInt(key, 0) / 100f
            }
            if (key == "pref_key_system_volumeblur_expanded") {
                blurExpanded = MainModule.mPrefs.getInt(key, 0) / 100f
            }
        }
    }

    @JvmStatic
    fun BlurVolumeDialogBackgroundHook(classLoader: ClassLoader) {
        blurCollapsed = MainModule.mPrefs.getInt("system_volumeblur_collapsed", 0) / 100f
        blurExpanded = MainModule.mPrefs.getInt("system_volumeblur_expanded", 0) / 100f
        if (!volumeBlurObserverRegistered) {
            volumeBlurObserverRegistered = true
            ModuleHelper.observePreferenceChange(volumeBlurPreferenceObserver)
        }
        ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.MiuiVolumeDialogImpl", classLoader, "updateDialogWindowH", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mWindow = XposedHelpers.getObjectField(param.getThisObject(), "mWindow") as Window
                mWindow.setDimAmount(0.0f)
                val mExpanded = XposedHelpers.getBooleanField(param.getThisObject(), "mExpanded")
                var blurRatio = blurCollapsed
                val isVisible = param.getArgs()[0] as Boolean
                if (mExpanded && !isVisible) {
                    blurRatio = blurExpanded
                }
                if (!mExpanded && blurCollapsed > 0.001f) {
                    mWindow.clearFlags(8)
                }
                if (mExpanded) {
                    XposedHelpers.callMethod(param.getThisObject(), "startBlurAnim", 0f, blurRatio, 0)
                }
            }
        })
        ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.MiuiVolumeDialogImpl", classLoader, "showH", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                if (blurCollapsed > 0.001f) {
                    val mWindow = XposedHelpers.getObjectField(param.getThisObject(), "mWindow") as Window
                    mWindow.clearFlags(8)
                    XposedHelpers.callMethod(param.getThisObject(), "startBlurAnim", 0f, blurCollapsed, 0)
                }
            }
        })
    }

    @JvmStatic
    fun BlurMTKVolumeBarHook(classLoader: ClassLoader) {
        ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.Util", classLoader, "isSupportBlurS", HookerClassHelper.returnConstant(true))
    }

    @JvmStatic
    fun initControlCenter() {
        val loader = pluginLoader ?: return
        if (MainModule.mPrefs.getBoolean("system_nosilentvibrate")) {
            ModuleHelper.hookAllMethods("com.android.systemui.miui.volume.MiuiVolumeDialogImpl", loader, "vibrateH", HookerClassHelper.DO_NOTHING)
        }
        if (MainModule.mPrefs.getInt("system_volumedialogdelay_collapsed", 0) > 0 || MainModule.mPrefs.getInt("system_volumedialogdelay_expanded", 0) > 0) {
            VolumeDialogAutohideDelayHook(loader)
        }
        if (MainModule.mPrefs.getInt("system_volumeblur_collapsed", 0) > 0 || MainModule.mPrefs.getInt("system_volumeblur_expanded", 0) > 0) {
            BlurVolumeDialogBackgroundHook(loader)
        }
        if (MainModule.mPrefs.getBoolean("system_volumebar_blur_mtk")) {
            BlurMTKVolumeBarHook(loader)
        }
        if (MainModule.mPrefs.getBoolean("system_volumetimer")) {
            VolumeTimerValuesRes(loader)
        }
        if (MainModule.mPrefs.getBoolean("system_cc_tile_roundedrect")) {
            CCTileCornerHook(loader)
        }
        if (MainModule.mPrefs.getBoolean("system_cc_volume_showpct")) {
            ShowVolumePctHook(loader)
        }
        if (MainModule.mPrefs.getBoolean("system_qs_hideoperator")
            || MainModule.mPrefs.getBoolean("system_cc_hideoperator_delimiter")
            || MainModule.mPrefs.getBoolean("system_cc_show_stepcount")
        ) {
            CCHeaderHook(loader)
        }
        val customCCGrid = MainModule.mPrefs.getInt("system_ccgridcolumns", 4) > 4
        if (customCCGrid) {
            SystemCCGridHookLoader(loader)
        }
        if (MainModule.mPrefs.getBoolean("system_cc_hide_edit")
            || MainModule.mPrefs.getBoolean("system_cc_hide_profile_monitoring")
        ) {
            CCHideEditButtonHook(loader)
        }
        if (MainModule.mPrefs.getBoolean("system_cc_btandtorch_ascard")) {
            CCBluetoothAsCardHook(loader)
        }
        if (MainModule.mPrefs.getBoolean("system_cc_tile_enabled_color")) {
            CCTileColorHook()
        }
        if (MainModule.mPrefs.getBoolean("system_cc_card_enabled_color")) {
            CCCardColorHook()
        }
        if (MainModule.mPrefs.getBoolean("system_cc_slider_color_enable")) {
            CCSliderColorHook()
        }
    }

    @JvmStatic
    fun CCHeaderHook(classLoader: ClassLoader) {
        val hideOperator = MainModule.mPrefs.getBoolean("system_qs_hideoperator")
        val hideDelimiter = MainModule.mPrefs.getBoolean("system_cc_hideoperator_delimiter")
        val showStep = MainModule.mPrefs.getBoolean("system_cc_show_stepcount")
        val stepViewId = ResourceHooks.getFakeResId("cc_step_view")
        val tag = "StepInControlCenter"
        val hideViewHook = object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val headerView = XposedHelpers.callMethod(param.getThisObject(), "getView") as ViewGroup
                if (hideOperator || hideDelimiter) {
                    val resId = headerView.resources.getIdentifier("header_carrier_view", "id", "miui.systemui.plugin")
                    val mCarrierText = headerView.findViewById<TextView>(resId)
                    if (hideOperator) {
                        mCarrierText.text = ""
                    } else {
                        mCarrierText.text = mCarrierText.text.toString().replace(" | ", "")
                    }
                }
                if (showStep) {
                    val stepView = headerView.findViewWithTag<TextView>(tag)
                    if (stepView != null) {
                        val promptInfo = XposedHelpers.getObjectField(param.getThisObject(), "promptInfo")
                        val miuiPromptInfo = XposedHelpers.getObjectField(param.getThisObject(), "miuiPromptInfo")
                        var viz = View.GONE
                        if (promptInfo == null && miuiPromptInfo == null) {
                            val CommonUtils = XposedHelpers.findClass("miui.systemui.util.CommonUtils", classLoader)
                            val INSTANCE = XposedHelpers.getStaticObjectField(CommonUtils, "INSTANCE")
                            val verticalMode = XposedHelpers.callMethod(INSTANCE, "getInVerticalMode", headerView.context) as Boolean
                            if (verticalMode) {
                                viz = View.VISIBLE
                            }
                        }
                        stepView.visibility = viz
                    }
                }
            }
        }
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.header.StatusHeaderController", classLoader, "adjustCarrierOrPrompt", hideViewHook)

        if (showStep) {
            ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.header.StatusHeaderController", classLoader, "onExpandChange", Float::class.javaPrimitiveType!!, object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val headerView = XposedHelpers.callMethod(param.getThisObject(), "getView") as ViewGroup
                    val stepView = headerView.findViewWithTag<TextView>(tag)
                    if (stepView != null) {
                        stepView.translationY = param.getArgs()[0] as Float
                    }
                }
            })
            val initStepViewHook = object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val headerView = XposedHelpers.callMethod(param.getThisObject(), "getView") as ViewGroup
                    var stepView = headerView.findViewWithTag<TextView>(tag)
                    if (stepView == null) {
                        stepView = TextView(headerView.context)
                        stepView.id = stepViewId
                        val res = headerView.resources
                        val styleId = res.getIdentifier("TextAppearance.Header.Text", "style", "miui.systemui.plugin")
                        stepView.setTextAppearance(styleId)
                        stepView.setTag(tag)
                        headerView.addView(stepView)
                        StepCounterController.bindStepView(stepView)
                    }
                }
            }
            ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.header.StatusHeaderController", classLoader, "createStatusBarViews", initStepViewHook)

            ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.header.StatusHeaderController", classLoader, "updateConstraint", object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    val headerView = XposedHelpers.callMethod(param.getThisObject(), "getView") as ViewGroup
                    val CommonUtils = XposedHelpers.findClass("miui.systemui.util.CommonUtils", classLoader)
                    val INSTANCE = XposedHelpers.getStaticObjectField(CommonUtils, "INSTANCE")
                    val verticalMode = XposedHelpers.callMethod(INSTANCE, "getInVerticalMode", headerView.context) as Boolean
                    if (verticalMode) {
                        val ConstraintSetClass = classLoader.loadClass("androidx.constraintlayout.widget.ConstraintSet")
                        val constraintSet = XposedHelpers.newInstance(ConstraintSetClass)
                        XposedHelpers.callMethod(constraintSet, "clone", headerView)
                        val carrierId = headerView.resources.getIdentifier("header_carrier_view", "id", "miui.systemui.plugin")
                        val iconsId = headerView.resources.getIdentifier("header_status_bar_icons", "id", "miui.systemui.plugin")
                        val dimId = headerView.resources.getIdentifier("header_carrier_vertical_mode_margin_bottom", "dimen", "miui.systemui.plugin")
                        val marginBottom = headerView.resources.getDimensionPixelSize(dimId)
                        XposedHelpers.callMethod(constraintSet, "connect", stepViewId, 4, iconsId, 3, marginBottom)
                        XposedHelpers.callMethod(constraintSet, "connect", stepViewId, 7, carrierId, 6, HookUtils.dp2px(4f).toInt())
                        XposedHelpers.callMethod(constraintSet, "applyTo", headerView)
                    }
                }
            })
        }
    }

    @JvmStatic
    fun hasControlCenterModifications(): Boolean {
        return MainModule.mPrefs.getBoolean("system_nosilentvibrate")
            || MainModule.mPrefs.getInt("system_volumedialogdelay_collapsed", 0) > 0
            || MainModule.mPrefs.getInt("system_volumedialogdelay_expanded", 0) > 0
            || MainModule.mPrefs.getInt("system_volumeblur_collapsed", 0) > 0
            || MainModule.mPrefs.getInt("system_volumeblur_expanded", 0) > 0
            || MainModule.mPrefs.getBoolean("system_volumebar_blur_mtk")
            || MainModule.mPrefs.getBoolean("system_volumetimer")
            || MainModule.mPrefs.getBoolean("system_cc_tile_roundedrect")
            || MainModule.mPrefs.getBoolean("system_cc_volume_showpct")
            || MainModule.mPrefs.getBoolean("system_qs_hideoperator")
            || MainModule.mPrefs.getBoolean("system_cc_hideoperator_delimiter")
            || MainModule.mPrefs.getBoolean("system_cc_show_stepcount")
            || MainModule.mPrefs.getInt("system_ccgridcolumns", 4) > 4
            || MainModule.mPrefs.getBoolean("system_cc_hide_edit")
            || MainModule.mPrefs.getBoolean("system_cc_hide_profile_monitoring")
            || MainModule.mPrefs.getBoolean("system_cc_btandtorch_ascard")
            || MainModule.mPrefs.getBoolean("system_cc_tile_enabled_color")
            || MainModule.mPrefs.getBoolean("system_cc_card_enabled_color")
            || MainModule.mPrefs.getBoolean("system_cc_slider_color_enable")
    }

    @JvmStatic
    fun ControlCenterPluginHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.android.systemui.shared.plugins.PluginInstance\$PluginFactory", lpparam.classLoader, "createPlugin", object : MethodHook() {
            private var isHooked = false
            override fun before(param: BeforeHookCallback) {
                if (isHooked) return
                val loader = extractPluginLoader(param.getThisObject()) ?: return
                isHooked = true
                if (pluginLoader == null) {
                    pluginLoader = loader
                    initControlCenter()
                }
            }
        })
    }

    private var iconScaleRatio = 1f

    @JvmStatic
    fun SystemCCGridHookLoader(pluginLoader: ClassLoader) {
        val cols = MainModule.mPrefs.getInt("system_ccgridcolumns", 4)
        iconScaleRatio = 4f / cols
        val resizeIconFrame = object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val thisView = param.getThisObject() as FrameLayout
                val resId = thisView.resources.getIdentifier("icon_frame", "id", "miui.systemui.plugin")
                val iconFrame = thisView.findViewById<View>(resId)
                val iconSize = HookUtils.dp2px(68f * iconScaleRatio).toInt()
                iconFrame.layoutParams.width = iconSize
                iconFrame.layoutParams.height = iconSize

                if (param.getMember().name == "onFinishInflate") {
                    XposedHelpers.callMethod(thisView, "changeExpand")
                }
            }
        }
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.qs.tileview.QSTileItemView", pluginLoader, "updateSize", resizeIconFrame)
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.qs.tileview.QSTileItemView", pluginLoader, "onFinishInflate", resizeIconFrame)
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.qs.tileview.QSTileItemView", pluginLoader, "updateContainerHeight", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val iconSize = HookUtils.dp2px(85f * iconScaleRatio + 1).toInt()
                XposedHelpers.setObjectField(param.getThisObject(), "containerHeight", iconSize)
            }
        })

        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelController", pluginLoader, "setUseSeparatedPanels", Boolean::class.java, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                if (param.getArg(0) == null) {
                    param.returnAndSkip(null)
                    return
                }
                val bool = param.getArg(0) as Boolean
                val oldVal = XposedHelpers.getObjectField(param.getThisObject(), "useSeparatedPanels")
                if (bool == oldVal) {
                    param.returnAndSkip(null)
                    return
                }
                XposedHelpers.setObjectField(param.getThisObject(), "useSeparatedPanels", bool)
                val horizontalMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "horizontalMainPanel") as LinearLayout
                val leftMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "leftMainPanel") as ViewGroup
                horizontalMainPanel.removeView(leftMainPanel)
                if (!bool) {
                    horizontalMainPanel.addView(leftMainPanel)
                    val layoutParams = leftMainPanel.layoutParams
                    (layoutParams as ViewGroup.MarginLayoutParams).setMarginEnd(0)
                    horizontalMainPanel.orientation = LinearLayout.VERTICAL
                } else {
                    horizontalMainPanel.addView(leftMainPanel, 0)
                    val marginId = horizontalMainPanel.resources.getIdentifier("control_center_horizontal_margin_center", "dimen", "miui.systemui.plugin")
                    val marginEnd = horizontalMainPanel.resources.getDimensionPixelSize(marginId)
                    XposedHelpers.setObjectField(param.getThisObject(), "panelMargin", marginEnd)
                    val layoutParams = leftMainPanel.layoutParams
                    (layoutParams as ViewGroup.MarginLayoutParams).setMarginEnd(marginEnd)
                    horizontalMainPanel.orientation = LinearLayout.HORIZONTAL
                }
                param.returnAndSkip(null)
            }
        })

        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelContentDistributor", pluginLoader, "distributePanels", Boolean::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val horizontal = param.getArgs()[0] as Boolean
                if (!horizontal && XposedHelpers.getBooleanField(param.getThisObject(), "inited")) {
                    val rightPanelContent = XposedHelpers.getObjectField(param.getThisObject(), "rightPanelContent") as ArrayList<*>
                    val leftPanelContent = XposedHelpers.getObjectField(param.getThisObject(), "leftPanelContent") as ArrayList<Any>
                    val size = rightPanelContent.size
                    for (i in size - 1 downTo 0) {
                        val controller = rightPanelContent[i]
                        val className = controller?.javaClass?.canonicalName ?: ""
                        if (className.contains("EditButtonController")
                            || className.contains("SecurityFooterController")
                            || className.contains("QSListController")
                        ) {
                            rightPanelContent.removeAt(i)
                            leftPanelContent.add(controller)
                        } else if (className.contains("FooterSpaceController")) {
                            rightPanelContent.removeAt(i)
                        }
                    }
                    leftPanelContent.sortWith(Comparator { lhs, rhs ->
                        val leftPriority = XposedHelpers.callMethod(lhs, "getPriority") as Int
                        val rightPriority = XposedHelpers.callMethod(rhs, "getPriority") as Int
                        leftPriority - rightPriority
                    })
                }
            }
        })

        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelController", pluginLoader, "updatePanelSize", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val useSeparatedPanels = XposedHelpers.getObjectField(param.getThisObject(), "useSeparatedPanels") as? Boolean
                if (useSeparatedPanels != true) {
                    val leftMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "leftMainPanel") as ViewGroup
                    val rightMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "rightMainPanel") as ViewGroup
                    val panelWidth = XposedHelpers.getIntField(param.getThisObject(), "panelWidth")
                    leftMainPanel.layoutParams.width = panelWidth
                    leftMainPanel.layoutParams.height = -2
                    rightMainPanel.layoutParams.width = panelWidth
                    rightMainPanel.layoutParams.height = -2
                    param.returnAndSkip(null)
                }
            }
        })

        val MainPanelAdapter = XposedHelpers.findClass("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter", pluginLoader)

        val spanSizeHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val adapter = XposedHelpers.getSurroundingThis(param.getThisObject())
                val leftAdapter = XposedHelpers.getAdditionalInstanceField(adapter, "leftAdapter") != null
                if (leftAdapter) {
                    val companion = XposedHelpers.getStaticObjectField(MainPanelAdapter, "Companion")
                    val contentMap = XposedHelpers.getObjectField(adapter, "contentMap")
                    val panelItem = XposedHelpers.callMethod(companion, "getItem", contentMap, param.getArg(0))
                    if (panelItem == null) {
                        param.returnAndSkip(cols)
                    } else {
                        param.returnAndSkip(XposedHelpers.callMethod(panelItem, "getSpanSize"))
                    }
                }
            }
        }

        ModuleHelper.hookAllMethods("miui.systemui.controlcenter.panel.main.recyclerview.MainPanelAdapter\$Factory", pluginLoader, "create", object : MethodHook() {
            private var hooked = false
            override fun after(param: AfterHookCallback) {
                if (!hooked) {
                    hooked = true
                    XposedHelpers.setAdditionalInstanceField(param.getResult(), "leftAdapter", true)
                    val layoutManager = XposedHelpers.getObjectField(param.getResult(), "layoutManager")
                    XposedHelpers.callMethod(layoutManager, "setSpanCount", cols)
                    val spanSizeLookup = XposedHelpers.callMethod(layoutManager, "getSpanSizeLookup")
                    ModuleHelper.findAndHookMethod(spanSizeLookup.javaClass, "getSpanSize", Int::class.javaPrimitiveType!!, spanSizeHook)
                }
            }
        })

        val columnsReplaceHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                param.returnAndSkip(cols)
            }
        }
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.header.HeaderSpaceController", pluginLoader, "getSpanSize", columnsReplaceHook)
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.security.SecurityFooterController", pluginLoader, "getSpanSize", columnsReplaceHook)
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.qs.EditButtonController", pluginLoader, "getSpanSize", columnsReplaceHook)
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.qs.QSListController\$EditModeDividerTextItem", pluginLoader, "getSpanSize", columnsReplaceHook)

        // handle secondary panel show
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelAnimController", pluginLoader, "updateVisibility", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val CommonUtils = XposedHelpers.findClass("miui.systemui.util.CommonUtils", pluginLoader)
                val INSTANCE = XposedHelpers.getStaticObjectField(CommonUtils, "INSTANCE")
                val mContext = XposedHelpers.callMethod(param.getThisObject(), "getContext")
                val verticalMode = XposedHelpers.callMethod(INSTANCE, "getInVerticalMode", mContext) as Boolean
                if (verticalMode) {
                    val leftMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "leftMainPanel") as ViewGroup
                    leftMainPanel.visibility = param.getArgs()[0] as Int
                }
            }
        })
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelAnimController", pluginLoader, "forceToShow", Object::class.java, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val leftMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "leftMainPanel") as ViewGroup
                leftMainPanel.alpha = 1.0f
            }
        })
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelAnimController", pluginLoader, "onAnimUpdate", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val CommonUtils = XposedHelpers.findClass("miui.systemui.util.CommonUtils", pluginLoader)
                val INSTANCE = XposedHelpers.getStaticObjectField(CommonUtils, "INSTANCE")
                val mContext = XposedHelpers.callMethod(param.getThisObject(), "getContext")
                val verticalMode = XposedHelpers.callMethod(INSTANCE, "getInVerticalMode", mContext) as Boolean
                if (verticalMode) {
                    val leftMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "leftMainPanel") as ViewGroup
                    val rightMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "rightMainPanel") as ViewGroup
                    val alpha = rightMainPanel.alpha
                    leftMainPanel.alpha = alpha
                }
            }
        })
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.panel.main.MainPanelAnimController", pluginLoader, "onConfigurationChanged", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val i = param.getArgs()[0] as Int
                if ((i and 128) != 0) {
                    val CommonUtils = XposedHelpers.findClass("miui.systemui.util.CommonUtils", pluginLoader)
                    val INSTANCE = XposedHelpers.getStaticObjectField(CommonUtils, "INSTANCE")
                    val mContext = XposedHelpers.callMethod(param.getThisObject(), "getContext")
                    val verticalMode = XposedHelpers.callMethod(INSTANCE, "getInVerticalMode", mContext) as Boolean
                    val leftMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "leftMainPanel") as ViewGroup
                    val rightMainPanel = XposedHelpers.getObjectField(param.getThisObject(), "rightMainPanel") as ViewGroup
                    if (verticalMode) {
                        leftMainPanel.alpha = rightMainPanel.alpha
                        leftMainPanel.visibility = rightMainPanel.visibility
                    } else {
                        leftMainPanel.alpha = 1.0f
                        leftMainPanel.visibility = View.VISIBLE
                    }
                }
            }
        })
    }

    @JvmStatic
    fun CCHideEditButtonHook(pluginLoader: ClassLoader) {
        val hideEdit = MainModule.mPrefs.getBoolean("system_cc_hide_edit")
        val hideSecurity = MainModule.mPrefs.getBoolean("system_cc_hide_profile_monitoring")
        if (!hideEdit && !hideSecurity) return

        fun shouldHide(controller: Any?): Boolean {
            val className = controller?.javaClass?.canonicalName ?: ""
            return (hideEdit && className.contains("EditButtonController"))
                || (hideSecurity && className.contains("SecurityFooterController"))
        }

        // Filter the source list when the distributor is first created.
        ModuleHelper.hookAllConstructors("miui.systemui.controlcenter.panel.main.MainPanelContentDistributor", pluginLoader, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val thisObj = param.getThisObject()
                val rawChildControllers = try {
                    XposedHelpers.getObjectField(thisObj, "childControllers")
                } catch (t: Throwable) {
                    return
                }

                val childControllers: MutableList<Any?> = when (rawChildControllers) {
                    is MutableList<*> -> rawChildControllers.toMutableList()
                    is List<*> -> ArrayList(rawChildControllers as Collection<Any?>)
                    else -> return
                }

                val iter = childControllers.iterator()
                while (iter.hasNext()) {
                    if (shouldHide(iter.next())) iter.remove()
                }

                try {
                    XposedHelpers.setObjectField(thisObj, "childControllers", childControllers)
                } catch (ignored: Throwable) {
                    // final or unmodifiable field: rely on the distributePanels fallback
                }
            }
        })

        // Also remove from the panel content lists each time they are rebuilt.
        ModuleHelper.hookAllMethods("miui.systemui.controlcenter.panel.main.MainPanelContentDistributor", pluginLoader, "distributePanels", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val thisObj = param.getThisObject()
                val rightPanelContent = try {
                    XposedHelpers.getObjectField(thisObj, "rightPanelContent") as? ArrayList<*>
                } catch (t: Throwable) {
                    null
                } ?: return
                val leftPanelContent = try {
                    XposedHelpers.getObjectField(thisObj, "leftPanelContent") as? ArrayList<*>
                } catch (t: Throwable) {
                    null
                } ?: return

                val sizeRight = rightPanelContent.size
                for (i in sizeRight - 1 downTo 0) {
                    if (shouldHide(rightPanelContent[i])) rightPanelContent.removeAt(i)
                }
                val sizeLeft = leftPanelContent.size
                for (i in sizeLeft - 1 downTo 0) {
                    if (shouldHide(leftPanelContent[i])) leftPanelContent.removeAt(i)
                }
            }
        })
    }

    @JvmStatic
    fun CCBluetoothAsCardHook(pluginLoader: ClassLoader) {
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.qs.QSController", pluginLoader, "getCardStyleTileSpecs", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                param.returnAndSkip(listOf("wifi", "cell", "bt", "flashlight"))
            }
        })
    }

    @JvmStatic
    fun CCTileColorHook() {
        val customColor = MainModule.mPrefs.getInt("system_cc_tile_enabled_color_custom", 0xff277af7.toInt())
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "qs_enabled_color", customColor)
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "qs_warning_color", customColor)

        val iconColor = MainModule.mPrefs.getInt("system_cc_tile_enabled_iconcolor_custom", 0xffffffff.toInt())
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "qs_icon_enabled_color", iconColor)
    }

    @JvmStatic
    fun CCCardColorHook() {
        val customColor = MainModule.mPrefs.getInt("system_cc_card_enabled_color_custom", 0xff3482ff.toInt())
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "qs_card_cellular_color", customColor)
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "qs_card_enabled_color", customColor)
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "qs_card_flashlight_color", customColor)

        val primaryColor = MainModule.mPrefs.getInt("system_cc_card_enabled_primary_textcolor", 0xffffffff.toInt())
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "qs_card_primary_text_enabled_color", primaryColor)
        val secondaryColor = MainModule.mPrefs.getInt("system_cc_card_enabled_secondary_textcolor", 0x80ffffff.toInt())
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "qs_card_secondary_text_enabled_color", secondaryColor)

        val iconColor = MainModule.mPrefs.getInt("system_cc_card_enabled_iconcolor_custom", 0xffffffff.toInt())
        if (iconColor != 0xffffffff.toInt()) {
            val loader = pluginLoader ?: return
            ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.qs.tileview.QSCardItemIconView", loader, "updateResources", object : MethodHook() {
                override fun after(param: AfterHookCallback) {
                    XposedHelpers.setObjectField(param.getThisObject(), "iconColor", iconColor)
                }
            })
        }
    }

    @JvmStatic
    fun CCSliderColorHook() {
        val customColor = MainModule.mPrefs.getInt("system_cc_slider_progress_color", 0xffffffff.toInt())
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "toggle_slider_progress_color", customColor)
        val blendColors = intArrayOf(customColor, 3)
        MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "integer-array", "toggle_slider_progress_blend_colors", blendColors)

        val iconColor = MainModule.mPrefs.getInt("system_cc_slider_icon_color", 0xff959595.toInt())
        if (iconColor != 0xff959595.toInt()) {
            MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "color", "toggle_slider_icon_color", iconColor)
            val iconBlendColors = intArrayOf(iconColor, 3)
            MainModule.resHooks.setThemeValueReplacement("miui.systemui.plugin", "integer-array", "toggle_slider_icon_blend_colors", iconBlendColors)
        }
    }

    @JvmStatic
    fun VolumeTimerValuesRes(pluginLoader: ClassLoader) {
        ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.MiuiVolumeTimerDrawableHelper", pluginLoader, "initTimerString", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                val mTimeSegmentTitle = arrayOfNulls<String>(11)
                val timerOffId = mContext.resources.getIdentifier("timer_off", "string", "miui.systemui.plugin")
                val minuteId = mContext.resources.getIdentifier("timer_30_minutes", "string", "miui.systemui.plugin")
                val hourId = mContext.resources.getIdentifier("timer_1_hour", "string", "miui.systemui.plugin")
                mTimeSegmentTitle[0] = mContext.resources.getString(timerOffId)
                mTimeSegmentTitle[1] = mContext.resources.getString(minuteId, 30)
                mTimeSegmentTitle[2] = mContext.resources.getString(hourId, 1)
                mTimeSegmentTitle[3] = mContext.resources.getString(hourId, 2)
                mTimeSegmentTitle[4] = mContext.resources.getString(hourId, 3)
                mTimeSegmentTitle[5] = mContext.resources.getString(hourId, 4)
                mTimeSegmentTitle[6] = mContext.resources.getString(hourId, 5)
                mTimeSegmentTitle[7] = mContext.resources.getString(hourId, 6)
                mTimeSegmentTitle[8] = mContext.resources.getString(hourId, 8)
                mTimeSegmentTitle[9] = mContext.resources.getString(hourId, 10)
                mTimeSegmentTitle[10] = mContext.resources.getString(hourId, 12)
                XposedHelpers.setObjectField(param.getThisObject(), "mTimeSegmentTitle", mTimeSegmentTitle)
            }
        })
        ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.TimerItem", pluginLoader, "getTimePos", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val timer = XposedHelpers.getObjectField(param.getThisObject(), "mTimerTime")
                val halfTimerWidth = (XposedHelpers.callMethod(timer, "getWidth") as Int) / 2.0f
                val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                val mTimerSeekbarWidth = ModuleHelper.getObjectFieldSilently(param.getThisObject(), "mTimerSeekbarWidth")
                val seekbarWidthResId: Int
                if (mTimerSeekbarWidth == ModuleHelper.NOT_EXIST_SYMBOL) {
                    seekbarWidthResId = mContext.resources.getIdentifier("miui_volume_timer_seelbar_width", "dimen", "miui.systemui.plugin")
                } else {
                    seekbarWidthResId = mTimerSeekbarWidth as Int
                }
                val mTimerSeekbarMarginLeft = mContext.resources.getIdentifier("miui_volume_timer_seekbar_margin_left", "dimen", "miui.systemui.plugin")
                val seekWidth = mContext.resources.getDimension(seekbarWidthResId)
                val marginLeft = mContext.resources.getDimensionPixelSize(mTimerSeekbarMarginLeft)
                val seg = XposedHelpers.getObjectField(param.getThisObject(), "mDeterminedSegment") as Int
                param.returnAndSkip(seekWidth / 10 * seg + marginLeft - halfTimerWidth)
            }
        })

        val segHook = object : MethodHook() {
            private var prevSeg = 0
            override fun before(param: BeforeHookCallback) {
                prevSeg = XposedHelpers.getIntField(param.getThisObject(), "mCurrentSegment")
                if (prevSeg < 3 || (prevSeg == 3 && XposedHelpers.getIntField(param.getThisObject(), "mDeterminedSegment") == 3)) {
                    XposedHelpers.setIntField(param.getThisObject(), "mCurrentSegment", 0)
                }
            }
            override fun after(param: AfterHookCallback) {
                XposedHelpers.setIntField(param.getThisObject(), "mCurrentSegment", prevSeg)
            }
        }

        ModuleHelper.findAndHookMethod("com.android.systemui.miui.volume.MiuiVolumeTimerDrawableHelper", pluginLoader, "updateDrawables", segHook)
    }

    @JvmStatic
    fun CCTileCornerHook(pluginLoader: ClassLoader) {
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView", pluginLoader, "getCornerRadius", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val radius = 20 * iconScaleRatio
                param.returnAndSkip(HookUtils.dp2px(radius))
            }
        })
        val radiusHook = object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val drawable = param.getResult()
                val gradientDrawable = drawable as? GradientDrawable
                if (gradientDrawable != null) {
                    val radius = 20 * iconScaleRatio
                    gradientDrawable.cornerRadius = HookUtils.dp2px(radius)
                }
            }
        }
        ModuleHelper.hookAllMethods("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView", pluginLoader, "getDisabledBackgroundDrawable", radiusHook)
        ModuleHelper.hookAllMethods("miui.systemui.controlcenter.qs.tileview.QSTileItemIconView", pluginLoader, "getActiveBackgroundDrawable", radiusHook)
    }

    private var isSlidingStart = false

    private var isSliding = false

    private var tapStartX = 0f

    private var tapStartY = 0f

    private var tapStartPointers = 0f

    private var tapStartBrightness = 0f

    private var topMinimumBacklight = 0.0f

    private var topMaximumBacklight = 1.0f

    private var currentTouchX = 0f

    private var currentTouchTime = 0L

    private var currentDownTime = 0L

    private var currentDownX = 0f

    private var nextBrightNess = -999f

    private val shadeExpansionTracker = ShadeExpansionTracker(0.33f)

    @JvmStatic
    fun StatusBarGesturesHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.shade.MiuiNotificationPanelViewController", lpparam.classLoader, "setExpandedHeightInternal", Float::class.javaPrimitiveType!!, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val mExpandedFraction = XposedHelpers.getFloatField(param.getThisObject(), "mExpandedFraction")
                if (shadeExpansionTracker.update(mExpandedFraction) && mExpandedFraction > 0.33f) {
                    currentTouchTime = 0L
                    currentTouchX = 0f
                    currentDownTime = 0L
                    currentDownX = 0f
                }
            }
        })

        val hook = object : MethodHook() {
            private var mBrightnessController: Any? = null
            private var mDisplayManager: Any? = null
            private var mDisplayId: Int = -1
            private var sbHeight = -1

            @SuppressLint("SetTextI18n")
            override fun before(param: BeforeHookCallback) {
                val clsName = param.getThisObject()!!.javaClass.simpleName
                val isInControlCenter = "ControlCenterWindowViewImpl" == clsName
                if (isInControlCenter) {
                    if (param.getArgs().size == 2 && (param.getArg(1) as Boolean)) {
                        return
                    }
                    val statusBarStateController = XposedHelpers.getObjectField(param.getThisObject(), "statusBarStateController")
                    val state = XposedHelpers.callMethod(statusBarStateController, "getState") as Int
                    if (state == 1 || state == 2) {
                        return
                    }
                }
                val mContext = (param.getThisObject() as View).context
                val res = mContext.resources
                if (sbHeight == -1) {
                    sbHeight = res.getDimensionPixelSize(res.getIdentifier("status_bar_height_default", "dimen", "android"))
                }
                val event = param.getArg(0) as MotionEvent
                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        tapStartX = event.x
                        tapStartY = event.y
                        isSlidingStart = !isInControlCenter || tapStartY <= sbHeight
                        tapStartPointers = 1f
                        if (mBrightnessController == null) {
                            val mControlCenterController: Any? = if (isInControlCenter) {
                                XposedHelpers.getObjectField(param.getThisObject(), "controlCenterController")
                            } else {
                                ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.controlcenter.policy.ControlCenterControllerImpl")
                            }
                            mBrightnessController = XposedHelpers.callMethod(XposedHelpers.getObjectField(mControlCenterController, "brightnessController"), "get")
                            mDisplayManager = XposedHelpers.getObjectField(mBrightnessController, "mDisplayManager")
                            mDisplayId = XposedHelpers.getIntField(mBrightnessController, "mDisplayId")
                        }
                        topMinimumBacklight = XposedHelpers.getObjectField(mBrightnessController, "mMinimumBacklight") as Float
                        topMaximumBacklight = XposedHelpers.getObjectField(mBrightnessController, "mMaximumBacklight") as Float
                        tapStartBrightness = XposedHelpers.callMethod(mDisplayManager, "getBrightness", mDisplayId) as Float
                        if (isSlidingStart) {
                            currentDownTime = java.lang.System.currentTimeMillis()
                            currentDownX = tapStartX
                        } else {
                            currentDownTime = 0L
                            currentDownX = 0f
                        }
                        nextBrightNess = -999f
                    }
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        tapStartPointers = event.pointerCount.toFloat()
                    }
                    MotionEvent.ACTION_UP -> {
                        val lastTouchTime = currentTouchTime
                        val lastTouchX = currentTouchX
                        currentTouchTime = java.lang.System.currentTimeMillis()
                        currentTouchX = event.x
                        val mTouchX = currentTouchX
                        val mTouchTime = currentTouchTime
                        if (currentTouchTime - lastTouchTime < 250L && Math.abs(currentTouchX - lastTouchX) < 80F) {
                            currentTouchTime = 0L
                            currentTouchX = 0F
                            val screenWidth = res.displayMetrics.widthPixels
                            var actionKey = "system_statusbarcontrols_dt"
                            if (mTouchX * 5 < screenWidth) {
                                actionKey = "system_statusbarcontrols_dt_left"
                            } else if (mTouchX > screenWidth * 0.8) {
                                actionKey = "system_statusbarcontrols_dt_right"
                            }
                            GlobalActions.handleAction(mContext, actionKey)
                        } else if ((mTouchTime - currentDownTime > 600 && mTouchTime - currentDownTime < 4000)
                            && Math.abs(mTouchX - currentDownX) < 80F) {
                            if (MainModule.mPrefs.getBoolean("system_statusbarcontrols_longpress_vibrate")) {
                                val ignoreOff = MainModule.mPrefs.getBoolean("system_statusbarcontrols_longpress_vibrate_ignoreoff")
                                HookUtils.performStrongVibration(mContext, ignoreOff)
                            }
                            GlobalActions.handleAction(mContext, "system_statusbarcontrols_longpress")
                        }
                        if (nextBrightNess > -10 && mDisplayManager != null) {
                            XposedHelpers.callMethod(mDisplayManager, "setBrightness", mDisplayId, nextBrightNess)
                            nextBrightNess = -999f
                        }
                        currentDownTime = 0L
                        currentDownX = 0f
                        isSlidingStart = false
                        isSliding = false
                        nextBrightNess = -999f
                    }
                    MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                        isSlidingStart = false
                        isSliding = false
                        nextBrightNess = -999f
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (!isSlidingStart) return
                        if (event.y - tapStartY > sbHeight) {
                            currentDownTime = 0L
                            currentDownX = 0f
                            return
                        }
                        val metrics = res.displayMetrics
                        val delta = event.x - tapStartX
                        if (delta == 0f) return
                        if (!isSliding && Math.abs(delta) > metrics.widthPixels / 10f) isSliding = true
                        if (!isSliding) return
                        val opt = MainModule.mPrefs.getStringAsInt(if (tapStartPointers == 2f) "system_statusbarcontrols_dual" else "system_statusbarcontrols_single", 1)
                        if (opt == 2) {
                            val sens = MainModule.mPrefs.getStringAsInt("system_statusbarcontrols_sens_bright", 2)
                            var ratio = delta / metrics.widthPixels
                            ratio = (if (sens == 1) 0.66f else if (sens == 3) 1.66f else 1.0f) * ratio * 0.618f
                            val nextLevel = Math.min(topMaximumBacklight, Math.max(topMinimumBacklight, tapStartBrightness + (topMaximumBacklight - topMinimumBacklight) * ratio))
                            if (mDisplayManager != null) {
                                XposedHelpers.callMethod(mDisplayManager, "setTemporaryBrightness", mDisplayId, nextLevel)
                            }
                            nextBrightNess = nextLevel
                        } else if (opt == 3) {
                            val sens = MainModule.mPrefs.getStringAsInt("system_statusbarcontrols_sens_vol", 2)
                            if (Math.abs(delta) < metrics.widthPixels / ((if (sens == 1) 0.66f else if (sens == 3) 1.66f else 1.0f) * 20 * metrics.density)) return
                            tapStartX = event.x
                            val audioManager = mContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                            @Suppress("WrongConstant")
                            audioManager.adjustVolume(if (delta > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, (1 shl 12) or AudioManager.FLAG_SHOW_UI or AudioManager.FLAG_ALLOW_RINGER_MODES or AudioManager.FLAG_VIBRATE)
                        }
                    }
                }
            }
        }
        val statusBarMachine = GestureMachine(
            classLoaderIdentity = lpparam.packageName.orEmpty(),
            configResolver = { GestureConfigResolver.resolve(MainModule.mPrefs) },
            depsResolver = StatusBarGestureDependenciesResolver(lpparam.classLoader),
            effectExecutor = StatusBarGestureEffectExecutor(),
        )
        val statusBarHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val thisObject = param.getThisObject() as? View ?: return
                val event = param.getArg(0) as? MotionEvent ?: return
                val entry = if (param.getMember().name == "onTouchEvent") GestureEntry.STATUS_BAR_TOUCH else GestureEntry.STATUS_BAR_INTERCEPT
                val gestureEvent = GestureEvent(
                    entry = entry,
                    actionMasked = event.actionMasked,
                    downTime = event.downTime,
                    eventTime = event.eventTime,
                    x = event.x,
                    y = event.y,
                    pointerCount = event.pointerCount,
                    ownerId = System.identityHashCode(thisObject),
                )
                ModuleHelper.guarded {
                    statusBarMachine.dispatch(gestureEvent, thisObject)
                }
            }
        }

        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBarView", lpparam.classLoader, "onInterceptTouchEvent", MotionEvent::class.java, statusBarHook)
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.PhoneStatusBarView", lpparam.classLoader, "onTouchEvent", MotionEvent::class.java, statusBarHook)
        ModuleHelper.hookAllMethods("com.android.systemui.shared.plugins.PluginInstance\$PluginFactory", lpparam.classLoader, "createPlugin", object : MethodHook() {
            private var isHooked = false
            override fun before(param: BeforeHookCallback) {
                if (isHooked) return
                val loader = extractPluginLoader(param.getThisObject()) ?: return
                isHooked = true
                if (pluginLoader == null) pluginLoader = loader
                ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.windowview.ControlCenterWindowViewImpl", loader, "handleMotionEvent", MotionEvent::class.java, Boolean::class.javaPrimitiveType!!, hook)
            }
        })
    }

    @JvmStatic
    fun SecureQSTilesHook(lpparam: PackageReadyParam) {
        val clickHook = object : MethodHook(XposedInterface.PRIORITY_HIGHEST) {
            override fun before(param: BeforeHookCallback) {
                val tileName = XposedHelpers.getObjectField(param.getThisObject(), "mTileSpec") as String
                var name = tileName
                if (name.startsWith("intent(")) name = "intent"
                else if (name.startsWith("custom(")) name = "custom"
                val secure = when (name) {
                    "wifi" -> MainModule.mPrefs.getBoolean("system_secureqs_wifi")
                    "bt" -> MainModule.mPrefs.getBoolean("system_secureqs_bt")
                    "cell" -> MainModule.mPrefs.getBoolean("system_secureqs_mobiledata")
                    "airplane" -> MainModule.mPrefs.getBoolean("system_secureqs_airplane")
                    "gps" -> MainModule.mPrefs.getBoolean("system_secureqs_location")
                    "hotspot" -> MainModule.mPrefs.getBoolean("system_secureqs_hotspot")
                    "nfc" -> MainModule.mPrefs.getBoolean("system_secureqs_nfc")
                    "sync" -> MainModule.mPrefs.getBoolean("system_secureqs_sync")
                    "intent", "custom" -> MainModule.mPrefs.getBoolean("system_secureqs_custom")
                    else -> false
                }
                if (secure) {
                    val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                    val kgMgr = mContext.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
                    if (!kgMgr.isKeyguardLocked || !kgMgr.isKeyguardSecure) return
                    val activityStater = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.plugins.ActivityStarter")
                    XposedHelpers.callMethod(activityStater, "postQSRunnableDismissingKeyguard", true, Runnable {
                        ModuleHelper.guarded {
                            val keepOpened = MainModule.mPrefs.getBoolean("system_secureqs_keepopened")
                            if (keepOpened) {
                                val handler = Handler(mContext.mainLooper)
                                handler.postDelayed({
                                    ModuleHelper.guarded {
                                        val openCCIntent = Intent(GlobalActions.ACTION_PREFIX + "ExpandSettings")
                                        openCCIntent.setPackage("com.android.systemui")
                                        mContext.sendBroadcast(openCCIntent)
                                    }
                                }, 800)
                            }
                        }
                    })
                    val mStatusBar = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.statusbar.CommandQueue")
                    XposedHelpers.callMethod(mStatusBar, "animateCollapsePanels", 0, false)
                    param.returnAndSkip(null)
                }
            }
        }
        ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader, "click", View::class.java, clickHook)
        ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader, "longClick", View::class.java, clickHook)
        ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader, "secondaryClick", View::class.java, clickHook)
    }

    private var mPctRef: WeakReference<TextView>? = null

    private val mPct: TextView?
        get() = mPctRef?.get()

    private fun initPct(container: ViewGroup, source: Int, context: Context): TextView {
        val res = context.resources
        var pct = mPct
        if (pct == null) {
            pct = TextView(container.context)
            pct.setTextSize(TypedValue.COMPLEX_UNIT_SP, 40f)
            pct.gravity = Gravity.CENTER
            val density = res.displayMetrics.density
            val lp = FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT)
            lp.topMargin = Math.round(MainModule.mPrefs.getInt("system_showpct_top", 28) * density)
            lp.gravity = Gravity.CENTER_HORIZONTAL or Gravity.TOP
            pct.setPadding(Math.round(20 * density), Math.round(10 * density), Math.round(18 * density), Math.round(12 * density))
            pct.layoutParams = lp
            try {
                val modRes = ModuleHelper.getModuleRes(context)
                pct.setTextColor(modRes.getColor(R.color.color_on_surface_variant, context.theme))
                pct.background = ResourcesCompat.getDrawable(modRes, R.drawable.input_background, context.theme)
            } catch (err: Throwable) {
                XposedHelpers.log(err)
            }
            container.addView(pct)
            mPctRef = WeakReference(pct)
        }
        pct.setTag(source)
        pct.visibility = View.GONE
        return pct
    }

    private fun removePct(mPctText: TextView?) {
        if (mPctText != null) {
            mPctText.visibility = View.GONE
            (mPctText.parent as? ViewGroup)?.removeView(mPctText)
            if (mPctRef?.get() === mPctText) mPctRef = null
        }
    }

    private fun startShowPct(lpparam: PackageReadyParam, mContext: Context) {
        val controlCenter = ModuleHelper.getDepInstance(lpparam.classLoader, "com.android.systemui.controlcenter.phone.ControlPanelWindowManager")
        val controlCenterWindowView = XposedHelpers.getObjectField(controlCenter, "windowView")
        val windowView = XposedHelpers.callMethod(controlCenterWindowView, "getView") as ViewGroup
        initPct(windowView, 2, mContext).visibility = View.VISIBLE
    }

    @JvmStatic
    fun BrightnessPctHook(lpparam: PackageReadyParam) {
        ModuleHelper.hookAllMethods("com.android.systemui.controlcenter.policy.MiuiBrightnessController", lpparam.classLoader, "onStart", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                startShowPct(lpparam, mContext)
            }
        })

        ModuleHelper.hookAllMethods("com.android.systemui.controlcenter.policy.MiuiBrightnessController", lpparam.classLoader, "setToggleSliderBase", object : MethodHook() {
            private var inited = false
            override fun after(param: AfterHookCallback) {
                if (!inited && param.getArgs()[0] != null) {
                    inited = true
                    val className = param.getArgs()[0]!!.javaClass.simpleName
                    if ("ToggleSliderViewHolder" == className) return
                    val brightnessSeekBar = param.getArgs()[0]
                    val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                    val mOnSeekBarChangeListener = XposedHelpers.getObjectField(brightnessSeekBar, "mOnSeekBarChangeListener") ?: return
                    ModuleHelper.findAndHookMethod(mOnSeekBarChangeListener.javaClass, "onStartTrackingTouch", SeekBar::class.java, object : MethodHook() {
                        override fun before(param: BeforeHookCallback) {
                            val thisObject = XposedHelpers.getSurroundingThis(param.getThisObject())
                            if (brightnessSeekBar != thisObject) return
                            startShowPct(lpparam, mContext)
                        }
                    })
                }
            }
        })

        ModuleHelper.hookAllMethods("com.android.systemui.controlcenter.policy.MiuiBrightnessController", lpparam.classLoader, "onStop", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                removePct(mPct)
            }
        })

        val BrightnessUtils = XposedHelpers.findClassIfExists("com.android.systemui.controlcenter.policy.BrightnessUtils", lpparam.classLoader)
        ModuleHelper.hookAllMethods("com.android.systemui.controlcenter.policy.MiuiBrightnessController", lpparam.classLoader, "onChanged", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val pct = mPct ?: return
                val pctTag = pct.getTag() as? Int ?: 0
                if (pctTag == 0) return
                val currentLevel = param.getArgs()[3] as Int
                if (BrightnessUtils != null) {
                    val maxLevel = XposedHelpers.getStaticObjectField(BrightnessUtils, "GAMMA_SPACE_MAX") as Int
                    pct.text = ((currentLevel * 100) / maxLevel).toString() + "%"
                }
            }
        })
    }

    @JvmStatic
    fun ShowVolumePctHook(pluginLoader: ClassLoader) {
        val MiuiVolumeDialogImpl = XposedHelpers.findClassIfExists("com.android.systemui.miui.volume.MiuiVolumeDialogImpl", pluginLoader)
        ModuleHelper.findAndHookMethod(MiuiVolumeDialogImpl, "showVolumeDialogH", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mDialogView = XposedHelpers.getObjectField(param.getThisObject(), "mDialogView") as View
                val windowView = mDialogView.parent as FrameLayout
                initPct(windowView, 3, windowView.context)
            }
        })

        ModuleHelper.findAndHookMethod(MiuiVolumeDialogImpl, "dismissH", Int::class.javaPrimitiveType!!, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                removePct(mPct)
            }
        })

        ModuleHelper.hookAllMethods("com.android.systemui.miui.volume.MiuiVolumeDialogImpl\$VolumeSeekBarChangeListener", pluginLoader, "onProgressChanged", object : MethodHook() {
            private var nowLevel = -233
            override fun after(param: AfterHookCallback) {
                if (nowLevel == param.getArgs()[1] as Int) return
                val pct = mPct ?: return
                val pctTag = pct.getTag() as? Int ?: 0
                if (pctTag != 3) return
                val mColumn = XposedHelpers.getObjectField(param.getThisObject(), "mColumn")
                val ss = XposedHelpers.getObjectField(mColumn, "ss")
                if (ss == null) return
                if (XposedHelpers.getIntField(mColumn, "stream") == 10) return

                val fromUser = param.getArgs()[2] as Boolean
                val currentLevel: Int = if (fromUser) {
                    param.getArgs()[1] as Int
                } else {
                    val anim = XposedHelpers.getObjectField(mColumn, "anim") as ObjectAnimator?
                    if (anim == null || !anim.isRunning) return
                    XposedHelpers.getIntField(mColumn, "animTargetProgress")
                }
                nowLevel = currentLevel
                pct.visibility = View.VISIBLE
                val levelMin = XposedHelpers.getIntField(ss, "levelMin")
                var adjustedLevel = currentLevel
                if (levelMin > 0 && adjustedLevel < levelMin * 1000) {
                    adjustedLevel = levelMin * 1000
                }
                val seekBar = param.getArgs()[0] as SeekBar
                val max = seekBar.max
                val maxLevel = max / 1000
                if (adjustedLevel != 0) {
                    val i3 = maxLevel - 1
                    adjustedLevel = if (adjustedLevel == max) maxLevel else (adjustedLevel * i3 / max) + 1
                }
                pct.text = ((adjustedLevel * 100) / maxLevel).toString() + "%"
            }
        })
    }

    @JvmStatic
    fun HideSafeVolumeDlgHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.volume.VolumeUI", lpparam.classLoader, "start", object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val volumeDialogComponent = XposedHelpers.getObjectField(param.getThisObject(), "mVolumeComponent")
                val volumeDialogControllerImpl = XposedHelpers.getObjectField(volumeDialogComponent, "mController")
                XposedHelpers.setObjectField(volumeDialogControllerImpl, "mShowSafetyWarning", false)
                val audioManager = XposedHelpers.getObjectField(volumeDialogControllerImpl, "mAudio")
                XposedHelpers.callMethod(audioManager, "disableSafeMediaVolume")
            }
        })
    }

    @JvmStatic
    fun LongClickTileOpenInFreeFormHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader, "handleLongClick", View::class.java, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val longClickIntent = XposedHelpers.callMethod(param.getThisObject(), "getLongClickIntent") as Intent?
                if (longClickIntent != null) {
                    val action = longClickIntent.action
                    val isSettings = action?.startsWith("android.settings") == true
                    if (!isSettings && longClickIntent.component != null) {
                        val foregroundInfo = ProcessManager.getForegroundInfo()
                        if (foregroundInfo != null) {
                            val topPackage = foregroundInfo.mForegroundPackageName
                            if ("com.miui.home" == topPackage) {
                                return
                            }
                        }
                        val bIntent = Intent(GlobalActions.ACTION_PREFIX + "SetFreeFormPackage")
                        bIntent.putExtra("package", longClickIntent.component!!.packageName)
                        bIntent.setPackage("android")
                        val mContext = XposedHelpers.getObjectField(param.getThisObject(), "mContext") as Context
                        mContext.sendBroadcast(bIntent)
                    }
                }
            }
        })
    }

    @JvmStatic
    fun CollapseCCAfterClickHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.qs.tileimpl.QSTileImpl", lpparam.classLoader, "click", View::class.java, object : MethodHook() {
            override fun after(param: AfterHookCallback) {
                val mState = XposedHelpers.callMethod(param.getThisObject(), "getState")
                val state = XposedHelpers.getIntField(mState, "state")
                if (state != 0) {
                    val tileSpec = XposedHelpers.callMethod(param.getThisObject(), "getTileSpec") as String
                    if (tileSpec != "edit") {
                        val mHost = XposedHelpers.getObjectField(param.getThisObject(), "mHost")
                        XposedHelpers.callMethod(mHost, "collapsePanels")
                    }
                }
            }
        })
    }

    @JvmStatic
    fun SwitchCCAndNotificationHook(lpparam: PackageReadyParam) {
        ModuleHelper.findAndHookMethod("com.android.systemui.statusbar.phone.MiuiPhoneStatusBarView", lpparam.classLoader, "handleEvent", MotionEvent::class.java, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val mPanelController = XposedHelpers.getObjectField(param.getThisObject(), "mPanelController")
                val useCC = XposedHelpers.callMethod(mPanelController, "isExpandable") as Boolean
                if (useCC) {
                    val bar = param.getThisObject() as FrameLayout
                    val mControlPanelWindowManager = XposedHelpers.getObjectField(param.getThisObject(), "mControlPanelWindowManager")
                    val dispatchToControlPanel = XposedHelpers.callMethod(mControlPanelWindowManager, "dispatchToControlPanel", param.getArg(0), bar.width) as Boolean
                    XposedHelpers.callMethod(mControlPanelWindowManager, "setTransToControlPanel", dispatchToControlPanel)
                    param.returnAndSkip(dispatchToControlPanel)
                    return
                }
                param.returnAndSkip(false)
            }
        })
        ModuleHelper.findAndHookMethod("com.android.systemui.controlcenter.phone.ControlPanelWindowManager", lpparam.classLoader, "dispatchToControlPanel", MotionEvent::class.java, Float::class.javaPrimitiveType!!, object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val added = XposedHelpers.getBooleanField(param.getThisObject(), "added")
                if (added) {
                    val controlCenterController = XposedHelpers.getObjectField(param.getThisObject(), "controlCenterController")
                    val useCC = XposedHelpers.getBooleanField(controlCenterController, "useControlCenter")
                    if (useCC) {
                        val motionEvent = param.getArg(0) as MotionEvent
                        if (motionEvent.actionMasked == MotionEvent.ACTION_DOWN) {
                            XposedHelpers.setObjectField(param.getThisObject(), "mDownX", motionEvent.rawX)
                        }
                        val controlCenterWindowView = XposedHelpers.getObjectField(param.getThisObject(), "windowView")
                        if (controlCenterWindowView == null) {
                            param.returnAndSkip(false)
                        } else {
                            val mDownX = XposedHelpers.getFloatField(param.getThisObject(), "downX")
                            val width = param.getArg(1) as Float
                            if (mDownX < width / 2.0f) {
                                param.returnAndSkip(XposedHelpers.callMethod(controlCenterWindowView, "handleMotionEvent", motionEvent, true))
                            } else {
                                param.returnAndSkip(false)
                            }
                        }
                        return
                    }
                }
                param.returnAndSkip(false)
            }
        })
    }

}
