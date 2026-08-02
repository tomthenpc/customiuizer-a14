package tv.withaibuild.customiuizer.mods.utils

import android.view.MotionEvent
import android.view.View
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.SystemUIControlCenterHooks
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.gesture.ControlCenterGestureDependenciesResolver
import tv.withaibuild.customiuizer.mods.utils.gesture.ControlCenterGestureRuntimeHolder
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureConfigPublisher
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureConfigResolver
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureEntry
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureEvent
import tv.withaibuild.customiuizer.mods.utils.gesture.GestureMachine
import tv.withaibuild.customiuizer.mods.utils.gesture.PhysicalGestureArbiter
import tv.withaibuild.customiuizer.mods.utils.gesture.StatusBarGestureEffectExecutor

/**
 * Single owner for the SystemUI control-center plugin lifecycle.
 *
 * Only one `PluginFactory.createPlugin` hook is installed, regardless of whether the
 * control-center UI modifications, the status-bar gestures, or both are enabled.
 */
internal object ControlCenterPluginRuntime {

    private val arbiter = PhysicalGestureArbiter()

    val configPublisher = GestureConfigPublisher(resolve = { GestureConfigResolver.resolve(MainModule.mPrefs) })

    private val runtimeHolder = ControlCenterGestureRuntimeHolder(
        configPublisher = configPublisher,
        effectExecutor = StatusBarGestureEffectExecutor(),
        arbiter = arbiter,
        dependenciesResolver = ControlCenterGestureDependenciesResolver(),
        installHooks = { classLoader, machine -> installHooks(classLoader, machine) },
    )

    private var activeLoader: ClassLoader? = null

    /** Expose the shared arbiter so status-bar and control-center gestures share one authority. */
    fun arbiter(): PhysicalGestureArbiter = arbiter

    /** Expose the runtime holder for diagnostics and tests. */
    fun runtimeHolder(): ControlCenterGestureRuntimeHolder = runtimeHolder

    /** Active plugin ClassLoader, or null if no plugin is currently bound. */
    fun activeLoader(): ClassLoader? = activeLoader

    /**
     * Bind a freshly detected [loader].
     *
     * - Same loader is idempotent.
     * - A new loader clears the previous gesture runtime.
     * - Gesture hooks are installed before the control-center UI hooks.
     * - The active loader is only published after both installations succeed, so a fatal
     *   failure during install does not leave a half-state.
     */
    private val defaultInstallPluginHooks: (ClassLoader) -> Unit = { SystemUIControlCenterHooks.initControlCenter(it) }
    private val defaultInstallHooks: (ClassLoader, GestureMachine) -> Unit = { classLoader, machine -> installControlCenterGestureHooks(classLoader, machine) }
    private val defaultInstallCreatePluginHook: (ClassLoader, MethodHook) -> Unit = { classLoader, hook ->
        ModuleHelper.hookAllMethods(
            "com.android.systemui.shared.plugins.PluginInstance\$PluginFactory",
            classLoader,
            "createPlugin",
            hook
        )
    }

    var installPluginHooks: (ClassLoader) -> Unit = defaultInstallPluginHooks
    var installHooks: (ClassLoader, GestureMachine) -> Unit = defaultInstallHooks
    var installCreatePluginHook: (ClassLoader, MethodHook) -> Unit = defaultInstallCreatePluginHook

    /** Reset all mutable seams and runtime state. For tests only. */
    fun resetForTests() {
        clear()
        hooked = false
        installPluginHooks = defaultInstallPluginHooks
        installHooks = defaultInstallHooks
        installCreatePluginHook = defaultInstallCreatePluginHook
    }

    fun bind(loader: ClassLoader) {
        if (activeLoader === loader) return
        clear()

        try {
            runtimeHolder.bind(loader)
            installPluginHooks(loader)
            activeLoader = loader
        } catch (e: Throwable) {
            clear()
            FatalErrors.rethrowIfFatal(e)
        }
    }

    /**
     * Explicitly detach the current plugin.
     *
     * Clears the gesture runtime, releases all physical-gesture tokens and drops the active
     * loader references so the old ClassLoader can be collected.
     */
    fun clear() {
        runtimeHolder.unbind()
        activeLoader = null
        arbiter.releaseAll()
    }

    private var hooked = false

    /**
     * Install a single `PluginFactory.createPlugin` hook on [lpparam.classLoader] if it has
     * not already been installed. The hook extracts the miui.systemui.plugin ClassLoader and
     * binds it through [bind].
     */
    fun hookIfNeeded(lpparam: PackageReadyParam) = hookIfNeeded(lpparam.classLoader)

    fun hookIfNeeded(classLoader: ClassLoader) {
        if (hooked) return
        hooked = true
        installCreatePluginHook(
            classLoader,
            object : MethodHook() {
                override fun before(param: BeforeHookCallback) {
                    val loader = SystemUIControlCenterHooks.extractPluginLoader(param.getThisObject()) ?: return
                    bind(loader)
                }
            }
        )
    }

    private fun installControlCenterGestureHooks(classLoader: ClassLoader, controlCenterMachine: GestureMachine) {
        val controlCenterHook = object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val thisObject = param.getThisObject() as? View ?: return
                if (param.getArgs().size == 2 && (param.getArg(1) as Boolean)) return
                val statusBarStateController = XposedHelpers.getObjectField(thisObject, "statusBarStateController")
                val state = XposedHelpers.callMethod(statusBarStateController, "getState") as Int
                if (state == 1 || state == 2) return
                val event = param.getArg(0) as? MotionEvent ?: return
                val gestureEvent = GestureEvent(
                    entry = GestureEntry.CONTROL_CENTER_TOUCH,
                    actionMasked = event.actionMasked,
                    downTime = event.downTime,
                    eventTime = event.eventTime,
                    x = event.x,
                    y = event.y,
                    pointerCount = event.pointerCount,
                    ownerId = System.identityHashCode(thisObject),
                    deviceId = event.deviceId,
                    source = event.source,
                )
                ModuleHelper.guarded {
                    controlCenterMachine.dispatch(gestureEvent, thisObject)
                }
            }
        }
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.windowview.ControlCenterWindowViewImpl", classLoader, "handleMotionEvent", MotionEvent::class.java, Boolean::class.javaPrimitiveType!!, controlCenterHook)
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.windowview.ControlCenterWindowViewImpl", classLoader, "onAttachedToWindow", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val thisObject = param.getThisObject() as? View ?: return
                ModuleHelper.guarded {
                    controlCenterMachine.prepare(System.identityHashCode(thisObject), thisObject)
                }
            }
        })
        ModuleHelper.findAndHookMethod("miui.systemui.controlcenter.windowview.ControlCenterWindowViewImpl", classLoader, "onDetachedFromWindow", object : MethodHook() {
            override fun before(param: BeforeHookCallback) {
                val thisObject = param.getThisObject() as? View ?: return
                ModuleHelper.guarded {
                    controlCenterMachine.clear(System.identityHashCode(thisObject))
                }
            }
        })
    }
}
