package tv.withaibuild.customiuizer.mods

import android.os.Message
import io.github.libxposed.api.XposedModuleInterface
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Field
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicInteger

/**
 * Non-destructive default USB function override for HyperOS 1 / Android 14.
 *
 * The ROM owns the persisted default ([UsbHandler.mScreenUnlockedFunctions]), ADB composition,
 * manual current-function sessions, policy paths and USB listeners.  This hook only rewrites the
 * effective function argument while the ROM itself is applying a default-function path.
 *
 * Mode mapping (read from the ListPreference as a String and parsed by PrefMap.getStringAsInt):
 *   0 -> follow system default
 *   1 -> charge only   (UsbManager.FUNCTION_NONE)
 *   2 -> file transfer  (UsbManager.FUNCTION_MTP)
 *   3 -> photo transfer (UsbManager.FUNCTION_PTP)
 *
 * The feature is installed even when the startup mode is "follow system" so a later preference
 * change can take effect on the next default-application event without reinstalling hooks.
 */
object SystemUsbDefaultHooks {

    internal const val PREF_KEY = "system_usb_default_function"

    private const val USB_DEVICE_MANAGER_CLASS = "com.android.server.usb.UsbDeviceManager"
    private const val USB_HANDLER_CLASS = "com.android.server.usb.UsbDeviceManager\$UsbHandler"
    private const val USB_HANDLER_HAL_CLASS = "com.android.server.usb.UsbDeviceManager\$UsbHandlerHal"
    private const val USB_HANDLER_LEGACY_CLASS = "com.android.server.usb.UsbDeviceManager\$UsbHandlerLegacy"

    // Preference modes (must match the ListPreference entryValues in prefs_system.xml).
    internal const val MODE_FOLLOW_SYSTEM = 0
    internal const val MODE_CHARGING = 1
    internal const val MODE_MTP = 2
    internal const val MODE_PTP = 3

    // USB function bitmasks.  Use literal AOSP values because the public SDK stubs hide these fields.
    internal const val FUNCTION_NONE = 0L
    internal const val FUNCTION_MTP = 4L // 1 << 2
    internal const val FUNCTION_PTP = 16L // 1 << 4

    private class ResolvedTargets(
        val handlerClass: Class<*>,
        val handlerHalClass: Class<*>,
        val handlerLegacyClass: Class<*>?,
        val msgUpdateState: Int,
        val msgSetScreenUnlockedFunctions: Int,
        val msgUpdateScreenLock: Int,
        val msgBootCompleted: Int,
        val msgSystemReady: Int,
        val fieldScreenUnlockedFunctions: Field,
        val fieldScreenLocked: Field,
        val fieldCurrentFunctions: Field,
        val fieldBootCompleted: Field,
        val operationCounter: AtomicInteger,
    )

    internal data class ContextFrame(val reason: ContextReason, val messageWhat: Int = -1)

    internal enum class ContextReason {
        HANDLE_MESSAGE,
        SET_SCREEN_UNLOCKED_FUNCTIONS,
        FINISH_BOOT,
    }

    internal object UsbDefaultContext {
        private val threadLocal = object : ThreadLocal<ArrayDeque<ContextFrame>>() {
            override fun initialValue() = ArrayDeque<ContextFrame>()
        }

        fun push(reason: ContextReason, messageWhat: Int = -1) {
            threadLocal.get().push(ContextFrame(reason, messageWhat))
        }

        fun pop() {
            threadLocal.get().pollFirst()
        }

        fun peek(): ContextFrame? = threadLocal.get().peekFirst()

        fun clear() {
            threadLocal.get().clear()
        }
    }

