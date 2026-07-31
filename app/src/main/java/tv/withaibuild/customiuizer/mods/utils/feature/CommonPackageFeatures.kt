package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.System as ModsSystem
import tv.withaibuild.customiuizer.mods.Various
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.utils.PrefMap

object CommonPackageFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureDefinition> = listOf(
        StatusBarHeightFeature(lpparam, mPrefs),
        AlarmCompatFeature(lpparam, mPrefs),
    )
}

internal class StatusBarHeightFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    StatusBarHeightFeatureId,
    "Status Bar Height",
    "system_statusbarheight",
    FeatureTarget.ANY,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getInt("system_statusbarheight", 11) > 11
    override fun installHook() = ModsSystem.StatusBarHeightHook(lpparam)
}

internal class AlarmCompatFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    AlarmCompatFeatureId,
    "Alarm Compat",
    "various_alarmcompat",
    FeatureTarget.ANY,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_alarmcompat") && prefs.getStringSet("various_alarmcompat_apps").contains(packageName)
    override fun installHook() = Various.AlarmCompatHook()
}
