package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.Various
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers
import tv.withaibuild.customiuizer.utils.PrefMap

object GuardProviderFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureDefinition> = listOf(
        GuardProviderDisableDefraudAppsFeature(lpparam, mPrefs),
    )
}

internal class GuardProviderDisableDefraudAppsFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    GuardProviderDisableDefraudAppsFeatureId,
    "Guard Provider Disable Defraud Apps",
    "various_disable_defraud_apps_detect",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_disable_defraud_apps_detect")
    override fun install(): FeatureInstallResult = try {
        MainModule.loadDexKit()
        XposedHelpers.createBridge(lpparam.applicationInfo.sourceDir)
        Various.DisableDefraudAppsCheck(lpparam)
        FeatureInstallResult.Installed
    } catch (t: Throwable) {
        XposedHelpers.log(t)
        FeatureInstallResult.FailedTransient(t.javaClass.name)
    } finally {
        XposedHelpers.closeBridge()
    }
}
