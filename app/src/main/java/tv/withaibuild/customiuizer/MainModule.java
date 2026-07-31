package tv.withaibuild.customiuizer;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;
import tv.withaibuild.customiuizer.mods.GlobalActionSystemServerHooks;
import tv.withaibuild.customiuizer.mods.utils.HookDiagnostics;
import tv.withaibuild.customiuizer.mods.utils.PreferenceBootstrap;
import tv.withaibuild.customiuizer.mods.utils.ReflectionCache;
import tv.withaibuild.customiuizer.mods.utils.SystemServerInstaller;
import tv.withaibuild.customiuizer.mods.GlobalActions;
import tv.withaibuild.customiuizer.mods.System;
import tv.withaibuild.customiuizer.installers.AndroidPackageInstaller;
import tv.withaibuild.customiuizer.installers.GenericAppInstaller;
import tv.withaibuild.customiuizer.installers.GuardProviderInstaller;
import tv.withaibuild.customiuizer.installers.InputMethodInstaller;
import tv.withaibuild.customiuizer.installers.LauncherInstaller;
import tv.withaibuild.customiuizer.installers.MediaInstaller;
import tv.withaibuild.customiuizer.installers.PackageInstallerRouter;
import tv.withaibuild.customiuizer.installers.PhoneInstaller;
import tv.withaibuild.customiuizer.installers.PowerKeeperInstaller;
import tv.withaibuild.customiuizer.installers.SecurityCenterInstaller;
import tv.withaibuild.customiuizer.installers.SettingsInstaller;
import tv.withaibuild.customiuizer.installers.SystemUiInstaller;
import tv.withaibuild.customiuizer.mods.SystemUIStatusBarHooks;
import tv.withaibuild.customiuizer.mods.Various;
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.BeforeHookCallback;
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.MethodHook;
import tv.withaibuild.customiuizer.mods.utils.ModuleHelper;
import tv.withaibuild.customiuizer.mods.utils.ResourceHooks;
import tv.withaibuild.customiuizer.mods.utils.XposedHelpers;
import tv.withaibuild.customiuizer.utils.PrefMap;

public class MainModule extends XposedModule {

    public static final PrefMap mPrefs = new PrefMap();
    public static ResourceHooks resHooks = new ResourceHooks();
    String processName;

    private PreferenceBootstrap preferenceBootstrap;

    private static boolean mSystemServerLoadMarkerLogged = false;

    @Override
    public void onModuleLoaded(@NonNull XposedModuleInterface.ModuleLoadedParam param) {
        processName = param.getProcessName();
        HookDiagnostics.currentProcessName = processName;
        XposedHelpers.moduleInst = this;
        // Stamp the build into every process's log. Without it a captured LSPosed log
        // cannot be told apart from one produced by a different build of the same
        // version, which is the first thing anyone reading the log needs to know.
        // Once per process, on the coldest path there is.
        XposedHelpers.log("CustoMIUIzer " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE
                + ") loaded in " + processName);

        preferenceBootstrap = PreferenceBootstrap.create(mPrefs, new PreferenceBootstrap.RemotePreferenceSource() {
            @Override
            public SharedPreferences get(String name) {
                return getRemotePreferences(name);
            }
        });
    }

