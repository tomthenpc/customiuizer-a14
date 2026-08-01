package tv.withaibuild.customiuizer.mods.utils

import android.app.MiuiThemeHelper
import android.content.Context
import android.content.res.Resources
import android.os.SystemClock
import android.util.SparseArray
import android.util.SparseIntArray
import io.github.libxposed.api.XposedInterface
import miui.content.res.ThemeValues
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ResourceHooks {

    internal class ResourceValue(val mType: ReplacementType, val mValue: Any?)

    class ThemeValue {
        var mValue: Any? = null
        var mNightValue: Any? = null
        var resId = -1
        var pkg: String? = null
        var name: String? = null
        var themeValueType: String? = null
        var resourceType: String? = null

        constructor(value: Any?) {
            mValue = value
            mNightValue = value
        }

        constructor(value: Any?, nightValue: Any?) {
            mValue = value
            mNightValue = nightValue
        }
    }

    enum class ReplacementType {
        ID, OBJECT
    }

    /**
     * The fixed getter kind for every hot-path Resources hook.
     *
     * Each kind carries its method name, parameter types and a direct Resources call.  This removes
     * the runtime `chain.executable.name` JNI lookup and the argument list materialization from the
     * per-resource hot path.
     */
    internal enum class ResourceGetterKind(
        val methodName: String,
        val paramTypes: Array<Class<*>>,
        val supportsIdReplacement: Boolean = true,
    ) {
        GET_TEXT("getText", arrayOf(Int::class.javaPrimitiveType!!)),
        GET_STRING("getString", arrayOf(Int::class.javaPrimitiveType!!)),
        GET_LAYOUT("getLayout", arrayOf(Int::class.javaPrimitiveType!!), supportsIdReplacement = false),
        GET_DRAWABLE_FOR_DENSITY(
            "getDrawableForDensity",
            arrayOf(
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!,
                Resources.Theme::class.java,
            ),
        );

        fun getValue(res: Resources, resId: Int, density: Int = 0, theme: Resources.Theme? = null): Any? {
            return when (this) {
                GET_TEXT -> res.getText(resId)
                GET_STRING -> res.getString(resId)
                GET_LAYOUT -> res.getLayout(resId)
                GET_DRAWABLE_FOR_DENSITY -> res.getDrawableForDensity(resId, density, theme)
            }
        }
    }

    internal enum class HookStatus {
        PENDING,
        HOOKED,
        FAILED,
    }

    private val themeValueReplacements = ConcurrentHashMap<String, ThemeValue>()

    /**
     * Replacement tables read by [ReplaceHook].
     *
     * The hook sits on `Resources.getText/getString/getLayout/getDrawableForDensity`, so it runs on
     * every resource read of the hooked process. Sparse containers keep the lookup free of key
     * boxing, and the tables are published copy-on-write: registration happens on hook setup and
     * preference changes, reads happen on every UI thread of the target process.
     */
    private val replacementsLock = Any()

    @Volatile
    private var fakes = SparseIntArray()

    @Volatile
    private var resourceIdReplacements = SparseArray<ResourceValue>()

    private val hookedGetters = ConcurrentHashMap<ResourceGetterKind, HookStatus>()
    private val getterLocks = ConcurrentHashMap<ResourceGetterKind, Any>()
    private val getterAttempts = ConcurrentHashMap<ResourceGetterKind, AtomicInteger>()

    private val themeHookLock = Any()
    private var themeHookStatus = HookStatus.FAILED
    private var themeHookAttempts = 0

    private val exceptionLogTimes = ConcurrentHashMap<String, Long>()

    // Test-only seams.  All are null in production.
    internal var testModuleRes: Resources? = null
    internal var hookInstallerForTest: ((ResourceGetterKind, HookerClassHelper.MethodHook) -> Boolean)? = null
    internal var themeHookInstallerForTest: (() -> Boolean)? = null
    internal var logSinkForTest: ((Throwable) -> Unit)? = null

    // Test-only backing for the copy-on-write SparseArrays, which are stubbed in JVM unit tests.
    internal var testReplacements: MutableMap<Int, ResourceValue>? = null
    internal var testFakes: MutableMap<Int, Int>? = null

    /**
     * Hot-path hook implementation.  Bound to a fixed [kind] so it never calls
     * `chain.executable.name` or `chain.getArgs()`.
     */
    private inner class ReplaceHook(private val kind: ResourceGetterKind) : HookerClassHelper.MethodHook() {
        @Throws(Throwable::class)
        override fun intercept(chain: XposedInterface.Chain): Any? {
            var skipValue: Any? = null
            var shouldSkip = false
            try {
                val resId = chain.getArg(0) as Int
                val replacement = testReplacements?.get(resId) ?: resourceIdReplacements[resId]
                if (replacement != null) {
                    if (replacement.mType == ReplacementType.OBJECT) {
                        skipValue = replacement.mValue
                        shouldSkip = true
                    } else if (kind.supportsIdReplacement) {
                        val moduleRes = resolveModuleRes()
                        if (moduleRes != null) {
                            val (density, theme) = extraArgs(chain)
                            val modResId = replacement.mValue as Int
                            val value = kind.getValue(moduleRes, modResId, density, theme)
                            if (value != null) {
                                skipValue = value
                                shouldSkip = true
                            }
                        }
                    }
                } else {
                    val modResId = testFakes?.get(resId) ?: fakes[resId]
                    if (modResId != 0) {
                        val moduleRes = resolveModuleRes()
                        if (moduleRes != null) {
                            val (density, theme) = extraArgs(chain)
                            val value = kind.getValue(moduleRes, modResId, density, theme)
                            if (value != null) {
                                skipValue = value
                                shouldSkip = true
                            }
                        }
                    }
                }
            } catch (t: Throwable) {
                if (t is OutOfMemoryError) throw t
                logThrottled(t)
            }
            return if (shouldSkip) skipValue else chain.proceed()
        }

        private fun extraArgs(chain: XposedInterface.Chain): Pair<Int, Resources.Theme?> {
            return if (kind == ResourceGetterKind.GET_DRAWABLE_FOR_DENSITY) {
                val density = chain.getArg(1) as Int
                val theme = chain.getArg(2) as Resources.Theme?
                Pair(density, theme)
            } else {
                Pair(0, null)
            }
        }
    }

    private fun resolveModuleRes(): Resources? {
        testModuleRes?.let { return it }
        val context = ModuleHelper.findContext() ?: return null
        return ModuleHelper.getModuleRes(context)
    }

    private fun logThrottled(t: Throwable) {
        val now = SystemClock.elapsedRealtime()
        val key = t::class.java.name
        val last = exceptionLogTimes.putIfAbsent(key, now)
        if (last == null || now - last > EXCEPTION_LOG_THROTTLE_MS) {
            exceptionLogTimes[key] = now
            logSinkForTest?.invoke(t) ?: XposedHelpers.log(t)
        }
    }

    private fun initThemeHook() {
        ModuleHelper.findAndHookMethod(
            miui.content.res.ThemeResources::class.java,
            "mergeThemeValues",
            String::class.java,
            ThemeValues::class.java,
            object : HookerClassHelper.MethodHook() {
                @Throws(Throwable::class)
                override fun intercept(chain: XposedInterface.Chain): Any? {
                    var result: Any? = null
                    var throwable: Throwable? = null
                    try {
                        result = chain.proceed()
                    } catch (t: Throwable) {
                        throwable = t
                        result = null
                    }
                    try {
                        val mThemeResources = chain.thisObject
                        val mPackageName = XposedHelpers.getObjectField(mThemeResources, "mPackageName") as String?
                        if (mPackageName != null && mPackageName != "miui" && (
                            mPackageName == ModuleHelper.currentPackageName
                            || "miui.systemui.plugin" == mPackageName
                        )) {
                            val packageName = chain.getArg(0) as String?
                            val mThemeValues = chain.getArg(1)
                            if (packageName != null && (
                                packageName == ModuleHelper.currentPackageName
                                || "miui.systemui.plugin" == packageName
                            ) && mThemeValues != null) {
                                val themeIntValues = SparseIntArray()
                                val themeIntegerArrays = SparseArray<IntArray>()
                                val themeStringArrays = SparseArray<Array<String>>()
                                val mResources = XposedHelpers.getObjectField(mThemeResources, "mResources") as Resources
                                val nightMode = XposedHelpers.getBooleanField(mThemeResources, "mNightMode")
                                @Suppress("UNCHECKED_CAST")
                                val mIntegers = XposedHelpers.getObjectField(mThemeValues, "mIntegers") as HashMap<Int, Int>
                                @Suppress("UNCHECKED_CAST")
                                val mIntegerArrays = XposedHelpers.getObjectField(mThemeValues, "mIntegerArrays") as HashMap<Int, IntArray>
                                @Suppress("UNCHECKED_CAST")
                                val mStringArrays = XposedHelpers.getObjectField(mThemeValues, "mStringArrays") as HashMap<Int, Array<String>>
                                for ((_, tv) in themeValueReplacements) {
                                    if (tv.resId == -1) {
                                        if (tv.pkg == mPackageName || "android" == tv.pkg) {
                                            tv.resId = mResources.getIdentifier(tv.name, tv.resourceType, tv.pkg)
                                        }
                                    }
                                    if (tv.resId > 0) {
                                        @Suppress("UNCHECKED_CAST")
                                        when (tv.themeValueType) {
                                            "string-array" -> themeStringArrays.put(tv.resId, (if (nightMode) tv.mNightValue else tv.mValue) as Array<String>)
                                            "integer-array" -> themeIntegerArrays.put(tv.resId, (if (nightMode) tv.mNightValue else tv.mValue) as IntArray)
                                            else -> themeIntValues.put(tv.resId, (if (nightMode) tv.mNightValue else tv.mValue) as Int)
                                        }
                                    }
                                }
                                for (i in 0 until themeIntValues.size()) {
                                    mIntegers[themeIntValues.keyAt(i)] = themeIntValues.valueAt(i)
                                }
                                for (i in 0 until themeIntegerArrays.size()) {
                                    mIntegerArrays[themeIntegerArrays.keyAt(i)] = themeIntegerArrays.valueAt(i) ?: continue
                                }
                                for (i in 0 until themeStringArrays.size()) {
                                    mStringArrays[themeStringArrays.keyAt(i)] = themeStringArrays.valueAt(i) ?: continue
                                }
                            }
                        }
                    } catch (t: Throwable) {
                        if (t is OutOfMemoryError) throw t
                        logThrottled(t)
                    }
                    return XposedHelpers.throwOrReturn(throwable, result)
                }
            }
        )
    }

    private fun initResourceIdHook(pkg: String, type: String, name: String, resourceType: ReplacementType, replaceValue: Any?) {
        val mContext = ModuleHelper.findContext()
        val rv = ResourceValue(resourceType, replaceValue)
        if (mContext != null) {
            val resId = mContext.resources.getIdentifier(name, type, pkg)
            if (resId > 0) {
                synchronized(replacementsLock) {
                    val updated = resourceIdReplacements.clone()
                    updated.put(resId, rv)
                    resourceIdReplacements = updated
                }
            } else {
                XposedHelpers.log("Resource not found: $pkg:$type/$name")
            }
        } else {
            XposedHelpers.log("Context not found: $pkg:$type/$name")
        }
    }

    private fun installGetter(kind: ResourceGetterKind) {
        val lock = getterLocks.getOrPut(kind) { Any() }
        val attempts = getterAttempts.getOrPut(kind) { AtomicInteger(0) }

        synchronized(lock) {
            when (hookedGetters[kind]) {
                HookStatus.HOOKED -> return
                HookStatus.FAILED -> if (attempts.get() >= MAX_HOOK_ATTEMPTS) return
                HookStatus.PENDING -> return
                else -> {}
            }
            hookedGetters[kind] = HookStatus.PENDING
        }

        val hook = ReplaceHook(kind)
        var installed = false
        var error: Throwable? = null
        try {
            val testInstaller = hookInstallerForTest
            installed = if (testInstaller != null) {
                testInstaller(kind, hook)
            } else {
                ModuleHelper.findAndHookMethod(Resources::class.java, kind.methodName, *kind.paramTypes, hook)
                true
            }
        } catch (t: Throwable) {
            if (t is OutOfMemoryError) throw t
            error = t
        }

        synchronized(lock) {
            if (installed) {
                hookedGetters[kind] = HookStatus.HOOKED
                attempts.set(0)
            } else {
                hookedGetters[kind] = HookStatus.FAILED
                attempts.incrementAndGet()
            }
        }

        if (!installed && error != null) {
            XposedHelpers.log("Failed to hook Resources.${kind.methodName}: $error")
        }
    }

    private fun applyHooks(type: String) {
        when (type) {
            "layout" -> installGetter(ResourceGetterKind.GET_LAYOUT)
            "string" -> {
                installGetter(ResourceGetterKind.GET_TEXT)
                installGetter(ResourceGetterKind.GET_STRING)
            }
            "drawable" -> installGetter(ResourceGetterKind.GET_DRAWABLE_FOR_DENSITY)
        }
    }

    /**
     * Add fake resources which can be replaced by module resources. eg: drawable, string, layout
     *
     * @param resName resource name
     * @param resId   module resource id
     * @param type    resource type
     * @return fake resource id
     */
    fun addFakeResource(resName: String, resId: Int, type: String): Int {
        return try {
            val fakeResId = getFakeResId(resName)
            synchronized(replacementsLock) {
                val updated = fakes.clone()
                updated.put(fakeResId, resId)
                fakes = updated
            }
            applyHooks(type)
            fakeResId
        } catch (t: Throwable) {
            if (t is OutOfMemoryError) throw t
            XposedHelpers.log(t)
            0
        }
    }

    /**
     * Replace package resources with module resources
     *
     * @param pkg              package name. * for all packages
     * @param type             resource type
     * @param name             resource name
     * @param replacementResId module resource id
     */
    fun setResReplacement(pkg: String, type: String, name: String, replacementResId: Int) {
        try {
            initResourceIdHook(pkg, type, name, ReplacementType.ID, replacementResId)
            applyHooks(type)
        } catch (t: Throwable) {
            if (t is OutOfMemoryError) throw t
            XposedHelpers.log(t)
        }
    }

    /**
     * Replace package resources with replacement value
     *
     * @param pkg                 package name. * for all packages
     * @param type                resource type
     * @param name                resource name
     * @param replacementResValue replacement value
     */
    fun setObjectReplacement(pkg: String, type: String, name: String, replacementResValue: Any?) {
        try {
            initResourceIdHook(pkg, type, name, ReplacementType.OBJECT, replacementResValue)
            applyHooks(type)
        } catch (t: Throwable) {
            if (t is OutOfMemoryError) throw t
            XposedHelpers.log(t)
        }
    }

    fun setThemeValueReplacement(pkg: String, type: String, name: String, resValue: Any?) {
        setThemeValueReplacement(pkg, type, name, resValue, resValue)
    }

    fun setThemeValueReplacement(pkg: String, type: String, name: String, resValue: Any?, nightResValue: Any?) {
        try {
            var value: Any? = resValue
            var nightValue: Any? = nightResValue
            if ("bool" == type) {
                value = if (value as Boolean) 1 else 0
                nightValue = if (nightValue as Boolean) 1 else 0
            } else if ("dimen" == type) {
                val valInDimen = "${value}dp"
                value = MiuiThemeHelper.parseDimension(valInDimen)
                nightValue = value
            }
            val tv = ThemeValue(value, nightValue)
            tv.pkg = pkg
            tv.name = name
            tv.themeValueType = type
            tv.resourceType = if ("string-array" == type || "integer-array" == type) "array" else type
            themeValueReplacements["$pkg:$type/$name"] = tv
            tryInitThemeHook()
        } catch (t: Throwable) {
            if (t is OutOfMemoryError) throw t
            XposedHelpers.log(t)
        }
    }

    private fun tryInitThemeHook() {
        synchronized(themeHookLock) {
            when (themeHookStatus) {
                HookStatus.HOOKED -> return
                HookStatus.FAILED -> if (themeHookAttempts >= MAX_HOOK_ATTEMPTS) return
                HookStatus.PENDING -> return
            }
            themeHookStatus = HookStatus.PENDING
        }

        var installed = false
        var error: Throwable? = null
        try {
            val testInstaller = themeHookInstallerForTest
            installed = if (testInstaller != null) {
                testInstaller()
            } else {
                initThemeHook()
                true
            }
        } catch (t: Throwable) {
            if (t is OutOfMemoryError) throw t
            error = t
        }

        synchronized(themeHookLock) {
            if (installed) {
                themeHookStatus = HookStatus.HOOKED
                themeHookAttempts = 0
            } else {
                themeHookStatus = HookStatus.FAILED
                themeHookAttempts++
            }
        }

        if (!installed && error != null) {
            XposedHelpers.log("Failed to hook ThemeResources.mergeThemeValues: $error")
        }
    }

    // Internal test helpers.  Not called in production.
    internal fun createReplaceHookForTest(kind: ResourceGetterKind): HookerClassHelper.MethodHook = ReplaceHook(kind)

    internal fun setResourceIdReplacementForTest(resId: Int, type: ReplacementType, value: Any?) {
        val map = testReplacements ?: HashMap<Int, ResourceValue>().also { testReplacements = it }
        map[resId] = ResourceValue(type, value)
    }

    internal fun setFakeForTest(resId: Int, modResId: Int) {
        val map = testFakes ?: HashMap<Int, Int>().also { testFakes = it }
        map[resId] = modResId
    }

    internal fun clearThrottlingForTest() {
        exceptionLogTimes.clear()
    }

    internal fun getterStatusForTest(kind: ResourceGetterKind): HookStatus? = hookedGetters[kind]
    internal fun getterAttemptCountForTest(kind: ResourceGetterKind): Int = getterAttempts[kind]?.get() ?: 0

    internal fun themeHookStatusForTest(): HookStatus = themeHookStatus
    internal fun themeHookAttemptCountForTest(): Int = themeHookAttempts

    internal fun installGetterForTest(kind: ResourceGetterKind) = installGetter(kind)
    internal fun tryInitThemeHookForTest() = tryInitThemeHook()

    companion object {
        @JvmStatic
        fun getFakeResId(resourceName: String): Int {
            return 0x7e00f000 or (resourceName.hashCode() and 0x00ffffff)
        }

        private const val EXCEPTION_LOG_THROTTLE_MS = 5000L
        private const val MAX_HOOK_ATTEMPTS = 3
    }
}