    /**
     * Installs the USB default-function hooks.  Called once at system_server startup.
     *
     * Any failure is fail-open: USB continues to behave natively and no host process is taken down.
     * Fatal errors ([OutOfMemoryError], [ThreadDeath], [VirtualMachineError]) are propagated.
     */
    @JvmStatic
    fun hook(lpparam: XposedModuleInterface.SystemServerStartingParam): FeatureInstallResult {
        return try {
            val targets = resolveTargets(lpparam.classLoader)
            if (targets == null) {
                return FeatureInstallResult.FAILED_TRANSIENT
            }
            if (!installContextHooks(targets)) {
                return FeatureInstallResult.FAILED_TRANSIENT
            }
            if (!installSetEnabledFunctionsHooks(targets)) {
                return FeatureInstallResult.FAILED_TRANSIENT
            }
            FeatureInstallResult.INSTALLED
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            XposedHelpers.log(t)
            FeatureInstallResult.FAILED_TRANSIENT
        }
    }

    /**
     * Resolves ROM classes, message ids and fields at cold install time.  No DexKit, no disk I/O.
     */
    private fun resolveTargets(classLoader: ClassLoader): ResolvedTargets? {
        val usbDeviceManagerClass = XposedHelpers.findClassIfExists(USB_DEVICE_MANAGER_CLASS, classLoader) ?: return null
        val handlerClass = XposedHelpers.findClassIfExists(USB_HANDLER_CLASS, classLoader) ?: return null
        val handlerHalClass = XposedHelpers.findClassIfExists(USB_HANDLER_HAL_CLASS, classLoader) ?: return null
        val handlerLegacyClass = XposedHelpers.findClassIfExists(USB_HANDLER_LEGACY_CLASS, classLoader)

        val msgUpdateState = getStaticIntQuiet(usbDeviceManagerClass, "MSG_UPDATE_STATE") ?: return null
        val msgSetScreenUnlockedFunctions = getStaticIntQuiet(usbDeviceManagerClass, "MSG_SET_SCREEN_UNLOCKED_FUNCTIONS") ?: return null
        val msgUpdateScreenLock = getStaticIntQuiet(usbDeviceManagerClass, "MSG_UPDATE_SCREEN_LOCK") ?: return null
        val msgBootCompleted = getStaticIntQuiet(usbDeviceManagerClass, "MSG_BOOT_COMPLETED") ?: return null
        val msgSystemReady = getStaticIntQuiet(usbDeviceManagerClass, "MSG_SYSTEM_READY") ?: return null

        val fieldScreenUnlockedFunctions = findFieldQuiet(handlerClass, "mScreenUnlockedFunctions") ?: return null
        val fieldScreenLocked = findFieldQuiet(handlerClass, "mScreenLocked") ?: return null
        val fieldCurrentFunctions = findFieldQuiet(handlerClass, "mCurrentFunctions") ?: return null
        val fieldBootCompleted = findFieldQuiet(handlerClass, "mBootCompleted") ?: return null

        val operationCounter = try {
            XposedHelpers.getStaticObjectField(usbDeviceManagerClass, "sUsbOperationCount") as? AtomicInteger
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            null
        } ?: return null

        return ResolvedTargets(
            handlerClass = handlerClass,
            handlerHalClass = handlerHalClass,
            handlerLegacyClass = handlerLegacyClass,
            msgUpdateState = msgUpdateState,
            msgSetScreenUnlockedFunctions = msgSetScreenUnlockedFunctions,
            msgUpdateScreenLock = msgUpdateScreenLock,
            msgBootCompleted = msgBootCompleted,
            msgSystemReady = msgSystemReady,
            fieldScreenUnlockedFunctions = fieldScreenUnlockedFunctions,
            fieldScreenLocked = fieldScreenLocked,
            fieldCurrentFunctions = fieldCurrentFunctions,
            fieldBootCompleted = fieldBootCompleted,
            operationCounter = operationCounter,
        )
    }

    private fun installContextHooks(targets: ResolvedTargets): Boolean {
        if (!ModuleHelper.findAndHookMethodSilently(
                targets.handlerClass,
                "setScreenUnlockedFunctions",
                Int::class.javaPrimitiveType,
                SetScreenUnlockedFunctionsHook(),
            )
        ) {
            return false
        }
        if (!ModuleHelper.findAndHookMethodSilently(
                targets.handlerClass,
                "finishBoot",
                Int::class.javaPrimitiveType,
                FinishBootHook(),
            )
        ) {
            return false
        }
        if (!ModuleHelper.findAndHookMethodSilently(
                targets.handlerClass,
                "handleMessage",
                Message::class.java,
                HandleMessageHook(targets),
            )
        ) {
            return false
        }
        return true
    }

