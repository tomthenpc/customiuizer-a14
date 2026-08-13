package tv.withaibuild.customiuizer.utils

import androidx.core.os.LocaleListCompat
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The language setting is a non-core feature, so an install that never uses it must not
 * pay for it on every application start. `apply()` runs from `MainApplication.onCreate`;
 * with the user on `auto` and nothing ever applied by us, it must not reach LocaleManager.
 *
 * The correctness constraint these pin down is the other half: the fast path must never
 * strand a user who switches an explicit language back to `auto`, because that switch is
 * exactly the case where the framework locale still has to be cleared.
 */
class AppLocaleFastPathTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private var originalDefaultLocale: Locale = Locale.getDefault()
    private val appliedLocaleLists = ArrayList<LocaleListCompat?>()
    private var currentApplicationLocales: LocaleListCompat = LocaleListCompat.getEmptyLocaleList()
    private var providerQueries = 0

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        originalDefaultLocale = Locale.getDefault()
        appliedLocaleLists.clear()
        currentApplicationLocales = LocaleListCompat.getEmptyLocaleList()
        providerQueries = 0
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalDefaultLocale)
        appliedLocaleLists.clear()
    }

    private fun markerValue(): String? =
        fakePrefs.getString(AppLocaleController.APPLIED_LOCALE_PREF_KEY, null)

    private fun runApply(
        prefs: FakeSharedPreferences = fakePrefs,
        applier: ((LocaleListCompat) -> Unit)? = { appliedLocaleLists.add(it) },
    ): Boolean = AppLocaleController.apply(
        prefs,
        applier = applier,
        provider = { providerQueries++; currentApplicationLocales },
    )

    @Test
    fun untouchedInstallNeverQueriesTheFrameworkLocale() {
        val changed = runApply()

        assertFalse(changed)
        assertEquals("the framework locale must not be read at all", 0, providerQueries)
        assertTrue(appliedLocaleLists.isEmpty())
        assertNull("no marker should be written", markerValue())
    }

    @Test
    fun untouchedInstallStaysOnTheFastPathAcrossRestarts() {
        repeat(5) { runApply() }

        assertEquals(0, providerQueries)
    }

    @Test
    fun explicitLocaleIsAppliedAndRecorded() {
        AppLocaleController.setUserLocale(fakePrefs, "zh-CN")

        val changed = runApply()

        assertTrue(changed)
        assertEquals(1, appliedLocaleLists.size)
        assertEquals("zh-CN", markerValue())
    }

    @Test
    fun explicitLocaleStillReconcilesOnEveryStart() {
        // The chosen trade-off: the cost is paid only while the feature is switched on.
        AppLocaleController.setUserLocale(fakePrefs, "en")
        runApply()
        currentApplicationLocales = appliedLocaleLists.last() ?: LocaleListCompat.getEmptyLocaleList()
        val queriesAfterFirstStart = providerQueries

        runApply()

        assertTrue("an explicit locale keeps reconciling", providerQueries > queriesAfterFirstStart)
    }

    @Test
    fun revertingToAutoClearsTheFrameworkLocaleAndTheMarker() {
        AppLocaleController.setUserLocale(fakePrefs, "en")
        runApply()
        currentApplicationLocales = appliedLocaleLists.last() ?: LocaleListCompat.getEmptyLocaleList()
        appliedLocaleLists.clear()

        AppLocaleController.setUserLocale(fakePrefs, "auto")
        val changed = runApply()

        assertTrue("switching back to auto must clear the framework locale", changed)
        assertEquals(1, appliedLocaleLists.size)
        assertTrue("auto clears the list", appliedLocaleLists[0]?.isEmpty ?: false)
        assertNull("the marker is dropped once the system is back in control", markerValue())
    }

    @Test
    fun revertingToAutoRestoresTheFastPath() {
        AppLocaleController.setUserLocale(fakePrefs, "en")
        runApply()
        AppLocaleController.setUserLocale(fakePrefs, "auto")
        runApply()

        currentApplicationLocales = LocaleListCompat.getEmptyLocaleList()
        val queriesBefore = providerQueries
        appliedLocaleLists.clear()

        runApply()

        assertEquals("back to zero cost", queriesBefore, providerQueries)
        assertTrue(appliedLocaleLists.isEmpty())
    }

    @Test
    fun installPredatingTheMarkerIsMigratedAndCanStillRevert() {
        // An install upgraded from a build without the marker: an explicit locale is
        // already stored and already in force, so apply() finds nothing to change.
        fakePrefs.put(AppLocaleController.LOCALE_PREF_KEY, "zh-CN")
        currentApplicationLocales = AppLocaleController.toLocaleListCompat("zh-CN")

        val changed = runApply()

        assertFalse("nothing to apply", changed)
        assertEquals("but the marker must be backfilled", "zh-CN", markerValue())

        // Without that backfill this switch would take the fast path and leave the user
        // stuck in Chinese for good.
        AppLocaleController.setUserLocale(fakePrefs, "auto")
        val reverted = runApply()

        assertTrue(reverted)
        assertTrue(appliedLocaleLists.last()?.isEmpty ?: false)
        assertNull(markerValue())
    }

    @Test
    fun aRestoredBackupForcesOneFullReconcile() {
        // This device has an explicit locale in force; the restored backup is on auto and
        // wiped the marker, so only the invalidation keeps the clear from being skipped.
        currentApplicationLocales = AppLocaleController.toLocaleListCompat("zh-CN")
        AppLocaleController.invalidateFastPath(fakePrefs)

        val changed = runApply()

        assertTrue("the restored auto setting must reach the framework", changed)
        assertTrue(appliedLocaleLists.last()?.isEmpty ?: false)
        assertNull("and the shortcut comes back afterwards", markerValue())
    }

    @Test
    fun invalidationIsNotMistakenForAnAppliedTag() {
        AppLocaleController.invalidateFastPath(fakePrefs)
        fakePrefs.put(AppLocaleController.LOCALE_PREF_KEY, "en")

        runApply()

        assertEquals("en", markerValue())
    }

    @Test
    fun aFailedFrameworkWriteDoesNotDropTheMarker() {
        AppLocaleController.setUserLocale(fakePrefs, "en")
        runApply()
        currentApplicationLocales = appliedLocaleLists.last() ?: LocaleListCompat.getEmptyLocaleList()

        // No applier and no Context: apply() cannot reach the framework and bails out.
        AppLocaleController.setUserLocale(fakePrefs, "auto")
        AppLocaleController.apply(
            fakePrefs,
            applier = null,
            provider = { providerQueries++; currentApplicationLocales },
        )

        assertEquals(
            "the marker must survive so the next start retries the clear",
            "en",
            markerValue()
        )
    }
}
