package tv.withaibuild.customiuizer.mods.utils

import io.github.libxposed.api.XposedModuleInterface
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.GlobalActionSystemServerHooks
import tv.withaibuild.customiuizer.mods.GlobalActions
import tv.withaibuild.customiuizer.mods.PackagePermissions
import tv.withaibuild.customiuizer.mods.utils.feature.PackagePermissionsFeatureId
import tv.withaibuild.customiuizer.mods.utils.feature.SystemServerFeatures
import tv.withaibuild.customiuizer.utils.PrefMap
/**
 * Installer for hooks that must run in `system_server`.
 *
 * This keeps [MainModule] focused on module-level lifecycle and delegates the long list of
 * per-preference system-server hooks to a dedicated, stateless object.  Each hook is still guarded
 * by the same preference check; nothing is installed unless the user has enabled it.
 */
object SystemServerInstaller {

    @JvmStatic
    @JvmOverloads
    fun install(lpparam: XposedModuleInterface.SystemServerStartingParam, prefReady: Boolean = true) {
        val mPrefs = MainModule.mPrefs

        // Base system_server hook: not preference-controlled, always installed.
        val registry = FeatureInstallRegistry()
        registry.register(PackagePermissionsFeature(lpparam, mPrefs))

        // All preference-guarded system_server features.
        for (feature in SystemServerFeatures.all(lpparam)) {
            registry.register(feature)
        }

        // Global actions are still checked eagerly because they depend on a cached map that must
        // not be built before preferences are ready.
        if (prefReady && GlobalActions.hasCustomActions()) {
            GlobalActionSystemServerHooks.setupGlobalActions(lpparam)
        }

        registry.installAll(FeatureTarget.SYSTEM_SERVER, InstallPhase.SYSTEM_SERVER_STARTING, mPrefs)
    }
}

internal class PackagePermissionsFeature(
    private val lpparam: XposedModuleInterface.SystemServerStartingParam,
    private val mPrefs: PrefMap
) : FeatureDefinition {
    override val id = PackagePermissionsFeatureId
    override val name = "Package permissions"
    override val preferenceKey: String? = null
    override val target = FeatureTarget.SYSTEM_SERVER
    override val phase = InstallPhase.SYSTEM_SERVER_STARTING
    override fun isEnabled(prefs: PrefMap) = true

    override fun install(): FeatureInstallResult {
        PackagePermissions.hook(lpparam)
        return FeatureInstallResult.INSTALLED
    }
}
