package tv.withaibuild.customiuizer.utils

import android.content.SharedPreferences

class FakeSharedPreferences : SharedPreferences {

    private val values = HashMap<String, Any?>()

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

    class FakeEditor(private val values: MutableMap<String, Any?>) : SharedPreferences.Editor {

        override fun putString(key: String, value: String?): SharedPreferences.Editor {
            if (value != null) values[key] = value
            return this
        }

        override fun putStringSet(key: String, values: Set<String>?): SharedPreferences.Editor {
            if (values != null) this.values[key] = values
            return this
        }

        override fun putInt(key: String, value: Int): SharedPreferences.Editor {
            values[key] = value
            return this
        }

        override fun putLong(key: String, value: Long): SharedPreferences.Editor {
            values[key] = value
            return this
        }

        override fun putFloat(key: String, value: Float): SharedPreferences.Editor {
            values[key] = value
            return this
        }

        override fun putBoolean(key: String, value: Boolean): SharedPreferences.Editor {
            values[key] = value
            return this
        }

        override fun remove(key: String): SharedPreferences.Editor {
            values.remove(key)
            return this
        }

        override fun clear(): SharedPreferences.Editor {
            values.clear()
            return this
        }

        override fun commit(): Boolean = true

        override fun apply() {}
    }
}
