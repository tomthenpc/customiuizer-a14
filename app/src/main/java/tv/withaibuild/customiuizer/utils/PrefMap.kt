package tv.withaibuild.customiuizer.utils

import java.util.Collections
import java.util.concurrent.ConcurrentHashMap

/**
 * Process-local preference snapshot used by hook callbacks.
 *
 * Remote preferences use `pref_key_` names. They are normalized once when inserted so
 * hot hook paths can read their short, source-level keys without allocating a prefixed String on
 * every invocation. ConcurrentHashMap also provides visibility between the preference listener
 * and callbacks running on binder, UI, and system-server threads.
 */
class PrefMap : ConcurrentHashMap<String, Any>() {

    private companion object {
        const val STORAGE_PREFIX = "pref_key_"
    }

    /** A parse result. [value] is null when [raw] could not be read as an integer. */
    private data class CachedInt(val raw: String, val value: Int?)

    private val parsedIntCache = ConcurrentHashMap<String, CachedInt>()

    private fun normalizeStorageKey(key: String): String {
        return if (key.startsWith(STORAGE_PREFIX)) key.substring(STORAGE_PREFIX.length) else key
    }

    private fun getValue(key: String): Any? {
        return get(if (key.startsWith(STORAGE_PREFIX)) normalizeStorageKey(key) else key)
    }

    override fun put(key: String, value: Any): Any? {
        val normalized = normalizeStorageKey(key)
        parsedIntCache.remove(normalized)
        return super.put(normalized, value)
    }

    override fun putAll(from: Map<out String, Any>) {
        from.forEach { put(it.key, it.value) }
    }

    override fun remove(key: String): Any? {
        val normalized = normalizeStorageKey(key)
        parsedIntCache.remove(normalized)
        return super.remove(normalized)
    }

    fun getInt(key: String, defaultValue: Int): Int {
        val value = getValue(key)
        return value as? Int ?: defaultValue
    }

    fun getLong(key: String, defaultValue: Long): Long {
        val value = getValue(key)
        return value as? Long ?: defaultValue
    }

    fun getString(key: String, defaultValue: String): String {
        val value = getValue(key)
        return value as? String ?: defaultValue
    }

    /**
     * Reads a list preference, which the framework stores as a String.
     *
     * Every caller of this runs inside a hook, most of them in SystemUI or system_server,
     * and most of them at process start while deciding which hooks to install. It therefore
     * must not throw for any stored value: a restored backup from an older build, a key
     * whose type changed between releases, or a String that is simply not a number used to
     * escape as ClassCastException or NumberFormatException and take the host process with
     * it. Anything unreadable is the caller's [defaultValue] instead.
     *
     * A failed parse is cached like a successful one so a bad value costs one parse rather
     * than one per read on a two-second ticker.
     */
    fun getStringAsInt(key: String, defaultValue: Int): Int {
        val value = getValue(key) ?: return defaultValue
        if (value is Number) return value.toInt()
        if (value !is String) return defaultValue

        val normalized = normalizeStorageKey(key)
        val cached = parsedIntCache[normalized]
        if (cached != null && cached.raw == value) return cached.value ?: defaultValue

        val parsed = value.toIntOrNull()
        parsedIntCache[normalized] = CachedInt(value, parsed)
        return parsed ?: defaultValue
    }

    @Suppress("UNCHECKED_CAST")
    fun getStringSet(key: String): Set<String> {
        val value = getValue(key)
        return value as? Set<String> ?: Collections.emptySet()
    }

    @JvmOverloads
    fun getBoolean(key: String, defaultValue: Boolean = false): Boolean {
        val value = getValue(key)
        return value as? Boolean ?: defaultValue
    }
}
