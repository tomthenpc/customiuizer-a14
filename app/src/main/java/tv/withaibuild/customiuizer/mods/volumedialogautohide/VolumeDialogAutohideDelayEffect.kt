package tv.withaibuild.customiuizer.mods.volumedialogautohide

import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.util.concurrent.atomic.AtomicReference

/**
 * Hot-path effect for the VolumeDialogAutohideDelay hook.
 *
 * The effect holds only an immutable [VolumeDialogAutohideDelayAbi] and a snapshot
 * reference. It never performs runtime member discovery, [ClassLoader] lookups,
 * string-based field lookups, or generic reflection. It does not retain runtime
 * [View], [Context], [Activity], or [MiuiVolumeDialogImpl] instances beyond the
 * hot method invocation.
 *
 * Each callback selects exactly one execution mode at the start and keeps it for
 * the entire invocation: FAST uses the frozen [java.lang.reflect.Field] handles;
 * LEGACY uses the existing XposedHelpers get helpers. Mixed access within a single
 * callback is forbidden.
 */
internal class VolumeDialogAutohideDelayEffect(
    val abi: VolumeDialogAutohideDelayAbi?,
    val snapshotRef: AtomicReference<VolumeDialogAutohideDelaySnapshot?>,
) {

    /**
     * Production entry point from the [MethodHook.before] callback.
     */
    fun before(param: BeforeHookCallback) {
        val thisObject = param.getThisObject()
        process(thisObject, param)
    }

    /**
     * Testable entry point.
     */
    internal fun process(thisObject: Any?, param: BeforeHookCallback) {
        val useFast = isFastEligible(thisObject)
        if (useFast) {
            processFast(thisObject!!, param)
        } else {
            processLegacy(thisObject, param)
        }
    }

    private fun isFastEligible(thisObject: Any?): Boolean {
        val a = abi ?: return false
        val snapshot = snapshotRef.get()
        return thisObject != null &&
            thisObject.javaClass === a.resolutionRootClass &&
            snapshot != null
    }

    /**
     * FAST path: all field access uses frozen [Field] handles. No preference read
     * is performed inside this path.
     */
    private fun processFast(thisObject: Any, param: BeforeHookCallback) {
        val a = abi ?: error("processFast called without ABI")
        val snapshot = snapshotRef.get() ?: error("processFast called without snapshot")

        val mHovering = withLegacyIllegalAccessError { a.mHoveringField.getBoolean(thisObject) }
        if (mHovering) {
            param.returnAndSkip(16000)
            return
        }

        val mSafetyWarning = readSafetyWarning(thisObject)
        if (mSafetyWarning) {
            val opt = snapshot.expanded
            param.returnAndSkip(if (opt > 0) opt else 5000)
            return
        }

        val mExpanded = withLegacyIllegalAccessError { a.mExpandedField.getBoolean(thisObject) }
        val opt = if (mExpanded) snapshot.expanded else snapshot.collapsed
        if (opt > 0) {
            param.returnAndSkip(opt)
        }
    }

    /**
     * LEGACY path: preserves the original dynamic reflection behavior.
     */
    private fun processLegacy(thisObject: Any?, param: BeforeHookCallback) {
        val mHovering = XposedHelpers.getBooleanField(thisObject, "mHovering")
        if (mHovering) {
            param.returnAndSkip(16000)
            return
        }

        val mSafetyWarning = try {
            XposedHelpers.getObjectField(thisObject, "mIsSafetyShowing") as Boolean
        } catch (e: Throwable) {
            FatalErrors.rethrowIfFatal(e)
            XposedHelpers.getObjectField(thisObject, "mSafetyWarning") as Boolean
        }

        if (mSafetyWarning) {
            val opt = MainModule.mPrefs.getInt("system_volumedialogdelay_expanded", 0)
            param.returnAndSkip(if (opt > 0) opt else 5000)
            return
        }

        val mExpanded = XposedHelpers.getBooleanField(thisObject, "mExpanded")
        val opt = MainModule.mPrefs.getInt(
            if (mExpanded) "system_volumedialogdelay_expanded" else "system_volumedialogdelay_collapsed",
            0,
        )
        if (opt > 0) {
            param.returnAndSkip(opt)
        }
    }

    /**
     * Strategy A: keep the exact legacy safety-alias block. Do not cache the
     * fields or resolve them at install time.
     */
    private fun readSafetyWarning(thisObject: Any): Boolean {
        return try {
            XposedHelpers.getObjectField(thisObject, "mIsSafetyShowing") as Boolean
        } catch (e: Throwable) {
            FatalErrors.rethrowIfFatal(e)
            XposedHelpers.getObjectField(thisObject, "mSafetyWarning") as Boolean
        }
    }

    /**
     * Wraps a fast field operation to map [IllegalAccessException] to
     * [IllegalAccessError], matching the legacy XposedHelpers contract.
     */
    private inline fun <T> withLegacyIllegalAccessError(action: () -> T): T {
        return try {
            action()
        } catch (e: IllegalAccessException) {
            XposedHelpers.log(e)
            throw IllegalAccessError(e.message)
        }
    }
}
