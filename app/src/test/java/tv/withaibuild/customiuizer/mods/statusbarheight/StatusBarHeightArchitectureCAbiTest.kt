package tv.withaibuild.customiuizer.mods.statusbarheight

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusBarHeightArchitectureCAbiTest {

    @Test
    fun selectTypeEncoding_modernPublic_withIdTypeConstructorAndPublicStatus() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = false,
            hasIdTypeConstructor = true,
            hasGetId = true,
            hasGetType = true,
            legacyStatusType = null,
            legacyNavigationType = null,
            publicStatusType = 1,
            publicNavigationType = 2,
            publicDisplayCutoutType = 128,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.MODERN_PUBLIC, info.encoding)
        assertEquals(1, info.statusBarType)
        assertEquals(2, info.navigationType)
        assertEquals(128, info.displayCutoutType)
    }

    @Test
    fun selectTypeEncoding_legacyInternal_withOneIntConstructorAndLegacyConstants() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = true,
            hasIdTypeConstructor = false,
            hasGetId = false,
            hasGetType = true,
            legacyStatusType = 0,
            legacyNavigationType = 1,
            publicStatusType = null,
            publicNavigationType = null,
            publicDisplayCutoutType = null,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.LEGACY_INTERNAL, info.encoding)
        assertEquals(0, info.statusBarType)
        assertEquals(1, info.navigationType)
    }

    @Test
    fun selectTypeEncoding_unsupported_whenNoValidEncoding() {
        val abi = InsetsSourceAbi(
            hasOneIntConstructor = false,
            hasIdTypeConstructor = false,
            hasGetId = false,
            hasGetType = false,
            legacyStatusType = null,
            legacyNavigationType = null,
            publicStatusType = null,
            publicNavigationType = null,
            publicDisplayCutoutType = null,
        )

        val info = StatusBarHeightResolver.selectTypeEncoding(abi)

        assertEquals(InsetsTypeEncoding.UNSUPPORTED, info.encoding)
        assertEquals(InsetsTypeInfo.TYPE_UNRESOLVED, info.statusBarType)
    }

    @Test
    fun resolveIntField_findsIntFieldByName() {
        val field = StatusBarHeightResolver.resolveIntField(FakeInsetsSource::class.java, "mType")

        assertNotNull(field)
        assertEquals("mType", field!!.name)
        assertEquals(Int::class.javaPrimitiveType, field.type)
    }

    @Test
    fun resolveIntField_returnsNullForWrongType() {
        val field = StatusBarHeightResolver.resolveIntField(FakeInsetsSource::class.java, "mName")

        assertNull(field)
    }

    @Test
    fun resolveDeclaredField_traversesSuperclass() {
        val field = StatusBarHeightResolver.resolveDeclaredField(FakeInsetsSourceChild::class.java, "mType")

        assertNotNull(field)
        assertEquals("mType", field!!.name)
    }

    @Test
    fun resolveCore_onSystemClassLoader_returnsFailClosedCapabilities() {
        val abi = StatusBarHeightResolver.resolveCore(javaClass.classLoader!!)

        assertFalse(abi.insets.coreSupported)
        assertEquals(InsetsTypeEncoding.UNSUPPORTED, abi.insets.typeInfo.encoding)
        // In a unit-test ClassLoader the fake com.android.server.wm.WindowState may exist,
        // so the WMS capability is non-null but client frames may be missing.
        assertNull(abi.decorInsets.infoClass)
    }

    @Test
    fun insetsCapability_typeFieldPreferred_overGetTypeMethod() {
        val cap = StatusBarHeightResolver.resolveInsetsSourceClass(FakeInsetsSource::class.java, javaClass.classLoader!!)

        assertNotNull(cap.typeField)
        assertNull(cap.getTypeMethod)
        assertEquals(InsetsTypeEncoding.UNSUPPORTED, cap.typeInfo.encoding)
    }

    @Test
    fun insetsCapability_mTypeMissing_fallsBackToGetTypeMethod() {
        val cap = StatusBarHeightResolver.resolveInsetsSourceClass(FakeInsetsSourceNoField::class.java, javaClass.classLoader!!)

        assertNull(cap.typeField)
        assertNotNull(cap.getTypeMethod)
    }

    @Test
    fun insetsCapability_noMTypeAndNoGetType_unsupported() {
        val cap = StatusBarHeightResolver.resolveInsetsSourceClass(FakeInsetsSourceNoAccess::class.java, javaClass.classLoader!!)

        assertNull(cap.typeField)
        assertNull(cap.getTypeMethod)
        assertEquals(InsetsTypeEncoding.UNSUPPORTED, cap.typeInfo.encoding)
    }

    @Test
    fun windowManagerCapability_optionalMembersAreNullable() {
        val cap = StatusBarHeightResolver.resolveWindowManagerClass(FakeWmsWithClientFrames::class.java, FakeLayoutParams::class.java)

        assertNotNull(cap.windowStateClass)
        assertNotNull(cap.clientWindowFramesClass)
        assertNotNull(cap.clientWindowFramesFrameField)
        assertNull(cap.windowStateGetFrameMethod)
        assertNull(cap.windowStateGetDisplayMetricsMethod)
    }

    @Test
    fun decorInsetsCapability_optionalDisplayMetricsMethodCanBeNull() {
        val cap = StatusBarHeightResolver.resolveDecorInsetsInfoClass(FakeDecorInsetsInfo::class.java, FakeDisplayContent::class.java)

        assertNotNull(cap.infoClass)
        assertNotNull(cap.updateMethod)
        assertNotNull(cap.nonDecorInsetsField)
        assertNotNull(cap.nonDecorFrameField)
    }

    @Test
    fun computeStatusBarFrameBottom_disabled_keepsOriginal() {
        val result = StatusBarHeightResolver.computeStatusBarFrameBottom(10, 100, 50, false)
        assertEquals(100, result)
    }

    @Test
    fun computeStatusBarFrameBottom_enabled_replacesBottom() {
        val result = StatusBarHeightResolver.computeStatusBarFrameBottom(10, 100, 50, true)
        assertEquals(60, result)
    }

    @Test
    fun computeNonDecorTop_enabled_returnsConfiguredPx() {
        val result = StatusBarHeightResolver.computeNonDecorTop(104, 50, true)
        assertEquals(50, result)
    }

    @Test
    fun computeNonDecorFrameTop_zeroOriginalInset_keepsFrame() {
        val result = StatusBarHeightResolver.computeNonDecorFrameTop(200, 0, 50, true)
        assertEquals(200, result)
    }

    @Test
    fun isStatusBarType_matchesStatusBarTypeOnly() {
        val typeInfo = InsetsTypeInfo(InsetsTypeEncoding.MODERN_PUBLIC, 1, 2, 128)
        assertTrue(StatusBarHeightResolver.isStatusBarType(1, typeInfo))
        assertFalse(StatusBarHeightResolver.isStatusBarType(2, typeInfo))
    }

    // ------------------------------------------------------------------------
    // Fake framework classes for cold resolver unit tests.
    // ------------------------------------------------------------------------

    class FakeInsetsSource {
        var mType: Int = 0
        var mName: String = "name"
    }

    open class FakeInsetsSourceBase {
        var mType: Int = 0
    }

    class FakeInsetsSourceChild : FakeInsetsSourceBase()

    class FakeInsetsSourceNoField {
        fun setFrame(rect: Rect) {}
        fun getType(): Int = 1
    }

    class FakeInsetsSourceNoAccess {
        fun setFrame(rect: Rect) {}
    }

    class ClientWindowFrames {
        @JvmField
        val frame: Rect = Rect()
    }

    class FakeWindowState {
        var mAttrs: FakeLayoutParams = FakeLayoutParams()
        fun setFrames(frames: ClientWindowFrames, something: Any?, something2: Any?) {}
    }

    class FakeWmsWithClientFrames {
        var mAttrs: FakeLayoutParams = FakeLayoutParams()
        fun setFrames(frames: ClientWindowFrames, something: Any?, something2: Any?) {}
    }

    class FakeLayoutParams {
        var type: Int = 0
        var height: Int = 0
        var packageName: String? = null
    }

    class FakeDecorInsetsInfo {
        var mNonDecorInsets: Rect = Rect()
        var mNonDecorFrame: Rect = Rect()
        fun update(content: Any?, rotation: Int, displayW: Int, displayH: Int) {}
    }

    class FakeDisplayContent
}
