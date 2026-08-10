package tv.withaibuild.customiuizer.mods.statusbarheight

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * Immutable typed capability description for the status bar height feature.
 *
 * Each member is either a resolved [Field]/[Method]/[Class] or null, expressing capability rather
 * than an all-or-nothing result.  Optional capabilities may be unavailable while the core feature
 * continues with the remaining capabilities.
 */

/** Which type encoding the ROM uses for `android.view.InsetsSource.getType()`. */
enum class InsetsTypeEncoding {
    MODERN_PUBLIC,
    LEGACY_INTERNAL,
    UNSUPPORTED,
}

/**
 * Resolved type encoding constants.
 *
 * @property statusBarType the value that identifies the status bar InsetsSource.
 * @property navigationType the value for navigation bar; `-1` if not resolvable.
 * @property displayCutoutType the value for display cutout; `-1` if not resolvable.
 */
internal data class InsetsTypeInfo(
    val encoding: InsetsTypeEncoding,
    val statusBarType: Int,
    val navigationType: Int,
    val displayCutoutType: Int,
) {
    val isSupported: Boolean get() = encoding != InsetsTypeEncoding.UNSUPPORTED

    companion object {
        /** Sentinel value meaning the type could not be resolved. */
        const val TYPE_UNRESOLVED = Int.MIN_VALUE
    }
}

/**
 * Cold-path description of `android.view.InsetsSource` constructors/methods.
 * Used to build [InsetsTypeInfo] and not retained at runtime.
 */
internal data class InsetsSourceAbi(
    val hasOneIntConstructor: Boolean,
    val hasIdTypeConstructor: Boolean,
    val hasGetId: Boolean,
    val hasGetType: Boolean,
    val legacyStatusType: Int?,
    val legacyNavigationType: Int?,
    val publicStatusType: Int?,
    val publicNavigationType: Int?,
    val publicDisplayCutoutType: Int?,
)

/** Raw public/legacy type values before normalization. */
internal data class RawTypeInfo(
    val statusBarType: Int?,
    val navigationType: Int?,
    val displayCutoutType: Int?,
)

/**
 * Immutable capability for the `android.view.InsetsSource` hot path.
 *
 * @property sourceClass the InsetsSource class; null if not found.
 * @property setFrameOneArg true if `setFrame(Rect)` exists.
 * @property setFrameFourArg true if `setFrame(int,int,int,int)` exists.
 * @property typeInfo resolved type encoding.
 * @property hasGetId true if `getId()` exists.
 * @property hasGetFrame true if `getFrame()` exists.
 * @property typeField preferred `mType` field; null if unavailable.
 * @property getTypeMethod fallback `getType()` method; null if unavailable.
 */
internal data class InsetsSourceCapability(
    val sourceClass: Class<*>?,
    val setFrameOneArg: Boolean,
    val setFrameFourArg: Boolean,
    val typeInfo: InsetsTypeInfo,
    val hasGetId: Boolean,
    val hasGetFrame: Boolean,
    val typeField: Field?,
    val getTypeMethod: Method?,
) {
    val coreSupported: Boolean
        get() = sourceClass != null && typeInfo.isSupported && (setFrameOneArg || setFrameFourArg)
}

/**
 * Immutable capability for the WindowManager / WindowState path.
 */
internal data class WindowManagerCapability(
    val windowStateClass: Class<*>?,
    val windowStateAttrsField: Field?,
    val layoutParamsTypeField: Field?,
    val layoutParamsHeightField: Field?,
    val layoutParamsPackageNameField: Field?,
    val windowStateGetFrameMethod: Method?,
    val windowStateGetDisplayMetricsMethod: Method?,
    val windowStateGetDisplayIdMethod: Method?,
    val clientWindowFramesClass: Class<*>?,
    val clientWindowFramesFrameField: Field?,
) {
    val hasWindowStateClass: Boolean get() = windowStateClass != null
    val hasClientWindowFrames: Boolean get() = clientWindowFramesClass != null && clientWindowFramesFrameField != null
}

/**
 * Immutable capability for DisplayPolicy.DecorInsets.Info.
 */
internal data class DecorInsetsCapability(
    val infoClass: Class<*>?,
    val updateMethod: Method?,
    val nonDecorInsetsField: Field?,
    val nonDecorFrameField: Field?,
    val displayContentGetDisplayMetricsMethod: Method?,
) {
    val hasInfo: Boolean get() = infoClass != null && updateMethod != null
    val canAdjustNonDecor: Boolean
        get() = nonDecorInsetsField != null && nonDecorFrameField != null
}

/**
 * Late ABI resolved on first real framework object.
 *
 * Each member is nullable.  `null` means resolved-unavailable or not yet resolved.
 */
internal data class LateAbi(
    val windowStateGetDisplayMetricsMethod: Method?,
    val windowStateGetDisplayIdMethod: Method?,
    val displayContentGetDisplayMetricsMethod: Method?,
    val displayContentGetDisplayPolicyMethod: Method?,
    val displayPolicyDecorInsetsField: Field?,
    val decorInsetsInvalidateMethod: Method?,
    val windowManagerServicePlacerField: Field?,
    val windowSurfacePlacerRequestTraversalMethod: Method?,
)

/**
 * Aggregate cold-resolved ABI for the status bar height feature.
 */
internal data class StatusBarHeightAbi(
    val insets: InsetsSourceCapability,
    val windowManager: WindowManagerCapability,
    val decorInsets: DecorInsetsCapability,
)
