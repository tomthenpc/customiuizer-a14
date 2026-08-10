package tv.withaibuild.customiuizer.mods.statusbarheight

import android.graphics.Rect
import android.view.WindowInsets
import tv.withaibuild.customiuizer.mods.utils.FatalErrors
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Cold resolver for the status bar height feature.
 *
 * All reflection and `ClassLoader` discovery happens here during install time.  The output is an
 * immutable [StatusBarHeightAbi] capability object.  Hot paths must not perform any further
 * discovery.
 */
internal object StatusBarHeightResolver {

    private const val INSETS_SOURCE_CLASS = "android.view.InsetsSource"
    private const val INSETS_STATE_CLASS = "android.view.InsetsState"
    private const val WINDOW_STATE_CLASS = "com.android.server.wm.WindowState"
    private const val DISPLAY_POLICY_CLASS = "com.android.server.wm.DisplayPolicy"
    private const val DECOR_INSETS_INFO_CLASS = "com.android.server.wm.DisplayPolicy\$DecorInsets\$Info"

    private const val SET_FRAME_METHOD = "setFrame"
    private const val GET_TYPE_METHOD = "getType"
    private const val GET_FRAME_METHOD = "getFrame"
    private const val GET_ID_METHOD = "getId"
    private const val SET_FRAMES_METHOD = "setFrames"
    private const val LAYOUT_WINDOW_LW_METHOD = "layoutWindowLw"
    private const val DECOR_INSETS_UPDATE_METHOD = "update"

    /** Public `WindowInsets.Type.statusBars()` bit. */
    private const val STATUS_BARS_TYPE = 1

    /** `WindowManager.LayoutParams.TYPE_STATUS_BAR` = 2000. */
    private const val TYPE_STATUS_BAR = 2000

    /**
     * Resolve the cold core ABI.
     *
     * @return an immutable capability description.  Individual capabilities may be unavailable while
     *         others are present.
     */
    fun resolveCore(classLoader: ClassLoader): StatusBarHeightAbi {
        val insets = resolveInsetsSourceCapability(classLoader)
        val windowManager = resolveWindowManagerCapability(classLoader)
        val decorInsets = resolveDecorInsetsCapability(classLoader)
        return StatusBarHeightAbi(insets, windowManager, decorInsets)
    }

    private fun resolveInsetsSourceCapability(classLoader: ClassLoader): InsetsSourceCapability {
        val sourceClass = XposedHelpers.findClassIfExists(INSETS_SOURCE_CLASS, classLoader)
        return resolveInsetsSourceClass(sourceClass, classLoader)
    }

    /**
     * Resolve InsetsSource capability from an already-loaded class.  Used by tests and by
     * [resolveCore].
     */
    fun resolveInsetsSourceClass(sourceClass: Class<*>?, classLoader: ClassLoader): InsetsSourceCapability {
        val setFrameMethods = if (sourceClass != null) {
            try {
                sourceClass.declaredMethods.filter { it.name == SET_FRAME_METHOD }
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                emptyList()
            }
        } else {
            emptyList()
        }

        val setFrameOneArg = setFrameMethods.any { it.parameterTypes.contentEquals(arrayOf(Rect::class.java)) }
        val setFrameFourArg = setFrameMethods.any {
            it.parameterTypes.contentEquals(arrayOf(Int::class.java, Int::class.java, Int::class.java, Int::class.java))
        }

        val sourceAbi = if (sourceClass != null) resolveInsetsSourceAbi(sourceClass, classLoader) else InsetsSourceAbi(
            hasOneIntConstructor = false,
            hasIdTypeConstructor = false,
            hasGetId = false,
            hasGetType = false,
            legacyStatusType = null,
            legacyNavigationType = null,
            publicStatusType = null,
            publicNavigationType = null,
            publicDisplayCutoutType = null,
        )

        val typeInfo = selectTypeEncoding(sourceAbi)

        val hasGetId = sourceClass != null && hasMethod(sourceClass, GET_ID_METHOD)
        val hasGetFrame = sourceClass != null && hasMethod(sourceClass, GET_FRAME_METHOD)

        val typeField = if (sourceClass != null) resolveIntField(sourceClass, "mType") else null
        val getTypeMethod = if (sourceClass != null) accessibleMethodOrNull(sourceClass, GET_TYPE_METHOD) else null

        return InsetsSourceCapability(
            sourceClass = sourceClass,
            setFrameOneArg = setFrameOneArg,
            setFrameFourArg = setFrameFourArg,
            typeInfo = typeInfo,
            hasGetId = hasGetId,
            hasGetFrame = hasGetFrame,
            typeField = typeField,
            getTypeMethod = getTypeMethod,
        )
    }

