package tv.withaibuild.customiuizer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Contract tests for the system_server Global Action Receiver registered by
 * [tv.withaibuild.customiuizer.mods.GlobalActionSystemServerHooks.setupGlobalActions].
 *
 * These are static source checks: the receiver runs inside system_server, so it
 * cannot be exercised in a plain JVM unit test. We verify the structural safety
 * boundaries instead.
 */
class GlobalActionSystemServerReceiverSafetyTest {

    private val sourceFile = "app/src/main/java/tv/withaibuild/customiuizer/mods/GlobalActionSystemServerHooks.kt"

    @Test
    fun phoneWindowManagerActionReceiver_onReceive_hasTopLevelGuard() {
        val receiver = phoneWindowManagerActionReceiverSource()

        assertTrue(
            "onReceive must wrap the full business body in ModuleHelper.guarded",
            receiver.contains("ModuleHelper.guarded {")
        )

        assertTrue(
            "guarded block must start before isTrustedBroadcast",
            receiver.indexOf("ModuleHelper.guarded {") <
                receiver.indexOf("ModuleHelper.isTrustedBroadcast")
        )

        assertTrue(
            "guarded block must enclose the when branches",
            receiver.indexOf("when (action) {") >
                receiver.indexOf("ModuleHelper.guarded {") &&
                receiver.lastIndexOf("}") > receiver.indexOf("when (action) {")
        )
    }

    @Test
    fun phoneWindowManagerActionReceiver_onReceive_doesNotRethrow() {
        val receiver = phoneWindowManagerActionReceiverSource()

        assertFalse(
            "onReceive must not rethrow a caught Throwable",
            receiver.contains("throw t") || receiver.contains("throw e") ||
                receiver.contains("throw ex")
        )
    }

    @Test
    fun phoneWindowManagerActionReceiver_failure_setsActionFailedForOrderedBroadcast() {
        val receiver = phoneWindowManagerActionReceiverSource()

        assertTrue(
            "failure path must set ACTION_FAILED for ordered broadcasts",
            receiver.contains("GlobalActions.ACTION_FAILED")
        )

        val failedBlock = receiver.sectionAfter("if (!completed && isOrderedBroadcast)")
        assertTrue(
            "ACTION_FAILED must be the result code when completed is false",
            failedBlock.contains("setResultCode(GlobalActions.ACTION_FAILED)")
        )
    }

    @Test
    fun phoneWindowManagerActionReceiver_success_setsActionHandled() {
        val receiver = phoneWindowManagerActionReceiverSource()

        val guardedBlock = receiver.section(
            "ModuleHelper.guarded {",
            "if (!completed && isOrderedBroadcast)"
        )
        assertTrue(
            "success path must set ACTION_HANDLED for ordered broadcasts",
            guardedBlock.contains("setResultCode(GlobalActions.ACTION_HANDLED)")
        )

        assertTrue(
            "completed flag must be set after successful handling",
            guardedBlock.contains("completed = true")
        )
    }

    @Test
    fun phoneWindowManagerActionReceiver_trustVerification_remains() {
        val receiver = phoneWindowManagerActionReceiverSource()

        assertTrue(
            "isTrustedBroadcast must still gate custom actions",
            receiver.contains("ModuleHelper.isTrustedBroadcast(")
        )

        assertTrue(
            "untrusted sender must still receive ACTION_FAILED",
            receiver.contains("rejectionResultCode = GlobalActions.ACTION_FAILED")
        )
    }

    @Test
    fun otherSystemUiReceivers_areNotModified() {
        val source = source()

        // FastReboot, status bar and freeform receivers are unrelated and must keep
        // their original registration shape. We do not wrap them in a completed flag.
        assertFalse(
            "fastRebootReceiver must not carry a completed flag",
            source.sectionAfter("setupFastRebootReceiver").contains("completed")
        )

        assertFalse(
            "statusBarActionReceiver must not carry a completed flag",
            source.sectionAfter("setupStatusBar").contains("completed")
        )

        assertFalse(
            "freeformModeReceiver must not carry a completed flag",
            source.sectionAfter("freeformModeReceiver").contains("completed")
        )
    }

    @Test
    fun topLevelGuard_usesModuleHelperGuarded_notJustLocalTryCatch() {
        val receiver = phoneWindowManagerActionReceiverSource()

        assertTrue(
            "use the existing ModuleHelper.guarded inline helper",
            receiver.contains("ModuleHelper.guarded")
        )
    }

    private fun phoneWindowManagerActionReceiverSource(): String {
        val source = source()
        return source.section(
            "phoneWindowManagerActionReceiver",
            "}, intentfilter, Context.RECEIVER_EXPORTED)"
        )
    }

    private fun source(): String = source(sourceFile)

    private fun source(relativePath: String): String {
        var directory = File(System.getProperty("user.dir")).absoluteFile
        while (true) {
            val candidate = File(directory, relativePath)
            if (candidate.isFile) return candidate.readText()
            directory = directory.parentFile
                ?: error("Repository root not found while locating $relativePath")
        }
    }

    private fun String.section(start: String, end: String): String {
        val startIndex = indexOf(start)
        check(startIndex >= 0) { "Could not find start marker '$start'" }
        val endIndex = indexOf(end, startIndex + start.length)
        check(endIndex > startIndex) { "Could not find end marker '$end' after '$start'" }
        return substring(startIndex, endIndex + end.length)
    }

    private fun String.sectionAfter(marker: String): String {
        val index = indexOf(marker)
        check(index >= 0) { "Could not find marker '$marker'" }
        return substring(index)
    }
}
