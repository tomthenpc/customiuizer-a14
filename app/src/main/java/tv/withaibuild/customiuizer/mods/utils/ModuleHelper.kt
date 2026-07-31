package tv.withaibuild.customiuizer.mods.utils

import android.annotation.SuppressLint
import android.app.ActivityOptions
import android.app.Application
import android.app.BroadcastOptions
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.os.UserHandle
import android.provider.Settings
import android.util.MiuiMultiWindowUtils
import android.view.View
import io.github.libxposed.api.XposedModuleInterface
import kotlinx.coroutines.CoroutineExceptionHandler
import miui.app.MiuiFreeFormManager
import miui.process.ForegroundInfo
import miui.process.ProcessManager
import tv.withaibuild.customiuizer.MainModule
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.CustomMethodUnhooker
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook
import tv.withaibuild.customiuizer.utils.Helpers
import java.io.RandomAccessFile
import java.lang.ref.WeakReference
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

class ModuleHelper private constructor() {

    interface PreferenceObserver {
        fun onChange(key: String?)
    }

    companion object {
        const val NOT_EXIST_SYMBOL = "ObjectFieldNotExist"
        const val prefsName = "customiuizer_prefs"

        @JvmField
        var currentPackageName: String? = null

        @SuppressLint("StaticFieldLeak")
        @JvmField
        var mModuleContext: Context? = null

        @SuppressLint("StaticFieldLeak")
        @JvmField
        var mCachedContext: Context? = null

        @JvmField
        var cachedModuleRes: Resources? = null

        @JvmField
        var cachedModuleConfig: Configuration? = null

        private val viewInfoTag = ResourceHooks.getFakeResId("view_info_tag")

        /** Process-scoped observers. Owned by module singletons, never collected. */
        private val prefObservers = CopyOnWriteArraySet<PreferenceObserver>()

        /**
         * Observers whose lifetime is bound to a hooked object.
         *
         * The strong reference lives in the owner's additional instance field, which
         * [XposedHelpers] keeps in a `WeakHashMap`. Holding only a weak reference here means a
         * recreated hook target (theme change, density change, panel rebuild) drops its old
         * observer instead of pinning the dead instance for the life of the process.
         */
        private val ownedPrefObservers = CopyOnWriteArrayList<WeakReference<PreferenceObserver>>()
        private const val PREF_OBSERVER_FIELD = "customiuizer_prefObserver"

        @JvmField
        internal var ActivityThreadClass: Class<*>? = null

        private var thermalId = -1

        private var thermalIdScanned = false

        private fun processName() = HookDiagnostics.currentProcessName
            ?: currentPackageName
            ?: android.os.Process.myPid().toString()

        private fun argList(vararg args: Any?): String {
            if (args.isEmpty()) return ""
            val lastIndex = args.size - 1
            val sb = StringBuilder()
            for (i in 0 until lastIndex) {
                if (i > 0) sb.append(',')
                sb.append(args[i]?.toString() ?: "")
            }
            return sb.toString()
        }

        private fun Method.descriptor(): String =
            parameterTypes.joinToString(",") { it.name ?: it.toString() }

        @JvmStatic
        fun hookMethod(method: Method, callback: MethodHook): CustomMethodUnhooker? {
            return try {
                val unhooker = XposedHelpers.doHookMethod(method, callback)
                if (unhooker != null) {
                    HookDiagnostics.record(
                        processName(),
                        HookDiagnostics.Kind.METHOD,
                        method.declaringClass?.name ?: "?",
                        method.name,
                        method.descriptor(),
                        HookDiagnostics.Status.INSTALLED,
                    )
                } else {
                    HookDiagnostics.record(
                        processName(),
                        HookDiagnostics.Kind.METHOD,
                        method.declaringClass?.name ?: "?",
                        method.name,
                        method.descriptor(),
                        HookDiagnostics.Status.INSTALL_FAILED,
                        "unhooker-null",
                    )
                }
                unhooker
            } catch (t: Throwable) {
                XposedHelpers.log("Failed to hook " + method.name + " method")
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.METHOD,
                    method.declaringClass?.name ?: "?",
                    method.name,
                    method.descriptor(),
                    HookDiagnostics.Status.INSTALL_FAILED,
                    t.javaClass.simpleName,
                )
                null
            }
        }

