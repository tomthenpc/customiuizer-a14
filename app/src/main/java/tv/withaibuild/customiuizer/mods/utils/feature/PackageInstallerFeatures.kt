package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.Various
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.utils.PrefMap

object PackageInstallerFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureDefinition> = listOf(
        PackageInstallerMiuiPackageFeature(lpparam, mPrefs),
        PackageInstallerAppInfoFeature(lpparam, mPrefs),
        PackageInstallerPurifyFeature(lpparam, mPrefs),
    )
}

internal class PackageInstallerMiuiPackageFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    PackageInstallerMiuiPackageFeatureId,
    "Package Installer Miui Package",
    "various_miuiinstaller",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_miuiinstaller")
    override fun installHook() = Various.MiuiPackageInstallerHook(lpparam)
}

internal class PackageInstallerAppInfoFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    PackageInstallerAppInfoFeatureId,
    "Package Installer App Info",
    "various_installappinfo",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_installappinfo")
    override fun installHook() = Various.AppInfoDuringMiuiInstallHook(lpparam)
}

internal class PackageInstallerPurifyFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    PackageInstallerPurifyFeatureId,
    "Package Installer Purify",
    "various_installer_purify",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_installer_purify")
    override fun installHook() = Various.PurePackageInstallerHook(lpparam)
}