    private fun resolveInsetsSourceAbi(sourceClass: Class<*>, classLoader: ClassLoader): InsetsSourceAbi {
        val constructors = try {
            sourceClass.declaredConstructors
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            emptyArray()
        }

        val hasOneIntConstructor = constructors.any { it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType)) }
        val hasIdTypeConstructor = constructors.any {
            it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType))
        }

        val hasGetType = hasMethod(sourceClass, GET_TYPE_METHOD)
        val hasGetId = hasMethod(sourceClass, GET_ID_METHOD)

        val publicTypes = if (hasIdTypeConstructor) resolvePublicTypes() else RawTypeInfo(null, null, null)
        val legacyTypes = if (hasOneIntConstructor && !hasIdTypeConstructor) resolveLegacyTypes(classLoader) else RawTypeInfo(null, null, null)

        return InsetsSourceAbi(
            hasOneIntConstructor = hasOneIntConstructor,
            hasIdTypeConstructor = hasIdTypeConstructor,
            hasGetId = hasGetId,
            hasGetType = hasGetType,
            legacyStatusType = legacyTypes.statusBarType,
            legacyNavigationType = legacyTypes.navigationType,
            publicStatusType = publicTypes.statusBarType,
            publicNavigationType = publicTypes.navigationType,
            publicDisplayCutoutType = publicTypes.displayCutoutType,
        )
    }

    /**
     * Select the type encoding from the cold-resolved ABI.
     *
     * This is the single source of truth for type encoding selection.
     */
    fun selectTypeEncoding(abi: InsetsSourceAbi): InsetsTypeInfo {
        // Modern public encoding: (int id, int type) constructor exists and public masks resolve.
        if (abi.hasIdTypeConstructor && !abi.hasOneIntConstructor && abi.hasGetType) {
            val status = abi.publicStatusType
            if (status != null && status >= 0) {
                return InsetsTypeInfo(
                    encoding = InsetsTypeEncoding.MODERN_PUBLIC,
                    statusBarType = status,
                    navigationType = abi.publicNavigationType?.takeIf { it >= 0 } ?: -1,
                    displayCutoutType = abi.publicDisplayCutoutType?.takeIf { it >= 0 } ?: -1,
                )
            }
        }

        // Legacy internal encoding: (int type) constructor, no modern constructor, getType, and
        // both legacy status/nav constants resolvable.
        if (abi.hasOneIntConstructor && !abi.hasIdTypeConstructor && abi.hasGetType) {
            val status = abi.legacyStatusType
            val nav = abi.legacyNavigationType
            if (status != null && status >= 0 && nav != null && nav >= 0) {
                val cutout = resolveLegacyDisplayCutout(classLoader = null)
                return InsetsTypeInfo(
                    encoding = InsetsTypeEncoding.LEGACY_INTERNAL,
                    statusBarType = status,
                    navigationType = nav,
                    displayCutoutType = cutout?.takeIf { it >= 0 } ?: -1,
                )
            }
        }

        return InsetsTypeInfo(
            encoding = InsetsTypeEncoding.UNSUPPORTED,
            statusBarType = InsetsTypeInfo.TYPE_UNRESOLVED,
            navigationType = -1,
            displayCutoutType = -1,
        )
    }

    private fun resolveLegacyDisplayCutout(classLoader: ClassLoader?): Int? {
        if (classLoader == null) return null
        val clazz = XposedHelpers.findClassIfExists(INSETS_STATE_CLASS, classLoader) ?: return null
        return getStaticInt(clazz, "ITYPE_DISPLAY_CUTOUT")
    }

    private fun resolvePublicTypes(): RawTypeInfo {
        return RawTypeInfo(
            statusBarType = safePublicType { WindowInsets.Type.statusBars() },
            navigationType = safePublicType { WindowInsets.Type.navigationBars() },
            displayCutoutType = safePublicType { WindowInsets.Type.displayCutout() },
        )
    }

    private fun resolveLegacyTypes(classLoader: ClassLoader): RawTypeInfo {
        val stateClass = XposedHelpers.findClassIfExists(INSETS_STATE_CLASS, classLoader)
            ?: return RawTypeInfo(null, null, null)
        return RawTypeInfo(
            statusBarType = getStaticInt(stateClass, "ITYPE_STATUS_BAR"),
            navigationType = getStaticInt(stateClass, "ITYPE_NAVIGATION_BAR"),
            displayCutoutType = getStaticInt(stateClass, "ITYPE_DISPLAY_CUTOUT"),
        )
    }

    private fun resolveWindowManagerCapability(classLoader: ClassLoader): WindowManagerCapability {
        val windowStateClass = XposedHelpers.findClassIfExists(WINDOW_STATE_CLASS, classLoader)
        val lpClass = findLayoutParamsClass(classLoader)
        return resolveWindowManagerClass(windowStateClass, lpClass)
    }

    /**
     * Resolve WindowManager capability from already-loaded classes.  Used by tests and by [resolveCore].
     */
    fun resolveWindowManagerClass(windowStateClass: Class<*>?, lpClass: Class<*>?): WindowManagerCapability {
        val clientFrames = if (windowStateClass != null) resolveClientWindowFrames(windowStateClass) else null

        return WindowManagerCapability(
            windowStateClass = windowStateClass,
            windowStateAttrsField = if (windowStateClass != null) resolveDeclaredField(windowStateClass, "mAttrs") else null,
            layoutParamsTypeField = if (lpClass != null) resolveDeclaredField(lpClass, "type") else null,
            layoutParamsHeightField = if (lpClass != null) resolveDeclaredField(lpClass, "height") else null,
            layoutParamsPackageNameField = if (lpClass != null) resolveDeclaredField(lpClass, "packageName") else null,
            windowStateGetFrameMethod = if (windowStateClass != null) accessibleMethodOrNull(windowStateClass, GET_FRAME_METHOD) else null,
            windowStateGetDisplayMetricsMethod = if (windowStateClass != null) accessibleMethodOrNull(windowStateClass, "getDisplayMetrics") else null,
            windowStateGetDisplayIdMethod = if (windowStateClass != null) accessibleMethodOrNull(windowStateClass, "getDisplayId") else null,
            clientWindowFramesClass = clientFrames?.first,
            clientWindowFramesFrameField = clientFrames?.second,
        )
    }

    private fun resolveDecorInsetsCapability(classLoader: ClassLoader): DecorInsetsCapability {
        val infoClass = XposedHelpers.findClassIfExists(DECOR_INSETS_INFO_CLASS, classLoader)
        val displayContentClass = XposedHelpers.findClassIfExists("com.android.server.wm.DisplayContent", classLoader)
        return resolveDecorInsetsInfoClass(infoClass, displayContentClass)
    }

    /**
     * Resolve DecorInsets.Info capability from already-loaded classes.  Used by tests and by [resolveCore].
     */
    fun resolveDecorInsetsInfoClass(infoClass: Class<*>?, displayContentClass: Class<*>?): DecorInsetsCapability {
        val updateMethod = if (infoClass != null) {
            try {
                infoClass.declaredMethods.singleOrNull { it.name == DECOR_INSETS_UPDATE_METHOD }?.also { it.isAccessible = true }
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                null
            }
        } else null

        val displayContentGetDisplayMetricsMethod = if (displayContentClass != null) {
            accessibleMethodOrNull(displayContentClass, "getDisplayMetrics")
        } else null

        return DecorInsetsCapability(
            infoClass = infoClass,
            updateMethod = updateMethod,
            nonDecorInsetsField = if (infoClass != null) resolveDeclaredField(infoClass, "mNonDecorInsets") else null,
            nonDecorFrameField = if (infoClass != null) resolveDeclaredField(infoClass, "mNonDecorFrame") else null,
            displayContentGetDisplayMetricsMethod = displayContentGetDisplayMetricsMethod,
        )
    }

    private fun resolveClientWindowFrames(windowStateClass: Class<*>): Pair<Class<*>?, Field?>? {
        val setFramesMethods = try {
            windowStateClass.declaredMethods.filter { it.name == SET_FRAMES_METHOD }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            emptyList()
        }

        val matched = setFramesMethods.firstOrNull { method ->
            method.parameterTypes.isNotEmpty() && method.parameterTypes[0].simpleName == "ClientWindowFrames"
        } ?: return null

        val clazz = matched.parameterTypes[0]
        val frameField = try {
            clazz.getField("frame").also { it.isAccessible = true }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
        return clazz to frameField
    }

    private fun findLayoutParamsClass(classLoader: ClassLoader): Class<*>? {
        return try {
            XposedHelpers.findClassIfExists("android.view.WindowManager\$LayoutParams", classLoader)
                ?: Class.forName("android.view.WindowManager\$LayoutParams")
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    /** Resolve a late ABI once a real framework object is available. */
    fun resolveLate(classLoader: ClassLoader, displayContentClass: Class<*>, windowManagerServiceClass: Class<*>): LateAbi {
        val windowStateClass = XposedHelpers.findClassIfExists(WINDOW_STATE_CLASS, classLoader)
        val displayPolicyClass = XposedHelpers.findClassIfExists(DISPLAY_POLICY_CLASS, classLoader)
        val decorInsetsClass = if (displayPolicyClass != null) {
            try {
                displayPolicyClass.declaredClasses.firstOrNull { it.simpleName == "DecorInsets" }
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                null
            }
        } else null

        val windowSurfacePlacerClass = if (windowManagerServiceClass != null) {
            resolvePlacerClass(windowManagerServiceClass)
        } else null

        return LateAbi(
            windowStateGetDisplayMetricsMethod = if (windowStateClass != null) accessibleMethodOrNull(windowStateClass, "getDisplayMetrics") else null,
            windowStateGetDisplayIdMethod = if (windowStateClass != null) accessibleMethodOrNull(windowStateClass, "getDisplayId") else null,
            displayContentGetDisplayMetricsMethod = accessibleMethodOrNull(displayContentClass, "getDisplayMetrics"),
            displayContentGetDisplayPolicyMethod = accessibleMethodOrNull(displayContentClass, "getDisplayPolicy"),
            displayPolicyDecorInsetsField = if (displayPolicyClass != null) resolveDeclaredField(displayPolicyClass, "mDecorInsets") else null,
            decorInsetsInvalidateMethod = if (decorInsetsClass != null) accessibleMethodOrNull(decorInsetsClass, "invalidate") else null,
            windowManagerServicePlacerField = if (windowManagerServiceClass != null) resolveDeclaredField(windowManagerServiceClass, "mWindowPlacerLocked") else null,
            windowSurfacePlacerRequestTraversalMethod = if (windowSurfacePlacerClass != null) accessibleMethodOrNull(windowSurfacePlacerClass, "requestTraversal") else null,
        )
    }

    private fun resolvePlacerClass(windowManagerServiceClass: Class<*>): Class<*>? {
        val field = resolveDeclaredField(windowManagerServiceClass, "mWindowPlacerLocked") ?: return null
        return field.type
    }

    // ----------------------------------------------------------------------------
    // Low-level reflection helpers (cold only).
    // ----------------------------------------------------------------------------

    fun resolveDeclaredField(clazz: Class<*>, name: String): Field? {
        var current: Class<*>? = clazz
        while (current != null) {
            try {
                val field = current.getDeclaredField(name)
                field.isAccessible = true
                return field
            } catch (_: NoSuchFieldException) {
                current = current.superclass
            } catch (t: Throwable) {
                FatalErrors.unwrapAndRethrowIfFatal(t)
                return null
            }
        }
        return null
    }

    fun resolveIntField(clazz: Class<*>?, name: String): Field? {
        if (clazz == null) return null
        val field = resolveDeclaredField(clazz, name) ?: return null
        return if (field.type == Int::class.javaPrimitiveType || field.type == Int::class.java) field else null
    }

    private fun accessibleMethodOrNull(clazz: Class<*>, methodName: String): Method? {
        return try {
            clazz.getMethod(methodName)?.also { it.isAccessible = true }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    private fun hasMethod(clazz: Class<*>, methodName: String): Boolean {
        return try {
            clazz.declaredMethods.any { it.name == methodName }
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            false
        }
    }

    private fun getStaticInt(clazz: Class<*>, fieldName: String): Int? {
        return try {
            val field = clazz.getDeclaredField(fieldName)
            field.isAccessible = true
            val value = field.get(null)
            if (value is Int) value else null
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    private fun safePublicType(block: () -> Int): Int? {
        return try {
            block()
        } catch (t: Throwable) {
            FatalErrors.unwrapAndRethrowIfFatal(t)
            null
        }
    }

    // ----------------------------------------------------------------------------
    // Pure geometry helpers.
    // ----------------------------------------------------------------------------

    fun computeStatusBarFrameBottom(originalTop: Int, originalBottom: Int, configuredPx: Int, enabled: Boolean): Int {
        if (!enabled || configuredPx <= 0) return originalBottom
        return originalTop + configuredPx
    }

    fun computeNonDecorTop(originalTop: Int, configuredPx: Int, enabled: Boolean): Int {
        if (!enabled || configuredPx <= 0) return originalTop
        return configuredPx
    }

    fun computeNonDecorFrameTop(originalFrameTop: Int, originalInsetTop: Int, configuredPx: Int, enabled: Boolean): Int {
        if (!enabled || configuredPx <= 0) return originalFrameTop
        if (originalInsetTop == 0) return originalFrameTop
        return originalFrameTop + configuredPx - originalInsetTop
    }

    fun isStatusBarType(type: Int, typeInfo: InsetsTypeInfo): Boolean {
        return type == typeInfo.statusBarType
    }
}