    private fun installSetEnabledFunctionsHooks(targets: ResolvedTargets): Boolean {
        val hook = SetEnabledFunctionsHook(targets)
        if (!ModuleHelper.hookAllMethodsSilently(targets.handlerHalClass, "setEnabledFunctions", hook)) {
            return false
        }
        targets.handlerLegacyClass?.let {
            if (!ModuleHelper.hookAllMethodsSilently(it, "setEnabledFunctions", hook)) {
                return false
            }
        }
        return true
    }

    /**
     * Fast-path hook installed on [UsbHandlerHal]/[UsbHandlerLegacy].setEnabledFunctions(JZI).
     *
     * Only rewrites the argument when the call originates from a recognized default-application
     * context.  Manual current-function, policy, user-switch, accessory, tethering and MIDI paths
     * are never in that context, so they are left untouched.
     */
    private class SetEnabledFunctionsHook(private val targets: ResolvedTargets) : HookerClassHelper.MethodHook() {

        override fun before(callback: HookerClassHelper.BeforeHookCallback) {
            if (UsbDefaultContext.peek() == null) return

            val mode = getMode()
            if (mode == MODE_FOLLOW_SYSTEM) return

            val args = callback.getArgs()
            if (args.size < 4) return
            val functions = args[0] as? Long ?: return
            val forceRestart = args[2] as? Boolean ?: return

            val handler = callback.getThisObject() ?: return
            val screenLocked = readBoolean(handler, targets.fieldScreenLocked)
            val nativeDefault = readLong(handler, targets.fieldScreenUnlockedFunctions)
            val transferAllowed = if (mode == MODE_MTP || mode == MODE_PTP) isUsbTransferAllowed(handler) else true

            val effective = computeEffectiveUsbFunctions(
                mode = mode,
                nativeDefault = nativeDefault,
                currentFunctions = functions,
                screenLocked = screenLocked,
                forceRestart = forceRestart,
                transferAllowed = transferAllowed,
            )
            if (effective != null) args[0] = effective
        }
    }

    private class SetScreenUnlockedFunctionsHook : HookerClassHelper.MethodHook() {
        override fun before(callback: HookerClassHelper.BeforeHookCallback) {
            UsbDefaultContext.push(ContextReason.SET_SCREEN_UNLOCKED_FUNCTIONS)
        }

        override fun after(callback: HookerClassHelper.AfterHookCallback) {
            UsbDefaultContext.pop()
        }
    }

    private class FinishBootHook : HookerClassHelper.MethodHook() {
        override fun before(callback: HookerClassHelper.BeforeHookCallback) {
            UsbDefaultContext.push(ContextReason.FINISH_BOOT)
        }

        override fun after(callback: HookerClassHelper.AfterHookCallback) {
            UsbDefaultContext.pop()
        }
    }

    private class HandleMessageHook(private val targets: ResolvedTargets) : HookerClassHelper.MethodHook() {
        override fun before(callback: HookerClassHelper.BeforeHookCallback) {
            val msg = callback.getArg(0) as? Message ?: return
            if (isDefaultMessage(targets, msg.what)) {
                UsbDefaultContext.push(ContextReason.HANDLE_MESSAGE, msg.what)
            }
        }

        override fun after(callback: HookerClassHelper.AfterHookCallback) {
            val msg = callback.getArgs().getOrNull(0) as? Message ?: return
            if (!isDefaultMessage(targets, msg.what)) return

            val handler = callback.getThisObject() ?: return
            try {
                maybeSupplementScreenUnlock(handler, targets, msg)
            } finally {
                UsbDefaultContext.pop()
            }
        }
    }

