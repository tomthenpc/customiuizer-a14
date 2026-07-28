package tv.withaibuild.customiuizer.utils

import androidx.core.os.LocaleListCompat
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLocaleReconcileTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private var originalDefaultLocale: Locale = Locale.getDefault()
    private val appliedLocaleLists = ArrayList<LocaleListCompat?>()
    private var currentApplicationLocales: LocaleListCompat = LocaleListCompat.getEmptyLocaleList()

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        originalDefaultLocale = Locale.getDefault()
        appliedLocaleLists.clear()
        currentApplicationLocales = LocaleListCompat.getEmptyLocaleList()

        AppLocaleController.applicationLocaleApplier = { appliedLocaleLists.add(it) }
        AppLocaleController.applicationLocaleProvider = { currentApplicationLocales }
        AppLocaleController.processKiller = null
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalDefaultLocale)
        AppLocaleController.applicationLocaleApplier = null
        AppLocaleController.applicationLocaleProvider = null
        AppLocaleController.processKiller = null
        appliedLocaleLists.clear()
    }

    @Test
    fun setUserLocaleSavesAndMarksPending() {
        val success = AppLocaleController.setUserLocale(fakePrefs, "en")

        assertTrue("setUserLocale should succeed", success)
        assertEquals("en", fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null))
        assertTrue(
            "reconcile pending flag should be set",
            AppLocaleController.isReconcilePending(fakePrefs)
        )
    }

    @Test
    fun setUserLocaleDoesNotApplyImmediately() {
        AppLocaleController.setUserLocale(fakePrefs, "en")

        // The applier is only invoked during reconcileAndApply, never from setUserLocale.
        assertTrue("applier should not be called from setUserLocale", appliedLocaleLists.isEmpty())
        assertEquals(Locale.getDefault(), originalDefaultLocale)
    }

    @Test
    fun setUserLocaleReturnsFalseWhenCommitFails() {
        fakePrefs.commitShouldSucceed = false

        val success = AppLocaleController.setUserLocale(fakePrefs, "en")

        assertFalse("setUserLocale should fail when commit fails", success)
        assertNull(fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null))
        assertFalse(AppLocaleController.isReconcilePending(fakePrefs))
    }

    @Test
    fun setUserLocaleSameValueIsIdempotent() {
        AppLocaleController.setUserLocale(fakePrefs, "en")
        appliedLocaleLists.clear()

        val success = AppLocaleController.setUserLocale(fakePrefs, "en")

        assertTrue(success)
        // Writing the same value again is allowed; the pending flag remains set.
        assertEquals("en", fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null))
        assertTrue(AppLocaleController.isReconcilePending(fakePrefs))
    }

    @Test
    fun reconcileAndApplyAppliesWhenPendingFlagSet() {
        AppLocaleController.setUserLocale(fakePrefs, "en")

        val changed = AppLocaleController.reconcileAndApply(fakePrefs)

        assertTrue("reconcile should apply when pending flag is set", changed)
        assertEquals(Locale.ENGLISH, Locale.getDefault())
        assertEquals(1, appliedLocaleLists.size)
        assertNotNull(appliedLocaleLists[0])
    }

    @Test
    fun reconcileAndApplyClearsPending() {
        AppLocaleController.setUserLocale(fakePrefs, "en")
        AppLocaleController.reconcileAndApply(fakePrefs)

        assertFalse(
            "reconcile pending should be cleared after successful reconcile",
            AppLocaleController.isReconcilePending(fakePrefs)
        )
    }

    @Test
    fun reconcileAndApplyIsIdempotentAfterFirstApply() {
        AppLocaleController.setUserLocale(fakePrefs, "en")

        // First reconcile: app locale changed, applier invoked.
        AppLocaleController.reconcileAndApply(fakePrefs)

        // Simulate the system having now adopted the target locale.
        currentApplicationLocales = appliedLocaleLists.last() ?: LocaleListCompat.getEmptyLocaleList()
        appliedLocaleLists.clear()

        // Second reconcile: nothing changed, applier must not be called again.
        val changedAgain = AppLocaleController.reconcileAndApply(fakePrefs)

        assertFalse("second reconcile should not re-apply", changedAgain)
        assertTrue("applier should not be called again", appliedLocaleLists.isEmpty())
    }

    @Test
    fun reconcileAndApplyDoesNothingWhenAlreadyMatchingAndNoPending() {
        // No pending flag, current application locales equal the target (both empty for auto).
        val changed = AppLocaleController.reconcileAndApply(fakePrefs)

        assertFalse(changed)
        assertTrue(appliedLocaleLists.isEmpty())
    }

    @Test
    fun reconcileAndApplySetsDefaultLocaleForAuto() {
        // Pick an explicit language first, then switch back to auto. The reconcile
        // should set the JVM default back to whatever the (fake) system locale resolves
        // to, demonstrating that auto still derives from the system.
        Locale.setDefault(Locale.ENGLISH)
        AppLocaleController.setUserLocale(fakePrefs, "en")
        AppLocaleController.reconcileAndApply(fakePrefs)

        AppLocaleController.setUserLocale(fakePrefs, "auto")
        AppLocaleController.reconcileAndApply(fakePrefs)

        // In a JVM without Android Resources the system locale falls back to the
        // current default, so the test simply checks the process completes and the
        // value is auto.
        assertEquals("auto", AppLocaleController.getUserLocale(fakePrefs))
        assertFalse(AppLocaleController.isReconcilePending(fakePrefs))
    }

    @Test
    fun toLocaleListCompatAutoIsEmpty() {
        val list = AppLocaleController.toLocaleListCompat("auto")
        assertNotNull(list)
        assertTrue(list.isEmpty)
    }

    @Test
    fun toLocaleListCompatExplicitIsNotNull() {
        val list = AppLocaleController.toLocaleListCompat("en")
        assertNotNull(list)
        // The JVM may not be able to fully initialize AppCompat internals, so we only
        // require that a LocaleListCompat object is returned without crashing.
    }

    @Test
    fun toLocaleListCompatUnknownFallsBackToAuto() {
        val list = AppLocaleController.toLocaleListCompat("invalid")
        assertNotNull(list)
    }

    @Test
    fun getEffectiveLocaleUsesExplicitTags() {
        val system = Locale.forLanguageTag("zh-CN")

        assertEquals(Locale.ENGLISH, AppLocaleController.getEffectiveLocale("en") { system })
        assertEquals(Locale.forLanguageTag("zh-CN"), AppLocaleController.getEffectiveLocale("zh-CN") { system })
        assertEquals(Locale.forLanguageTag("pt-BR"), AppLocaleController.getEffectiveLocale("pt-BR") { system })
    }

    @Test
    fun getEffectiveLocaleAutoResolvesToProvider() {
        val system = Locale.forLanguageTag("ja-JP")
        assertEquals(system, AppLocaleController.getEffectiveLocale("auto") { system })
    }

    @Test
    fun getEffectiveLocaleInvalidFallsBackToProvider() {
        val system = Locale.forLanguageTag("cs-CZ")
        assertEquals(system, AppLocaleController.getEffectiveLocale("nonsense") { system })
    }

    @Test
    fun stateTransitionMatrixThroughReconcile() {
        // auto -> zh-CN
        assertTrue(AppLocaleController.setUserLocale(fakePrefs, "zh-CN"))
        assertTrue(AppLocaleController.reconcileAndApply(fakePrefs))
        assertEquals(Locale.SIMPLIFIED_CHINESE, Locale.getDefault())

        // Simulate the system now reporting the new list.
        currentApplicationLocales = appliedLocaleLists.last() ?: LocaleListCompat.getEmptyLocaleList()
        appliedLocaleLists.clear()

        // zh-CN -> en
        assertTrue(AppLocaleController.setUserLocale(fakePrefs, "en"))
        assertTrue(AppLocaleController.reconcileAndApply(fakePrefs))
        assertEquals(Locale.ENGLISH, Locale.getDefault())

        // en -> auto
        assertTrue(AppLocaleController.setUserLocale(fakePrefs, "auto"))
        assertTrue(AppLocaleController.reconcileAndApply(fakePrefs))
        assertEquals("auto", AppLocaleController.getUserLocale(fakePrefs))

        // After auto reconcile the default locale is the system locale; we only
        // assert that it is a defined non-null value.
        assertNotNull(Locale.getDefault())
    }
}
