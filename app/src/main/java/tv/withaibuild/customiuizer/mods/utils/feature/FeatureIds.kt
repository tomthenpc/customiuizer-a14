package tv.withaibuild.customiuizer.mods.utils.feature

import tv.withaibuild.customiuizer.mods.utils.FeatureId

/**
 * Typed identities for all features in the module.
 *
 * Keeping feature ids together makes it easy to see the complete list and avoids accidental
 * duplicate identities across different installers.
 */

data object PackagePermissionsFeatureId : FeatureId {
    override val name = "package_permissions"
}
