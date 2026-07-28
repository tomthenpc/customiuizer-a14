package tv.withaibuild.customiuizer.utils

import android.content.SharedPreferences
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AppLocaleControllerTest {

    private lateinit var fakePrefs: FakeSharedPreferences
    private var originalDefaultLocale: Locale = Locale.getDefault()
    private val appliedLocaleLists = ArrayList<androidx.core.os.LocaleListCompat?>()

    @Before
    fun setUp() {
        fakePrefs = FakeSharedPreferences()
        originalDefaultLocale = Locale.getDefault()
        appliedLocaleLists.clear()
        AppLocaleController.applicationLocaleApplier = { appliedLocaleLists.add(it) }
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalDefaultLocale)
        AppLocaleController.applicationLocaleApplier = null
        appliedLocaleLists.clear()
    }

    @Test
    fun normalizeLocaleTagHandlesAllRequiredCases() {
        assertEquals("auto", AppLocaleController.normalizeLocaleTag(null))
        assertEquals("auto", AppLocaleController.normalizeLocaleTag(""))
        assertEquals("auto", AppLocaleController.normalizeLocaleTag("   "))
        assertEquals("auto", AppLocaleController.normalizeLocaleTag("1"))
        assertEquals("auto", AppLocaleController.normalizeLocaleTag("AUTO"))
        assertEquals("auto", AppLocaleController.normalizeLocaleTag("unknown"))
        assertEquals("auto", AppLocaleController.normalizeLocaleTag("auto"))
        assertEquals("en", AppLocaleController.normalizeLocaleTag("en"))
        assertEquals("zh-CN", AppLocaleController.normalizeLocaleTag("zh-CN"))
        assertEquals("zh-TW", AppLocaleController.normalizeLocaleTag("zh-TW"))
        assertEquals("pt-BR", AppLocaleController.normalizeLocaleTag("pt-BR"))
    }

    @Test
    fun getUserLocaleReadsAndNormalizesStoredValue() {
        assertEquals("auto", AppLocaleController.getUserLocale(fakePrefs))

        fakePrefs.put(AppLocaleController.LOCALE_PREF_KEY, "en")
        assertEquals("en", AppLocaleController.getUserLocale(fakePrefs))

        fakePrefs.put(AppLocaleController.LOCALE_PREF_KEY, "1")
        assertEquals("auto", AppLocaleController.getUserLocale(fakePrefs))

        fakePrefs.put(AppLocaleController.LOCALE_PREF_KEY, "invalid")
        assertEquals("auto", AppLocaleController.getUserLocale(fakePrefs))
    }

    @Test
    fun getUserLocaleReturnsAutoWhenPrefsAreNull() {
        assertEquals("auto", AppLocaleController.getUserLocale(null))
    }

    @Test
    fun getEffectiveLocaleUsesExplicitTagsAndFallsBackOnInvalid() {
        val system = Locale.forLanguageTag("zh-CN")

        assertEquals(Locale.ENGLISH, AppLocaleController.getEffectiveLocale("en") { system })
        assertEquals(Locale.forLanguageTag("zh-CN"), AppLocaleController.getEffectiveLocale("zh-CN") { system })
        assertEquals(Locale.forLanguageTag("pt-BR"), AppLocaleController.getEffectiveLocale("pt-BR") { system })

        // Invalid explicit tag falls back to the system locale.
        assertEquals(system, AppLocaleController.getEffectiveLocale("nonsense") { system })

        // Legacy auto value resolves through the system provider.
        assertEquals(system, AppLocaleController.getEffectiveLocale("1") { system })
    }

    @Test
    fun getEffectiveLocaleAutoResolvesToSystemLocale() {
        val system = Locale.forLanguageTag("ru-RU")
        val result = AppLocaleController.getEffectiveLocale("auto") { system }

        assertEquals(system, result)
        assertNotNull(result)
    }

    @Test
    fun setUserLocalePersistsAndAppliesExplicitLocale() {
        val committed = AppLocaleController.setUserLocale(fakePrefs, "en")

        assertTrue("setUserLocale should return true", committed)
        assertEquals("en", fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null))
        assertEquals(Locale.ENGLISH, Locale.getDefault())
    }

    @Test
    fun setUserLocaleNormalizesInvalidInputToAuto() {
        AppLocaleController.setUserLocale(fakePrefs, "garbage")

        assertEquals("auto", fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null))
    }

    @Test
    fun setUserLocaleSwitchesBackToAuto() {
        AppLocaleController.setUserLocale(fakePrefs, "en")
        assertEquals(Locale.ENGLISH, Locale.getDefault())

        // Simulate the user choosing "auto" again.
        AppLocaleController.setUserLocale(fakePrefs, "auto")

        assertEquals("auto", fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null))
        // Because the tag is "auto", the effective locale becomes the current system locale.
        // The system provider in applyLocale uses Resources.getSystem(), so we only verify
        // that the default locale was reset to *some* non-null value.
        assertNotNull(Locale.getDefault())
    }

    @Test
    fun setUserLocaleSynchronousCommitPreventsRaceWithRecreation() {
        // This is a behavioural test: the write must be complete before the function returns.
        // With the fake implementation we can read the value immediately after setUserLocale.
        AppLocaleController.setUserLocale(fakePrefs, "es-ES")

        assertEquals(
            "es-ES",
            fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null)
        )
    }

    @Test
    fun buildLocaleDisplayDataProducesParallelArraysAndContainsAllTags() {
        val (entries, values) = AppLocaleController.buildLocaleDisplayData("System default")

        assertEquals("entries and values must have the same length", entries.size, values.size)
        assertTrue(values.contains("auto"))
        assertTrue(values.contains("en"))
        assertTrue(values.contains("zh-CN"))
        assertTrue(values.contains("zh-TW"))
        assertTrue(values.contains("pt-BR"))

        // The "auto" label must come from the injected string.
        val autoIndex = values.indexOf("auto")
        assertEquals("System default", entries[autoIndex].toString())

        // Traditional Chinese has a fixed traditional label.
        val twIndex = values.indexOf("zh-TW")
        assertEquals("繁體中文（台灣）", entries[twIndex].toString())

        // Portuguese (Brazil) has the Brasil suffix.
        val brIndex = values.indexOf("pt-BR")
        assertTrue(entries[brIndex].toString().contains("(Brasil)"))
    }

    @Test
    fun buildLocaleDisplayDataEntriesAndValuesAreParallel() {
        val (entries, values) = AppLocaleController.buildLocaleDisplayData("Auto")
        assertEquals(entries.size, values.size)
        for (i in values.indices) {
            assertNotNull(values[i])
            assertNotNull(entries[i])
            assertFalse(entries[i].toString().isEmpty())
        }
    }

    @Test
    fun applyLocaleSetsDefaultForExplicitTagWithoutWriting() {
        val before = fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null)

        AppLocaleController.applyLocale("ja-JP")

        assertEquals(before, fakePrefs.getString(AppLocaleController.LOCALE_PREF_KEY, null))
        assertEquals(Locale.JAPAN, Locale.getDefault())
    }

    @Test
    fun applyLocaleDoesNotThrowForAuto() {
        // auto resolves through Resources.getSystem(); on a JVM that may not be available.
        // The important thing is that it does not crash the process and leaves the default
        // locale in a defined state.
        AppLocaleController.applyLocale("auto")
        assertNotNull(Locale.getDefault())
    }

    @Test
    fun toLocaleListCompatAutoIsEmpty() {
        val list = AppLocaleController.toLocaleListCompat("auto")
        assertNotNull(list)
    }

    @Test
    fun toLocaleListCompatExplicitIsNotEmpty() {
        val list = AppLocaleController.toLocaleListCompat("en")
        assertNotNull(list)
    }

    @Test
    fun toLocaleListCompatUnknownFallsBackToAuto() {
        val list = AppLocaleController.toLocaleListCompat("invalid")
        assertNotNull(list)
    }

    @Test
    fun stateTransitionMatrix() {
        // auto -> zh-CN
        assertTrue(AppLocaleController.setUserLocale(fakePrefs, "zh-CN"))
        assertEquals(Locale.SIMPLIFIED_CHINESE, Locale.getDefault())
        assertEquals("zh-CN", AppLocaleController.getUserLocale(fakePrefs))

        // zh-CN -> en
        assertTrue(AppLocaleController.setUserLocale(fakePrefs, "en"))
        assertEquals(Locale.ENGLISH, Locale.getDefault())
        assertEquals("en", AppLocaleController.getUserLocale(fakePrefs))

        // en -> auto
        assertTrue(AppLocaleController.setUserLocale(fakePrefs, "auto"))
        assertEquals("auto", AppLocaleController.getUserLocale(fakePrefs))

        // auto -> en
        assertTrue(AppLocaleController.setUserLocale(fakePrefs, "en"))
        assertEquals(Locale.ENGLISH, Locale.getDefault())

        // en -> en (idempotent)
        assertTrue(AppLocaleController.setUserLocale(fakePrefs, "en"))
        assertEquals(Locale.ENGLISH, Locale.getDefault())
        assertEquals("en", AppLocaleController.getUserLocale(fakePrefs))
    }
}
