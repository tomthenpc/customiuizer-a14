package tv.withaibuild.customiuizer;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Build;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.Set;

import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;
import tv.withaibuild.customiuizer.mods.Controls;
import tv.withaibuild.customiuizer.mods.GlobalActionSystemServerHooks;
import tv.withaibuild.customiuizer.mods.utils.HookDiagnostics;
import tv.withaibuild.customiuizer.mods.GlobalActions;
import tv.withaibuild.customiuizer.mods.LauncherGestureHooks;
import tv.withaibuild.customiuizer.mods.LauncherIconHooks;
import tv.withaibuild.customiuizer.mods.LauncherFolderHooks;
import tv.withaibuild.customiuizer.mods.LauncherLayoutHooks;
import tv.withaibuild.customiuizer.mods.LauncherAnimationHooks;
import tv.withaibuild.customiuizer.mods.Launcher;
import tv.withaibuild.customiuizer.mods.PackagePermissions;
import tv.withaibuild.customiuizer.mods.SystemSecurityHooks;
import tv.withaibuild.customiuizer.mods.SystemShareMenuHooks;
import tv.withaibuild.customiuizer.mods.SystemLockScreenHooks;
import tv.withaibuild.customiuizer.mods.SystemNotificationHooks;
import tv.withaibuild.customiuizer.mods.SystemAudioHooks;
import tv.withaibuild.customiuizer.mods.SystemDisplayHooks;
import tv.withaibuild.customiuizer.mods.SystemWindowHooks;
import tv.withaibuild.customiuizer.mods.SystemClockHooks;
import tv.withaibuild.customiuizer.mods.SystemStatusBarIconHooks;
import tv.withaibuild.customiuizer.mods.SystemStatusBarBackgroundHooks;
import tv.withaibuild.customiuizer.mods.SystemColorizeNotificationHooks;
import tv.withaibuild.customiuizer.mods.System;
import tv.withaibuild.customiuizer.mods.SystemUIScreenshotHooks;
import tv.withaibuild.customiuizer.mods.SystemUILockScreenHooks;
import tv.withaibuild.customiuizer.mods.SystemUINotificationHooks;
import tv.withaibuild.customiuizer.mods.SystemUIControlCenterHooks;
import tv.withaibuild.customiuizer.mods.SystemUIStatusBarHooks;
import tv.withaibuild.customiuizer.mods.SystemUI;
import tv.withaibuild.customiuizer.mods.Various;
import tv.withaibuild.customiuizer.mods.utils.HookerClassHelper.AfterHookCallback;
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

    SharedPreferences remotePrefs;

    OnSharedPreferenceChangeListener mListener;

    private enum PrefsState { UNINITIALIZED, LOADED, VALID_EMPTY, UNAVAILABLE }
    private static PrefsState mPrefsState = PrefsState.UNINITIALIZED;
    private static boolean mPrefsWatcherRegistered = false;
    private static boolean mValidEmptyReported = false;
    private static boolean mUnavailableReported = false;
    private static int mPrefsInitAttempts = 0;
    private static final int MAX_PREF_INIT_ATTEMPTS = 5;
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
    }

    private boolean isSupportedAndroidVersion() {
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return true;
        }
        XposedHelpers.log("HyperOS 1 A14 build disabled on Android API " + Build.VERSION.SDK_INT);
        return false;
    }

    /**
     * Loads the remote preference snapshot into the process-local {@link PrefMap}.
     *
     * <p>The provider is distinguished from an empty-but-valid configuration:
     * <ul>
     *   <li>{@code null} or thrown from {@link RemotePreferences#getAll()} means the provider is
     *       not ready ({@link PrefsState#UNAVAILABLE}).</li>
     *   <li>An empty, non-null map means the user has no custom settings yet
     *       ({@link PrefsState#VALID_EMPTY}); this is a real state, not a failure.</li>
     * </ul>
     * Retries are bounded and do not sleep the caller's thread.</p>
     */
    private void initPrefs() {
        if (mPrefsState == PrefsState.LOADED || mPrefsState == PrefsState.VALID_EMPTY) return;
        if (mPrefsState == PrefsState.UNAVAILABLE && mPrefsInitAttempts >= MAX_PREF_INIT_ATTEMPTS) return;

        mPrefsInitAttempts++;
        if (remotePrefs == null) {
            try {
                remotePrefs = getRemotePreferences(ModuleHelper.prefsName + "_remote");
            } catch (Throwable t) {
                mPrefsState = PrefsState.UNAVAILABLE;
                HookDiagnostics.recordPreferencesUnavailable(t.getClass().getName(), "getRemotePreferences");
                return;
            }
        }
        if (remotePrefs == null) {
            mPrefsState = PrefsState.UNAVAILABLE;
            HookDiagnostics.recordPreferencesUnavailable("", "getRemotePreferences returned null");
            return;
        }
        Map<String, ?> allPrefs;
        try {
            allPrefs = remotePrefs.getAll();
        } catch (Throwable t) {
            mPrefsState = PrefsState.UNAVAILABLE;
            HookDiagnostics.recordPreferencesUnavailable(t.getClass().getName(), "getAll");
            return;
        }
        if (allPrefs == null) {
            mPrefsState = PrefsState.UNAVAILABLE;
            if (!mUnavailableReported) {
                mUnavailableReported = true;
                XposedHelpers.log("Remote preferences unavailable: getAll returned null");
            }
            return;
        }
        if (allPrefs.isEmpty()) {
            mPrefsState = PrefsState.VALID_EMPTY;
            if (!mValidEmptyReported) {
                mValidEmptyReported = true;
                XposedHelpers.log("Remote preferences are valid but empty");
            }
            return;
        }
        mPrefs.putAll(allPrefs);
        mPrefsState = PrefsState.LOADED;
    }

    private void loadDexKit() {
        try {
            java.lang.System.loadLibrary("dexkit");
        } catch (Throwable t) {
            HookDiagnostics.recordDexKit("dexkit", "loadLibrary", t.getClass().getName());
            XposedHelpers.log(t);
            throw t;
        }
    }

    private void watchPreferenceChange() {
        if (mPrefsWatcherRegistered) return;
        mListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, @Nullable String key) {
                if (sharedPreferences == null || key == null) return;
                try {
                    Object val;
                    if (sharedPreferences.contains(key)) {
                        Object oldVal = mPrefs.get(key);
                        if (oldVal instanceof Boolean) {
                            val = sharedPreferences.getBoolean(key, false);
                        } else if (oldVal instanceof Integer) {
                            val = sharedPreferences.getInt(key, 0);
                        } else if (oldVal instanceof Long) {
                            val = sharedPreferences.getLong(key, 0L);
                        } else if (oldVal instanceof Float) {
                            val = sharedPreferences.getFloat(key, 0f);
                        } else if (oldVal instanceof String) {
                            val = sharedPreferences.getString(key, null);
                        } else if (oldVal instanceof Set) {
                            val = sharedPreferences.getStringSet(key, null);
                        } else {
                            val = sharedPreferences.getAll().get(key);
                        }
                    } else {
                        val = null;
                    }
                    if (val == null) {
                        mPrefs.remove(key);
                    }
                    else {
                        mPrefs.put(key, val);
                    }
                    if (!"pref_key_systemui_restart_time".equals(key)) {
                        ModuleHelper.handlePreferenceChanged(key);
                    }
                } catch (Throwable t) {
                    // A failed preference update must not take down the host process.
                    XposedHelpers.log(t);
                }
            }
        };
        initPrefs();
        if (remotePrefs == null) {
            try {
                remotePrefs = getRemotePreferences(ModuleHelper.prefsName + "_remote");
            } catch (Throwable t) {
                HookDiagnostics.recordPreferencesUnavailable(t.getClass().getName(), "getRemotePreferences");
                return;
            }
        }
        if (remotePrefs == null) {
            HookDiagnostics.recordPreferencesUnavailable("", "getRemotePreferences returned null");
            return;
        }
        try {
            remotePrefs.registerOnSharedPreferenceChangeListener(mListener);
            mPrefsWatcherRegistered = true;
        } catch (Throwable t) {
            HookDiagnostics.recordPreferencesUnavailable(t.getClass().getName(), "registerOnSharedPreferenceChangeListener");
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
        initPrefs();
        PackagePermissions.hook(lpparam);
        if (GlobalActions.hasCustomActions()) GlobalActionSystemServerHooks.setupGlobalActions(lpparam);

        if (mPrefs.getBoolean("system_screenshot_overlay")) {
            SystemWindowHooks.TempHideOverlayAppHook(lpparam);
        }

        if (mPrefs.getBoolean("system_notify_openinfw")
            || mPrefs.getBoolean("system_fw_forcein_actionsend")
            || mPrefs.getBoolean("system_betterpopups_allowfloat")
            || mPrefs.getBoolean("system_cc_freeform_when_longclick")
        ) {
            SystemWindowHooks.OpenAppInFreeFormHook(lpparam);
        }

        if (mPrefs.getInt("controls_backlong_action", 1) > 1 ||
            mPrefs.getInt("controls_homelong_action", 1) > 1 ||
            mPrefs.getInt("controls_menulong_action", 1) > 1) Controls.NavBarActionsHook(lpparam);
        if (mPrefs.getInt("controls_powerdt_action", 1) > 1 || mPrefs.getBoolean("controls_volumedowndt_torch")) Controls.PowerDoubleTapActionHook(lpparam);
        if (mPrefs.getInt("system_screenanim_duration", 0) > 0) SystemDisplayHooks.ScreenAnimHook(lpparam);
        if (mPrefs.getInt("system_applock_timeout", 1) > 1) SystemLockScreenHooks.AppLockTimeoutHook(lpparam);
        if (mPrefs.getInt("system_dimtime", 0) > 0) SystemDisplayHooks.ScreenDimTimeHook(lpparam);
        if (mPrefs.getInt("system_toasttime", 0) > 0) System.ToastTimeHook(lpparam);
        if (mPrefs.getBoolean("system_removesecure")) SystemSecurityHooks.RemoveSecureHook(lpparam);
        if (mPrefs.getBoolean("system_remove_startactconfirm")) SystemSecurityHooks.RemoveActStartConfirmHook(lpparam);
        if (mPrefs.getBoolean("system_securelock")) SystemLockScreenHooks.EnhancedSecurityHook(lpparam);
        if (mPrefs.getBoolean("system_downgrade")) SystemSecurityHooks.NoVersionCheckHook(lpparam);
        if (mPrefs.getBoolean("system_orientationlock")) SystemWindowHooks.OrientationLockHook(lpparam);
        if (mPrefs.getBoolean("system_noducking")) SystemAudioHooks.NoDuckingHook(lpparam);
        if (mPrefs.getBoolean("system_cleanshare")) SystemShareMenuHooks.CleanShareMenuServiceHook(lpparam);
        if (mPrefs.getBoolean("system_cleanopenwith")) SystemShareMenuHooks.CleanOpenWithMenuServiceHook(lpparam);
        if (mPrefs.getBoolean("system_autobrightness")) SystemDisplayHooks.AutoBrightnessRangeHook(lpparam);
        if (mPrefs.getBoolean("system_autobrightness_reset_when_screenoff")) SystemDisplayHooks.AutoBrightnessAfterScreenOffHook(lpparam);
        if (mPrefs.getBoolean("system_lockscreen_disable_strongauth_72h")) SystemLockScreenHooks.Disable72hStrongAuthHook(lpparam);
        if (mPrefs.getBoolean("system_applock")) SystemLockScreenHooks.AppLockHook(lpparam);
        if (mPrefs.getBoolean("system_applock_skip")) SystemLockScreenHooks.SkipAppLockHook(lpparam);
        if (mPrefs.getBoolean("various_alarmcompat")) Various.AlarmCompatServiceHook(lpparam);
        if (mPrefs.getBoolean("system_ignorecalls")) SystemAudioHooks.NoCallInterruptionHook(lpparam);
        if (mPrefs.getBoolean("system_forceclose")) System.ForceCloseHook(lpparam);
        if (mPrefs.getBoolean("system_hideproxywarn")) System.HideProximityWarningHook(lpparam);
        if (mPrefs.getBoolean("system_firstpress")) SystemAudioHooks.FirstVolumePressHook(lpparam);
        if (mPrefs.getBoolean("system_apksign")) SystemSecurityHooks.NoSignatureVerifyServiceHook(lpparam);
        if (mPrefs.getBoolean("system_disableintegrity")) SystemSecurityHooks.DisableSystemIntegrityHook(lpparam);
        if (mPrefs.getBoolean("system_vibration_amp")) SystemAudioHooks.MuffledVibrationHook(lpparam);
        if (mPrefs.getBoolean("system_clearalltasks")) System.ClearAllTasksHook(lpparam);
        if (mPrefs.getBoolean("system_force_darken_allapps")) SystemDisplayHooks.ForceDarkAllAppsHook(lpparam);
        if (mPrefs.getBoolean("system_lswallpaper")) SystemLockScreenHooks.SetLockscreenWallpaperHook(lpparam);
        if (mPrefs.getBoolean("controls_powerflash")) Controls.PowerKeyHook(lpparam);
        if (mPrefs.getBoolean("controls_fingerprintfailure")) Controls.FingerprintHapticFailureHook(lpparam);
        if (mPrefs.getBoolean("controls_fingerprintscreen")) Controls.FingerprintScreenOnHook(lpparam);
        if (mPrefs.getBoolean("controls_fingerprintwake")) Controls.NoFingerprintWakeHook(lpparam);
        if (mPrefs.getBoolean("various_disableapp")) Various.AppsDisableServiceHook(lpparam);
        if (mPrefs.getBoolean("system_disableanynotif")) SystemNotificationHooks.DisableAnyNotificationBlockHook(lpparam);
        if (mPrefs.getStringAsInt("system_allrotations2", 1) > 1) SystemWindowHooks.AllRotationsHook(lpparam);
        if (mPrefs.getStringAsInt("system_nolightuponcharges", 1) == 2) SystemDisplayHooks.NoLightUpOnChargeHook(lpparam);
        if (mPrefs.getStringAsInt("system_vibration", 1) > 1) SystemAudioHooks.SelectiveVibrationHook(lpparam);
        if (mPrefs.getStringAsInt("system_blocktoasts", 1) > 1) System.SelectiveToastsHook(lpparam);
        if (mPrefs.getStringAsInt("controls_fingerprintsuccess", 1) > 1) Controls.FingerprintHapticSuccessHook(lpparam);
        if (mPrefs.getStringAsInt("controls_volumemedia_up", 0) > 0 ||
            mPrefs.getStringAsInt("controls_volumemedia_down", 0) > 0) Controls.VolumeMediaButtonsHook(lpparam);

        if (mPrefs.getBoolean("system_fw_splitscreen")) SystemWindowHooks.MultiWindowPlusHook(lpparam);
        if (mPrefs.getBoolean("system_fw_noblacklist")) SystemWindowHooks.NoFloatingWindowBlacklistHook(lpparam);
        if (mPrefs.getBoolean("various_disable_access_devicelogs")) {
            SystemSecurityHooks.NoAccessDeviceLogsRequest(lpparam);
        }
        if (mPrefs.getInt("system_other_wallpaper_scale", 6) > 6) SystemDisplayHooks.WallpaperScaleLevelHook(lpparam);
        if (mPrefs.getBoolean("various_allow_untrusted_touch")) SystemWindowHooks.AllowUntrustedTouchHook(lpparam);

        watchPreferenceChange();
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
        initPrefs();

        if (pkg.equals("android")) {
            if (mPrefs.getBoolean("system_cleanshare")) SystemShareMenuHooks.CleanShareMenuHook(lpparam);
            if (mPrefs.getBoolean("system_cleanopenwith")) SystemShareMenuHooks.CleanOpenWithMenuHook(lpparam);
            if (mPrefs.getStringAsInt("system_allrotations2", 1) > 1) {
                MainModule.resHooks.setThemeValueReplacement("android", "bool", "config_allowAllRotations", mPrefs.getStringAsInt("system_allrotations2", 1) == 2);
            }
            watchPreferenceChange();
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
            if (mPrefs.getBoolean("controls_volumecursor")) Controls.VolumeCursorHook(lpparam);
            if (mPrefs.getBoolean("controls_nonavbar_fix_inputmethod")
                && mPrefs.getBoolean("controls_nonavbar")) {
                Various.FixInputMethodBottomMarginHook(lpparam);
            }
            if (pkg.startsWith("com.google.android.inputmethod")) {
                if (mPrefs.getInt("various_gboardpadding_port", 0) > 0 || mPrefs.getInt("various_gboardpadding_land", 0) > 0) Various.GboardPaddingHook(lpparam);
            }
            HookDiagnostics.printSummaryForStage("onPackageReady");
            return;
        }

        if (mPrefs.getInt("system_statusbarheight", 11) > 11) {
            System.StatusBarHeightHook(lpparam);
        }

        if (mPrefs.getBoolean("various_alarmcompat") && mPrefs.getStringSet("various_alarmcompat_apps").contains(pkg)) {
            Various.AlarmCompatHook();
        }

        if (pkg.equals("com.miui.miwallpaper")) {
            if (mPrefs.getBoolean("launcher_disable_wallpaperscale")) LauncherAnimationHooks.DisableUnlockWallpaperScale(lpparam);
        }
        if (pkg.equals("com.android.systemui")) {
            Context mContext = ModuleHelper.findContext(lpparam);
            final boolean[] fastRebootReceiverReady = { false };
            long currentTime = java.lang.System.currentTimeMillis();

            if (mContext != null) {
                GlobalActionSystemServerHooks.setupFastRebootReceiver(mContext);
                fastRebootReceiverReady[0] = true;
                long restartTime = Settings.System.getLong(mContext.getContentResolver(), "systemui_restart_time", 0L);
                if (currentTime - restartTime < 10000) {
                    HookDiagnostics.printSummaryForStage("onPackageReady");
                    return;
                }
            } else {
                XposedHelpers.log("MainModule: SystemUI context not ready at package ready, deferring FastReboot receiver");
            }

            MethodHook initStatusBarHook = new MethodHook() {
                private boolean isHooked = false;
                @Override
                protected void before(final BeforeHookCallback param) throws Throwable {
                    if (!isHooked && param.getThisObject() != null) {
                        isHooked = true;
                        Context context = (Context) XposedHelpers.getObjectField(param.getThisObject(), "mContext");
                        if (!fastRebootReceiverReady[0] && context != null) {
                            GlobalActionSystemServerHooks.setupFastRebootReceiver(context);
                            fastRebootReceiverReady[0] = true;
                        }
                        if (context != null) {
                            SystemUIStatusBarHooks.setupStatusBar(context);
                            watchPreferenceChange();
                        }
                        HookDiagnostics.printSummaryForStage("post-init");
                    }
                }
            };

            ModuleHelper.findAndHookMethod("com.android.systemui.SystemUIInitializer", lpparam.getClassLoader(),
                "init", boolean.class, initStatusBarHook);
            if (GlobalActions.hasCustomActions()) GlobalActionSystemServerHooks.setupStatusBar(lpparam);

            if (mPrefs.getStringAsInt("various_showcallui", 0) > 0
                || mPrefs.getBoolean("controls_volumecursor")
            ) GlobalActions.setupForegroundMonitor(lpparam);

            if (mPrefs.getBoolean("system_screenshot_overlay")) {
                SystemUIScreenshotHooks.TempHideOverlaySystemUIHook(lpparam);
            }

            if (
                mPrefs.getBoolean("system_fivegtile")
                || mPrefs.getBoolean("system_cc_fpstile")
                || mPrefs.getBoolean("system_cc_floatingtimetile")
            ) {
                SystemUIControlCenterHooks.AddCustomTileHook(lpparam);
            }

            if (mPrefs.getBoolean("system_hidestatusbar_whenscreenshot")) {
                SystemUIScreenshotHooks.HideStatusBarWhenCaptureHook(lpparam);
            }

            if (mPrefs.getBoolean("system_networkindicator_wifi")) System.NetworkIndicatorWifi(lpparam);

            if (mPrefs.getInt("system_drawer_blur", 100) < 100) SystemDisplayHooks.DrawerBlurRatioHook(lpparam);
            if (mPrefs.getInt("system_chargeanimtime", 20) < 20) SystemDisplayHooks.ChargeAnimationHook(lpparam);
            if (mPrefs.getInt("system_betterpopups_delay", 0) > 0 && !mPrefs.getBoolean("system_betterpopups_nohide")) SystemNotificationHooks.BetterPopupsHideDelayHook(lpparam);
            if (mPrefs.getInt("controls_fsg_assist_left_action", 1) > 1
                || mPrefs.getInt("controls_fsg_assist_right_action", 1) > 1
            ) Controls.AssistGestureActionHook(lpparam);
            if (mPrefs.getInt("controls_navbarleft_action", 1) > 1 ||
                    mPrefs.getInt("controls_navbarleftlong_action", 1) > 1 ||
                    mPrefs.getInt("controls_navbarright_action", 1) > 1 ||
                    mPrefs.getInt("controls_navbarrightlong_action", 1) > 1) Controls.NavBarButtonsHook(lpparam);
            if (mPrefs.getBoolean("system_scramblepin")) SystemLockScreenHooks.ScramblePINHook(lpparam);
            if (mPrefs.getBoolean("system_dttosleep")) SystemLockScreenHooks.DoubleTapToSleepHook(lpparam);
            if (mPrefs.getBoolean("system_statusbar_clocktweak")
                || mPrefs.getBoolean("system_cc_clocktweak")
                || mPrefs.getBoolean("system_cc_hidedate")
                || mPrefs.getBoolean("system_drawer_hidedate")
                || mPrefs.getBoolean("system_statusbaricons_clock")
                || mPrefs.getString("system_cc_dateformat", "").length() > 0
                || mPrefs.getString("system_drawer_dateformat", "").length() > 0
            ) SystemClockHooks.StatusBarClockTweakHook(lpparam);
            if (mPrefs.getBoolean("system_cc_clocktweak")
                || mPrefs.getBoolean("system_qs_force_systemfonts")
            ) SystemClockHooks.CCClockTweakHook(lpparam);
            if (mPrefs.getBoolean("system_qs_disable_fakeclock_anim")) {
                SystemUIStatusBarHooks.DisableFakeClockAnimHook(lpparam);
            }
            if (
                mPrefs.getBoolean("system_cc_clock_centeralign")
                || (!mPrefs.getBoolean("system_drawer_hidedate") && mPrefs.getBoolean("system_drawer_date_centeralign"))
            ) SystemClockHooks.CCClockCenterAlignHook(lpparam);
            if (mPrefs.getBoolean("system_noscreenlock_act")) SystemLockScreenHooks.NoScreenLockHook(lpparam);
            if (mPrefs.getBoolean("system_albumartonlock")) SystemUILockScreenHooks.LockScreenAlbumArtHook(lpparam);
            if (mPrefs.getStringAsInt("system_expandheadups", 1) > 1) SystemNotificationHooks.ExpandHeadsUpHook(lpparam);
            if (mPrefs.getBoolean("system_betterpopups_nohide")) SystemNotificationHooks.BetterPopupsNoHideHook(lpparam);
            if (mPrefs.getBoolean("system_betterpopups_center")) SystemNotificationHooks.BetterPopupsCenteredHook(lpparam);
            if (mPrefs.getBoolean("system_notifafterunlock")) SystemLockScreenHooks.ShowNotificationsAfterUnlockHook(lpparam);
            if (mPrefs.getBoolean("system_notifrowmenu")) SystemNotificationHooks.NotificationRowMenuHook(lpparam);
            if (mPrefs.getBoolean("system_removedismiss")) SystemUINotificationHooks.HideDismissViewHook(lpparam);
            if (mPrefs.getBoolean("system_drawer_removeshortcut")) SystemUINotificationHooks.HideNoficationAccessIconHook(lpparam);
            if (mPrefs.getBoolean("system_drawer_remove_emptynotify")) SystemUINotificationHooks.HideNoNotificationsHook(lpparam);
            if (mPrefs.getBoolean("controls_nonavbar")) Controls.HideNavBarHook(lpparam);
            else if (mPrefs.getBoolean("controls_hidenavbar_whenscreenshot")) SystemUIScreenshotHooks.HideNavBarBeforeScreenshotHook(lpparam);
            if (mPrefs.getBoolean("system_visualizer")) SystemAudioHooks.AudioVisualizerHook(lpparam);
            if (SystemUIControlCenterHooks.hasControlCenterModifications()) SystemUIControlCenterHooks.ControlCenterPluginHook(lpparam);
            if (mPrefs.getBoolean("system_batteryindicator")) SystemUIStatusBarHooks.BatteryIndicatorHook(lpparam);
            if (mPrefs.getBoolean("system_disableanynotif")) SystemNotificationHooks.DisableAnyNotificationHook(lpparam);
            if (mPrefs.getBoolean("system_lockscreenshortcuts")) SystemUILockScreenHooks.LockScreenShortcutHook(lpparam);
            if (mPrefs.getBoolean("system_4gtolte")
                || (mPrefs.getBoolean("system_statusbar_mobiletype_single")
                && !mPrefs.getString("system_statusbar_mobile_showname", "").equals(""))
            ) SystemUIStatusBarHooks.MobileNetworkTypeHook(lpparam);

            boolean dualRows = mPrefs.getBoolean("system_statusbar_dualrows");
            boolean netspeedAtRow2 = dualRows && mPrefs.getBoolean("system_statusbar_netspeed_atsecondrow");
            boolean showBatteryDetail = mPrefs.getBoolean("system_statusbar_batterytempandcurrent");
            boolean showDeviceTemp = mPrefs.getBoolean("system_statusbar_showdevicetemperature");
            boolean batteryAtRight = showBatteryDetail && !dualRows && mPrefs.getBoolean("system_statusbar_batterytempandcurrent_atright");
            boolean tempAtRight = showDeviceTemp && !dualRows && mPrefs.getBoolean("system_statusbar_showdevicetemperature_atright");
            boolean batteryAtLeft = showBatteryDetail && !mPrefs.getBoolean("system_statusbar_batterytempandcurrent_atright");
            boolean tempAtLeft = showDeviceTemp && !mPrefs.getBoolean("system_statusbar_showdevicetemperature_atright");

            boolean alwaysShowAtRight = mPrefs.getBoolean("system_statusbar_alarm_atright")
                || mPrefs.getBoolean("system_statusbar_nfc_atright")
                || mPrefs.getBoolean("system_statusbar_btbattery_atright")
                || mPrefs.getBoolean("system_statusbar_headset_atright")
                || mPrefs.getBoolean("system_statusbar_vpn_atright")
                || batteryAtRight || tempAtRight;
            boolean moveLeft = mPrefs.getBoolean("system_statusbar_alarm_atleft")
                || mPrefs.getBoolean("system_statusbar_sound_atleft")
                || mPrefs.getBoolean("system_statusbar_netspeed_atleft")
                || mPrefs.getBoolean("system_statusbar_dnd_atleft")
                || mPrefs.getBoolean("system_statusbar_gps_atleft")
                || mPrefs.getBoolean("system_statusbaricons_wifi_mobile_atleft")
                || batteryAtLeft || tempAtLeft;
            if (alwaysShowAtRight || moveLeft
                || netspeedAtRow2
                || mPrefs.getBoolean("system_statusbaricons_swap_wifi_mobile")
            ) {
                SystemUIStatusBarHooks.StatusBarIconsPositionAdjustHook(lpparam, moveLeft);
            }
            if (showBatteryDetail || showDeviceTemp) {
                SystemUIStatusBarHooks.MonitorDeviceInfoHook(lpparam, mPrefs);
            }
            if (mPrefs.getStringAsInt("system_statusbar_clock_position", 1) > 1 && !dualRows) {
                SystemUIStatusBarHooks.StatusBarClockPositionHook(lpparam);
            }
            if (mPrefs.getBoolean("system_statusbar_batterystyle")) {
                SystemUIStatusBarHooks.StatusBarStyleBatteryIconHook(lpparam);
            }
            if (mPrefs.getBoolean("system_statusbar_topmargin") && mPrefs.getBoolean("system_statusbar_topmargin_unset_lockscreen")) SystemUILockScreenHooks.LockScreenTopMarginHook(lpparam);
            if (mPrefs.getBoolean("system_statusbar_horizmargin")) SystemUIStatusBarHooks.HorizMarginHook(lpparam);
            if (mPrefs.getBoolean("system_showpct")) SystemUIControlCenterHooks.BrightnessPctHook(lpparam);
            if (mPrefs.getBoolean("system_hidelsstatusbar")) SystemLockScreenHooks.HideLockScreenStatusBarHook(lpparam);
            if (mPrefs.getBoolean("system_hidelsclock")) SystemLockScreenHooks.HideLockScreenClockHook(lpparam);
            if (mPrefs.getBoolean("system_ls_force_systemfonts")) SystemUIStatusBarHooks.ForceClockUseSystemFontsHook(lpparam);
            if (mPrefs.getBoolean("system_hidelshint")) SystemLockScreenHooks.HideLockScreenHintHook(lpparam);
            if (mPrefs.getBoolean("system_allownotifonkeyguard")) SystemLockScreenHooks.AllowAllKeyguardHook(lpparam);
            if (mPrefs.getBoolean("system_allownotiffloat")) SystemWindowHooks.AllowAllFloatHook(lpparam);
            if (mPrefs.getBoolean("system_lsalarm")) SystemLockScreenHooks.LockScreenAlarmHook(lpparam);
            if (mPrefs.getBoolean("system_statusbarcontrols")) SystemUIControlCenterHooks.StatusBarGesturesHook(lpparam);
            if (mPrefs.getInt("system_netspeedinterval", 4) != 4) SystemUIStatusBarHooks.NetSpeedIntervalHook(lpparam);
            if (mPrefs.getStringAsInt("system_detailednetspeed_style", 1) > 1) SystemUIStatusBarHooks.DetailedNetSpeedHook(lpparam);
            if (mPrefs.getStringAsInt("system_detailednetspeed_style", 1) > 1
                || mPrefs.getBoolean("system_netspeed_boldfont")
                || mPrefs.getBoolean("system_netspeed_use_clock_style")
                || mPrefs.getInt("system_netspeed_fontsize", 13) > 13
                || mPrefs.getInt("system_netspeed_fixedcontent_width", 10) > 10
                || mPrefs.getInt("system_netspeed_leftmargin", 0) > 0
                || mPrefs.getInt("system_netspeed_rightmargin", 0) > 0
                || mPrefs.getInt("system_netspeed_verticaloffset", 8) != 8
                || mPrefs.getStringAsInt("system_detailednetspeed_align", 1) > 1
            ) {
                SystemUIStatusBarHooks.NetSpeedStyleHook(lpparam);
            }
            if (mPrefs.getBoolean("system_taptounlock")) SystemLockScreenHooks.TapToUnlockHook(lpparam);
            if (mPrefs.getBoolean("system_nosos")) SystemLockScreenHooks.NoSOSHook(lpparam);
            if (mPrefs.getBoolean("system_morenotif")) SystemUINotificationHooks.RemovePackageNotificationsLimitHook(lpparam);
            if (mPrefs.getBoolean("system_notif_disable_fold")) SystemUINotificationHooks.DisableFoldNotificationsHook(lpparam);
            if (mPrefs.getBoolean("system_notif_disable_strong_toast")) SystemUI.DisableStrongToastHook(lpparam);
//            if (mPrefs.getInt("system_notif_strong_toast_width", 100) < 100) SystemUI.TweakStrongToastHook(lpparam);
            if (mPrefs.getBoolean("system_charginginfo")) SystemLockScreenHooks.ChargingInfoHook(lpparam);
            if (mPrefs.getBoolean("system_secureqs")) SystemUIControlCenterHooks.SecureQSTilesHook(lpparam);
            if (mPrefs.getBoolean("system_mutevisiblenotif")) SystemNotificationHooks.MuteVisibleNotificationsHook(lpparam);
            if (mPrefs.getBoolean("system_statusbaricons_battery1")) SystemStatusBarIconHooks.HideIconsBattery1Hook(lpparam);
            if (mPrefs.getBoolean("system_statusbaricons_battery3")
                || mPrefs.getBoolean("system_statusbaricons_battery4")
                || mPrefs.getBoolean("system_statusbaricons_battery2")
            ) SystemStatusBarIconHooks.HideIconsBattery2Hook(lpparam);
            if (mPrefs.getStringAsInt("system_statusbaricons_wifistandard", 1) > 1) SystemStatusBarIconHooks.DisplayWifiStandardHook(lpparam);
            if (mPrefs.getBoolean("system_statusbaricons_privacy_prompt")) SystemUIStatusBarHooks.HidePrivacyIndicatorHook(lpparam);
            if (mPrefs.getBoolean("system_statusbaricons_signal")
                || mPrefs.getBoolean("system_statusbaricons_sim1")
                || mPrefs.getBoolean("system_statusbaricons_sim2")
                || mPrefs.getBoolean("system_statusbaricons_sim_nodata")
                || mPrefs.getBoolean("system_statusbaricons_roaming")
                || mPrefs.getBoolean("system_statusbaricons_volte")
            ) SystemUIStatusBarHooks.HideIconsSignalHook(lpparam);
            if (mPrefs.getBoolean("system_statusbaricons_vowifi")) SystemUIStatusBarHooks.HideIconsVoWiFiHook(lpparam);
            if (!mPrefs.getBoolean("system_statusbaricons_alarm") && mPrefs.getInt("system_statusbaricons_alarmn", 0) > 0) SystemStatusBarIconHooks.HideIconsSelectiveAlarmHook(lpparam);
            if (!mPrefs.getString("system_shortcut_app", "").equals("")
                || !mPrefs.getString("system_calendar_app", "").equals("")
                || !mPrefs.getString("system_clock_app", "").equals("")) SystemUILockScreenHooks.ReplaceShortcutAppHook(lpparam);
            if (mPrefs.getStringAsInt("system_qshaptics", 1) > 1) SystemAudioHooks.QSHapticHook(lpparam);
            if (mPrefs.getBoolean("system_cc_collapse_after_clicked")) SystemUIControlCenterHooks.CollapseCCAfterClickHook(lpparam);
            if (mPrefs.getBoolean("system_cc_freeform_when_longclick")) SystemUIControlCenterHooks.LongClickTileOpenInFreeFormHook(lpparam);
            if (mPrefs.getBoolean("system_cc_switch_qsandnotification")) SystemUIControlCenterHooks.SwitchCCAndNotificationHook(lpparam);
            if (mPrefs.getStringAsInt("system_expandnotifs", 1) > 1) SystemNotificationHooks.ExpandNotificationsHook(lpparam);
            if (mPrefs.getStringAsInt("system_mobiletypeicon", 1) > 1
                || mPrefs.getBoolean("system_networkindicator_mobile")
                || mPrefs.getBoolean("system_statusbar_mobiletype_show_wificonnected")
            ) {
                SystemUIStatusBarHooks.HideMobileNetworkIndicatorHook(lpparam);
            }
            if (mPrefs.getBoolean("system_epm")) SystemUI.ExtendedPowerMenuHook(lpparam);

            boolean hideIconsActive =
                mPrefs.getBoolean("system_statusbaricons_wifi") ||
                mPrefs.getBoolean("system_statusbaricons_dualwifi") ||
                mPrefs.getBoolean("system_statusbaricons_alarm") ||
                mPrefs.getBoolean("system_statusbaricons_profile") ||
                mPrefs.getBoolean("system_statusbaricons_sound") ||
                mPrefs.getBoolean("system_statusbaricons_dnd") ||
                mPrefs.getBoolean("system_statusbaricons_secondspace") ||
                mPrefs.getBoolean("system_statusbaricons_headset") ||
                mPrefs.getBoolean("system_statusbaricons_nfc") ||
                mPrefs.getBoolean("system_statusbaricons_vpn") ||
                mPrefs.getBoolean("system_statusbaricons_airplane") ||
                mPrefs.getBoolean("system_statusbaricons_hotspot") ||
                mPrefs.getBoolean("system_statusbaricons_nosims") ||
                mPrefs.getBoolean("system_statusbaricons_gps") ||
                mPrefs.getBoolean("system_statusbaricons_btbattery") ||
                mPrefs.getBoolean("system_statusbaricons_ble_unlock") ||
                mPrefs.getBoolean("system_statusbaricons_bluetoothicn") ||
                mPrefs.getBoolean("system_statusbaricons_volte");
            if (hideIconsActive) SystemUIStatusBarHooks.HideIconsHook(lpparam);

            if (
                mPrefs.getBoolean("system_statusbaricons_privacy")
                || mPrefs.getBoolean("system_statusbaricons_mute")
                || mPrefs.getBoolean("system_statusbaricons_speaker")
                || mPrefs.getBoolean("system_statusbaricons_record")
                || mPrefs.getBoolean("system_statusbaricons_wireless_headset")
            ) SystemUIStatusBarHooks.HideIconsFromSystemManager(lpparam);
            if (mPrefs.getBoolean("system_betterpopups_allowfloat")) SystemWindowHooks.BetterPopupsAllowFloatHook(lpparam);
            if (mPrefs.getBoolean("system_betterpopups_autoclose_expanded")) SystemNotificationHooks.AutoDismissExpandedPopupsHook(lpparam);
            if (mPrefs.getBoolean("system_betterpopups_disablewhenmute")) SystemUINotificationHooks.DisableHeadsUpWhenMuteHook(lpparam);
            if (mPrefs.getBoolean("system_minimalnotifview")) SystemNotificationHooks.MinimalNotificationViewHook(lpparam);
            if (mPrefs.getBoolean("system_notifchannelsettings")) SystemNotificationHooks.NotificationChannelSettingsHook(lpparam);
            if (mPrefs.getStringAsInt("system_maxsbicons", 0) != 0) SystemNotificationHooks.MaxNotificationIconsHook(lpparam);
            if (mPrefs.getBoolean("system_statusbar_mobiletype_single")) {
                SystemUIStatusBarHooks.MobileTypeSingleHook(lpparam);
            }
            if (mPrefs.getBoolean("system_statusbar_mobile_digital_signal")) {
                SystemUIStatusBarHooks.StatusBarDigitalSignalHook(lpparam);
            }
            else if (mPrefs.getBoolean("system_statusbar_dualsimin2rows")) {
                SystemUIStatusBarHooks.DualRowSignalHook(lpparam);
            }
            if (dualRows) {
                SystemUIStatusBarHooks.DualRowsStatusbarHook(lpparam);
            }
            if (mPrefs.getStringAsInt("system_colorizenotifs", 1) > 1) SystemColorizeNotificationHooks.ColorizeNotificationCardHook(lpparam);
            if (mPrefs.getBoolean("system_notify_openinfw")) SystemUINotificationHooks.OpenNotifyInFloatingWindowHook(lpparam);
            if (mPrefs.getBoolean("system_fw_noblacklist")) SystemWindowHooks.DisableSideBarSuggestionHook(lpparam);

            if (mPrefs.getBoolean("system_nosafevolume")) {
                SystemUIControlCenterHooks.HideSafeVolumeDlgHook(lpparam);
            }
            if (mPrefs.getBoolean("system_lockscreen_hidezenmode")) {
                SystemUILockScreenHooks.HideLockscreenZenModeHook(lpparam);
            }
            if (mPrefs.getBoolean("system_lockscreen_disable_edit")) {
                SystemUILockScreenHooks.DisableKeyguardEditorHook(lpparam);
            }
            if (mPrefs.getBoolean("system_nopassword")) SystemLockScreenHooks.NoPasswordHook(lpparam);

            if (mPrefs.getBoolean("system_notifimportance")) {
                SystemUINotificationHooks.NotificationImportanceHook(lpparam);
            }
            if (mPrefs.getStringAsInt("system_nolightuponcharges", 1) > 1) SystemUI.NoLightUpOnChargeHook(lpparam);
        }

        if (pkg.equals("com.miui.guardprovider")) {
            if (mPrefs.getBoolean("various_disable_defraud_apps_detect")) {
                try {
                    loadDexKit();
                    XposedHelpers.createBridge(lpparam.getApplicationInfo().sourceDir);
                    Various.DisableDefraudAppsCheck(lpparam);
                } catch (Throwable t) {
                    XposedHelpers.log(t);
                } finally {
                    XposedHelpers.closeBridge();
                }
            }
        }

        if (pkg.equals("com.android.incallui")) {
            if (mPrefs.getStringAsInt("various_showcallui", 0) > 0) Various.ShowCallUIHook(lpparam);
            if (mPrefs.getBoolean("various_calluibright")) Various.InCallBrightnessHook(lpparam);
            if (mPrefs.getBoolean("various_answerinheadup")) Various.AnswerCallInHeadUpHook(lpparam);
        }

        if (pkg.equals("com.miui.securitycenter")) {
            if (mPrefs.getBoolean("various_appdetails")) Various.AppInfoHook(lpparam);
            if (mPrefs.getBoolean("various_disableapp")) Various.AppsDisableHook(lpparam);
            if (mPrefs.getBoolean("various_restrictapp")) Various.AppsRestrictHook(lpparam);
            if (mPrefs.getBoolean("various_hide_report_ondetails")) Various.HideReportButtonHook(lpparam);
            if (mPrefs.getBoolean("system_applock_scramblepin")) SystemLockScreenHooks.ScrambleAppLockPINHook(lpparam);
            if (mPrefs.getStringAsInt("various_appsort", 1) > 1) Various.AppsDefaultSortHook(lpparam);
            if (mPrefs.getBoolean("various_skip_interceptperm")) Various.InterceptPermHook(lpparam);
            if (mPrefs.getBoolean("various_replace_defaultopen_with_openbydefault")) Various.OpenByDefaultHook(lpparam);
            if (mPrefs.getBoolean("various_skip_securityscan")) Various.SkipSecurityScanHook(lpparam);
            if (mPrefs.getBoolean("various_show_battery_temperature")) Various.ShowTempInBatteryHook(lpparam);
            if (mPrefs.getBoolean("various_disable_freeform_suggest_blacklist")) SystemWindowHooks.DisableSideBarSuggestionHook(lpparam);
            if (mPrefs.getBoolean("various_disable_dock_suggest")) Various.DisableDockSuggestHook(lpparam);
            if ("com.miui.securitycenter:ui".equals(processName)
                && mPrefs.getBoolean("various_enable_expand_sidebar")) {
                Various.AddSideBarExpandReceiverHook(lpparam);
            }
            if (mPrefs.getBoolean("system_hidelowbatwarn")) {
                Various.NoLowBatteryWarningHook();
            }
            if (mPrefs.getBoolean("various_privacyapps_column_nums4")) {
                Various.PrivacyAppsLayoutHook(lpparam);
            }
            if (mPrefs.getBoolean("various_disable_reset_recents_privacy_blur")) {
                Various.PersistPrivacyThumbnailBlur(lpparam);
            }
        }

        if (pkg.equals("com.miui.powerkeeper")) {
            if (mPrefs.getBoolean("various_restrictapp")) Various.AppsRestrictPowerHook(lpparam);
            if (mPrefs.getBoolean("various_persist_batteryoptimization")) Various.PersistBatteryOptimizationHook(lpparam);
        }

        if (pkg.equals("com.android.settings")) {
            if (mPrefs.getStringAsInt("miuizer_settingsiconpos", 1) > 0) {
                GlobalActions.miuizerSettingsHook(lpparam);
            }
            if (mPrefs.getBoolean("system_disableanynotif")) {
                SystemNotificationHooks.DisableAnyNotificationHook(lpparam);
                SystemNotificationHooks.DisableAnyNotificationBlockHook(lpparam);
            }
            if (mPrefs.getBoolean("system_notifimportance")) {
                SystemNotificationHooks.NotificationImportanceHook(lpparam);
            }
            if (mPrefs.getBoolean("system_wifipassword")) {
                System.ViewWifiPasswordHook(lpparam);
            }
        }

        if (pkg.equals("com.miui.packageinstaller")) {
            if (mPrefs.getBoolean("various_miuiinstaller")) Various.MiuiPackageInstallerHook(lpparam);
            if (mPrefs.getBoolean("various_installappinfo")) Various.AppInfoDuringMiuiInstallHook(lpparam);
            if (mPrefs.getBoolean("various_installer_purify")) Various.PurePackageInstallerHook(lpparam);
        }

        if (pkg.equals("com.miui.screenshot")) {
            if (mPrefs.getBoolean("system_screenshot")) {
                try {
                    loadDexKit();
                    XposedHelpers.createBridge(lpparam.getApplicationInfo().sourceDir);
                    System.ScreenshotConfigHook(lpparam);
                } catch (Throwable t) {
                    XposedHelpers.log(t);
                } finally {
                    XposedHelpers.closeBridge();
                }
            }
        }

        if (pkg.equals("com.miui.gallery")) {
            int folder = mPrefs.getStringAsInt("system_gallery_screenshots_path", 1);
            if (folder > 1) {
                System.GalleryScreenshotPathHook(lpparam);
            }
        }

        final boolean isLauncherPkg = pkg.equals("com.miui.home");

        if (isLauncherPkg) {
            int folderCols = mPrefs.getInt("launcher_folder_cols", 1);
            if (folderCols > 1) LauncherFolderHooks.FolderColumnsRes(folderCols);
            if (mPrefs.getInt("launcher_horizmargin", 0) > 0) LauncherLayoutHooks.HorizontalSpacingRes();
            if (mPrefs.getInt("launcher_indicatorheight", 9) > 9) LauncherLayoutHooks.IndicatorHeightRes();
            if (mPrefs.getInt("launcher_indicator_topmargin", 0) > 0) LauncherLayoutHooks.IndicatorMarginTopHook(lpparam);
            if (mPrefs.getBoolean("launcher_unlockgrids")) {
                LauncherLayoutHooks.UnlockGridsRes();
                LauncherLayoutHooks.UnlockGridsHook(lpparam);
            }
            if (mPrefs.getBoolean("launcher_docktitles")) LauncherIconHooks.ShowHotseatTitlesHook(lpparam);
            if (mPrefs.getBoolean("launcher_disable_log")) {
                Launcher.DisableLauncherLogHook(lpparam);
            }
            if (mPrefs.getInt("launcher_topmargin", 0) > 0) LauncherLayoutHooks.WorkspaceCellPaddingTopHook(lpparam);
            if (mPrefs.getInt("launcher_dock_topmargin", 0) > 0) LauncherLayoutHooks.DockMarginTopHook(lpparam);
            if (mPrefs.getInt("launcher_dock_bottommargin", 0) > 0) LauncherLayoutHooks.DockMarginBottomHook(lpparam);
            if (mPrefs.getInt("launcher_dock_height", 60) > 60) LauncherLayoutHooks.DockHeightHook(lpparam);
            if (mPrefs.getBoolean("launcher_privacyapps_gest")) Launcher.setupLauncher(lpparam);
            watchPreferenceChange();
        }

        final boolean isStatusBarColor = mPrefs.getBoolean("system_statusbarcolor") && mPrefs.getStringSet("system_statusbarcolor_apps").contains(pkg);
        final boolean isNoOverscroll = mPrefs.getBoolean("system_nooverscroll") && mPrefs.getStringSet("system_nooverscroll_apps").contains(pkg);
        final boolean controlMedia = (mPrefs.getStringAsInt("controls_volumemedia_up", 0) > 0
            || mPrefs.getStringAsInt("controls_volumemedia_down", 0) > 0) && mPrefs.getStringSet("controls_mediaplayer_apps").contains(pkg);
        if (isLauncherPkg || isStatusBarColor || isNoOverscroll || controlMedia) {
            ModuleHelper.findAndHookMethod(Application.class, "attach", Context.class, new MethodHook() {
                @Override
                protected void after(AfterHookCallback param) throws Throwable {
                    if (isLauncherPkg) handleLoadLauncher(lpparam);
                    if (isStatusBarColor) {
                        SystemStatusBarBackgroundHooks.StatusBarBackgroundCompatHook(lpparam);
                        SystemStatusBarBackgroundHooks.StatusBarBackgroundHook(lpparam);
                    }
                    if (isNoOverscroll) SystemWindowHooks.NoOverscrollAppHook(lpparam);
                    if (controlMedia) Controls.VolumeMediaPlayerHook(lpparam);
                    HookDiagnostics.printSummaryForStage("post-attach");
                }
            });
        }

        HookDiagnostics.printSummaryForStage("onPackageReady");
    }

    private void handleLoadLauncher(final PackageReadyParam lpparam) {
        boolean closeOnLaunch = false;
        if (mPrefs.getInt("launcher_swipedown_action", 1) != 1 ||
                mPrefs.getInt("launcher_swipeup_action", 1) != 1 ||
                mPrefs.getInt("launcher_swipedown2_action", 1) != 1 ||
                mPrefs.getInt("launcher_swipeup2_action", 1) != 1) LauncherGestureHooks.HomescreenSwipesHook(lpparam);
        if (mPrefs.getInt("launcher_swipeleft_action", 1) != 1 ||
                mPrefs.getInt("launcher_swiperight_action", 1) != 1) LauncherGestureHooks.HotSeatSwipesHook(lpparam);
        if (mPrefs.getInt("launcher_shake_action", 1) != 1) LauncherGestureHooks.ShakeHook(lpparam);
        if (mPrefs.getInt("launcher_doubletap_action", 1) != 1) LauncherGestureHooks.LauncherDoubleTapHook(lpparam);
        if (mPrefs.getInt("launcher_pinch_action", 1) != 1) LauncherGestureHooks.LauncherPinchHook(lpparam);
        if (mPrefs.getInt("launcher_folder_cols", 1) > 1) LauncherFolderHooks.FolderColumnsHook(lpparam);
        if (mPrefs.getInt("launcher_iconscale", 45) > 45) LauncherIconHooks.IconScaleHook(lpparam);
        if (mPrefs.getInt("launcher_titlefontsize", 5) > 5) LauncherIconHooks.TitleFontSizeHook(lpparam);
        if (mPrefs.getInt("launcher_titletopmargin", 0) > 0) LauncherIconHooks.TitleTopMarginHook(lpparam);
        if (mPrefs.getBoolean("launcher_noclockhide")) LauncherIconHooks.NoClockHideHook(lpparam);
        if (mPrefs.getBoolean("launcher_renameapps")) LauncherIconHooks.RenameShortcutsHook(lpparam);
        if (mPrefs.getBoolean("launcher_darkershadow")) LauncherIconHooks.TitleShadowHook(lpparam);
        if (mPrefs.getBoolean("controls_nonavbar")) Launcher.HideNavBarHook(lpparam);
        if (mPrefs.getBoolean("launcher_infinitescroll")) LauncherLayoutHooks.InfiniteScrollHook(lpparam);
        if (mPrefs.getBoolean("launcher_hidetitles")) LauncherIconHooks.HideTitlesHook(lpparam);
        if (mPrefs.getBoolean("launcher_fixlaunch")) Launcher.FixAppInfoLaunchHook(lpparam);
        if (mPrefs.getBoolean("launcher_nowidgetonly")) LauncherLayoutHooks.NoWidgetOnlyHook(lpparam);
        if (mPrefs.getBoolean("launcher_sensorportrait")) Launcher.ReverseLauncherPortraitHook(lpparam);
        if (mPrefs.getBoolean("launcher_unlockhotseat")) LauncherLayoutHooks.MaxHotseatIconsCountHook(lpparam);
        if (mPrefs.getStringAsInt("launcher_closefolders", 1) > 1) { LauncherFolderHooks.CloseFolderOnLaunchHook(lpparam); closeOnLaunch = true; }
        if (mPrefs.getInt("system_recents_blur", 100) < 100) Launcher.RecentsBlurRatioHook(lpparam);
        if (mPrefs.getInt("controls_fsg_coverage", 60) != 60) Controls.BackGestureAreaHeightHook(lpparam);
        if (mPrefs.getInt("controls_fsg_width", 100) > 100) Controls.BackGestureAreaWidthHook(lpparam);
        if (mPrefs.getBoolean("controls_fsg_horiz")) LauncherGestureHooks.FSGesturesHook(lpparam);
        if (mPrefs.getBoolean("system_removecleaner")) System.HideMemoryCleanHook(lpparam, true);
        if (mPrefs.getBoolean("system_recents_disable_wallpaperscale") || mPrefs.getBoolean("launcher_disable_wallpaperscale")) LauncherAnimationHooks.DisableLauncherWallpaperScale(lpparam);
        if (mPrefs.getBoolean("system_recents_hide_statusbar")) Launcher.HideStatusBarInRecentsHook(lpparam);
        if (mPrefs.getBoolean("system_fw_splitscreen")) SystemWindowHooks.MultiWindowPlusHook(lpparam);
        if (mPrefs.getBoolean("launcher_fixanim")) LauncherAnimationHooks.FixAnimHook(lpparam);
        if (mPrefs.getBoolean("launcher_hideseekpoints")) LauncherLayoutHooks.HideSeekPointsHook(lpparam);
        if (mPrefs.getBoolean("launcher_privacyapps_gest")
            || mPrefs.getInt("launcher_spread_action", 1) != 1) LauncherFolderHooks.PrivacyFolderHook(lpparam);
        if (mPrefs.getBoolean("system_hidefromrecents")) Launcher.HideFromRecentsHook(lpparam);
        if (mPrefs.getInt("launcher_folderblur_opacity", 0) > 0) LauncherFolderHooks.FolderBlurHook(lpparam);
        if (mPrefs.getBoolean("launcher_nounlockanim")) LauncherAnimationHooks.NoUnlockAnimationHook(lpparam);
        if (mPrefs.getBoolean("launcher_nozoomanim")) LauncherAnimationHooks.NoZoomAnimationHook(lpparam);
        if (mPrefs.getBoolean("launcher_oldlaunchanim")) LauncherAnimationHooks.UseOldLaunchAnimationHook(lpparam);
        if (mPrefs.getBoolean("launcher_closedrawer")) { LauncherFolderHooks.CloseDrawerOnLaunchHook(lpparam); closeOnLaunch = true; }
        if (mPrefs.getInt("launcher_horizwidgetmargin", 0) > 0) LauncherLayoutHooks.HorizontalWidgetSpacingHook(lpparam);
        if (mPrefs.getInt("controls_fsg_assist_left_action", 1) > 1
            || mPrefs.getInt("controls_fsg_assist_right_action", 1) > 1
        )  LauncherGestureHooks.AssistGestureActionHook(lpparam);
        if (mPrefs.getInt("controls_fsg_swipeandstop_action", 1) > 1) LauncherGestureHooks.SwipeAndStopActionHook(lpparam);
        if (closeOnLaunch) LauncherFolderHooks.CloseFolderOrDrawerOnLaunchShortcutMenuHook(lpparam);
        if (mPrefs.getBoolean("system_resizablewidgets")) LauncherLayoutHooks.ResizableWidgetsHook(lpparam);
        if (mPrefs.getStringAsInt("launcher_wallpaper_colormode", 1) > 1) LauncherAnimationHooks.WallpaperColorModeHook(lpparam);
    }

}
