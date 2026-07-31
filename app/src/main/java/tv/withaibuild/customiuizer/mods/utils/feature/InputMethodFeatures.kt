package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.Controls
import tv.withaibuild.customiuizer.mods.Various
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.utils.PrefMap

object InputMethodFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureDefinition> = listOf(
        InputMethodVolumeCursorFeature(lpparam, mPrefs),
        InputMethodFixBottomMarginFeature(lpparam, mPrefs),
        InputMethodGboardPaddingFeature(lpparam, mPrefs),
    )
}

internal class InputMethodVolumeCursorFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    InputMethodVolumeCursorFeatureId,
    "Input Method Volume Cursor",
    "controls_volumecursor",
    FeatureTarget.ANY,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("controls_volumecursor")
    override fun installHook() = Controls.VolumeCursorHook(lpparam)
}

internal class InputMethodFixBottomMarginFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    InputMethodFixBottomMarginFeatureId,
    "Input Method Fix Bottom Margin",
    "controls_nonavbar_fix_inputmethod",
    FeatureTarget.ANY,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("controls_nonavbar_fix_inputmethod") && prefs.getBoolean("controls_nonavbar")
    override fun installHook() = Various.FixInputMethodBottomMarginHook(lpparam)
}

internal class InputMethodGboardPaddingFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    InputMethodGboardPaddingFeatureId,
    "Input Method Gboard Padding",
    "various_gboardpadding_port",
    FeatureTarget.ANY,
) {
    override fun isEnabledCondition(prefs: PrefMap) = packageName.startsWith("com.google.android.inputmethod") && (prefs.getInt("various_gboardpadding_port", 0) > 0 || prefs.getInt("various_gboardpadding_land", 0) > 0)
    override fun installHook() = Various.GboardPaddingHook(lpparam)
}
