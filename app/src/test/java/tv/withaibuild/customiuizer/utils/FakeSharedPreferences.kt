package tv.withaibuild.customiuizer.utils

import android.content.SharedPreferences

class FakeSharedPreferences : SharedPreferences {

    private val values = HashMap<String, Any?>()

    /** Set to false to simulate a failing [commit]/[apply]. */
    var commitShouldSucceed = true

    /** Optional sequence of commit results. If set, each call to [commit] consumes one value. */
    var commitSequence: Iterator<Boolean>? = null

    fun put(key: String, value: Any?) {
        values[key] = value
    }

    override fun getAll(): Map<String, *> = HashMap(values)

    override fun getString(key: String, defValue: String?): String? {
        val v = values[key]
        return if (v is String) v else defValue
    }

    override fun getStringSet(key: String, defValues: Set<String>?): Set<String>? {
        val v = values[key]
        @Suppress("UNCHECKED_CAST")
        return if (v is Set<*>) v as Set<String> else defValues
    }

    override fun getInt(key: String, defValue: Int): Int {
        val v = values[key]
        return if (v is Int) v else defValue
    }

    override fun getLong(key: String, defValue: Long): Long {
        val v = values[key]
        return if (v is Long) v else defValue
    }

    override fun getFloat(key: String, defValue: Float): Float {
        val v = values[key]
        return if (v is Float) v else defValue
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        val v = values[key]
        return if (v is Boolean) v else defValue
    }

    override fun contains(key: String): Boolean = values.containsKey(key)

    override fun edit(): SharedPreferences.Editor = FakeEditor(values)

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}

    inner class FakeEditor(private val values: MutableMap<String, Any?>) : SharedPreferences.Editor {

        private val staging = HashMap<String, Any?>()
        private var clearOnApply = false

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            if (value == null) {
                staging[key] = RemoveMarker
            } else {
                staging[key] = value
            }
            return this
        }

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            if (values == null) {
                staging[key] = RemoveMarker
            } else {
                staging[key] = values
            }
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            staging[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            staging[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            staging[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            staging[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            staging[key] = RemoveMarker
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            clearOnApply = true
            staging.clear()
            return this
        }

        override fun commit(): Boolean {
            val sequence = this@FakeSharedPreferences.commitSequence
            val shouldSucceed = if (sequence != null && sequence.hasNext()) {
                sequence.next()
            } else {
                this@FakeSharedPreferences.commitShouldSucceed
            }
            if (!shouldSucceed) return false
            applyStaged()
            return true
        }

        override fun apply() {
            applyStaged()
        }

        private fun applyStaged() {
            if (clearOnApply) values.clear()
            for ((key, value) in staging) {
                if (value === RemoveMarker) {
                    values.remove(key)
                } else {
                    values[key] = value
                }
            }
            staging.clear()
            clearOnApply = false
        }
    }

    private companion object {
        private val RemoveMarker = Any()
    }
}
