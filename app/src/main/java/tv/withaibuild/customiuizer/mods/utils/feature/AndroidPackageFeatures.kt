package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.SystemShareMenuHooks
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.utils.PrefMap

object AndroidPackageFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureDefinition> = listOf(
        AndroidCleanShareMenuFeature(lpparam, mPrefs),
        AndroidCleanOpenWithMenuFeature(lpparam, mPrefs),
        AndroidAllRotationsFeature(lpparam, mPrefs),
    )
}

internal class AndroidCleanShareMenuFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    AndroidCleanShareMenuFeatureId,
    "Android Clean Share Menu",
    "system_cleanshare",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_cleanshare")
    override fun installHook() = SystemShareMenuHooks.CleanShareMenuHook(lpparam)
}

internal class AndroidCleanOpenWithMenuFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    AndroidCleanOpenWithMenuFeatureId,
    "Android Clean Open With Menu",
    "system_cleanopenwith",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("system_cleanopenwith")
    override fun installHook() = SystemShareMenuHooks.CleanOpenWithMenuHook(lpparam)
}

internal class AndroidAllRotationsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    AndroidAllRotationsFeatureId,
    "Android All Rotations",
    "system_allrotations2",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("system_allrotations2", 1) > 1
    override fun install(): FeatureInstallResult = try {
        val allRotations = mPrefs.getStringAsInt("system_allrotations2", 1)
        MainModule.resHooks.setThemeValueReplacement("android", "bool", "config_allowAllRotations", allRotations == 2)
        FeatureInstallResult.Installed
    } catch (t: Throwable) {
        FeatureInstallResult.FailedTransient(t.javaClass.name)
    }
}
