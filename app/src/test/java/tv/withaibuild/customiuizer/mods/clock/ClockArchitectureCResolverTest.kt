package tv.withaibuild.customiuizer.mods.clock

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Method
import java.util.Calendar

class ClockArchitectureCResolverTest {

    // ------------------------------------------------------------------------
    // Controller resolution.
    // ------------------------------------------------------------------------

    @Test
    fun resolveControllerClass_fieldsResolvedThroughSuperclass() {
        val cap = ClockResolver.resolveControllerClass(FakeControllerChild::class.java)

        assertNotNull(cap)
        assertEquals(FakeControllerChild::class.java, cap!!.controllerClass)
        assertEquals(FakeControllerBase::class.java, cap.calendarField.declaringClass)
        assertEquals(FakeControllerBase::class.java, cap.clockListenersField.declaringClass)
        assertEquals(FakeControllerBase::class.java, cap.is24Field.declaringClass)
    }

    @Test
    fun resolveControllerClass_rejectsNonPrimitiveBooleanIs24() {
        val cap = ClockResolver.resolveControllerClass(FakeControllerWithBoxedIs24::class.java)
        assertNull(cap)
    }

    @Test
    fun resolveControllerClass_nullClass_returnsNull() {
        assertNull(ClockResolver.resolveControllerClass(null))
    }

    // ------------------------------------------------------------------------
    // Target resolution.
    // ------------------------------------------------------------------------

    @Test
    fun resolveClockTargetClass_controllerFieldAndUpdateTimeResolvedIndependently() {
        val miuiClock = ClockResolver.resolveClockTargetClass(FakeMiuiClock::class.java)
        val statusBarClock = ClockResolver.resolveClockTargetClass(FakeMiuiStatusBarClock::class.java)

        assertNotNull(miuiClock)
        assertNotNull(statusBarClock)

        assertEquals(FakeMiuiClock::class.java, miuiClock!!.targetClass)
        assertEquals(FakeMiuiStatusBarClock::class.java, statusBarClock!!.targetClass)

        assertEquals("mMiuiStatusBarClockController", miuiClock.controllerField.name)
        assertEquals("mMiuiStatusBarClockController", statusBarClock.controllerField.name)

        assertEquals("updateTime", miuiClock.updateTimeMethod.name)
        assertEquals("updateTime", statusBarClock.updateTimeMethod.name)
    }

    @Test
    fun resolveClockTargetClass_updateTimeResolvedFromSuperclass() {
        val cap = ClockResolver.resolveClockTargetClass(FakeMiuiClockWithInheritedUpdate::class.java)

        assertNotNull(cap)
        assertEquals(FakeMiuiClockWithInheritedUpdate::class.java, cap!!.targetClass)
        assertEquals("mMiuiStatusBarClockController", cap.controllerField.name)
        assertEquals("updateTime", cap.updateTimeMethod.name)
        assertEquals(FakeClockBase::class.java, cap.updateTimeMethod.declaringClass)
    }

    // ------------------------------------------------------------------------
    // Calendar cold primary.
    // ------------------------------------------------------------------------

    @Test
    fun resolveCalendarFromDeclaredType_fullCapabilityResolvesCold() {
        val cap = ClockResolver.resolveCalendarFromDeclaredType(
            FakeCalendarBase::class.java,
            Context::class.java,
        )

        assertNotNull(cap)
        assertEquals(FakeCalendarBase::class.java, cap!!.calendarClass)
        assertEquals("setTimeInMillis", cap.setTimeInMillisMethod.name)
        assertEquals("format", cap.formatMethod.name)
        assertSame(Long::class.javaPrimitiveType, cap.setTimeInMillisMethod.parameterTypes[0])
    }

    @Test
    fun resolveCalendarFromDeclaredType_missingFormat_returnsNull() {
        val cap = ClockResolver.resolveCalendarFromDeclaredType(
            FakeCalendarNoFormat::class.java,
            Context::class.java,
        )

        assertNull(cap)
    }

    @Test
    fun resolveCalendarFromDeclaredType_missingSetTimeInMillis_returnsNull() {
        val cap = ClockResolver.resolveCalendarFromDeclaredType(
            FakeCalendarNoSetTime::class.java,
            Context::class.java,
        )

        assertNull(cap)
    }

