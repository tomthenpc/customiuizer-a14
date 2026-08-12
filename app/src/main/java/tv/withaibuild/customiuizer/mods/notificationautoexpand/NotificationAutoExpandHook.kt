package tv.withaibuild.customiuizer.mods.notificationautoexpand

import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers

/**
 * Thin hook installer for the Notification Auto-Expand feature.
 *
 * Resolves the frozen ABI, installs the process-scoped runtime state, and wires the
 * [MethodHook] to the [NotificationAutoExpandEffect]. The original hook surface,
 * `ModuleHelper.hookAllMethods(..., "setFeedbackIcon", ...)`, is preserved.
 */
internal object NotificationAutoExpandHook {

    private const val TARGET_CLASS = "com.android.systemui.statusbar.notification.row.ExpandableNotificationRow"
    private const val HOOK_METHOD = "setFeedbackIcon"

    @JvmStatic
    fun install(classLoader: ClassLoader?) {
        val resolutionRootClass = XposedHelpers.findClassIfExists(TARGET_CLASS, classLoader)
        if (resolutionRootClass == null) {
            // Preserve the legacy ModuleHelper diagnostics for a missing hook target class:
            // log and record TARGET_CLASS_MISSING, then return without installing a callback,
            // without building runtime state, and without entering FAST or COMPLETE_LEGACY dispatch.
            ModuleHelper.hookAllMethods(TARGET_CLASS, classLoader, HOOK_METHOD, object : MethodHook() {})
            return
        }

        val abi = NotificationAutoExpandResolver.resolve(resolutionRootClass)
        val runtimeState = NotificationAutoExpandRuntimeState.install()
        val effect = NotificationAutoExpandEffect(abi, runtimeState.snapshotRef)

        val hook = object : MethodHook() {
            override fun intercept(chain: io.github.libxposed.api.XposedInterface.Chain): Any? {
                var result: Any? = null
                var throwable: Throwable? = null
                try {
                    result = effect.intercept(chain)
                } catch (t: Throwable) {
                    throwable = t
                    result = null
                }
                return XposedHelpers.throwOrReturn(throwable, result)
            }
        }

        ModuleHelper.hookAllMethods(resolutionRootClass, HOOK_METHOD, hook)
    }
}
