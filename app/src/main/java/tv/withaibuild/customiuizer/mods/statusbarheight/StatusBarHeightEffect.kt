package tv.withaibuild.customiuizer.mods.statusbarheight

import android.graphics.Rect
import android.util.DisplayMetrics
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Hot-path effect for the status bar height feature.
 *
 * The effect holds only frozen `Class` / `Field` / `Method` references resolved by
 * [StatusBarHeightResolver].  It never performs runtime member discovery, generic reflection,
 * or string-based lookups.  Android owners (`WindowState`, `DisplayContent`, etc.) are never
 * retained as strong references; they only pass through method/field invocations.
 */
internal class StatusBarHeightEffect(
    private val abi: StatusBarHeightAbi,
) {

    private companion object {
        const val TYPE_UNRESOLVED = -1
        const val WINDOW_STATE_CLASS_NAME = "com.android.server.wm.WindowState"
    }

    private val windowManager get() = abi.windowManager
    private val decorInsets get() = abi.decorInsets

    /** Allocation-free `WindowState` test; falls back to the class name before the ABI is resolved. */
    fun isWindowState(win: Any): Boolean {
        val resolved = windowManager.windowStateClass
        return if (resolved != null) resolved.isInstance(win) else win.javaClass.name == WINDOW_STATE_CLASS_NAME
    }

    /** Reads `WindowState.mAttrs` through the frozen field. */
    fun readWindowAttrs(win: Any): Any? {
        val field = windowManager.windowStateAttrsField ?: return null
        if (!field.declaringClass.isInstance(win)) return null
        return readField(field, win)
    }

    /** Reads `WindowManager.LayoutParams.type` through the frozen field. */
    fun readAttrsType(attrs: Any): Int {
        val field = windowManager.layoutParamsTypeField ?: return TYPE_UNRESOLVED
        if (!field.declaringClass.isInstance(attrs)) return TYPE_UNRESOLVED
        return readInt(field, attrs)
    }

    /** Reads `WindowManager.LayoutParams.packageName` through the frozen field. */
    fun readPackageName(attrs: Any): String? {
        val field = windowManager.layoutParamsPackageNameField ?: return null
        if (!field.declaringClass.isInstance(attrs)) return null
        return readField(field, attrs) as? String
    }

    /**
     * Reads the display metrics for a `WindowState`.
     *
     * Preferred path: the frozen `WindowState.getDisplayMetrics()` method.
     * Fallback path: the frozen `WindowState.mDisplayContent` field +
     * `DisplayContent.getDisplayMetrics()` method from the DecorInsets ABI.
     *
     * If the preferred method is available but throws, the exception is treated as fatal/non-fatal
     * and the fallback is **not** attempted.  The fallback only runs when the preferred method is
     * unavailable or returns `null`.
     */
    fun readWindowDisplayMetrics(win: Any): DisplayMetrics? {
        val directMethod = windowManager.windowStateGetDisplayMetricsMethod
        if (directMethod != null && directMethod.declaringClass.isInstance(win)) {
            val result = try {
                directMethod.invoke(win)
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                return null
            }
            if (result is DisplayMetrics) return result
            // result is null or unexpected type: fall through to the DisplayContent path.
        }

        val displayContent = readDisplayContent(win) ?: return null
        val fallbackMethod = decorInsets.displayContentGetDisplayMetricsMethod ?: return null
        if (!fallbackMethod.declaringClass.isInstance(displayContent)) return null

        val result = try {
            fallbackMethod.invoke(displayContent)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            return null
        }
        return result as? DisplayMetrics
    }

    /** Reads the display id for a `WindowState` through the frozen `getDisplayId()` method. */
    fun readDisplayId(win: Any): Int {
        val method = windowManager.windowStateGetDisplayIdMethod ?: return -1
        if (!method.declaringClass.isInstance(win)) return -1
        val result = try {
            method.invoke(win)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            return -1
        }
        return result as? Int ?: -1
    }

    /** Allocation-free `ClientWindowFrames` test. */
    fun isClientWindowFrames(value: Any): Boolean {
        val clazz = windowManager.clientWindowFramesClass ?: return false
        return clazz.isInstance(value)
    }

    /** Reads `ClientWindowFrames.frame` through the frozen field. */
    fun readClientWindowFrame(clientFrames: Any): Rect? {
        val field = windowManager.clientWindowFramesFrameField ?: return null
        if (!field.declaringClass.isInstance(clientFrames)) return null
        return try {
            field.get(clientFrames) as? Rect
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    /**
     * Reads the `WindowState` frame for first-hit diagnostics.
     *
     * Preferred path: the frozen `WindowState.getFrame()` method.
     * Fallback path: the frozen `WindowState.mWindowFrames` field +
     * `WindowFrames.mFrame` field.
     */
    fun readWindowFrame(win: Any): Rect? {
        val directMethod = windowManager.windowStateGetFrameMethod
        if (directMethod != null && directMethod.declaringClass.isInstance(win)) {
            val result = try {
                directMethod.invoke(win)
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                return null
            }
            if (result is Rect) return result
        }

        val frames = readWindowFrames(win) ?: return null
        val frameField = windowManager.windowFramesFrameField ?: return null
        if (!frameField.declaringClass.isInstance(frames)) return null
        return readField(frameField, frames) as? Rect
    }

    /** Writes the configured pixel height into `WindowManager.LayoutParams.height`. */
    fun applyStatusBarHeight(win: Any, configuredPx: Int): Boolean {
        val attrs = readWindowAttrs(win) ?: return false
        return setHeight(attrs, configuredPx)
    }

    /** Reads the current `WindowManager.LayoutParams.height`. */
    fun readStatusBarHeight(attrs: Any): Int? {
        val field = windowManager.layoutParamsHeightField ?: return null
        if (!field.declaringClass.isInstance(attrs)) return null
        return readInt(field, attrs)
    }

    /** Writes `WindowManager.LayoutParams.height` and returns `true` on success. */
    fun setHeight(attrs: Any, configuredPx: Int): Boolean {
        val field = windowManager.layoutParamsHeightField ?: return false
        if (!field.declaringClass.isInstance(attrs)) return false
        return try {
            field.setInt(attrs, configuredPx)
            true
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            false
        }
    }

    private fun readDisplayContent(win: Any): Any? {
        val field = windowManager.windowStateDisplayContentField ?: return null
        if (!field.declaringClass.isInstance(win)) return null
        return readField(field, win)
    }

    private fun readWindowFrames(win: Any): Any? {
        val field = windowManager.windowStateWindowFramesField ?: return null
        if (!field.declaringClass.isInstance(win)) return null
        return readField(field, win)
    }

    private fun readField(field: Field, target: Any): Any? {
        return try {
            field.get(target)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    private fun readInt(field: Field, target: Any): Int {
        return try {
            field.getInt(target)
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            TYPE_UNRESOLVED
        }
    }
}