    // ------------------------------------------------------------------------
    // setTimeInMillis resolution.
    // ------------------------------------------------------------------------

    @Test
    fun setTimeInMillis_primitiveLongAccepted() {
        val cap = ClockResolver.resolveCalendarFromDeclaredType(
            FakeCalendarBase::class.java,
            Context::class.java,
        )

        assertNotNull(cap)
        assertSame(Long::class.javaPrimitiveType, cap!!.setTimeInMillisMethod.parameterTypes[0])
    }

    @Test
    fun setTimeInMillis_longWrapperRejected() {
        val cap = ClockResolver.resolveCalendarFromDeclaredType(
            FakeCalendarWrapperLong::class.java,
            Context::class.java,
        )

        assertNull(cap)
    }

    // ------------------------------------------------------------------------
    // format resolution.
    // ------------------------------------------------------------------------

    @Test
    fun format_stringBuilderExactAccepted() {
        val cap = ClockResolver.resolveCalendarFromDeclaredType(
            FakeCalendarFormatVariants::class.java,
            Context::class.java,
        )

        assertNotNull(cap)
        assertEquals(StringBuilder::class.java, cap!!.formatMethod.parameterTypes[1])
        assertEquals(StringBuilder::class.java, cap.formatMethod.parameterTypes[2])
    }

    @Test
    fun format_appendableCompatibleAccepted() {
        val cap = ClockResolver.resolveCalendarFromDeclaredType(
            FakeCalendarFormatAppendable::class.java,
            Context::class.java,
        )

        assertNotNull(cap)
        assertEquals(Appendable::class.java, cap!!.formatMethod.parameterTypes[1])
    }

    @Test
    fun format_charSequenceCompatibleAccepted() {
        val cap = ClockResolver.resolveCalendarFromDeclaredType(
            FakeCalendarFormatCharSequence::class.java,
            Context::class.java,
        )

        assertNotNull(cap)
        assertEquals(CharSequence::class.java, cap!!.formatMethod.parameterTypes[1])
    }

    @Test
    fun format_stringBufferOnlyRejected() {
        val cap = ClockResolver.resolveCalendarFromDeclaredType(
            FakeCalendarFormatStringBuffer::class.java,
            Context::class.java,
        )

        assertNull(cap)
    }

    @Test
    fun format_mostSpecificSelectsNarrowerStringBuilder() {
        val cap = ClockResolver.resolveCalendarFromDeclaredType(
            FakeCalendarFormatMostSpecific::class.java,
            Context::class.java,
        )

        assertNotNull(cap)
        assertEquals(StringBuilder::class.java, cap!!.formatMethod.parameterTypes[1])
    }

    @Test
    fun format_incomparableAppendableAndCharSequenceIsAmbiguous() {
        val cap = ClockResolver.resolveCalendarFromDeclaredType(
            FakeCalendarFormatAmbiguous::class.java,
            Context::class.java,
        )

        assertNull(cap)
    }

    @Test
    fun format_returnTypeDoesNotControlSelection() {
        val cap = ClockResolver.resolveCalendarFromDeclaredType(
            FakeCalendarFormatReturnsString::class.java,
            Context::class.java,
        )

        assertNotNull(cap)
        assertEquals(String::class.java, cap!!.formatMethod.returnType)
    }

    // ------------------------------------------------------------------------
    // Runtime fallback.
    // ------------------------------------------------------------------------

    @Test
    fun resolveCalendarFromRuntime_subclassSuppliesMissingFormat() {
        val instance = FakeCalendarChildWithFormat()
        val cap = ClockResolver.resolveCalendarFromRuntime(
            instance,
            FakeContextForResolver::class.java,
        )

        assertNotNull(cap)
        assertEquals(FakeCalendarChildWithFormat::class.java, cap!!.calendarClass)
        assertEquals("format", cap.formatMethod.name)
    }

    @Test
    fun resolveCalendarFromRuntime_doesNotRetainInstance() {
        val instance = FakeCalendarBase()
        val cap = ClockResolver.resolveCalendarFromRuntime(
            instance,
            FakeContextForResolver::class.java,
        )

        assertNotNull(cap)
        assertFalse(cap!!.calendarClass.isInstance(Any()))
    }