        @JvmStatic
        fun findAndHookMethod(className: String, classLoader: ClassLoader?, methodName: String, vararg parameterTypesAndCallback: Any?): CustomMethodUnhooker? {
            val hookClass = XposedHelpers.findClassIfExists(className, classLoader)
            if (hookClass == null) {
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.METHOD,
                    className,
                    methodName,
                    argList(*parameterTypesAndCallback),
                    HookDiagnostics.Status.TARGET_CLASS_MISSING,
                )
                XposedHelpers.log("Failed to hook " + methodName + " method in " + className + " (class not found)")
                return null
            }
            return try {
                val unhooker = XposedHelpers.findAndHookMethod(hookClass, methodName, *parameterTypesAndCallback)
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.METHOD,
                    className,
                    methodName,
                    argList(*parameterTypesAndCallback),
                    HookDiagnostics.Status.INSTALLED,
                )
                unhooker
            } catch (t: Throwable) {
                XposedHelpers.log("Failed to hook " + methodName + " method in " + className)
                val status = when {
                    HookDiagnostics.isMemberMissingException(t) -> HookDiagnostics.Status.TARGET_MEMBER_MISSING
                    else -> HookDiagnostics.Status.INSTALL_FAILED
                }
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.METHOD,
                    className,
                    methodName,
                    argList(*parameterTypesAndCallback),
                    status,
                    t.javaClass.simpleName,
                )
                null
            }
        }

        @JvmStatic
        fun callMethodSilently(obj: Any?, methodName: String, vararg args: Any?): Any? {
            return try {
                XposedHelpers.callMethod(obj, methodName, *args)
            } catch (e: Throwable) {
                XposedHelpers.log(e)
                NOT_EXIST_SYMBOL
            }
        }

        @JvmStatic
        fun findAndHookMethod(clazz: Class<*>, methodName: String, vararg parameterTypesAndCallback: Any?): CustomMethodUnhooker? {
            val className = clazz.canonicalName ?: clazz.name
            return try {
                val unhooker = XposedHelpers.findAndHookMethod(clazz, methodName, *parameterTypesAndCallback)
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.METHOD,
                    className,
                    methodName,
                    argList(*parameterTypesAndCallback),
                    HookDiagnostics.Status.INSTALLED,
                )
                unhooker
            } catch (t: Throwable) {
                XposedHelpers.log("Failed to hook " + methodName + " method in " + className)
                val status = when {
                    HookDiagnostics.isMemberMissingException(t) -> HookDiagnostics.Status.TARGET_MEMBER_MISSING
                    else -> HookDiagnostics.Status.INSTALL_FAILED
                }
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.METHOD,
                    className,
                    methodName,
                    argList(*parameterTypesAndCallback),
                    status,
                    t.javaClass.simpleName,
                )
                null
            }
        }

        @JvmStatic
        @Suppress("UNUSED_RETURN_VALUE")
        fun findAndHookMethodSilently(className: String, classLoader: ClassLoader?, methodName: String, vararg parameterTypesAndCallback: Any?): Boolean {
            val hookClass = XposedHelpers.findClassIfExists(className, classLoader)
            if (hookClass == null) {
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.METHOD,
                    className,
                    methodName,
                    argList(*parameterTypesAndCallback),
                    HookDiagnostics.Status.SILENTLY_SKIPPED,
                    "class-not-found",
                )
                return false
            }
            return try {
                val ok = XposedHelpers.findAndHookMethod(hookClass, methodName, *parameterTypesAndCallback) != null
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.METHOD,
                    className,
                    methodName,
                    argList(*parameterTypesAndCallback),
                    if (ok) HookDiagnostics.Status.INSTALLED else HookDiagnostics.Status.SILENTLY_SKIPPED,
                )
                ok
            } catch (t: Throwable) {
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.METHOD,
                    className,
                    methodName,
                    argList(*parameterTypesAndCallback),
                    HookDiagnostics.Status.SILENTLY_SKIPPED,
                    t.javaClass.simpleName,
                )
                false
            }
        }

        @JvmStatic
        @Suppress("UNUSED_RETURN_VALUE")
        fun findAndHookMethodSilently(clazz: Class<*>, methodName: String, vararg parameterTypesAndCallback: Any?): Boolean {
            val className = clazz.canonicalName ?: clazz.name
            return try {
                val ok = XposedHelpers.findAndHookMethod(clazz, methodName, *parameterTypesAndCallback) != null
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.METHOD,
                    className,
                    methodName,
                    argList(*parameterTypesAndCallback),
                    if (ok) HookDiagnostics.Status.INSTALLED else HookDiagnostics.Status.SILENTLY_SKIPPED,
                )
                ok
            } catch (t: Throwable) {
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.METHOD,
                    className,
                    methodName,
                    argList(*parameterTypesAndCallback),
                    HookDiagnostics.Status.SILENTLY_SKIPPED,
                    t.javaClass.simpleName,
                )
                false
            }
        }

        @JvmStatic
        fun findAndHookConstructor(className: String, classLoader: ClassLoader?, vararg parameterTypesAndCallback: Any?): CustomMethodUnhooker? {
            val hookClass = XposedHelpers.findClassIfExists(className, classLoader)
            if (hookClass == null) {
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.CONSTRUCTOR,
                    className,
                    "<init>",
                    argList(*parameterTypesAndCallback),
                    HookDiagnostics.Status.TARGET_CLASS_MISSING,
                )
                XposedHelpers.log("Failed to hook constructor in " + className + " (class not found)")
                return null
            }
            return try {
                val unhooker = XposedHelpers.findAndHookConstructor(hookClass, *parameterTypesAndCallback)
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.CONSTRUCTOR,
                    className,
                    "<init>",
                    argList(*parameterTypesAndCallback),
                    HookDiagnostics.Status.INSTALLED,
                )
                unhooker
            } catch (t: Throwable) {
                XposedHelpers.log("Failed to hook constructor in " + className)
                val status = when {
                    HookDiagnostics.isMemberMissingException(t) -> HookDiagnostics.Status.TARGET_MEMBER_MISSING
                    else -> HookDiagnostics.Status.INSTALL_FAILED
                }
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.CONSTRUCTOR,
                    className,
                    "<init>",
                    argList(*parameterTypesAndCallback),
                    status,
                    t.javaClass.simpleName,
                )
                null
            }
        }

        @JvmStatic
        fun hookAllConstructors(className: String, classLoader: ClassLoader?, callback: MethodHook) {
            try {
                val hookClass = XposedHelpers.findClassIfExists(className, classLoader)
                if (hookClass == null) {
                    XposedHelpers.log("Failed to hook " + className + " constructor (class not found)")
                    HookDiagnostics.record(
                        processName(),
                        HookDiagnostics.Kind.ALL_CONSTRUCTORS,
                        className,
                        "<all>",
                        "",
                        HookDiagnostics.Status.TARGET_CLASS_MISSING,
                    )
                    return
                }
                val unhookers = XposedHelpers.hookAllConstructors(hookClass, callback)
                if (unhookers.isEmpty()) {
                    XposedHelpers.log("Failed to hook " + className + " constructor (no constructors found)")
                    HookDiagnostics.record(
                        processName(),
                        HookDiagnostics.Kind.ALL_CONSTRUCTORS,
                        className,
                        "<all>",
                        "",
                        HookDiagnostics.Status.TARGET_MEMBER_MISSING,
                    )
                } else {
                    HookDiagnostics.record(
                        processName(),
                        HookDiagnostics.Kind.ALL_CONSTRUCTORS,
                        className,
                        "<all>",
                        "",
                        HookDiagnostics.Status.INSTALLED,
                    )
                }
            } catch (t: Throwable) {
                XposedHelpers.log(t)
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.ALL_CONSTRUCTORS,
                    className,
                    "<all>",
                    "",
                    HookDiagnostics.Status.INSTALL_FAILED,
                    t.javaClass.simpleName,
                )
            }
        }

        @JvmStatic
        fun hookAllConstructors(hookClass: Class<*>?, callback: MethodHook) {
            val className = hookClass?.canonicalName ?: "?"
            if (hookClass == null) {
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.ALL_CONSTRUCTORS,
                    className,
                    "<all>",
                    "",
                    HookDiagnostics.Status.TARGET_CLASS_MISSING,
                )
                return
            }
            try {
                val unhookers = XposedHelpers.hookAllConstructors(hookClass, callback)
                if (unhookers.isEmpty()) {
                    XposedHelpers.log("Failed to hook " + className + " constructor (no constructors found)")
                    HookDiagnostics.record(
                        processName(),
                        HookDiagnostics.Kind.ALL_CONSTRUCTORS,
                        className,
                        "<all>",
                        "",
                        HookDiagnostics.Status.TARGET_MEMBER_MISSING,
                    )
                } else {
                    HookDiagnostics.record(
                        processName(),
                        HookDiagnostics.Kind.ALL_CONSTRUCTORS,
                        className,
                        "<all>",
                        "",
                        HookDiagnostics.Status.INSTALLED,
                    )
                }
            } catch (t: Throwable) {
                XposedHelpers.log(t)
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.ALL_CONSTRUCTORS,
                    className,
                    "<all>",
                    "",
                    HookDiagnostics.Status.INSTALL_FAILED,
                    t.javaClass.simpleName,
                )
            }
        }

        @JvmStatic
        fun hookAllMethods(className: String, classLoader: ClassLoader?, methodName: String, callback: MethodHook) {
            try {
                val hookClass = XposedHelpers.findClassIfExists(className, classLoader)
                if (hookClass == null) {
                    XposedHelpers.log("Failed to hook " + methodName + " method in " + className + " (class not found)")
                    HookDiagnostics.record(
                        processName(),
                        HookDiagnostics.Kind.ALL_METHODS,
                        className,
                        methodName,
                        "",
                        HookDiagnostics.Status.TARGET_CLASS_MISSING,
                    )
                    return
                }
                val unhookers = XposedHelpers.hookAllMethods(hookClass, methodName, callback)
                if (unhookers.isEmpty()) {
                    XposedHelpers.log("Failed to hook " + methodName + " method in " + className + " (no methods found)")
                    HookDiagnostics.record(
                        processName(),
                        HookDiagnostics.Kind.ALL_METHODS,
                        className,
                        methodName,
                        "",
                        HookDiagnostics.Status.TARGET_MEMBER_MISSING,
                    )
                } else {
                    HookDiagnostics.record(
                        processName(),
                        HookDiagnostics.Kind.ALL_METHODS,
                        className,
                        methodName,
                        "",
                        HookDiagnostics.Status.INSTALLED,
                    )
                }
            } catch (t: Throwable) {
                XposedHelpers.log(t)
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.ALL_METHODS,
                    className,
                    methodName,
                    "",
                    HookDiagnostics.Status.INSTALL_FAILED,
                    t.javaClass.simpleName,
                )
            }
        }

        @JvmStatic
        fun hookAllMethods(hookClass: Class<*>?, methodName: String, callback: MethodHook) {
            val className = hookClass?.canonicalName ?: "?"
            if (hookClass == null) {
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.ALL_METHODS,
                    className,
                    methodName,
                    "",
                    HookDiagnostics.Status.TARGET_CLASS_MISSING,
                )
                return
            }
            try {
                val unhookers = XposedHelpers.hookAllMethods(hookClass, methodName, callback)
                if (unhookers.isEmpty()) {
                    XposedHelpers.log("Failed to hook " + methodName + " method in " + className + " (no methods found)")
                    HookDiagnostics.record(
                        processName(),
                        HookDiagnostics.Kind.ALL_METHODS,
                        className,
                        methodName,
                        "",
                        HookDiagnostics.Status.TARGET_MEMBER_MISSING,
                    )
                } else {
                    HookDiagnostics.record(
                        processName(),
                        HookDiagnostics.Kind.ALL_METHODS,
                        className,
                        methodName,
                        "",
                        HookDiagnostics.Status.INSTALLED,
                    )
                }
            } catch (t: Throwable) {
                XposedHelpers.log(t)
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.ALL_METHODS,
                    className,
                    methodName,
                    "",
                    HookDiagnostics.Status.INSTALL_FAILED,
                    t.javaClass.simpleName,
                )
            }
        }

        @JvmStatic
        fun proxySystemProperties(method: String, prop: String, value: String, classLoader: ClassLoader?): Any? {
            val sysPropClass = XposedHelpers.findClassIfExists("android.os.SystemProperties", classLoader) ?: return null
            return XposedHelpers.callStaticMethod(sysPropClass, method, prop, value)
        }

        @JvmStatic
        fun proxySystemProperties(method: String, prop: String, value: Int, classLoader: ClassLoader?): Any? {
            val sysPropClass = XposedHelpers.findClassIfExists("android.os.SystemProperties", classLoader) ?: return null
            return XposedHelpers.callStaticMethod(sysPropClass, method, prop, value)
        }

        @JvmStatic
        fun hookAllMethodsSilently(className: String, classLoader: ClassLoader?, methodName: String, callback: MethodHook): Boolean {
            return try {
                val hookClass = XposedHelpers.findClassIfExists(className, classLoader)
                if (hookClass == null) {
                    HookDiagnostics.record(
                        processName(),
                        HookDiagnostics.Kind.ALL_METHODS,
                        className,
                        methodName,
                        "",
                        HookDiagnostics.Status.SILENTLY_SKIPPED,
                        "class-not-found",
                    )
                    return false
                }
                val ok = XposedHelpers.hookAllMethods(hookClass, methodName, callback).isNotEmpty()
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.ALL_METHODS,
                    className,
                    methodName,
                    "",
                    if (ok) HookDiagnostics.Status.INSTALLED else HookDiagnostics.Status.SILENTLY_SKIPPED,
                    if (ok) "" else "no-methods-found",
                )
                ok
            } catch (t: Throwable) {
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.ALL_METHODS,
                    className,
                    methodName,
                    "",
                    HookDiagnostics.Status.SILENTLY_SKIPPED,
                    t.javaClass.simpleName,
                )
                false
            }
        }

        @JvmStatic
        fun hookAllMethodsSilently(hookClass: Class<*>?, methodName: String, callback: MethodHook): Boolean {
            val className = hookClass?.canonicalName ?: "?"
            if (hookClass == null) {
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.ALL_METHODS,
                    className,
                    methodName,
                    "",
                    HookDiagnostics.Status.SILENTLY_SKIPPED,
                    "class-null",
                )
                return false
            }
            return try {
                val ok = XposedHelpers.hookAllMethods(hookClass, methodName, callback).isNotEmpty()
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.ALL_METHODS,
                    className,
                    methodName,
                    "",
                    if (ok) HookDiagnostics.Status.INSTALLED else HookDiagnostics.Status.SILENTLY_SKIPPED,
                    if (ok) "" else "no-methods-found",
                )
                ok
            } catch (t: Throwable) {
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.ALL_METHODS,
                    className,
                    methodName,
                    "",
                    HookDiagnostics.Status.SILENTLY_SKIPPED,
                    t.javaClass.simpleName,
                )
                false
            }
        }

        @JvmStatic
        fun getStaticObjectFieldSilently(clazz: Class<*>, fieldName: String): Any? {
            return try {
                XposedHelpers.getStaticObjectField(clazz, fieldName)
            } catch (t: Throwable) {
                NOT_EXIST_SYMBOL
            }
        }

        @JvmStatic
        fun getObjectFieldSilently(obj: Any?, fieldName: String): Any? {
            return try {
                XposedHelpers.getObjectField(obj, fieldName)
            } catch (t: Throwable) {
                NOT_EXIST_SYMBOL
            }
        }

        @JvmStatic
        fun getUserId(): Int {
            return Process.myUid() / 100000
        }

        @JvmStatic
        fun findContext(): Context? {
            if (mCachedContext != null) return mCachedContext
            var context: Context? = null
            try {
                val atClass = ActivityThreadClass
                    ?: XposedHelpers.findClass("android.app.ActivityThread", null).also { ActivityThreadClass = it }
                context = XposedHelpers.callStaticMethod(atClass, "currentApplication") as? Application
                if (context == null) {
                    val currentActivityThread = XposedHelpers.callStaticMethod(atClass, "currentActivityThread")
                    if (currentActivityThread != null) {
                        context = XposedHelpers.callMethod(currentActivityThread, "getSystemContext") as? Context
                    }
                }
            } catch (ignore: Throwable) {
            }
            if (context != null) mCachedContext = context
            return context
        }

        @JvmStatic
        fun findContext(lpparam: XposedModuleInterface.PackageReadyParam?): Context? {
            var context: Context? = null
            try {
                val classLoader = lpparam?.classLoader
                context = XposedHelpers.callStaticMethod(
                    XposedHelpers.findClass("android.app.ActivityThread", classLoader),
                    "currentApplication"
                ) as? Application
                if (context == null) {
                    val currentActivityThread = XposedHelpers.callStaticMethod(
                        XposedHelpers.findClass("android.app.ActivityThread", null),
                        "currentActivityThread"
                    )
                    if (currentActivityThread != null) {
                        context = XposedHelpers.callMethod(currentActivityThread, "getSystemContext") as? Context
                    }
                }
            } catch (ignore: Throwable) {
            }
            return context
        }

        @JvmStatic
        fun getNextMIUIAlarmTime(context: Context): Long {
            var nextTime = 0L
            try {
                nextTime = Settings.Global.getLong(context.contentResolver, "next_alarm_clock_long")
            } catch (e: Settings.SettingNotFoundException) {
            }
            return nextTime
        }

        @JvmStatic
        fun openAppInfo(context: Context, pkg: String, user: Int) {
            try {
                val intent = Intent("miui.intent.action.APP_MANAGER_APPLICATION_DETAIL")
                intent.setPackage("com.miui.securitycenter")
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                intent.putExtra("package_name", pkg)
                if (user != 0) intent.putExtra("miui.intent.extra.USER_ID", user)
                context.startActivity(intent)
            } catch (t: Throwable) {
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    intent.data = Uri.parse("package:$pkg")
                    if (user != 0) {
                        val userHandle = XposedHelpers.newInstance(UserHandle::class.java, user) as UserHandle
                        XposedHelpers.callMethod(context, "startActivityAsUser", intent, userHandle)
                    } else {
                        context.startActivity(intent)
                    }
                } catch (t2: Throwable) {
                    XposedHelpers.log(t2)
                }
            }
        }

        @JvmStatic
        fun observePreferenceChange(prefObserver: PreferenceObserver?) {
            if (prefObserver != null) prefObservers.add(prefObserver)
        }

        @JvmStatic
        fun observePreferenceChange(prefObserver: PreferenceObserver?, owner: Any?) {
            if (prefObserver == null) return
            if (owner == null) {
                observePreferenceChange(prefObserver)
                return
            }
            val old = XposedHelpers.getAdditionalInstanceField(owner, PREF_OBSERVER_FIELD)
            if (old is PreferenceObserver) {
                dropOwnedObserver(old)
            }
            XposedHelpers.setAdditionalInstanceField(owner, PREF_OBSERVER_FIELD, prefObserver)
            ownedPrefObservers.add(WeakReference(prefObserver))
        }

        @JvmStatic
        fun removePreferenceObserver(owner: Any?) {
            val old = XposedHelpers.removeAdditionalInstanceField(owner, PREF_OBSERVER_FIELD)
            if (old is PreferenceObserver) {
                dropOwnedObserver(old)
            }
        }

        /**
         * Removes [observer] and every reference the garbage collector has already cleared.
         *
         * Uses [CopyOnWriteArrayList.removeIf], which performs one atomic array copy. The Kotlin
         * `removeAll { }` extension would instead walk the list with indexed writes, copying the
         * backing array once per removal and without atomicity.
         */
        private fun dropOwnedObserver(observer: PreferenceObserver?) {
            ownedPrefObservers.removeIf { ref ->
                val referent = ref.get()
                referent == null || referent === observer
            }
        }

        /**
         * Fans a preference change out to every observer.
         *
         * Runs on the remote-preferences listener thread of system_server, SystemUI and Launcher.
         * A throwing observer must neither kill that process nor stop the remaining observers from
         * seeing the change, so each callback is isolated.
         */
        @JvmStatic
        fun handlePreferenceChanged(key: String?) {
            for (prefObserver in prefObservers) {
                try {
                    prefObserver.onChange(key)
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
            }
            if (ownedPrefObservers.isEmpty()) return
            var sawCleared = false
            for (ref in ownedPrefObservers) {
                val prefObserver = ref.get()
                if (prefObserver == null) {
                    sawCleared = true
                    continue
                }
                try {
                    prefObserver.onChange(key)
                } catch (t: Throwable) {
                    XposedHelpers.log(t)
                }
            }
            if (sawCleared) dropOwnedObserver(null)
        }

        private enum class RegistrationState {
            PENDING_REGISTER,
            ACTIVE,
            PENDING_UNREGISTER,
            STALE,
            RELEASED,
            REGISTER_FAILED
        }

        private class ModuleReceiverRegistration(
            val context: Context,
            val receiver: BroadcastReceiver,
            val generation: Long,
            val state: AtomicReference<RegistrationState> = AtomicReference(RegistrationState.PENDING_REGISTER)
        )

        private val moduleReceivers = ConcurrentHashMap<String, ModuleReceiverRegistration>()
        private val moduleReceiverGeneration = AtomicLong(0)

        /** Maximum stale receivers held per key while waiting for a retry. */
        private const val MAX_STALE_MODULE_RECEIVERS = 3

        /** Receivers whose framework unregister failed. Retried on the next same-key operation. */
        private val staleModuleReceivers = ConcurrentHashMap<String, ConcurrentLinkedDeque<ModuleReceiverRegistration>>()



        /**
         * Registers [receiver] under [key], replacing whatever the module last registered there.
         *
         * Hook targets are recreated while the process lives: a new `BluetoothControllerImpl`, a
         * new keyguard controller after a theme change, a new Launcher after a rotation. Every
         * recreation runs the constructor or init hook again. Cleanup keyed on the hooked instance
         * cannot see the previous registration — that instance is gone — so each recreation used
         * to leave one more live receiver behind. The module then did the same work N times per
         * broadcast and pinned N dead Contexts.
         *
         * A process-scoped key keeps exactly one live receiver per logical registration,
         * regardless of how many times the hook fires.
         *
         * The registration and replacement sequence is atomic:
         * 1. The map is updated with a new, unique [ModuleReceiverRegistration] first.
         * 2. The previous registration (if any) is unregistered outside the map lock.
         * 3. The framework is asked to register the new receiver.
         * 4. After the framework call, the map is checked again. If another thread has replaced
         *    this registration in the meantime, this thread self-unregisters so the winner is the
         *    only tracked, active receiver.
         * 5. If the framework registration throws, only this thread's own map entry is removed.
         *
         * The registration holds the [Context] strongly because it is required for safe
         * unregistration and the key is process-scoped. Only [Context.getApplicationContext] is
         * retained to avoid pinning an Activity / View context.
         */
        @JvmStatic
        @JvmOverloads
        fun registerModuleReceiver(
            context: Context,
            key: String,
            receiver: BroadcastReceiver,
            filter: IntentFilter,
            flags: Int,
            permission: String? = null
        ): Boolean {
            val appContext = context.applicationContext ?: context

            // Retry any stale receivers left over from a previous failed unregister before we
            // touch the active slot. This is the only safe retry point, not a hot path.
            retryStaleModuleReceivers(key)

            // Calling with the exact same receiver instance is a no-op. This keeps repeated init
            // from the same hook target idempotent and avoids a second framework registration.
            val current = moduleReceivers[key]
            if (current != null && current.receiver === receiver) return true

            val generation = moduleReceiverGeneration.incrementAndGet()
            val newReg = ModuleReceiverRegistration(appContext, receiver, generation)

            // Atomically install the new registration. The previous value (if any) is captured so
            // it can be unregistered outside this compute block, avoiding any framework call inside
            // a potentially blocking map operation.
            val previousRef = java.util.concurrent.atomic.AtomicReference<ModuleReceiverRegistration?>(null)
            val installed = moduleReceivers.compute(key) { _, old ->
                previousRef.set(old)
                // If the same receiver was installed concurrently between the first read above and
                // this compute, keep the existing one. This keeps the map consistent with the
                // framework, which would reject a duplicate registration of the same receiver.
                if (old?.receiver === receiver) old else newReg
            }

            // If the compute kept the previous registration because of a same-receiver race, the
            // framework already has this receiver. Do not touch it again.
            val previous = previousRef.get()
            if (previous?.receiver === receiver) return true

            // `installed` is non-null here (it is either `newReg` or a non-null old registration
            // kept because of a same-receiver race, which we just returned from above).
            if (installed == null) return false

            // Unregister the previous receiver, but do not let a failed unregister prevent the new
            // one from being registered. Failed unregistrations move to the bounded stale queue for
            // a later retry instead of being silently lost.
            if (previous != null) {
                previous.state.set(RegistrationState.PENDING_UNREGISTER)
                if (!releaseModuleRegistration(previous)) {
                    recordStaleModuleReceiver(key, previous)
                } else {
                    previous.state.set(RegistrationState.RELEASED)
                }
            }

            return try {
                appContext.registerReceiver(receiver, filter, permission, null, flags)

                // Another thread may have replaced this same key while we were inside
                // registerReceiver. If our registration is no longer current, we are the loser and
                // must self-unregister.
                val stillCurrent = moduleReceivers[key]
                if (stillCurrent !== installed) {
                    installed.state.set(RegistrationState.PENDING_UNREGISTER)
                    if (!releaseModuleRegistration(installed)) {
                        recordStaleModuleReceiver(key, installed)
                    } else {
                        installed.state.set(RegistrationState.RELEASED)
                    }
                    return false
                }
                installed.state.compareAndSet(RegistrationState.PENDING_REGISTER, RegistrationState.ACTIVE)
                true
            } catch (t: Throwable) {
                XposedHelpers.log(t)
                installed.state.set(RegistrationState.REGISTER_FAILED)
                // Registration failed. Roll back only our own record, leaving any newer record
                // untouched.
                moduleReceivers.computeIfPresent(key) { _, reg ->
                    if (reg === installed) null else reg
                }
                false
            }
        }

        /** Unregisters the receiver held under [key], if the module still has one. */
        @JvmStatic
        @JvmOverloads
        fun unregisterModuleReceiver(key: String, expectedReceiver: BroadcastReceiver? = null) {
            // Retry any stale receivers before touching the active slot.
            retryStaleModuleReceivers(key)

            val current = moduleReceivers[key] ?: return
            // If a specific receiver was expected, only remove that exact registration. This
            // prevents a concurrent replacement from being accidentally torn down.
            if (expectedReceiver != null && current.receiver !== expectedReceiver) return
            current.state.set(RegistrationState.PENDING_UNREGISTER)
            // remove(key, value) is atomic: the entry is removed only if it is still the one
            // we observed, so a registration that has just been replaced by another thread is
            // never deleted under our feet.
            if (moduleReceivers.remove(key, current)) {
                if (!releaseModuleRegistration(current)) {
                    recordStaleModuleReceiver(key, current)
                } else {
                    current.state.set(RegistrationState.RELEASED)
                }
            }
        }

        /**
         * Unregisters [reg] from the framework. Returns true on success, false if the framework
         * call threw. This is the only place that calls [Context.unregisterReceiver] for a module
         * receiver, so failed unregistrations are handled by the stale queue above.
         */
        private fun releaseModuleRegistration(reg: ModuleReceiverRegistration): Boolean {
            return try {
                reg.context.unregisterReceiver(reg.receiver)
                true
            } catch (_: Throwable) {
                false
            }
        }

        /**
         * Records [reg] as stale so a future same-key operation can retry the framework unregister.
         * The queue is bounded; if it is full, the oldest stale receiver is evicted and a single
         * retry is attempted. Receivers that still cannot be unregistered after that are dropped
         * with a diagnostic record so the process is not taken down by cleanup code.
         */
        private fun recordStaleModuleReceiver(key: String, reg: ModuleReceiverRegistration) {
            reg.state.set(RegistrationState.STALE)
            staleModuleReceivers.compute(key) { _, queue ->
                val newQueue = ConcurrentLinkedDeque(queue ?: emptyList())
                if (newQueue.size >= MAX_STALE_MODULE_RECEIVERS) {
                    val oldest = newQueue.pollFirst()
                    if (oldest != null) {
                        if (releaseModuleRegistration(oldest)) {
                            oldest.state.set(RegistrationState.RELEASED)
                        } else {
                            // Bounded best effort: the oldest is evicted because we cannot track
                            // an unbounded number of stuck receivers. It may still be in the
                            // framework, but it is no longer our responsibility.
                            oldest.state.set(RegistrationState.RELEASED)
                            HookDiagnostics.record(
                                processName(),
                                HookDiagnostics.Kind.RECEIVER,
                                "ModuleReceiverRegistry",
                                reg.receiver.javaClass.name,
                                key,
                                HookDiagnostics.Status.RECEIVER_STALE_DROPPED,
                                "stale receiver evicted due to bounded queue",
                            )
                        }
                    }
                }
                newQueue.addLast(reg)
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.RECEIVER,
                    "ModuleReceiverRegistry",
                    reg.receiver.javaClass.name,
                    key,
                    HookDiagnostics.Status.RECEIVER_UNREGISTER_FAILED,
                    "receiver moved to stale queue",
                )
                newQueue
            }
        }

        /**
         * Retries unregistration for every stale receiver under [key]. Receivers that still fail
         * are kept in the bounded stale queue; receivers that succeed are removed.
         */
        private fun retryStaleModuleReceivers(key: String) {
            staleModuleReceivers.compute(key) { _, queue ->
                if (queue == null) return@compute null
                val stillStale = ConcurrentLinkedDeque<ModuleReceiverRegistration>()
                for (reg in queue) {
                    if (reg.state.get() == RegistrationState.RELEASED) continue
                    if (releaseModuleRegistration(reg)) {
                        reg.state.set(RegistrationState.RELEASED)
                    } else if (stillStale.size < MAX_STALE_MODULE_RECEIVERS) {
                        reg.state.set(RegistrationState.STALE)
                        stillStale.addLast(reg)
                    } else {
                        // Bounded best effort: drop on retry if the queue is already full.
                        reg.state.set(RegistrationState.RELEASED)
                        HookDiagnostics.record(
                            processName(),
                            HookDiagnostics.Kind.RECEIVER,
                            "ModuleReceiverRegistry",
                            reg.receiver.javaClass.name,
                            key,
                            HookDiagnostics.Status.RECEIVER_STALE_DROPPED,
                            "stale receiver dropped on retry due to bounded queue",
                        )
                    }
                }
                if (stillStale.isEmpty()) null else stillStale
            }
        }

        private class OwnedReceiver(
            val ownerRef: WeakReference<Any>,
            val contextRef: WeakReference<Context>,
            val receiver: BroadcastReceiver
        )

        private val ownedReceivers = ConcurrentHashMap<String, CopyOnWriteArrayList<OwnedReceiver>>()

        /**
         * Callback for [registerOwnedReceiver]. The receiver is passed so callers can call
         * [BroadcastReceiver.setResultCode] and [BroadcastReceiver.isOrderedBroadcast].
         *
         * Implementations must not close over the [owner]; use the [owner] parameter instead.
         * Closing over the owner turns the weak-reference design into a strong reference and leaks
         * the hook target.
         */
        fun interface OwnedReceiverCallback {
            fun onReceive(receiver: BroadcastReceiver, owner: Any, context: Context, intent: Intent)
        }

        /**
         * A [BroadcastReceiver] that only holds a [WeakReference] to its owner.
         *
         * When a broadcast arrives and the owner is still alive, the owner is passed to the
         * [OwnedReceiverCallback]. If the owner has been collected, the broadcast is ignored and the
         * receiver will be unregistered by the next [registerOwnedReceiver] sweep for this key.
         */
        internal class WeakOwnerReceiver(
            owner: Any,
            private val registeredKey: String? = null,
            private val callback: OwnedReceiverCallback
        ) : BroadcastReceiver() {
            private val ownerRef = WeakReference(owner)

            override fun onReceive(context: Context, intent: Intent) = guarded {
                val owner = ownerRef.get()
                if (owner == null) {
                    cleanupIfOwnerGone(context)
                    return@guarded
                }
                callback.onReceive(this, owner, context, intent)
            }

            private fun cleanupIfOwnerGone(fallbackContext: Context) {
                // Remove this receiver from the ownedReceivers registry. Always try to unregister;
                // a previous cleanup may have failed or the registry may already be gone. Failure is
                // logged and ignored so the host process never crashes because the owner was
                // collected before the broadcast arrived.
                val receiver = this
                val registration = removeOwnedRegistration(receiver)

                // Prefer the Context that was used at registration time; fall back to the Context
                // supplied by the broadcast delivery.
                val context = registration?.contextRef?.get() ?: fallbackContext
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Throwable) {
                    // Already unregistered or Context is gone; the next broadcast will retry.
                }
            }

            private fun removeOwnedRegistration(receiver: BroadcastReceiver): OwnedReceiver? {
                val key = registeredKey
                return if (key != null) {
                    removeOwnedRegistrationForKey(key, receiver)
                } else {
                    // Fallback for receivers created without a key (unit tests).
                    removeOwnedRegistrationFallback(receiver)
                }
            }

            private fun removeOwnedRegistrationForKey(key: String, receiver: BroadcastReceiver): OwnedReceiver? {
                val removedRef = java.util.concurrent.atomic.AtomicReference<OwnedReceiver?>(null)
                ownedReceivers.compute(key) { _, list ->
                    val newList = list?.let { CopyOnWriteArrayList(it) }
                    val found = newList?.find { it.receiver === receiver }
                    if (found != null) {
                        newList.remove(found)
                        removedRef.set(found)
                    }
                    if (newList.isNullOrEmpty()) null else newList
                }
                return removedRef.get()
            }

            private fun removeOwnedRegistrationFallback(receiver: BroadcastReceiver): OwnedReceiver? {
                // Tests may create WeakOwnerReceiver directly without a key. In that case we still
                // need to find and remove the registration, but the list of keys is small.
                for ((key, _) in ownedReceivers) {
                    val found = removeOwnedRegistrationForKey(key, receiver)
                    if (found != null) return found
                }
                return null
            }
        }

        /**
         * Registers a weakly-owned receiver for [owner] and unregisters the receivers of owners that
         * have since been collected.
         *
         * Use this instead of [registerModuleReceiver] when several hook targets can legitimately
         * be alive at once — two clock controllers, one status bar per display — so a single
         * process-wide slot would silently disable all but the newest.
         *
         * The [OwnedReceiverCallback] must not capture the owner; it receives the owner (or nothing,
         * if it has been collected) as a parameter.
         */
        @JvmStatic
        @JvmOverloads
        fun registerOwnedReceiver(
            context: Context,
            owner: Any,
            key: String,
            filter: IntentFilter,
            flags: Int,
            permission: String? = null,
            callback: OwnedReceiverCallback
        ): BroadcastReceiver {
            val receiver = WeakOwnerReceiver(owner, key, callback)
            val newReg = OwnedReceiver(WeakReference(owner), WeakReference(context), receiver)

            // Atomically replace stale / same-owner registrations and add the new one.
            // No Android framework calls are made inside the remapping function.
            val removedRef = java.util.concurrent.atomic.AtomicReference<List<OwnedReceiver>>(emptyList())
            ownedReceivers.compute(key) { _, oldList ->
                val toRemove = ArrayList<OwnedReceiver>()
                val newList = CopyOnWriteArrayList<OwnedReceiver>()
                if (oldList != null) {
                    for (reg in oldList) {
                        val regOwner = reg.ownerRef.get()
                        // Keep live registrations that belong to a different owner. Remove stale
                        // owners (collected) and any previous registration for the same owner/key
                        // so the same hook target only has one receiver at a time.
                        if (regOwner != null && regOwner !== owner) {
                            newList.add(reg)
                        } else {
                            toRemove.add(reg)
                        }
                    }
                }
                newList.add(newReg)
                removedRef.set(toRemove)
                newList
            }

            // Unregister whatever the atomic update displaced. This is safe to do outside the
            // compute because the map already reflects the new state.
            for (reg in removedRef.get()) {
                releaseReceiver(reg.contextRef, reg.receiver)
            }

            return try {
                context.registerReceiver(receiver, filter, permission, null, flags)

                // Another thread may have replaced this same-owner registration while this thread
                // was inside registerReceiver. If newReg is no longer in the map, we are the loser
                // and must self-unregister so the winner is the only tracked receiver.
                val stillTracked = ownedReceivers[key]?.any { it === newReg } == true
                if (!stillTracked) {
                    try {
                        context.unregisterReceiver(receiver)
                    } catch (_: Throwable) {
                        // Already unregistered or Context is gone; the winner's cleanup has already
                        // handled it.
                    }
                }

                receiver
            } catch (t: Throwable) {
                XposedHelpers.log(t)
                // Framework registration failed; undo the map entry so we do not keep a dead
                // receiver around and can retry on the next hook.
                ownedReceivers.compute(key) { _, list ->
                    val newList = list?.let { CopyOnWriteArrayList(it) }
                    newList?.remove(newReg)
                    if (newList.isNullOrEmpty()) null else newList
                }
                receiver
            }
        }

        private class ModuleRegistration(
            val key: String,
            val cleanup: Runnable,
            val generation: Long = moduleRegistrationGeneration.incrementAndGet(),
            val state: AtomicReference<RegistrationState> = AtomicReference(RegistrationState.PENDING_REGISTER)
        )

        private val moduleRegistrations = ConcurrentHashMap<String, ModuleRegistration>()
        private val moduleRegistrationGeneration = AtomicLong(0)

        private const val MAX_STALE_MODULE_REGISTRATIONS = 3
        private val staleModuleRegistrations = ConcurrentHashMap<String, ConcurrentLinkedDeque<ModuleRegistration>>()

        /**
         * Records [cleanup] under [key] and runs whatever cleanup was recorded there before.
         *
         * The general form of [registerModuleReceiver], for registrations that are not broadcast
         * receivers — content observers, listeners added to ROM objects. Call it immediately after
         * registering, passing the action that undoes that registration.
         *
         * The same reason applies: a hook target that gets recreated cannot see the registration
         * its predecessor made, so per-instance cleanup silently accumulates live registrations.
         *
         * Replacement is two-stage: the new cleanup is installed first, the old cleanup is run,
         * and failed cleanups are tracked in a bounded stale queue for one retry on the next call.
         */
        @JvmStatic
        fun replaceModuleRegistration(key: String, cleanup: Runnable): Boolean {
            // Retry stale cleanups before touching the active slot.
            retryStaleModuleRegistrations(key)

            val newReg = ModuleRegistration(key, cleanup)

            val previous = moduleRegistrations.put(key, newReg) ?: run {
                newReg.state.set(RegistrationState.ACTIVE)
                return true
            }

            previous.state.set(RegistrationState.PENDING_UNREGISTER)
            if (!runModuleCleanup(previous)) {
                recordStaleModuleRegistration(key, previous)
            } else {
                previous.state.set(RegistrationState.RELEASED)
            }

            newReg.state.set(RegistrationState.ACTIVE)
            return true
        }

        private fun runModuleCleanup(reg: ModuleRegistration): Boolean {
            return try {
                reg.cleanup.run()
                true
            } catch (_: Throwable) {
                false
            }
        }

        private fun recordStaleModuleRegistration(key: String, reg: ModuleRegistration) {
            reg.state.set(RegistrationState.STALE)
            staleModuleRegistrations.compute(key) { _, queue ->
                val newQueue = ConcurrentLinkedDeque(queue ?: emptyList())
                if (newQueue.size >= MAX_STALE_MODULE_REGISTRATIONS) {
                    val oldest = newQueue.pollFirst()
                    if (oldest != null) {
                        if (runModuleCleanup(oldest)) {
                            oldest.state.set(RegistrationState.RELEASED)
                        } else {
                            oldest.state.set(RegistrationState.RELEASED)
                            HookDiagnostics.record(
                                processName(),
                                HookDiagnostics.Kind.RECEIVER,
                                "ModuleRegistrationRegistry",
                                reg.cleanup.javaClass.name,
                                key,
                                HookDiagnostics.Status.RECEIVER_STALE_DROPPED,
                                "stale cleanup evicted due to bounded queue",
                            )
                        }
                    }
                }
                newQueue.addLast(reg)
                HookDiagnostics.record(
                    processName(),
                    HookDiagnostics.Kind.RECEIVER,
                    "ModuleRegistrationRegistry",
                    reg.cleanup.javaClass.name,
                    key,
                    HookDiagnostics.Status.RECEIVER_UNREGISTER_FAILED,
                    "cleanup moved to stale queue",
                )
                newQueue
            }
        }

        private fun retryStaleModuleRegistrations(key: String) {
            staleModuleRegistrations.compute(key) { _, queue ->
                if (queue == null) return@compute null
                val stillStale = ConcurrentLinkedDeque<ModuleRegistration>()
                for (reg in queue) {
                    if (reg.state.get() == RegistrationState.RELEASED) continue
                    if (runModuleCleanup(reg)) {
                        reg.state.set(RegistrationState.RELEASED)
                    } else if (stillStale.size < MAX_STALE_MODULE_REGISTRATIONS) {
                        reg.state.set(RegistrationState.STALE)
                        stillStale.addLast(reg)
                    } else {
                        reg.state.set(RegistrationState.RELEASED)
                        HookDiagnostics.record(
                            processName(),
                            HookDiagnostics.Kind.RECEIVER,
                            "ModuleRegistrationRegistry",
                            reg.cleanup.javaClass.name,
                            key,
                            HookDiagnostics.Status.RECEIVER_STALE_DROPPED,
                            "stale cleanup dropped on retry due to bounded queue",
                        )
                    }
                }
                if (stillStale.isEmpty()) null else stillStale
            }
        }

        private fun releaseReceiver(contextRef: WeakReference<Context>, receiver: BroadcastReceiver) {
            val context = contextRef.get() ?: return
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Throwable) {
                // Already gone with its context, or never completed registration.
            }
        }

        /**
         * Exception handler for every coroutine scope the module runs inside a host process.
         *
         * A `SupervisorJob` stops one child's failure from cancelling its siblings; it does not
         * stop the failure itself. Without a handler an uncaught exception in `launch` reaches
         * the thread's default handler, which in SystemUI or Launcher means the process dies.
         * Attach this to the scope rather than wrapping each `launch` body, so a coroutine added
         * later cannot forget it.
         */
        @JvmField
        val coroutineFailureHandler: CoroutineExceptionHandler =
            CoroutineExceptionHandler { _, throwable -> XposedHelpers.log(throwable) }

        /**
         * Runs [block], logging instead of propagating any failure.
         *
         * Framework-invoked callbacks — `Handler.handleMessage`, `BroadcastReceiver.onReceive`,
         * `ContentObserver.onChange`, `Runnable.run` — execute outside the [MethodHook] try/catch.
         * A throw there kills system_server, SystemUI or Launcher, so every such body is wrapped.
         * The function is inline: no object is allocated and no frame is added on the hot path.
         */
        inline fun guarded(block: () -> Unit) {
            try {
                block()
            } catch (t: Throwable) {
                XposedHelpers.log(t)
            }
        }

        /**
         * [guarded] for a callback that has to return a value, such as `OnLongClickListener`.
         *
         * [fallback] is what the framework sees when the body fails, so it must be the answer
         * that leaves the host's own behavior intact — usually "not consumed".
         */
        inline fun <T> guarded(fallback: T, block: () -> T): T {
            return try {
                block()
            } catch (t: Throwable) {
                XposedHelpers.log(t)
                fallback
            }
        }

        /**
         * Broadcast identity helpers.
         *
         * Android 14 lets a sender share its package/UID with the receiver via
         * [BroadcastOptions.setShareIdentityEnabled]. Receivers that handle high-privilege
         * commands use [isTrustedBroadcast] to verify the sender package against an
         * action-specific whitelist. These functions are cold-path; one BroadcastOptions
         * instance is allocated per user-triggered broadcast.
         */

        @JvmStatic
        fun sendBroadcastWithIdentity(context: Context, intent: Intent, permission: String? = null) {
            val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
            context.sendBroadcast(intent, permission, options)
        }

        @JvmStatic
        fun sendOrderedBroadcastWithIdentity(
            context: Context,
            intent: Intent,
            permission: String? = null,
            resultReceiver: BroadcastReceiver? = null,
            scheduler: android.os.Handler? = null,
            initialCode: Int = 0,
            initialData: String? = null,
            initialExtras: Bundle? = null
        ) {
            val options = BroadcastOptions.makeBasic().setShareIdentityEnabled(true).toBundle()
            context.sendOrderedBroadcast(intent, permission, options, resultReceiver, scheduler, initialCode, initialData, initialExtras)
        }

        @JvmStatic
        fun isTrustedBroadcast(
            receiver: BroadcastReceiver,
            vararg allowedPackages: String,
            allowNull: Boolean = false,
            rejectionResultCode: Int? = null
        ): Boolean {
            val fromPackage = receiver.getSentFromPackage()
            if (fromPackage == null) return allowNull
            if (fromPackage !in allowedPackages) {
                if (rejectionResultCode != null && receiver.isOrderedBroadcast) {
                    receiver.setResultCode(rejectionResultCode)
                }
                return false
            }
            return true
        }

        @JvmStatic
        @Synchronized
        @JvmOverloads
        fun getModuleContext(context: Context, config: Configuration? = null): Context {
            if (mModuleContext == null) {
                mModuleContext = context.createPackageContext(Helpers.modulePkg, Context.CONTEXT_IGNORE_SECURITY)
            }
            return if (config == null) mModuleContext!! else mModuleContext!!.createConfigurationContext(config)
        }

        @JvmStatic
        @Synchronized
        fun getModuleRes(context: Context): Resources {
            val newConfig = context.resources.configuration
            if (cachedModuleRes != null && cachedModuleConfig == newConfig) {
                return cachedModuleRes!!
            }
            val config = Configuration(newConfig)
            val moduleContext = getModuleContext(context, config)
            cachedModuleRes = moduleContext.resources
            cachedModuleConfig = config
            return cachedModuleRes!!
        }

        @JvmStatic
        fun getDepInstance(classLoader: ClassLoader?, className: String): Any? {
            return ReflectionCache.getDepInstance(classLoader, className)
        }

        @JvmStatic
        fun getViewInfo(view: View?, key: String): Any? {
            if (view == null) return null
            val info = view.getTag(viewInfoTag)
            if (info == null) return null
            @Suppress("UNCHECKED_CAST")
            val viewInfo = info as HashMap<String, Any?>
            return viewInfo[key]
        }

        @JvmStatic
        fun setViewInfo(view: View?, key: String, value: Any?) {
            if (view == null) return
            val info = view.getTag(viewInfoTag)
            val viewInfo: HashMap<String, Any?> = if (info == null) {
                val newInfo = HashMap<String, Any?>()
                view.setTag(viewInfoTag, newInfo)
                newInfo
            } else {
                @Suppress("UNCHECKED_CAST")
                info as HashMap<String, Any?>
            }
            viewInfo[key] = value
        }

        @JvmStatic
        @Throws(PendingIntent.CanceledException::class)
        fun getFreeformOptions(mContext: Context, pkgName: String, pendingIntent: PendingIntent, ignoreCheck: Boolean): Bundle? {
            if (!ignoreCheck) {
                val foregroundInfo: ForegroundInfo? = ProcessManager.getForegroundInfo()
                if (foregroundInfo != null) {
                    val topPackage = foregroundInfo.mForegroundPackageName
                    if (pkgName == topPackage) return null
                }
                val freeFormStackInfoList = MiuiFreeFormManager.getAllFreeFormStackInfosOnDisplay(
                    mContext.display?.displayId ?: 0
                )
                val freeFormCount = freeFormStackInfoList?.size ?: 0
                if (freeFormCount == 2) return null
                freeFormStackInfoList?.forEach { rootTaskInfo ->
                    if (pkgName == rootTaskInfo.packageName) return null
                }
            }
            if (!pendingIntent.isActivity) {
                val bIntent = Intent(tv.withaibuild.customiuizer.mods.GlobalActions.ACTION_PREFIX + "SetFreeFormPackage")
                bIntent.putExtra("package", pkgName)
                bIntent.setPackage("android")
                mContext.sendBroadcast(bIntent)
            }
            val options: ActivityOptions? = MiuiMultiWindowUtils.getActivityOptions(mContext, pkgName, true, false)
            if (options != null) {
                XposedHelpers.callMethod(options, "setFreeformAnimation", false)
            }
            return options?.toBundle()
        }

        @JvmStatic
        fun getFreeformIntent(pkgName: String): Intent {
            val intent = Intent()
            if (pkgName != "com.tencent.tim") {
                XposedHelpers.callMethod(intent, "addFlags", 134217728)
                XposedHelpers.callMethod(intent, "addFlags", 268435456)
                XposedHelpers.callMethod(intent, "addMiuiFlags", 256)
            }
            return intent
        }

        /**
         * Resolves the first CPU thermal zone once per process.
         *
         * The zone list never changes at runtime, so the sysfs scan is memoized even when nothing
         * matches; otherwise the SystemUI monitor tick would reopen 19 sysfs files every 2 s.
         * Called from the NetworkSpeedController background handler thread only.
         */
        @JvmStatic
        fun getCPUThermalId(): Int {
            if (thermalIdScanned) return thermalId
            thermalIdScanned = true
            for (i in 2 until 40 step 2) {
                val sensorType = try {
                    RandomAccessFile("/sys/devices/virtual/thermal/thermal_zone$i/type", "r").use { it.readLine() }
                } catch (ign: Throwable) {
                    null
                }
                if (sensorType != null && (sensorType.startsWith("cpu-") || sensorType.startsWith("cpu_big"))) {
                    thermalId = i
                    break
                }
            }
            return thermalId
        }

        @JvmStatic
        fun replacePkgAndFrameworkValue(pkg: String, type: String, name: String, resValue: Any?) {
            if (pkg != "android") {
                MainModule.resHooks.setThemeValueReplacement("android", type, name, resValue)
            }
            MainModule.resHooks.setThemeValueReplacement(pkg, type, name, resValue)
        }

        @JvmStatic
        fun getObjectFieldByPath(target: Any?, path: String): Any? {
            if (target == null) return null
            var obj: Any? = target
            for (field in path.split('.')) {
                obj = getObjectFieldSilently(obj, field)
                if (obj == NOT_EXIST_SYMBOL) {
                    return NOT_EXIST_SYMBOL
                }
            }
            return obj
        }
    }
}
