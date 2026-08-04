package tv.withaibuild.customiuizer.mods.utils

import org.junit.Assert.assertSame
import org.junit.Test

class ModuleHelperFatalBoundaryTest {

    @Test(expected = ThreadDeath::class)
    fun callMethodSilentlyPropagatesThreadDeath() {
        ModuleHelper.callMethodSilently(FatalThrowingTarget(), "throwThreadDeath")
    }

    @Test(expected = InternalError::class)
    fun callMethodSilentlyPropagatesVirtualMachineError() {
        ModuleHelper.callMethodSilently(FatalThrowingTarget(), "throwInternalError")
    }

    @Test(expected = OutOfMemoryError::class)
    fun callMethodSilentlyPropagatesOutOfMemoryError() {
        ModuleHelper.callMethodSilently(FatalThrowingTarget(), "throwOutOfMemoryError")
    }

    @Test
    fun callMethodSilentlyReturnsNotExistSymbolForMissingMethod() {
        val result = ModuleHelper.callMethodSilently(Object(), "nonExistentMethod")
        assertSame(ModuleHelper.NOT_EXIST_SYMBOL, result)
    }

    @Test
    fun getObjectFieldSilentlyReturnsNotExistSymbolForMissingField() {
        val result = ModuleHelper.getObjectFieldSilently(Object(), "nonExistentField")
        assertSame(ModuleHelper.NOT_EXIST_SYMBOL, result)
    }

    @Test
    fun getStaticObjectFieldSilentlyReturnsNotExistSymbolForMissingField() {
        val result = ModuleHelper.getStaticObjectFieldSilently(ModuleHelperFatalBoundaryTest::class.java, "nonExistentField")
        assertSame(ModuleHelper.NOT_EXIST_SYMBOL, result)
    }

    class FatalThrowingTarget {

        @Suppress("unused")
        fun throwThreadDeath() {
            throw ThreadDeath()
        }

        @Suppress("unused")
        fun throwInternalError() {
            throw InternalError("vm")
        }

        @Suppress("unused")
        fun throwOutOfMemoryError() {
            throw OutOfMemoryError("oom")
        }
    }
}
