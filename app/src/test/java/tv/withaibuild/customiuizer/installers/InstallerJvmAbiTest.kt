package tv.withaibuild.customiuizer.installers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class InstallerJvmAbiTest(
    private val installerClassName: String,
    private val installMethodName: String,
) {

    companion object {
        const val EXPECTED_INSTALLER_COUNT = 12

        @JvmStatic
        @Parameterized.Parameters(name = "{0}.{1}")
        fun data(): List<Array<Any>> = listOf(
            arrayOf("AndroidPackageInstaller", "install"),
            arrayOf("GenericAppInstaller", "installPostAttach"),
            arrayOf("GuardProviderInstaller", "install"),
            arrayOf("InputMethodInstaller", "install"),
            arrayOf("LauncherInstaller", "install"),
            arrayOf("MediaInstaller", "install"),
            arrayOf("PackageInstallerRouter", "install"),
            arrayOf("PhoneInstaller", "install"),
            arrayOf("PowerKeeperInstaller", "install"),
            arrayOf("SecurityCenterInstaller", "install"),
            arrayOf("SettingsInstaller", "install"),
            arrayOf("SystemUiInstaller", "install"),
        )
    }

    @Test
    fun installerCountIsExpected() {
        assertEquals("installer class count changed", EXPECTED_INSTALLER_COUNT, data().size)
    }

    @Test
    fun jvmStaticInstallMethodExists() {
        val cls = Class.forName("tv.withaibuild.customiuizer.installers.$installerClassName")
        assertTrue("$installerClassName must be public", java.lang.reflect.Modifier.isPublic(cls.modifiers))

        val lpparamClass = Class.forName("io.github.libxposed.api.XposedModuleInterface\$PackageReadyParam")
        val install = cls.methods.firstOrNull {
            it.name == installMethodName &&
                java.lang.reflect.Modifier.isPublic(it.modifiers) &&
                java.lang.reflect.Modifier.isStatic(it.modifiers) &&
                it.parameterCount >= 2 &&
                it.parameterTypes[0] == lpparamClass &&
                it.parameterTypes[1] == tv.withaibuild.customiuizer.utils.PrefMap::class.java
        }
        assertNotNull(
            "$installerClassName.$installMethodName(PackageReadyParam, PrefMap, ...) missing or not public static",
            install
        )
        assertTrue(
            "$installerClassName.$installMethodName must be annotated with @JvmStatic",
            install!!.isAnnotationPresent(kotlin.jvm.JvmStatic::class.java)
        )
    }
}