    // ------------------------------------------------------------------------
    // Core resolve on system classloader.
    // ------------------------------------------------------------------------

    @Test
    fun resolveCore_systemClassLoader_unknownRomClasses_returnsNull() {
        val abi = ClockResolver.resolveCore(javaClass.classLoader!!)
        assertNull(abi)
    }

    // ------------------------------------------------------------------------
    // Fake classes.
    // ------------------------------------------------------------------------

    open class FakeControllerBase {
        @JvmField
        var mCalendar: Any = FakeCalendarBase()

        @JvmField
        var mClockListeners: ArrayList<Any> = ArrayList()

        @JvmField
        var mIs24: Boolean = false
    }

    class FakeControllerChild : FakeControllerBase()

    class FakeControllerWithBoxedIs24 {
        @JvmField
        var mCalendar: Any = FakeCalendarBase()

        @JvmField
        var mClockListeners: ArrayList<Any> = ArrayList()

        @JvmField
        var mIs24: Boolean? = false
    }

    open class FakeClockBase {
        @JvmField
        var mMiuiStatusBarClockController: Any = FakeControllerBase()

        open fun updateTime() {}
    }

    open class FakeMiuiClock : FakeClockBase() {
        override fun updateTime() {}
    }

    open class FakeMiuiStatusBarClock : FakeMiuiClock() {
        override fun updateTime() {}
    }

    class FakeMiuiClockWithInheritedUpdate : FakeClockBase()

    open class FakeCalendarBase {
        fun setTimeInMillis(millis: Long) {}
        fun format(ctx: Context, out: StringBuilder, pattern: StringBuilder) {}
    }

    open class FakeCalendarNoFormat {
        fun setTimeInMillis(millis: Long) {}
    }

    open class FakeCalendarNoSetTime {
        fun format(ctx: Context, out: StringBuilder, pattern: StringBuilder) {}
    }

    open class FakeCalendarWrapperLong {
        fun setTimeInMillis(millis: Long?) {}
        fun format(ctx: Context, out: StringBuilder, pattern: StringBuilder) {}
    }

    open class FakeCalendarFormatVariants {
        fun setTimeInMillis(millis: Long) {}
        fun format(ctx: Context, out: StringBuilder, pattern: StringBuilder) {}
    }

    open class FakeCalendarFormatAppendable {
        fun setTimeInMillis(millis: Long) {}
        fun format(ctx: Context, out: Appendable, pattern: StringBuilder) {}
    }

    open class FakeCalendarFormatCharSequence {
        fun setTimeInMillis(millis: Long) {}
        fun format(ctx: Context, out: CharSequence, pattern: StringBuilder) {}
    }

    open class FakeCalendarFormatStringBuffer {
        fun setTimeInMillis(millis: Long) {}
        fun format(ctx: Context, out: StringBuffer, pattern: StringBuffer) {}
    }

    open class FakeCalendarFormatMostSpecific {
        fun setTimeInMillis(millis: Long) {}
        fun format(ctx: Context, out: StringBuilder, pattern: StringBuilder) {}
        fun format(ctx: Context, out: CharSequence, pattern: StringBuilder) {}
    }

    open class FakeCalendarFormatAmbiguous {
        fun setTimeInMillis(millis: Long) {}
        fun format(ctx: Context, out: Appendable, pattern: StringBuilder) {}
        fun format(ctx: Context, out: CharSequence, pattern: StringBuilder) {}
    }

    open class FakeCalendarFormatReturnsString {
        fun setTimeInMillis(millis: Long) {}
        fun format(ctx: Context, out: StringBuilder, pattern: StringBuilder): String = ""
    }

    open class FakeCalendarNoFormatBase {
        fun setTimeInMillis(millis: Long) {}
    }

    class FakeCalendarChildWithFormat : FakeCalendarNoFormatBase() {
        fun format(ctx: Context, out: StringBuilder, pattern: StringBuilder) {}
    }

    class FakeContextForResolver : ContextWrapper(null)
}