    private boolean isSupportedAndroidVersion() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true;
        }
        XposedHelpers.log("HyperOS 1 A14 build disabled on Android API " + Build.VERSION.SDK_INT);
        return false;
    }

    /**
     * Single transaction to load the remote preference snapshot into the process-local {@link PrefMap}.
     *
     * The real state machine now lives in {@link PreferenceBootstrap}.  This wrapper returns whether
     * the snapshot is ready for hook-installation decisions.
     */
    private boolean initPrefs() {
        if (preferenceBootstrap != null) {
            preferenceBootstrap.bootstrap();
            return preferenceBootstrap.isReady();
        }
        return false;
    }

    public static void loadDexKit() {
        try {
            java.lang.System.loadLibrary("dexkit");
        } catch (Throwable t) {
            HookDiagnostics.recordDexKit("dexkit", "loadLibrary", t.getClass().getName());
            XposedHelpers.log(t);
            throw t;
        }
    }

    @Override
    public void onSystemServerStarting(final SystemServerStartingParam lpparam) {
        if (!isSupportedAndroidVersion()) return;
        ModuleHelper.currentPackageName = "android";
        if (processName == null) {
            processName = "system_server";
            HookDiagnostics.currentProcessName = processName;
        }
        if (!mSystemServerLoadMarkerLogged) {
            mSystemServerLoadMarkerLogged = true;
            XposedHelpers.log("CustoMIUIzer " + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ") loaded in " + processName);
        }
        boolean prefReady = initPrefs();
        if (!prefReady) {
            HookDiagnostics.recordPreferencesMissed("android", preferenceBootstrap.getState().name());
        }
        SystemServerInstaller.install(lpparam, prefReady);
        HookDiagnostics.printSummaryForStage("onSystemServerStarting");
    }

    @Override
    public void onPackageReady(final PackageReadyParam lpparam) {
        if (!isSupportedAndroidVersion()) return;
        if (!lpparam.isFirstPackage()) return;

        String pkg = lpparam.getPackageName();
        if (
            pkg.equals("com.android.settings") && !"com.android.settings".equals(processName)
            || pkg.equals("com.miui.securitycenter") && "com.miui.securitycenter.bootaware".equals(processName)
            || pkg.equals("com.android.location.fused")
            || pkg.startsWith("com.android.networkstack")
        ) {
            return;
        }

        ModuleHelper.currentPackageName = lpparam.getPackageName();
        boolean prefReady = initPrefs();
        if (!prefReady) {
            HookDiagnostics.recordPreferencesMissed(pkg, preferenceBootstrap.getState().name());
        }

        if (pkg.equals("android")) {
            AndroidPackageInstaller.install(lpparam, mPrefs);
        }

        if (pkg.equals("com.baidu.input")
            || pkg.equals("com.baidu.input_mi")
            || pkg.equals("com.iflytek.inputmethod")
            || pkg.equals("com.iflytek.inputmethod.miui")
            || pkg.equals("com.sohu.inputmethod.sogou")
            || pkg.equals("com.sohu.inputmethod.sogou.xiaomi")
            || pkg.startsWith("com.google.android.inputmethod")
            || pkg.startsWith("com.touchtype.swiftkey")
            || pkg.startsWith("com.tencent.wetype")
        ) {
            InputMethodInstaller.install(lpparam, mPrefs);
            HookDiagnostics.printSummaryForStage("onPackageReady");
            return;
        }

        if (mPrefs.getInt("system_statusbarheight", 11) > 11) {
            System.StatusBarHeightHook(lpparam);
        }

        if (mPrefs.getBoolean("various_alarmcompat") && mPrefs.getStringSet("various_alarmcompat_apps").contains(pkg)) {
            Various.AlarmCompatHook();
        }

        if (pkg.equals("com.miui.miwallpaper")
            || pkg.equals("com.miui.screenshot")
            || pkg.equals("com.miui.gallery")) {
            MediaInstaller.install(lpparam, mPrefs);
        }
        if (pkg.equals("com.android.systemui")) {
            ReflectionCache.onSafeLifecycle(lpparam.getClassLoader());

            // 1. The SystemUIInitializer.init hook is always installed first. It is the only place
            // where we can safely obtain a live Context and finish context-dependent init.
            final boolean[] fastRebootReceiverReady = { false };
            final boolean[] statusBarSetupDone = { false };
            final boolean[] preferenceWatchDone = { false };

            MethodHook initStatusBarHook = new MethodHook() {
                private boolean isHooked = false;
                @Override
                protected void before(final BeforeHookCallback param) throws Throwable {
                    if (isHooked || param.getThisObject() == null) return;

                    Object mContextField;
                    try {
                        mContextField = XposedHelpers.getObjectField(param.getThisObject(), "mContext");
                    } catch (Throwable t) {
                        XposedHelpers.log(t);
                        return;
                    }
                    if (!(mContextField instanceof Context)) {
                        XposedHelpers.log("MainModule: SystemUI mContext field is not a Context");
                        return;
                    }
                    Context context = (Context) mContextField;
                    if (context == null) {
                        XposedHelpers.log("MainModule: SystemUI mContext is null in SystemUIInitializer.init, deferring context-dependent init");
                        return;
                    }

                    try {
                        if (!fastRebootReceiverReady[0]) {
                            fastRebootReceiverReady[0] = GlobalActionSystemServerHooks.setupFastRebootReceiver(context);
                        }
                        if (!statusBarSetupDone[0]) {
                            SystemUIStatusBarHooks.setupStatusBar(context);
                            statusBarSetupDone[0] = true;
                        }
                        if (!preferenceWatchDone[0]) {
                            preferenceWatchDone[0] = initPrefs();
                        }
                        if (fastRebootReceiverReady[0] && statusBarSetupDone[0] && preferenceWatchDone[0]) {
                            isHooked = true;
                            HookDiagnostics.printSummaryForStage("post-init");
                        }
                    } catch (Throwable t) {
                        XposedHelpers.log(t);
                        // Do not set isHooked: one failed init step must not mark the whole pass as complete.
                    }
                }
            };

            ModuleHelper.findAndHookMethod("com.android.systemui.SystemUIInitializer", lpparam.getClassLoader(),
                "init", boolean.class, initStatusBarHook);

            // 2. Base hooks whose original install timing must never be skipped by the 10s restart check.
            Context mContext = ModuleHelper.findContext(lpparam);
            if (mContext != null) {
                if (!fastRebootReceiverReady[0]) {
                    fastRebootReceiverReady[0] = GlobalActionSystemServerHooks.setupFastRebootReceiver(mContext);
                }
            } else {
                XposedHelpers.log("MainModule: SystemUI context not ready at package ready, deferring FastReboot receiver");
            }
            if (GlobalActions.hasCustomActions()) GlobalActionSystemServerHooks.setupStatusBar(lpparam);

            // 3. The 10s restart check is only allowed to skip the non-essential hooks below.
            boolean skipNonEssential = false;
            if (mContext != null) {
                try {
                    long restartTime = Settings.System.getLong(mContext.getContentResolver(), "systemui_restart_time", 0L);
                    long currentTime = java.lang.System.currentTimeMillis();
                    if (currentTime - restartTime < 10000) skipNonEssential = true;
                } catch (Throwable t) {
                    XposedHelpers.log(t);
                }
            }

            if (skipNonEssential) {
                HookDiagnostics.printSummaryForStage("onPackageReady");
                return;
            }

            SystemUiInstaller.install(lpparam, mPrefs);
        }

        if (pkg.equals("com.miui.guardprovider")) {
            GuardProviderInstaller.install(lpparam, mPrefs);
        }

        if (pkg.equals("com.android.incallui")) {
            PhoneInstaller.install(lpparam, mPrefs);
        }

        if (pkg.equals("com.miui.securitycenter")) {
            SecurityCenterInstaller.install(lpparam, mPrefs);
        }

        if (pkg.equals("com.miui.powerkeeper")) {
            PowerKeeperInstaller.install(lpparam, mPrefs);
        }

        if (pkg.equals("com.android.settings")) {
            SettingsInstaller.install(lpparam, mPrefs);
        }

        if (pkg.equals("com.miui.packageinstaller")) {
            PackageInstallerRouter.install(lpparam, mPrefs);
        }

        final boolean isLauncherPkg = pkg.equals("com.miui.home");

        if (isLauncherPkg) {
            ReflectionCache.onSafeLifecycle(lpparam.getClassLoader());
            LauncherInstaller.install(lpparam, mPrefs);
            initPrefs();
        }

        final boolean isStatusBarColor = mPrefs.getBoolean("system_statusbarcolor") && mPrefs.getStringSet("system_statusbarcolor_apps").contains(pkg);
        final boolean isNoOverscroll = mPrefs.getBoolean("system_nooverscroll") && mPrefs.getStringSet("system_nooverscroll_apps").contains(pkg);
        final boolean controlMedia = (mPrefs.getStringAsInt("controls_volumemedia_up", 0) > 0
            || mPrefs.getStringAsInt("controls_volumemedia_down", 0) > 0) && mPrefs.getStringSet("controls_mediaplayer_apps").contains(pkg);
        if (isLauncherPkg || isStatusBarColor || isNoOverscroll || controlMedia) {
            GenericAppInstaller.installPostAttach(lpparam, mPrefs, isLauncherPkg, isStatusBarColor, isNoOverscroll, controlMedia);
        }

        HookDiagnostics.printSummaryForStage("onPackageReady");
    }

}
