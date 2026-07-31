package tv.withaibuild.customiuizer.mods.utils.feature

import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import tv.withaibuild.customiuizer.mods.Various
import tv.withaibuild.customiuizer.mods.utils.FeatureDefinition
import tv.withaibuild.customiuizer.mods.utils.FeatureInstallResult
import tv.withaibuild.customiuizer.mods.utils.FeatureTarget
import tv.withaibuild.customiuizer.mods.utils.InstallPhase
import tv.withaibuild.customiuizer.utils.PrefMap

object PhoneFeatures {
    @JvmStatic
    fun all(lpparam: PackageReadyParam, mPrefs: PrefMap): List<FeatureDefinition> = listOf(
        PhoneShowCallUiFeature(lpparam, mPrefs),
        PhoneInCallBrightnessFeature(lpparam, mPrefs),
        PhoneAnswerCallInHeadUpFeature(lpparam, mPrefs),
    )
}

internal class PhoneShowCallUiFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    PhoneShowCallUiFeatureId,
    "Phone Show Call Ui",
    "various_showcallui",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getStringAsInt("various_showcallui", 0) > 0
    override fun installHook() = Various.ShowCallUIHook(lpparam)
}

internal class PhoneInCallBrightnessFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    PhoneInCallBrightnessFeatureId,
    "Phone In Call Brightness",
    "various_calluibright",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_calluibright")
    override fun installHook() = Various.InCallBrightnessHook(lpparam)
}

internal class PhoneAnswerCallInHeadUpFeature(
    lpparam: PackageReadyParam,
    mPrefs: PrefMap
) : BasePackageReadyFeature(
    lpparam,
    mPrefs,
    PhoneAnswerCallInHeadUpFeatureId,
    "Phone Answer Call In Head Up",
    "various_answerinheadup",
    FeatureTarget.SYSTEM_PACKAGE,
) {
    override fun isEnabledCondition(prefs: PrefMap) = prefs.getBoolean("various_answerinheadup")
    override fun installHook() = Various.AnswerCallInHeadUpHook(lpparam)
}
