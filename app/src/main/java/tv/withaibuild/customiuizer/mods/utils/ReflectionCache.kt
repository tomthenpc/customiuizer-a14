package tv.withaibuild.customiuizer.mods.utils

import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * Class/method/dependency cache for hook setup.
 *
 * The cache is kept separate from [ModuleHelper] so the reflection bookkeeping does not grow the
 * helper into a kitchen-sink class.  All map values are non-null: a missing or null result is
 * represented by a sentinel object, which lets [ConcurrentHashMap] safely cache negative results
 * without throwing on null.
 */
object ReflectionCache {

    /** Sentinel for a dependency that resolved to null. */
    private object DependencyNotFound

    /** Sentinel for a class that could not be found in the supplied class loader. */
    private object ClassNotFound

    private data class ClassCacheKey(val classLoader: ClassLoader?, val className: String)

    private val missingClasses = ConcurrentHashMap<ClassCacheKey, Any>()
    private val depInstanceCache = ConcurrentHashMap<Class<*>, Any>()

    private val dependencyLock = Any()
    private var DependencyClass: Class<*>? = null
    private var DependencyGetMethod: Method? = null
    private var DependencyClassLoader: ClassLoader? = null

    /**
     * Returns a SystemUI [Dependency] instance for [className] in [classLoader].
     *
     * Results are cached by the resolved [Class] object.  A null result from [Dependency.get] is
     * cached as [DependencyNotFound] so a missing dependency is only looked up once per class.
     * A class that cannot be found is cached as [ClassNotFound] per class-loader/name pair, so the
     * same failed lookup is not repeated on every hook setup.
     */
    @JvmStatic
    fun getDepInstance(classLoader: ClassLoader?, className: String): Any? {
        val cacheKey = ClassCacheKey(classLoader, className)
        if (missingClasses[cacheKey] === ClassNotFound) return null

        val clazz = try {
            XposedHelpers.findClassIfExists(className, classLoader)
        } catch (t: Throwable) {
            XposedHelpers.log(t)
            null
        }

        if (clazz == null) {
            missingClasses[cacheKey] = ClassNotFound
            return null
        }

        val cached = depInstanceCache[clazz]
        if (cached === DependencyNotFound) return null
        if (cached != null) return cached

        synchronized(dependencyLock) {
            val cached2 = depInstanceCache[clazz]
            if (cached2 === DependencyNotFound) return null
            if (cached2 != null) return cached2

            if (DependencyClass == null || DependencyClassLoader != classLoader) {
                DependencyClass = try {
                    XposedHelpers.findClassIfExists("com.android.systemui.Dependency", classLoader)
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                    null
                }
                DependencyGetMethod = if (DependencyClass != null) {
                    try {
                        DependencyClass!!.getDeclaredMethod("get", Class::class.java).apply {
                            isAccessible = true
                        }
                    } catch (t: Throwable) {
                        XposedHelpers.log(t)
                        null
                    }
                } else {
                    null
                }
                DependencyClassLoader = classLoader
            }

            val dependencyClass = DependencyClass
            val getMethod = DependencyGetMethod
            if (dependencyClass == null || getMethod == null) {
                depInstanceCache[clazz] = DependencyNotFound
                return null
            }

            val instance = try {
                getMethod.invoke(null, clazz)
            } catch (t: Throwable) {
                XposedHelpers.log(t)
                null
            }

            depInstanceCache[clazz] = if (instance != null) instance else DependencyNotFound
            return instance
        }
    }
}
