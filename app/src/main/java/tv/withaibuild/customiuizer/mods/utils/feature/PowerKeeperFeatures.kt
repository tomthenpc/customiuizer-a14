package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.Various
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.utils.PrefMap

object PowerKeeperFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureDefinition> = listOf(
        PowerKeeperAppsRestrictFeature(lpparam, mPrefs),
        PowerKeeperPersistBatteryOptimizationFeature(lpparam, mPrefs),
    )
}

internal class PowerKeeperAppsRestrictFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    PowerKeeperAppsRestrictFeatureId,
    "Power Keeper Apps Restrict",
    "various_restrictapp",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_restrictapp")
    override fun installHook() = Various.AppsRestrictPowerHook(lpparam)
}

internal class PowerKeeperPersistBatteryOptimizationFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    PowerKeeperPersistBatteryOptimizationFeatureId,
    "Power Keeper Persist Battery Optimization",
    "various_persist_batteryoptimization",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_persist_batteryoptimization")
    override fun installHook() = Various.PersistBatteryOptimizationHook(lpparam)
}
