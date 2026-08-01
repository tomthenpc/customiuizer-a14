package tv.withaibuild.customiuizer.mods.utils

import java.lang.reflect.Method
import java.util.Collections
import java.util.LinkedHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Possible outcomes of a dependency lookup.
 *
 * All cache values are non-null so the underlying maps can store misses safely.
 * Only [ClassMissing] is considered permanently negative: a class that is not
 * present in a class loader will not appear later.  [MethodMissing] and
 * [DependencyNotReady] are tied to the current lifecycle and will be retried
 * after [ReflectionCache.onSafeLifecycle] is called at a safe boundary such as
 * SystemUIInitializer.init, Application init or PackageReady.
 */
internal sealed class DepResult {
    internal object ClassMissing : DepResult()
    internal data class MethodMissing(val clazz: Class<*>, val lifecycle: Long) : DepResult()
    internal data class DependencyNotReady(val clazz: Class<*>, val lifecycle: Long) : DepResult()
    internal data class DependencyFound(val value: Any, val clazz: Class<*>) : DepResult()
}

/**
 * State kept per [ClassLoader].
 *
 * The class loader key isolates the Dependency method and the per-class cache.
 * Both maps are bounded so a runaway hook setup cannot grow the cache without
 * limit.  Dependency instances are cached as strong references because they are
 * process-lifetime singletons in SystemUI / Launcher; the cache itself does not
 * hold short-lived objects such as Activity or View instances.
 */
internal class LoaderState {
    @JvmField
    internal var dependencyMethod: Method? = null

    @JvmField
    internal var dependencyMethodResolved: Boolean = false

    @JvmField
    internal val classResults: MutableMap<String, DepResult> = Collections.synchronizedMap(
        object : LinkedHashMap<String, DepResult>(ReflectionCache.MAX_CLASSES_PER_LOADER, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, DepResult>?): Boolean {
                return size > ReflectionCache.MAX_CLASSES_PER_LOADER
            }
        }
    )
}

/**
 * Class/method/dependency cache for hook setup.
 *
 * The cache is keyed by [ClassLoader] and then by class name.  Once a result is
 * cached, the hot path is a single map lookup and a `when` branch, with no
 * reflection and no short-lived key object allocation.
 */
object ReflectionCache {

    internal const val MAX_LOADERS = 4
    internal const val MAX_CLASSES_PER_LOADER = 64

    /** Name of the SystemUI Dependency class.  Overridable in tests. */
    @JvmField
    internal var dependencyClassName: String = "com.android.systemui.Dependency"

    private val lifecycle = AtomicLong(0L)