    /**
     * Handles the special case where the ROM applies a screen-unlock/default branch for a native
     * default of [FUNCTION_NONE] and does not call [setEnabledFunctions] on its own.  We only
     * inject the override when the user has chosen a data mode, the screen is unlocked, and the
     * ROM's transfer policy allows it.
     */
    private fun maybeSupplementScreenUnlock(handler: Any, targets: ResolvedTargets, msg: Message) {
        if (msg.what != targets.msgUpdateScreenLock) return
        if (msg.arg1 != 0) return // 0 == screen unlocked
        if (!readBoolean(handler, targets.fieldBootCompleted)) return

        val mode = getMode()
        if (mode != MODE_MTP && mode != MODE_PTP) return

        val nativeDefault = readLong(handler, targets.fieldScreenUnlockedFunctions)
        if (nativeDefault != FUNCTION_NONE) return

        val currentFunctions = readLong(handler, targets.fieldCurrentFunctions)
        if (currentFunctions != FUNCTION_NONE) return

        if (readBoolean(handler, targets.fieldScreenLocked)) return
        if (!isUsbTransferAllowed(handler)) return

        val operationId = targets.operationCounter.incrementAndGet()
        val effective = when (mode) {
            MODE_MTP -> FUNCTION_MTP
            MODE_PTP -> FUNCTION_PTP
            else -> FUNCTION_NONE
        }
        try {
            XposedHelpers.callMethod(handler, "setEnabledFunctions", effective, false, operationId)
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            XposedHelpers.log(t)
        }
    }

    private fun isDefaultMessage(targets: ResolvedTargets, what: Int): Boolean {
        return what == targets.msgUpdateState ||
            what == targets.msgSetScreenUnlockedFunctions ||
            what == targets.msgUpdateScreenLock ||
            what == targets.msgBootCompleted ||
            what == targets.msgSystemReady
    }

    /**
     * Pure preference-to-function mapping.  Used by the hook and by tests.
     */
    internal fun resolveEffective(mode: Int, nativeDefault: Long): Long {
        return when (mode) {
            MODE_FOLLOW_SYSTEM -> nativeDefault
            MODE_CHARGING -> FUNCTION_NONE
            MODE_MTP -> FUNCTION_MTP
            MODE_PTP -> FUNCTION_PTP
            else -> nativeDefault
        }
    }

    /**
     * Applies the policy/transfer guards and returns the function value to write, or `null` when
     * the original argument must be preserved.
     */
    internal fun computeEffectiveUsbFunctions(
        mode: Int,
        nativeDefault: Long,
        currentFunctions: Long,
        screenLocked: Boolean,
        forceRestart: Boolean,
        transferAllowed: Boolean,
    ): Long? {
        if (mode == MODE_FOLLOW_SYSTEM) return null
        if (screenLocked || forceRestart) return null
        val effective = resolveEffective(mode, nativeDefault)
        if (effective == currentFunctions) return null
        if ((mode == MODE_MTP || mode == MODE_PTP) && !transferAllowed) {
            return if (currentFunctions == FUNCTION_NONE) null else FUNCTION_NONE
        }
        return effective
    }

    private fun getMode(): Int = MainModule.mPrefs.getStringAsInt(PREF_KEY, MODE_FOLLOW_SYSTEM)

    private fun isUsbTransferAllowed(handler: Any): Boolean {
        return try {
            XposedHelpers.callMethod(handler, "isUsbTransferAllowed") as? Boolean ?: false
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            false
        }
    }

    // --- Reflection helpers with fatal propagation ---

    private fun readLong(obj: Any, field: Field): Long {
        return field.getLong(obj)
    }

    private fun readBoolean(obj: Any, field: Field): Boolean {
        return field.getBoolean(obj)
    }

    private fun getStaticIntQuiet(clazz: Class<*>, name: String): Int? {
        return try {
            XposedHelpers.getStaticIntField(clazz, name)
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            null
        }
    }

    private fun findFieldQuiet(clazz: Class<*>, name: String): Field? {
        return try {
            clazz.getDeclaredField(name).also { it.isAccessible = true }
        } catch (t: Throwable) {
            FatalErrors.rethrowIfFatal(t)
            null
        }
    }
}
