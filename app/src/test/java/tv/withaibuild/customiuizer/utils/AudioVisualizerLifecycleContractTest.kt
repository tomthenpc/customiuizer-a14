package tv.withaibuild.customiuizer.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.ref.WeakReference

class AudioVisualizerLifecycleContractTest {

    @Test
    fun preferenceObserverDoesNotStronglyRetainViewOwner() {
        val ownerClassName = "tv.withaibuild.customiuizer.utils.AudioVisualizer"
        val observerClass = Class.forName(
            "$ownerClassName\$AudioVisualizerPreferenceObserver"
        )
        val ownerFields = observerClass.declaredFields.filter { field ->
            field.type.name == ownerClassName
        }
        val weakOwnerFields = observerClass.declaredFields.filter { field ->
            field.type == WeakReference::class.java
        }

        assertTrue("observer must not contain a strong AudioVisualizer field", ownerFields.isEmpty())
        assertEquals("observer must contain exactly one weak owner field", 1, weakOwnerFields.size)
    }

    @Test
    fun observerRegistrationAndDisposalRemainOwnerBound() {
        val source = source()
        assertTrue(source.contains("AudioVisualizerPreferenceObserver(this)"))
        assertTrue(source.contains("ModuleHelper.observePreferenceChange(preferenceObserver, this)"))
        assertTrue(source.contains("ModuleHelper.unregisterPreferenceObserver(this)"))
    }

    private fun source(): String {
        val relativePath = "app/src/main/java/tv/withaibuild/customiuizer/utils/AudioVisualizer.kt"
        var directory = File(java.lang.System.getProperty("user.dir").orEmpty()).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }
}