    private val loaderStates: MutableMap<ClassLoader?, LoaderState> = Collections.synchronizedMap(
        object : LinkedHashMap<ClassLoader?, LoaderState>(MAX_LOADERS, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<ClassLoader?, LoaderState>?): Boolean {
                return size > MAX_LOADERS
            }
        }
    )

    /**
     * Returns a SystemUI [Dependency] instance for [className] in [classLoader].
     *
     * Positive results are cached and returned without reflection on subsequent
     * calls.  Missing class/method results are cached per class loader.  A null
     * result from [Dependency.get] is stored as [DepResult.DependencyNotReady]
     * against the current lifecycle; it is retried only after a safe lifecycle
     * boundary has been signalled via [onSafeLifecycle].
     */
    @JvmStatic
    fun getDepInstance(classLoader: ClassLoader?, className: String): Any? {
        val loaderState = loaderStates[classLoader]
        if (loaderState != null) {
            return readResult(loaderState, classLoader, className)
        }
        return resolveNewLoader(classLoader, className)
    }

    /**
     * Signals that a safe lifecycle boundary has been reached.
     *
     * Dependency lookups that returned null or failed to find the Dependency
     * class before this boundary are now eligible for retry.  Callers should
     * invoke this from safe lifecycle points such as
     * [com.android.systemui.SystemUIInitializer.init] or Application init.
     *
     * @param classLoader the loader to refresh, or `null` to refresh all loaders.
     */
    @JvmStatic
    @JvmOverloads
    fun onSafeLifecycle(classLoader: ClassLoader? = null) {
        lifecycle.incrementAndGet()
        if (classLoader == null) {
            val states = synchronized(loaderStates) { ArrayList(loaderStates.values) }
            for (state in states) {
                resetLoaderState(state)
            }
        } else {
            loaderStates[classLoader]?.let { resetLoaderState(it) }
        }
    }

    private fun resetLoaderState(loaderState: LoaderState) {
        synchronized(loaderState) {
            // A safe lifecycle is the right time to re-resolve the Dependency
            // method.  ClassMissing stays negative; MethodMissing and
            // DependencyNotReady will be re-evaluated by resolve() against the
            // new lifecycle.
            loaderState.dependencyMethod = null
            loaderState.dependencyMethodResolved = false
        }
    }

    private fun readResult(loaderState: LoaderState, classLoader: ClassLoader?, className: String): Any? {
        val result = loaderState.classResults[className]
        if (result == null) {
            return resolve(loaderState, classLoader, className)
        }

        return when (result) {
            is DepResult.DependencyFound -> result.value
            is DepResult.ClassMissing -> null
            is DepResult.MethodMissing -> if (result.lifecycle == lifecycle.get()) null else resolve(loaderState, classLoader, className)
            is DepResult.DependencyNotReady -> if (result.lifecycle == lifecycle.get()) null else resolve(loaderState, classLoader, className)
        }
    }

    private fun resolveNewLoader(classLoader: ClassLoader?, className: String): Any? {
        val loaderState = synchronized(loaderStates) {
            loaderStates[classLoader] ?: LoaderState().also { loaderStates[classLoader] = it }
        }
        return resolve(loaderState, classLoader, className)
    }

    private fun resolve(loaderState: LoaderState, classLoader: ClassLoader?, className: String): Any? {
        synchronized(loaderState) {
            val current = lifecycle.get()

            val existing = loaderState.classResults[className]
            when (existing) {
                is DepResult.DependencyFound -> return existing.value
                is DepResult.ClassMissing -> return null
                is DepResult.MethodMissing -> if (existing.lifecycle == current) return null
                is DepResult.DependencyNotReady -> if (existing.lifecycle == current) return null
                null -> { /* proceed */ }
            }

            // Resolve the target class first.  CLASS_MISSING is permanent per
            // class loader and takes precedence over METHOD_MISSING so that we
            // do not report a method problem for a class that does not exist.
            val clazz = when (existing) {
                is DepResult.MethodMissing -> existing.clazz
                is DepResult.DependencyNotReady -> existing.clazz
                else -> try {
                    XposedHelpers.findClassIfExists(className, classLoader)
                } catch (oom: OutOfMemoryError) {
                    throw oom
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                    null
                }
            }

            if (clazz == null) {
                loaderState.classResults[className] = DepResult.ClassMissing
                return null
            }

            val method = resolveDependencyMethod(loaderState, classLoader)
            if (method == null) {
                loaderState.classResults[className] = DepResult.MethodMissing(clazz, current)
                return null
            }

            val instance = try {
                method.invoke(null, clazz)
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (ite: java.lang.reflect.InvocationTargetException) {
                val cause = ite.cause
                if (cause is OutOfMemoryError) throw cause
                XposedHelpers.log(ite)
                null
            } catch (t: Throwable) {
                XposedHelpers.log(t)
                null
            }

            return if (instance != null) {
                val found = DepResult.DependencyFound(instance, clazz)
                loaderState.classResults[className] = found
                instance
            } else {
                loaderState.classResults[className] = DepResult.DependencyNotReady(clazz, current)
                null
            }
        }
    }

    private fun resolveDependencyMethod(loaderState: LoaderState, classLoader: ClassLoader?): Method? {
        if (loaderState.dependencyMethodResolved) {
            return loaderState.dependencyMethod
        }

        val depClass = try {
            XposedHelpers.findClassIfExists(dependencyClassName, classLoader)
        } catch (oom: OutOfMemoryError) {
            throw oom
        } catch (t: Throwable) {
            XposedHelpers.log(t)
            null
        }

        val method = if (depClass != null) {
            try {
                if (dependencyMethodThrowableForTest != null) throw dependencyMethodThrowableForTest!!
                depClass.getDeclaredMethod("get", Class::class.java).apply { isAccessible = true }
            } catch (oom: OutOfMemoryError) {
                throw oom
            } catch (t: Throwable) {
                XposedHelpers.log(t)
                null
            }
        } else null

        loaderState.dependencyMethod = method
        loaderState.dependencyMethodResolved = true
        return method
    }

    // --- test-only helpers --------------------------------------------------

    /** Optional throwable to inject before resolving the Dependency.get method. Tests only. */
    @JvmField
    internal var dependencyMethodThrowableForTest: Throwable? = null

    @JvmStatic
    internal fun loaderStateForTest(classLoader: ClassLoader?): LoaderState? = loaderStates[classLoader]

    @JvmStatic
    internal fun loaderCountForTest(): Int = loaderStates.size

    @JvmStatic
    internal fun clearForTests() {
        lifecycle.set(0L)
        loaderStates.clear()
        dependencyMethodThrowableForTest = null
    }
}
